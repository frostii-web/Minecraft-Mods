package dev.frostii.irg.network;

import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.block.GeneratorBlockEntity.*;
import dev.frostii.irg.client.ClientPackets;
import dev.frostii.irg.menu.GeneratorMenu;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

public final class ModNetwork {
    private static final String VERSION = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(InfiniteResourceGenerators.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private ModNetwork() { }
    public record SetGeneration(int menuId, int slot, ResourceLocation item, boolean enabled) { }
    public record SetRoute(int menuId, Direction side, OutputMode mode, FilterMode filter, List<ResourceLocation> items) { }
    public record RequestCatalog(int menuId) { }
    public record CatalogEntry(ResourceLocation item, boolean available, boolean immediate) { }
    public record CatalogChunk(int menuId, boolean reset, boolean complete, List<CatalogEntry> entries) { }
    public record ConfigurationState(int menuId, CompoundTag tag) { }

    private static GeneratorMenu valid(ServerPlayer player, int id) {
        return player != null && player.containerMenu instanceof GeneratorMenu menu && menu.containerId == id && menu.stillValid(player) ? menu : null;
    }
    public static void register() {
        CHANNEL.messageBuilder(SetGeneration.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> { b.writeVarInt(m.menuId); b.writeVarInt(m.slot); b.writeBoolean(m.item != null); if (m.item != null) b.writeResourceLocation(m.item); b.writeBoolean(m.enabled); })
                .decoder(b -> new SetGeneration(b.readVarInt(), b.readVarInt(), b.readBoolean() ? b.readResourceLocation() : null, b.readBoolean()))
                .consumerMainThread((m, c) -> {
                    ServerPlayer player = c.get().getSender(); GeneratorMenu menu = valid(player, m.menuId);
                    if (menu != null) {
                        if (!menu.generator.configureSlot(m.slot, m.item, m.enabled)) player.displayClientMessage(Component.translatable("gui.infiniteresourcegenerators.rejected"), true);
                        menu.syncState(player);
                    }
                }).add();
        CHANNEL.messageBuilder(RequestCatalog.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m,b) -> b.writeVarInt(m.menuId)).decoder(b -> new RequestCatalog(b.readVarInt()))
                .consumerMainThread((m,c) -> { ServerPlayer player = c.get().getSender(); GeneratorMenu menu = valid(player,m.menuId); if (menu != null) menu.requestCatalog(player); }).add();
        CHANNEL.messageBuilder(CatalogChunk.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ModNetwork::writeCatalog).decoder(ModNetwork::readCatalog)
                .consumerMainThread((m,c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.catalog(m))).add();
        CHANNEL.messageBuilder(ConfigurationState.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m,b) -> { b.writeVarInt(m.menuId); b.writeNbt(m.tag); })
                .decoder(b -> new ConfigurationState(b.readVarInt(), Objects.requireNonNull(b.readNbt())))
                .consumerMainThread((m,c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.configuration(m))).add();
        CHANNEL.messageBuilder(SetRoute.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m,b) -> { b.writeVarInt(m.menuId); b.writeEnum(m.side); b.writeEnum(m.mode); b.writeEnum(m.filter); b.writeCollection(m.items,FriendlyByteBuf::writeResourceLocation); })
                .decoder(b -> new SetRoute(b.readVarInt(), b.readEnum(Direction.class), b.readEnum(OutputMode.class), b.readEnum(FilterMode.class), readIds(b,6)))
                .consumerMainThread((m,c) -> {
                    ServerPlayer player = c.get().getSender(); GeneratorMenu menu = valid(player,m.menuId);
                    if (menu != null) { menu.generator.configureRoute(m.side,m.mode,m.filter,m.items); menu.syncState(player); }
                }).add();
    }
    private static List<ResourceLocation> readIds(FriendlyByteBuf b, int limit) {
        int count = b.readVarInt(); if (count < 0 || count > limit) throw new IllegalArgumentException("Too many filter resources");
        List<ResourceLocation> result = new ArrayList<>(count); for (int i=0;i<count;i++) result.add(b.readResourceLocation()); return result;
    }
    public static void generation(int menuId,int slot,ResourceLocation id,boolean enabled) { CHANNEL.sendToServer(new SetGeneration(menuId,slot,id,enabled)); }
    public static void route(int menuId,Direction side,OutputMode mode,FilterMode filter,Collection<ResourceLocation> items) { CHANNEL.sendToServer(new SetRoute(menuId,side,mode,filter,List.copyOf(items))); }
    public static void requestCatalog(int menuId) { CHANNEL.sendToServer(new RequestCatalog(menuId)); }
    public static void send(ServerPlayer player,Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),packet); }
    private static void writeCatalog(CatalogChunk c,FriendlyByteBuf b) {
        b.writeVarInt(c.menuId); b.writeBoolean(c.reset); b.writeBoolean(c.complete); b.writeVarInt(c.entries.size());
        for (CatalogEntry e:c.entries) { b.writeResourceLocation(e.item); b.writeBoolean(e.available); b.writeBoolean(e.immediate); }
    }
    private static CatalogChunk readCatalog(FriendlyByteBuf b) {
        int id=b.readVarInt(); boolean reset=b.readBoolean(),complete=b.readBoolean(); int count=b.readVarInt();
        if(count<0||count>256) throw new IllegalArgumentException("Invalid catalog chunk size");
        List<CatalogEntry> result=new ArrayList<>(count);
        for(int i=0;i<count;i++) result.add(new CatalogEntry(b.readResourceLocation(),b.readBoolean(),b.readBoolean()));
        return new CatalogChunk(id,reset,complete,result);
    }
}
