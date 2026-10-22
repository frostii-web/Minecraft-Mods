package dev.frostii.irg.resource;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;

/** Stored in the overworld: shared by players, machines, and dimensions in this save only. */
public final class WorldUnlocks extends SavedData {
    private static final String NAME = "infiniteresourcegenerators_unlocks";
    private final Set<ResourceLocation> unlocked = new HashSet<>();
    private long revision;

    public static WorldUnlocks get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(WorldUnlocks::load, WorldUnlocks::new, NAME);
    }
    public boolean contains(ResourceLocation id) { return unlocked.contains(id); }
    public long revision() { return revision; }
    public boolean unlock(ResourceLocation id) {
        if (!unlocked.add(id)) return false;
        revision++;
        setDirty();
        return true;
    }
    public static WorldUnlocks load(CompoundTag tag) {
        WorldUnlocks data = new WorldUnlocks();
        ListTag list = tag.getList("Unlocked", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) data.unlocked.add(id);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        unlocked.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("Unlocked", list);
        tag.putInt("DataVersion", 1);
        return tag;
    }
}
