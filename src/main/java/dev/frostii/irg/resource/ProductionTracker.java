package dev.frostii.irg.resource;

import dev.frostii.irg.InfiniteResourceGenerators;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

public final class ProductionTracker {
    private ProductionTracker() { }

    public static void obtained(ServerLevel level, ItemStack stack) {
        if (!ResourcePolicy.processed(stack)) return;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        var entry = id == null ? null : ResourceCatalog.get(id);
        if (entry != null && !entry.immediate()) WorldUnlocks.get(level).unlock(id);
    }

    public static ItemStack[] snapshot(IItemHandler output) {
        ItemStack[] before = new ItemStack[output.getSlots()];
        for (int i = 0; i < before.length; i++) before[i] = output.getStackInSlot(i).copy();
        return before;
    }

    public static void produced(ServerLevel level, IItemHandler output, ItemStack[] before) {
        for (int slot = 0; slot < Math.min(before.length, output.getSlots()); slot++) {
            ItemStack after = output.getStackInSlot(slot);
            ItemStack old = before[slot];
            if (!after.isEmpty() && (!ItemStack.isSameItemSameTags(old, after) || after.getCount() > old.getCount())) obtained(level, after);
        }
    }

    // Only custom GT method names are reflected. Minecraft classes and methods above use Forge's reobfuscation.
    // This avoids pulling LDLib types into the mixin's signature. Signatures checked against GTCEu 7.5.1.
    public static void completedGtOutput(Object handler, ItemStack[] before) {
        try {
            Object machine = handler.getClass().getMethod("getMachine").invoke(handler);
            Object level = machine.getClass().getMethod("getLevel").invoke(machine);
            if (level instanceof ServerLevel server) produced(server, (IItemHandler) handler, before);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("GTCEu 7.5.1 output hook no longer matches the installed API", exception);
        }
    }
}
