package dev.frostii.irg.client;

import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.network.ModNetwork;
import net.minecraft.client.Minecraft;

/** This class is only reached through DistExecutor on the physical client. */
public final class ClientPackets {
    private ClientPackets() { }
    public static void catalog(ModNetwork.CatalogChunk message) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof GeneratorMenu menu) || menu.containerId != message.menuId()) return;
        if (message.reset()) { menu.clientEntries.clear(); menu.catalogReady = false; }
        menu.clientEntries.addAll(message.entries());
        menu.catalogReady = message.complete();
        menu.catalogVersion++;
    }
    public static void selection(ModNetwork.SelectionState message) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof GeneratorMenu menu && menu.containerId == message.menuId()) menu.clientSelected = message.item();
    }
}
