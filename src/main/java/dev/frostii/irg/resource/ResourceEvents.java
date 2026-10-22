package dev.frostii.irg.resource;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ResourceEvents {
    private ResourceEvents() { }
    @SubscribeEvent public static void started(ServerStartedEvent event) { ResourceCatalog.rebuild(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ResourceCatalog.clear(); }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) ResourceCatalog.rebuild(event.getPlayerList().getServer());
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.getEntity().getAbilities().instabuild && event.getEntity().level() instanceof ServerLevel level) ProductionTracker.obtained(level, event.getCrafting());
    }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!event.getEntity().getAbilities().instabuild && event.getEntity().level() instanceof ServerLevel level) ProductionTracker.obtained(level, event.getSmelting());
    }
    @SubscribeEvent public static void pickedUp(PlayerEvent.ItemPickupEvent event) {
        if (!event.getEntity().getAbilities().instabuild && event.getEntity().level() instanceof ServerLevel level) ProductionTracker.obtained(level, event.getStack());
    }
}
