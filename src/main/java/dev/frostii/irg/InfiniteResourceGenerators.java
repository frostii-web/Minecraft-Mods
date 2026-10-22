package dev.frostii.irg;

import com.mojang.logging.LogUtils;
import dev.frostii.irg.block.GeneratorBlock;
import dev.frostii.irg.block.GeneratorBlockEntity;
import dev.frostii.irg.config.GeneratorConfig;
import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.network.ModNetwork;
import dev.frostii.irg.resource.ResourceEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(InfiniteResourceGenerators.MOD_ID)
public final class InfiniteResourceGenerators {
    public static final String MOD_ID = "infiniteresourcegenerators";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Block> GENERATOR = BLOCKS.register("resource_generator", GeneratorBlock::new);
    public static final RegistryObject<Item> GENERATOR_ITEM = ITEMS.register("resource_generator", () -> new BlockItem(GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = ENTITIES.register("resource_generator", () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, GENERATOR.get()).build(null));
    public static final RegistryObject<MenuType<GeneratorMenu>> GENERATOR_MENU = MENUS.register("resource_generator", () -> IForgeMenuType.create(GeneratorMenu::fromNetwork));

    static {
        TABS.register("generators", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.infiniteresourcegenerators"))
                .icon(() -> GENERATOR_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> output.accept(GENERATOR_ITEM.get())).build());
    }

    public InfiniteResourceGenerators() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GeneratorConfig.SPEC, "infiniteresourcegenerators-common.toml");
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.register(ResourceEvents.class);
    }
}
