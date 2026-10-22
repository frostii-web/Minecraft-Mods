package dev.frostii.irg.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class GeneratorConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue GENERATION_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue OUTPUT_INVENTORY_SLOTS;
    public static final ForgeConfigSpec.BooleanValue AUTO_OUTPUT_ENABLED;
    public static final ForgeConfigSpec.IntValue AUTO_OUTPUT_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue AUTO_OUTPUT_MAX_ITEMS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Infinite Resource Generators common settings.").push("generator");
        GENERATION_INTERVAL_TICKS = builder
                .comment("Number of ticks required for a generation cycle. 20 ticks is approximately one second.")
                .defineInRange("generationIntervalTicks", 20, 1, 72000);
        OUTPUT_INVENTORY_SLOTS = builder
                .comment("Number of output inventory slots. Requires a game/server restart.",
                        "Existing generators retain enough trailing slots to preserve stored items when this value is reduced.")
                .worldRestart()
                .defineInRange("outputInventorySlots", 9, 1, 54);
        AUTO_OUTPUT_ENABLED = builder
                .comment("When true, generators push output into adjacent sided item handlers.",
                        "Pipes and hoppers may still extract when this is false.")
                .define("autoOutputEnabled", true);
        AUTO_OUTPUT_INTERVAL_TICKS = builder
                .comment("How often, in ticks, a generator attempts automatic output.")
                .defineInRange("autoOutputIntervalTicks", 5, 1, 1200);
        AUTO_OUTPUT_MAX_ITEMS = builder
                .comment("Maximum total item count transferred during one automatic output attempt.")
                .defineInRange("autoOutputMaxItems", 64, 1, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private GeneratorConfig() { }
}
