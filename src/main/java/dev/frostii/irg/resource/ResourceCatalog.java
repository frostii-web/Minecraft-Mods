package dev.frostii.irg.resource;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import dev.frostii.irg.InfiniteResourceGenerators;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class ResourceCatalog {
    private static final TagKey<Item> ORE_TAG = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "ores"));
    private static Map<ResourceLocation, Entry> entries = Map.of();
    private static long revision;

    public record Entry(ResourceLocation id, boolean immediate) { }
    private ResourceCatalog() { }

    /** Runs after KubeJS/server datapacks have populated and modified world generation. */
    public static void rebuild(MinecraftServer server) {
        Set<Holder<Biome>> biomes = new HashSet<>();
        Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Set<Holder<Biome>>> dimensionBiomes = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Set<Holder<Biome>> possible = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
            dimensionBiomes.put(level.dimension(), possible);
            biomes.addAll(possible);
        }
        Set<Item> naturalBlocks = new HashSet<>();
        Set<Material> naturalMaterials = new HashSet<>();

        for (GTOreDefinition vein : GTRegistries.ORE_VEINS.values()) {
            if (vein.veinGenerator() == null || vein.density() <= 0 || vein.clusterSize().getMaxValue() <= 0) continue;
            if (vein.dimensionFilter() == null || vein.dimensionFilter().stream()
                    .flatMap(key -> dimensionBiomes.getOrDefault(key, Set.of()).stream())
                    .noneMatch(b -> vein.isForBiome(b) && vein.weightForBiome(b) > 0)) continue;
            for (var ore : vein.veinGenerator().getAllEntries()) {
                if (ore.chance() <= 0) continue;
                Material material = ore.mapToMaterial();
                if (!material.isNull()) naturalMaterials.add(material);
                ItemStack stack = new ItemStack(ore.mapToBlockState().getBlock());
                if (ResourcePolicy.ore(stack)) naturalBlocks.add(stack.getItem());
            }
        }

        // Only traverse placed features actually referenced by this world's biomes, not all registered ore items.
        Set<PlacedFeature> placedFeatures = new HashSet<>();
        for (Holder<Biome> biome : biomes) {
            for (var step : biome.value().getGenerationSettings().features()) {
                for (var placed : step) placedFeatures.add(placed.value());
            }
        }
        for (PlacedFeature placed : placedFeatures) {
            placed.getFeatures().forEach(configured -> {
                if (configured.config() instanceof OreConfiguration ore) {
                    for (var target : ore.targetStates) {
                        ItemStack stack = new ItemStack(target.state.getBlock());
                        if (stack.is(ORE_TAG) && ResourcePolicy.ore(stack)) naturalBlocks.add(stack.getItem());
                    }
                }
            });
        }

        Map<ResourceLocation, Entry> built = new TreeMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = item.getDefaultInstance();
            if (!ResourcePolicy.plain(stack)) continue;
            MaterialEntry material = ChemicalHelper.getMaterialEntry(item);
            boolean natural = naturalBlocks.contains(item) || (!material.isEmpty()
                    && ResourcePolicy.ORES.contains(material.tagPrefix())
                    && naturalMaterials.contains(material.material()) && ResourcePolicy.ore(stack));
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null && (natural || ResourcePolicy.processed(stack))) built.put(id, new Entry(id, natural));
        }
        entries = Collections.unmodifiableMap(built);
        revision++;
        InfiniteResourceGenerators.LOGGER.info("Resource catalog: {} naturally available ores, {} locked material forms",
                entries.values().stream().filter(Entry::immediate).count(), entries.values().stream().filter(e -> !e.immediate()).count());
    }

    public static Collection<Entry> entries() { return entries.values(); }
    public static long revision() { return revision; }
    public static Entry get(ResourceLocation id) { return entries.get(id); }
    public static boolean isAvailable(ServerLevel level, ResourceLocation id) {
        Entry entry = entries.get(id);
        return entry != null && (entry.immediate() || WorldUnlocks.get(level).contains(id));
    }
    public static ItemStack stack(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = item.getDefaultInstance();
        stack.setCount(1);
        return ResourcePolicy.plain(stack) ? stack : ItemStack.EMPTY;
    }
    public static void clear() { entries = Map.of(); revision++; }
}
