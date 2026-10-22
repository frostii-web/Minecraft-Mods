package dev.frostii.irg.network;

import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.block.GeneratorBlockEntity;
import dev.frostii.irg.client.ClientPackets;
import dev.frostii.irg.menu.GeneratorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;

public final class ModNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(InfiniteResourceGenerators.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private ModNetwork() { }

    public record SelectResource(int menuId, ResourceLocation item) { }
    public record RequestCatalog(int menuId) { }
    public record SetMode(int menuId, GeneratorBlockEntity.Mode mode) { }
    public record CatalogEntry(ResourceLocation item, boolean available, boolean immediate) { }
    public record CatalogChunk(int menuId, boolean reset, boolean complete, List<CatalogEntry> entries) { }
    public record SelectionState(int menuId, List<ResourceLocation> items, GeneratorBlockEntity.Mode mode) { }

    public static void register() {
        CHANNEL.messageBuilder(SelectResource.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> { buffer.writeVarInt(message.menuId); buffer.writeResourceLocation(message.item); })
                .decoder(buffer -> new SelectResource(buffer.readVarInt(), buffer.readResourceLocation()))
                .consumerMainThread((message, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null && player.containerMenu instanceof GeneratorMenu menu
                            && menu.containerId == message.menuId && menu.stillValid(player)
                            && !menu.generator.toggleSelection(message.item)) {
                        player.displayClientMessage(Component.translatable("gui.infiniteresourcegenerators.rejected"), true);
                    }
                }).add();
        CHANNEL.messageBuilder(RequestCatalog.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> buffer.writeVarInt(message.menuId))
                .decoder(buffer -> new RequestCatalog(buffer.readVarInt()))
                .consumerMainThread((message, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null && player.containerMenu instanceof GeneratorMenu menu && menu.containerId == message.menuId && menu.stillValid(player)) menu.requestCatalog(player);
                }).add();
        CHANNEL.messageBuilder(CatalogChunk.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ModNetwork::writeCatalog).decoder(ModNetwork::readCatalog)
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.catalog(message))).add();
        CHANNEL.messageBuilder(SelectionState.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.menuId);
                    buffer.writeCollection(message.items, FriendlyByteBuf::writeResourceLocation);
                    buffer.writeEnum(message.mode);
                }).decoder(buffer -> new SelectionState(buffer.readVarInt(), buffer.readList(FriendlyByteBuf::readResourceLocation), buffer.readEnum(GeneratorBlockEntity.Mode.class)))
                .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.selection(message))).add();
        CHANNEL.messageBuilder(SetMode.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> { buffer.writeVarInt(message.menuId); buffer.writeEnum(message.mode); })
                .decoder(buffer -> new SetMode(buffer.readVarInt(), buffer.readEnum(GeneratorBlockEntity.Mode.class)))
                .consumerMainThread((message, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null && player.containerMenu instanceof GeneratorMenu menu && menu.containerId == message.menuId && menu.stillValid(player)) menu.generator.setMode(message.mode);
                }).add();
    }

    public static void select(int menuId, ResourceLocation item) { CHANNEL.sendToServer(new SelectResource(menuId, item)); }
    public static void setMode(int menuId, GeneratorBlockEntity.Mode mode) { CHANNEL.sendToServer(new SetMode(menuId, mode)); }
    public static void requestCatalog(int menuId) { CHANNEL.sendToServer(new RequestCatalog(menuId)); }
    public static void send(ServerPlayer player, Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }

    private static void writeCatalog(CatalogChunk chunk, FriendlyByteBuf buffer) {
        buffer.writeVarInt(chunk.menuId);
        buffer.writeBoolean(chunk.reset);
        buffer.writeBoolean(chunk.complete);
        buffer.writeVarInt(chunk.entries.size());
        for (CatalogEntry entry : chunk.entries) {
            buffer.writeResourceLocation(entry.item);
            buffer.writeBoolean(entry.available);
            buffer.writeBoolean(entry.immediate);
        }
    }
    private static CatalogChunk readCatalog(FriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        boolean reset = buffer.readBoolean();
        boolean complete = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid resource catalog chunk size");
        List<CatalogEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new CatalogEntry(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readBoolean()));
        return new CatalogChunk(menuId, reset, complete, entries);
    }
}
