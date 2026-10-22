package dev.frostii.irg.menu;

import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.block.GeneratorBlockEntity;
import dev.frostii.irg.network.ModNetwork;
import dev.frostii.irg.resource.ResourceCatalog;
import dev.frostii.irg.resource.WorldUnlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GeneratorMenu extends AbstractContainerMenu {
    public final GeneratorBlockEntity generator;
    public final int outputSlotCount;
    public final int outputRows;
    public final int playerInventoryY;
    private final ContainerData data;
    private final Player owner;
    public final List<ModNetwork.CatalogEntry> clientEntries = new ArrayList<>();
    public int catalogVersion;
    public boolean catalogReady;
    public final List<ResourceLocation> clientSelections = new ArrayList<>();
    public GeneratorBlockEntity.Mode clientMode = GeneratorBlockEntity.Mode.ROUND_ROBIN;
    private List<ResourceLocation> lastSelections = List.of();
    private GeneratorBlockEntity.Mode lastMode;
    private boolean selectionSent;
    private long unlockRevision = -1;
    private long catalogRevision = -1;
    private long lastRequestTick = -100;

    public static GeneratorMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        var entity = inventory.player.level().getBlockEntity(buffer.readBlockPos());
        if (!(entity instanceof GeneratorBlockEntity generator)) throw new IllegalStateException("Generator menu opened without its block entity");
        return new GeneratorMenu(id, inventory, generator, buffer.readVarInt());
    }

    public GeneratorMenu(int id, Inventory inventory, GeneratorBlockEntity generator) {
        this(id, inventory, generator, generator.outputSlots());
    }

    private GeneratorMenu(int id, Inventory inventory, GeneratorBlockEntity generator, int advertisedSlots) {
        super(InfiniteResourceGenerators.GENERATOR_MENU.get(), id);
        this.generator = generator;
        this.owner = inventory.player;
        generator.ensureClientOutputSlots(advertisedSlots);
        this.outputSlotCount = generator.outputSlots();
        this.outputRows = (outputSlotCount + 8) / 9;
        this.playerInventoryY = 154 + outputRows * 18;
        if (inventory.player.level().isClientSide) data = new SimpleContainerData(2);
        else data = new ContainerData() {
            @Override public int get(int index) { return index == 0 ? generator.status() : generator.progress(); }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 2; }
        };
        addDataSlots(data);
        for (int slot = 0; slot < outputSlotCount; slot++) {
            addSlot(new SlotItemHandler(generator.output(), slot, 69 + (slot % 9) * 18, 140 + (slot / 9) * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 69 + column * 18, playerInventoryY + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 69 + column * 18, playerInventoryY + 58));
    }

    public int status() { return data.get(0); }
    public int progress() { return data.get(1); }

    @Override public boolean stillValid(Player player) {
        return !generator.isRemoved() && player.level() == generator.getLevel()
                && player.level().getBlockEntity(generator.getBlockPos()) == generator
                && player.distanceToSqr(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY() + 0.5, generator.getBlockPos().getZ() + 0.5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        // Player slots can never shift-insert into the output. Allow rearranging main inventory/hotbar.
        int mainStart = outputSlotCount;
        int hotbarStart = mainStart + 27;
        int end = mainStart + 36;
        boolean moved = index < outputSlotCount ? moveItemStackTo(stack, mainStart, end, true)
                : index < hotbarStart ? moveItemStackTo(stack, hotbarStart, end, false) : moveItemStackTo(stack, mainStart, hotbarStart, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (generator.getLevel() == null || generator.getLevel().isClientSide) return;
        if (owner instanceof ServerPlayer player && player.containerMenu == this) {
            if (!selectionSent || !Objects.equals(lastSelections, generator.selections()) || lastMode != generator.mode()) {
                ModNetwork.send(player, new ModNetwork.SelectionState(containerId, generator.selections(), generator.mode()));
                lastSelections = generator.selections();
                lastMode = generator.mode();
                selectionSent = true;
            }
            long currentUnlocks = WorldUnlocks.get(player.serverLevel()).revision();
            if (unlockRevision != currentUnlocks || catalogRevision != ResourceCatalog.revision()) syncCatalog(player);
        }
    }

    public void requestCatalog(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        if (now - lastRequestTick < 20) return;
        lastRequestTick = now;
        syncCatalog(player);
        ModNetwork.send(player, new ModNetwork.SelectionState(containerId, generator.selections(), generator.mode()));
    }

    private void syncCatalog(ServerPlayer player) {
        List<ModNetwork.CatalogEntry> all = ResourceCatalog.entries().stream()
                .map(entry -> new ModNetwork.CatalogEntry(entry.id(), ResourceCatalog.isAvailable(player.serverLevel(), entry.id()), entry.immediate())).toList();
        if (all.isEmpty()) ModNetwork.send(player, new ModNetwork.CatalogChunk(containerId, true, true, List.of()));
        for (int offset = 0; offset < all.size(); offset += 256) {
            int end = Math.min(offset + 256, all.size());
            ModNetwork.send(player, new ModNetwork.CatalogChunk(containerId, offset == 0, end == all.size(), all.subList(offset, end)));
        }
        unlockRevision = WorldUnlocks.get(player.serverLevel()).revision();
        catalogRevision = ResourceCatalog.revision();
    }
}
