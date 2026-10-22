package dev.frostii.irg.resource;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Set;

/** Positive allowlists, independent of names and JEI visibility. No NBT is ever copied. */
public final class ResourcePolicy {
    public static final Set<TagPrefix> ORES = Set.of(TagPrefix.ore, TagPrefix.oreGranite, TagPrefix.oreDiorite,
            TagPrefix.oreAndesite, TagPrefix.oreRedGranite, TagPrefix.oreMarble, TagPrefix.oreDeepslate,
            TagPrefix.oreTuff, TagPrefix.oreSand, TagPrefix.oreRedSand, TagPrefix.oreGravel,
            TagPrefix.oreBasalt, TagPrefix.oreNetherrack, TagPrefix.oreBlackstone, TagPrefix.oreEndstone);
    public static final Set<TagPrefix> WIRES = Set.of(TagPrefix.wireGtSingle, TagPrefix.wireGtDouble,
            TagPrefix.wireGtQuadruple, TagPrefix.wireGtOctal, TagPrefix.wireGtHex);
    public static final Set<TagPrefix> FORMS = Set.of(TagPrefix.rawOre, TagPrefix.ingot, TagPrefix.nugget,
            TagPrefix.gem, TagPrefix.gemChipped, TagPrefix.gemFlawed, TagPrefix.gemFlawless, TagPrefix.gemExquisite,
            TagPrefix.dust, TagPrefix.dustSmall, TagPrefix.dustTiny, TagPrefix.dustImpure, TagPrefix.dustPure,
            TagPrefix.crushed, TagPrefix.crushedPurified, TagPrefix.crushedRefined,
            TagPrefix.plate, TagPrefix.plateDouble, TagPrefix.plateDense, TagPrefix.foil,
            TagPrefix.rod, TagPrefix.rodLong, TagPrefix.bolt, TagPrefix.screw, TagPrefix.ring,
            TagPrefix.spring, TagPrefix.springSmall, TagPrefix.wireFine,
            TagPrefix.wireGtSingle, TagPrefix.wireGtDouble, TagPrefix.wireGtQuadruple, TagPrefix.wireGtOctal, TagPrefix.wireGtHex);

    private ResourcePolicy() { }

    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && !stack.hasTag() && !stack.isDamageableItem()
                && stack.getMaxStackSize() > 1
                && !stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                && !stack.getCapability(ForgeCapabilities.ENERGY).isPresent()
                && !stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
    }

    public static boolean processed(ItemStack stack) {
        if (!plain(stack)) return false;
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(stack.getItem());
        if (entry.isEmpty() || !FORMS.contains(entry.tagPrefix())) return false;
        String type = stack.getItem().getClass().getName();
        if (WIRES.contains(entry.tagPrefix())) {
            // GT bare wires are material pipe block items. Do not allow cable, pipe, battery, or machine prefixes.
            return type.equals("com.gregtechceu.gtceu.api.item.MaterialPipeBlockItem");
        }
        // GT generated material forms, or passive simple items mapped to material tags by the pack.
        return type.equals("com.gregtechceu.gtceu.api.item.TagPrefixItem") || stack.getItem().getClass() == Item.class;
    }

    public static boolean ore(ItemStack stack) {
        return plain(stack) && stack.getItem() instanceof BlockItem block
                && !block.getBlock().defaultBlockState().hasBlockEntity();
    }
}
