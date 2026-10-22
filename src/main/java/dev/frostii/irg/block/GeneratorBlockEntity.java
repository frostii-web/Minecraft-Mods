package dev.frostii.irg.block;

import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.config.GeneratorConfig;
import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.resource.ResourceCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public final class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_OUTPUT_SLOTS = 54, GENERATION_SLOTS = 6, DATA_VERSION = 2;
    public enum OutputMode { PUSH, EXTRACT_ONLY, DISABLED }
    public enum FilterMode { ALL_ACTIVE, CUSTOM, NONE }
    public static final class Generation {
        public ResourceLocation resource;
        public boolean enabled = true;
        public int progress, status;
    }
    public static final class Route {
        public OutputMode mode = OutputMode.PUSH;
        public FilterMode filter = FilterMode.ALL_ACTIVE;
        public final Set<ResourceLocation> custom = new LinkedHashSet<>();
    }
    private final Generation[] generation = new Generation[6];
    private final Route[] routes = new Route[6];
    private final List<ItemStack> recovery = new ArrayList<>();
    private ListTag legacySelections = new ListTag();
    private CompoundTag legacyConfiguration = new CompoundTag();
    private ListTag unresolvedItems = new ListTag();
    private final Set<ResourceLocation> active = new LinkedHashSet<>();
    private final Set<ResourceLocation> activeView = Collections.unmodifiableSet(active);
    private final ItemStackHandler output = new ItemStackHandler(GeneratorConfig.OUTPUT_INVENTORY_SLOTS.get()) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final List<LazyOptional<IItemHandler>> capabilities = new ArrayList<>();
    private int autoOutputTicks, pushStart;
    private long revision;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(InfiniteResourceGenerators.GENERATOR_ENTITY.get(), pos, state);
        for (int i = 0; i < 6; i++) { generation[i] = new Generation(); routes[i] = new Route(); }
        createCaps();
    }
    public Generation generation(int slot) { return generation[slot]; }
    public Route route(Direction side) { return routes[side.ordinal()]; }
    public long revision() { return revision; }
    public ItemStackHandler output() { return output; }
    public int outputSlots() { return output.getSlots(); }
    public void ensureClientOutputSlots(int slots) {
        if (level != null && !level.isClientSide) return;
        CompoundTag inventory = output.serializeNBT();
        inventory.putInt("Size", Math.max(1, Math.min(MAX_OUTPUT_SLOTS, slots)));
        output.deserializeNBT(inventory);
    }
    @Nullable public ResourceLocation selected() { return generation[0].resource; }
    public List<ResourceLocation> selections() {
        return Arrays.stream(generation).map(s -> s.resource).filter(Objects::nonNull).toList();
    }
    public Set<ResourceLocation> activeResources() {
        return activeView;
    }
    public boolean select(ResourceLocation id) { return configureSlot(0, id, true); }
    public int status() { return generation[0].status; }
    public int progress() { return generation[0].progress; }
    public boolean configureSlot(int index, @Nullable ResourceLocation id, boolean enabled) {
        if (index < 0 || index >= 6 || !(level instanceof ServerLevel server)
                || (id != null && !ResourceCatalog.isAvailable(server, id))) return false;
        Generation slot = generation[index];
        if (Objects.equals(slot.resource, id) && slot.enabled == enabled) return true;
        slot.resource = id;
        slot.enabled = enabled;
        slot.progress = 0;
        slot.status = id == null ? 0 : enabled ? 1 : 4;
        pruneFilters();
        changedConfig();
        return true;
    }
    public boolean configureRoute(Direction side, OutputMode mode, FilterMode filter, Collection<ResourceLocation> custom) {
        if (!(level instanceof ServerLevel) || custom.size() > 6 || !activeResources().containsAll(custom)) return false;
        Route route = route(side);
        // Re-enabling a disabled face starts with the safe, predictable ALL ACTIVE default.
        if (route.mode == OutputMode.DISABLED && mode != OutputMode.DISABLED) filter = FilterMode.ALL_ACTIVE;
        route.mode = mode; route.filter = filter; route.custom.clear(); route.custom.addAll(custom);
        changedConfig();
        return true;
    }
    private void pruneFilters() {
        active.clear();
        for (Generation slot : generation) if (slot.enabled && slot.resource != null) active.add(slot.resource);
        for (Route route : routes) route.custom.retainAll(active);
    }
    private void changedConfig() { revision++; setChanged(); }
    public boolean allows(Direction side, ItemStack stack) {
        if (stack.isEmpty() || stack.hasTag()) return false;
        Route route = route(side);
        if (route.mode == OutputMode.DISABLED || route.filter == FilterMode.NONE) return false;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return activeResources().contains(id) && (route.filter == FilterMode.ALL_ACTIVE || route.custom.contains(id));
    }
    // 0 disabled, 1 no permitted resources, 2 waiting/blocked, 3 ready to push, 4 extraction only.
    public int routingStatus(Direction side) {
        Route route = route(side);
        if (route.mode == OutputMode.DISABLED) return 0;
        if (route.filter == FilterMode.NONE || activeResources().stream().noneMatch(id ->
                route.filter == FilterMode.ALL_ACTIVE || route.custom.contains(id))) return 1;
        if (route.mode == OutputMode.EXTRACT_ONLY) return 4;
        if (!GeneratorConfig.AUTO_OUTPUT_ENABLED.get() || level == null || !level.hasChunkAt(worldPosition.relative(side))) return 2;
        BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
        if (neighbor == null) return 2;
        IItemHandler destination = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
        if (destination == null) return 2;
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (allows(side, stack) && ItemHandlerHelper.insertItemStacked(destination, stack.copyWithCount(1), true).isEmpty()) return 3;
        }
        return 2;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        if (!(level instanceof ServerLevel server)) return;
        generator.restoreRecovery();
        int interval = GeneratorConfig.GENERATION_INTERVAL_TICKS.get();
        boolean progressed = false;
        for (Generation slot : generator.generation) {
            if (slot.resource == null || !slot.enabled) { slot.status = slot.resource == null ? 0 : 4; slot.progress = 0; continue; }
            if (!ResourceCatalog.isAvailable(server, slot.resource)) { slot.status = 3; continue; }
            slot.status = 1;
            int previousProgress = slot.progress;
            slot.progress = Math.min(interval, slot.progress + 1);
            progressed |= previousProgress != slot.progress;
            if (slot.progress >= interval) {
                ItemStack stack = ResourceCatalog.stack(slot.resource);
                if (stack.isEmpty()) slot.status = 3;
                else if (!ItemHandlerHelper.insertItemStacked(generator.output, stack, true).isEmpty()) slot.status = 2;
                else { ItemHandlerHelper.insertItemStacked(generator.output, stack, false); slot.progress = 0; }
            }
        }
        if (progressed && level.getGameTime() % 20 == 0) generator.setChanged(); // Persist partial progress without block-update packets.
        if (!GeneratorConfig.AUTO_OUTPUT_ENABLED.get()) generator.autoOutputTicks = 0;
        else if (++generator.autoOutputTicks >= GeneratorConfig.AUTO_OUTPUT_INTERVAL_TICKS.get()) {
            generator.autoOutputTicks = 0;
            generator.ejectToNeighbors(GeneratorConfig.AUTO_OUTPUT_MAX_ITEMS.get());
        }
    }
    private void ejectToNeighbors(int budget) {
        if (level == null) return;
        // Rotate priority so a shared resource can reach each configured destination.
        for (int n = 0; n < 6 && budget > 0; n++) {
            Direction side = Direction.values()[(pushStart + n) % 6];
            if (route(side).mode != OutputMode.PUSH || !level.hasChunkAt(worldPosition.relative(side))) continue;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) continue;
            IItemHandler destination = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
            if (destination == null) continue;
            for (int slot = 0; slot < output.getSlots() && budget > 0; slot++) {
                ItemStack stored = output.getStackInSlot(slot);
                if (!allows(side, stored)) continue;
                ItemStack offered = stored.copyWithCount(Math.min(stored.getCount(), budget));
                int accepted = offered.getCount() - ItemHandlerHelper.insertItemStacked(destination, offered, true).getCount();
                if (accepted <= 0) continue;
                // Reserve real items before invoking another handler; restore any rejected remainder.
                ItemStack extracted = output.extractItem(slot, accepted, false);
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, extracted.copy(), false);
                budget -= extracted.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) {
                    ItemStack left = ItemHandlerHelper.insertItemStacked(output, remainder, false);
                    if (!left.isEmpty()) recovery.add(left);
                }
            }
        }
        pushStart = (pushStart + 1) % 6;
    }
    private void restoreRecovery() {
        for (int i = recovery.size() - 1; i >= 0; i--) {
            ItemStack before = recovery.get(i);
            ItemStack after = ItemHandlerHelper.insertItemStacked(output, before, false);
            if (after.isEmpty()) recovery.remove(i); else recovery.set(i, after);
            if (before.getCount() != after.getCount()) setChanged();
        }
    }
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < output.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.getStackInSlot(i));
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (ItemStack stack : recovery) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        recovery.clear();
    }
    public CompoundTag configurationTag() {
        CompoundTag tag = new CompoundTag();
        ListTag slots = new ListTag(), faces = new ListTag();
        for (Generation slot : generation) {
            CompoundTag value = new CompoundTag();
            if (slot.resource != null) value.putString("Resource", slot.resource.toString());
            value.putBoolean("Enabled", slot.enabled); slots.add(value);
        }
        for (Direction side : Direction.values()) {
            Route route = route(side);
            CompoundTag value = new CompoundTag();
            value.putString("Direction", side.getName()); value.putString("Mode", route.mode.name()); value.putString("Filter", route.filter.name());
            ListTag custom = new ListTag();
            for (ResourceLocation id : route.custom) custom.add(StringTag.valueOf(id.toString()));
            value.put("Custom", custom); faces.add(value);
        }
        tag.putInt("DataVersion", DATA_VERSION); tag.put("Generation", slots); tag.put("Routes", faces);
        return tag;
    }
    public void readConfiguration(CompoundTag tag) {
        ListTag slots = tag.getList("Generation", Tag.TAG_COMPOUND);
        for (int i = 0; i < 6; i++) {
            CompoundTag value = slots.getCompound(i);
            generation[i].resource = parseResource(value.getString("Resource"));
            generation[i].enabled = !value.contains("Enabled") || value.getBoolean("Enabled");
            generation[i].progress = Math.max(0, Math.min(GeneratorConfig.GENERATION_INTERVAL_TICKS.get(), value.getInt("Progress")));
        }
        for (Direction side : Direction.values()) { routes[side.ordinal()] = new Route(); }
        ListTag faces = tag.getList("Routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < faces.size(); i++) {
            CompoundTag value = faces.getCompound(i);
            Direction side = Direction.byName(value.getString("Direction"));
            if (side == null) continue;
            Route route = route(side);
            try { route.mode = OutputMode.valueOf(value.getString("Mode")); } catch (IllegalArgumentException ignored) { route.mode = OutputMode.DISABLED; }
            try { route.filter = FilterMode.valueOf(value.getString("Filter")); } catch (IllegalArgumentException ignored) { route.filter = FilterMode.NONE; }
            ListTag custom = value.getList("Custom", Tag.TAG_STRING);
            for (int j = 0; j < custom.size(); j++) {
                ResourceLocation id = parseResource(custom.getString(j));
                if (id != null) route.custom.add(id);
            }
        }
        pruneFilters(); revision++;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag config = configurationTag();
        ListTag slots = config.getList("Generation", Tag.TAG_COMPOUND);
        for (int i = 0; i < 6; i++) slots.getCompound(i).putInt("Progress", generation[i].progress);
        tag.merge(config); tag.put("Output", output.serializeNBT()); tag.putInt("AutoOutputTicks", autoOutputTicks);
        ListTag overflow = new ListTag();
        for (ItemStack stack : recovery) overflow.add(stack.save(new CompoundTag()));
        tag.put("Recovery", overflow);
        if (!legacySelections.isEmpty()) tag.put("LegacySelections", legacySelections.copy());
        if (!legacyConfiguration.isEmpty()) tag.put("LegacyConfiguration", legacyConfiguration.copy());
        if (!unresolvedItems.isEmpty()) tag.put("UnresolvedItems", unresolvedItems.copy());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag inventory = tag.getCompound("Output").copy();
        recovery.clear(); unresolvedItems = new ListTag();
        ListTag overflow = tag.getList("Recovery", Tag.TAG_COMPOUND).copy();
        overflow.addAll(tag.getList("UnresolvedItems", Tag.TAG_COMPOUND));
        for (int i = 0; i < overflow.size(); i++) preserveItem(overflow.getCompound(i));
        ListTag items = inventory.getList("Items", Tag.TAG_COMPOUND), kept = new ListTag();
        int highest = -1;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag value = items.getCompound(i);
            int slot = value.getInt("Slot");
            if (ItemStack.of(value).isEmpty()) { preserveItem(value); continue; }
            if (slot >= 0 && slot < MAX_OUTPUT_SLOTS) { kept.add(value.copy()); highest = Math.max(highest, slot); }
            else preserveItem(value);
        }
        inventory.put("Items", kept);
        inventory.putInt("Size", Math.max(GeneratorConfig.OUTPUT_INVENTORY_SLOTS.get(), highest + 1));
        output.deserializeNBT(inventory);
        legacySelections = tag.getList("LegacySelections", Tag.TAG_STRING).copy();
        legacyConfiguration = tag.getCompound("LegacyConfiguration").copy();
        if (tag.contains("Generation", Tag.TAG_LIST)) readConfiguration(tag); else {
            legacyConfiguration = tag.copy(); legacyConfiguration.remove("Output"); legacyConfiguration.remove("Recovery");
            migrateLegacy(tag);
        }
        autoOutputTicks = Math.max(0, Math.min(GeneratorConfig.AUTO_OUTPUT_INTERVAL_TICKS.get() - 1, tag.getInt("AutoOutputTicks")));
    }
    private static ResourceLocation parseResource(String value) {
        // ResourceLocation accepts an empty path as minecraft:; absence means an empty generation row.
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }
    private void preserveItem(CompoundTag value) {
        ItemStack stack = ItemStack.of(value);
        if (!stack.isEmpty()) recovery.add(stack);
        else if (value.getByte("Count") != 0 && value.contains("id")) unresolvedItems.add(value.copy());
    }
    private void migrateLegacy(CompoundTag tag) {
        for (int i = 0; i < 6; i++) { generation[i] = new Generation(); routes[i] = new Route(); }
        ListTag old = tag.getList("Selections", Tag.TAG_STRING);
        int next = 0;
        for (int i = 0; i < old.size(); i++) {
            ResourceLocation id = parseResource(old.getString(i));
            if (id == null) continue;
            if (next < 6) generation[next++].resource = id; else legacySelections.add(old.get(i).copy());
        }
        if (next == 0 && tag.contains("Selected")) generation[next++].resource = parseResource(tag.getString("Selected"));
        // Earlier experimental face saves: Faces (or Sides), compounds keyed by Direction/Side and Resource/Selected.
        ListTag faces = tag.getList(tag.contains("Faces") ? "Faces" : "Sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < faces.size(); i++) {
            CompoundTag value = faces.getCompound(i);
            ListTag faceItems = value.getCompound("Output").getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < faceItems.size(); j++) preserveItem(faceItems.getCompound(j));
            Direction side = Direction.byName(value.getString(value.contains("Direction") ? "Direction" : "Side").toLowerCase(Locale.ROOT));
            if (side == null && i < 6) side = Direction.values()[i];
            ResourceLocation id = parseResource(value.getString(value.contains("Resource") ? "Resource" : "Selected"));
            if (id != null && next < 6) generation[next++].resource = id;
            if (side != null) {
                Route route = route(side);
                try { route.mode = OutputMode.valueOf(value.getString("Mode")); } catch (IllegalArgumentException ignored) { route.mode = OutputMode.DISABLED; }
                route.filter = id == null ? FilterMode.NONE : FilterMode.CUSTOM;
                if (id != null) route.custom.add(id);
            }
        }
        for (Generation slot : generation) slot.progress = Math.max(0, Math.min(GeneratorConfig.GENERATION_INTERVAL_TICKS.get() - 1, tag.getInt("Ticks")));
        pruneFilters(); revision++;
    }
    private void createCaps() {
        capabilities.clear();
        for (Direction side : Direction.values()) capabilities.add(LazyOptional.of(() -> new IItemHandler() {
            @Override public int getSlots() { return output.getSlots(); }
            @Override public @NotNull ItemStack getStackInSlot(int slot) {
                ItemStack stack = output.getStackInSlot(slot);
                return allows(side, stack) ? stack.copy() : ItemStack.EMPTY;
            }
            @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }
            @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return allows(side, output.getStackInSlot(slot)) ? output.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            @Override public int getSlotLimit(int slot) { return output.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
        }));
    }
    @Override public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && !isRemoved()) {
            // Unsided access cannot bypass routing. Cached sided handlers recheck permissions on every operation.
            return side == null ? LazyOptional.empty() : capabilities.get(side.ordinal()).cast();
        }
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); capabilities.forEach(LazyOptional::invalidate); }
    @Override public void reviveCaps() { super.reviveCaps(); createCaps(); }
    @Override public Component getDisplayName() { return Component.translatable("block.infiniteresourcegenerators.resource_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new GeneratorMenu(id, inventory, this); }
}
