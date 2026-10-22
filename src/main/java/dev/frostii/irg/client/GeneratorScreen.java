package dev.frostii.irg.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.frostii.irg.block.GeneratorBlockEntity.*;
import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private EditBox search;
    private List<ModNetwork.CatalogEntry> filtered = List.of();
    private int tab, selectedSlot, scroll, seenVersion=-1;
    private Direction selectedFace=Direction.SOUTH;
    private double yaw=Math.toRadians(-35),pitch=Math.toRadians(25),pressX,pressY;
    private boolean rotating,dragged;
    public GeneratorScreen(GeneratorMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title); imageWidth=420; imageHeight=Math.max(244,menu.playerInventoryY+78);
        titleLabelX=8; titleLabelY=7; inventoryLabelX=129; inventoryLabelY=menu.playerInventoryY-11;
    }
    @Override protected void init() {
        String query=search==null?"":search.getValue(); super.init();
        search=new EditBox(font,leftPos+214,topPos+43,196,17,Component.literal("Search resources"));
        search.setMaxLength(100); search.setHint(Component.literal("Search resources")); search.setValue(query);
        search.setResponder(value->{scroll=0;filter();}); addRenderableWidget(search); updateTab(); filter();
        ModNetwork.requestCatalog(menu.containerId);
    }
    private void updateTab() { search.visible=tab==0; search.setFocused(false); menu.storageVisible=tab==2; }
    private ItemStack stack(ResourceLocation id) { var item=id==null?null:ForgeRegistries.ITEMS.getValue(id); return item==null?ItemStack.EMPTY:item.getDefaultInstance(); }
    private void filter() {
        String query=search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered=menu.clientEntries.stream().filter(e->e.item().toString().contains(query)||stack(e.item()).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing((ModNetwork.CatalogEntry e)->!e.available()).thenComparing(e->stack(e.item()).getHoverName().getString(),String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.item().toString())).toList();
        scroll=Math.min(scroll,Math.max(0,filtered.size()-7)); seenVersion=menu.catalogVersion;
    }
    @Override protected void containerTick() { super.containerTick(); search.tick(); }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        if(seenVersion!=menu.catalogVersion) filter(); renderBackground(g); super.render(g,mx,my,partial); renderTooltip(g,mx,my);
        int row=pickerRow(mx,my);
        if(tab==0&&row>=0&&row+scroll<filtered.size()) {
            var entry=filtered.get(row+scroll);
            g.renderComponentTooltip(font,List.of(stack(entry.item()).getHoverName(),Component.literal(entry.item().toString()),Component.translatable(entry.available()?(entry.immediate()?"gui.infiniteresourcegenerators.natural":"gui.infiniteresourcegenerators.unlocked"):"gui.infiniteresourcegenerators.lock_hint")),mx,my);
        }
        if(tab==1) {
            int face=CubeGeometry.pick(faces(),mx,my);
            if(face>=0) g.renderTooltip(font,Component.literal(Direction.values()[face].getName().toUpperCase(Locale.ROOT)+" — click to select, drag to rotate"),mx,my);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,8,7,0xffe8edf5,false);
        if(tab==2) g.drawString(font,"Player inventory",129,inventoryLabelY,0xffe8edf5,false);
    }
    private void text(GuiGraphics g,String value,int x,int y,int color) { g.drawString(font,value,leftPos+x,topPos+y,color,false); }
    private void button(GuiGraphics g,int x,int y,int w,String label,boolean active) {
        g.fill(leftPos+x,topPos+y,leftPos+x+w,topPos+y+17,active?0xff367f76:0xff414f60);
        g.drawCenteredString(font,label,leftPos+x+w/2,topPos+y+5,0xffffffff);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff17202c);
        g.fill(leftPos+2,topPos+20,leftPos+418,topPos+imageHeight-2,0xff253141);
        button(g,8,22,125,"Generation",tab==0); button(g,140,22,133,"Output Routing",tab==1); button(g,280,22,132,"Stored Items",tab==2);
        if(tab==0) drawGeneration(g); else if(tab==1) drawRouting(g); else {
            text(g,"Shared output",8,47,0xffe8edf5);
            text(g,"Take / shift-click",8,63,0xffb8cadb);
            text(g,"items to retrieve",8,74,0xffb8cadb);
            text(g,"stored leftovers.",8,85,0xffb8cadb);
            for(var slot:menu.slots) {
                int sx=leftPos+slot.x-1,sy=topPos+slot.y-1;
                g.fill(sx,sy,sx+18,sy+18,0xff617084);g.fill(sx+1,sy+1,sx+17,sy+17,0xff141e29);
            }
        }
    }
    private void drawGeneration(GuiGraphics g) {
        for(int i=0;i<6;i++) {
            int y=43+i*28; var slot=menu.generator.generation(i);
            g.fill(leftPos+8,topPos+y,leftPos+205,topPos+y+26,i==selectedSlot?0xff355b68:0xff303f50);
            text(g,Integer.toString(i+1),11,y+4,0xffffffff); g.renderItem(stack(slot.resource),leftPos+23,topPos+y+2);
            text(g,font.plainSubstrByWidth(slot.resource==null?"Choose resource":stack(slot.resource).getHoverName().getString(),98),43,y+3,0xffeff5fa);
            text(g,new String[]{"Empty","Generating","Storage full","Unavailable","Disabled"}[Math.max(0,Math.min(4,menu.status(i)))],43,y+14,0xffb6cadb);
            button(g,148,y+2,31,slot.enabled?"ON":"OFF",slot.enabled);button(g,182,y+2,20,"X",false);
            int progress=(int)(130L*menu.progress(i)/menu.interval());
            g.fill(leftPos+12,topPos+y+24,leftPos+12+Math.min(130,progress),topPos+y+26,0xff55ccb1);
        }
        for(int row=0;row<7;row++) {
            int index=scroll+row,y=64+row*22;if(index>=filtered.size())break;var entry=filtered.get(index);
            g.fill(leftPos+214,topPos+y,leftPos+410,topPos+y+21,entry.available()?0xff3c5261:0xff343d48);
            g.renderItem(stack(entry.item()),leftPos+216,topPos+y+2);
            text(g,font.plainSubstrByWidth(stack(entry.item()).getHoverName().getString(),160),235,y+3,entry.available()?0xfff1f6fb:0xff98a4b4);
            text(g,entry.available()?"Click to assign":"Locked: obtain through gameplay",235,y+12,entry.available()?0xff8fe3ba:0xffd6b38d);
        }
        if(filtered.isEmpty())text(g,menu.catalogReady?"No matching resources":"Loading catalog...",218,76,0xffd0dbe6);
        text(g,"Slot "+(selectedSlot+1)+" • select a row, then a resource",8,216,0xffb8cadb);
    }
    private List<CubeGeometry.Face> faces() { return CubeGeometry.faces(yaw,pitch,leftPos+102,topPos+116,82); }
    private void drawRouting(GuiGraphics g) {
        drawCube(g);
        text(g,"Drag to rotate • click a face",10,191,0xffc4d3df);
        text(g,"Green: PUSH",10,204,0xff75dba2);text(g,"Yellow: EXTRACT",10,215,0xfff4d96b);text(g,"Gray: DISABLED",105,215,0xffa6afb9);
        var route=menu.generator.route(selectedFace);
        text(g,selectedFace.getName().toUpperCase(Locale.ROOT)+" output",214,45,0xffeff5fa);
        button(g,214,58,196,route.mode.name()+" (click to cycle)",false);
        FilterMode[] filters=FilterMode.values();String[] names={"ALL ACTIVE","CUSTOM","NONE"};
        for(int i=0;i<3;i++)button(g,214+i*66,79,64,names[i],route.filter==filters[i]);
        String status=new String[]{"Disabled","No permitted resources","Waiting / destination blocked","Destination ready","External extraction only"}[Math.max(0,Math.min(4,menu.routingStatus(selectedFace)))];
        text(g,status,214,101,0xffb8cadb);
        int row=0;
        for(ResourceLocation id:menu.generator.activeResources()) {
            int y=114+row++*17;boolean checked=route.filter==FilterMode.ALL_ACTIVE||(route.filter==FilterMode.CUSTOM&&route.custom.contains(id));
            text(g,checked?"[x]":"[ ]",214,y+4,checked?0xff83e1b8:0xff9aacbd);g.renderItem(stack(id),leftPos+235,topPos+y);
            text(g,font.plainSubstrByWidth(stack(id).getHoverName().getString(),151),254,y+4,0xffe0e9f2);
        }
        if(row==0)text(g,"Enable a generation resource first.",214,122,0xffa8bacb);
        // Compact summary always reflects the server-confirmed route configuration.
        StringBuilder summary=new StringBuilder();
        for(Direction side:Direction.values()) { var r=menu.generator.route(side); if(r.mode!=OutputMode.DISABLED) summary.append(side.getName().substring(0,1).toUpperCase(Locale.ROOT)).append(':').append(r.mode==OutputMode.PUSH?'P':'E').append(r.filter==FilterMode.ALL_ACTIVE?"*":r.filter==FilterMode.NONE?"0":r.custom.size()).append(' '); }
        text(g,summary.toString(),8,imageHeight-11,0xffb8cadb);
    }
    private int color(OutputMode mode) { return switch(mode) { case PUSH->0xff65d695;case EXTRACT_ONLY->0xffedd064;case DISABLED->0xff88919e; }; }
    private void drawCube(GuiGraphics g) {
        g.flush();RenderSystem.enableBlend();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for(var face:faces()) {
            Direction side=Direction.values()[face.direction()];int color=color(menu.generator.route(side).mode);
            String texture=side==Direction.UP?"top":side==Direction.DOWN?"bottom":"side";
            RenderSystem.setShaderTexture(0,new ResourceLocation("infiniteresourcegenerators","textures/block/generator_"+texture+".png"));
            BufferBuilder buffer=Tesselator.getInstance().getBuilder();buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            float[][] uv={{0,0},{0,1},{1,1},{1,0}};
            for(int i=0;i<4;i++)buffer.vertex(g.pose().last().pose(),(float)face.points()[i][0],(float)face.points()[i][1],0).uv(uv[i][0],uv[i][1]).color(color).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        }
        RenderSystem.enableCull();RenderSystem.disableBlend();
        for(var face:faces()) {
            boolean selected=face.direction()==selectedFace.ordinal();int outline=selected?0xffffffff:0xff142430;
            for(int i=0;i<4;i++)line(g,face.points()[i],face.points()[(i+1)%4],outline,selected?2:1);
            int cx=0,cy=0;for(var point:face.points()){cx+=point[0];cy+=point[1];}
            g.drawCenteredString(font,Direction.values()[face.direction()].getName().toUpperCase(Locale.ROOT),cx/4,cy/4-3,0xffffffff);
        }
    }
    private void line(GuiGraphics g,double[] a,double[] b,int color,int width) {
        int steps=(int)Math.ceil(Math.max(Math.abs(b[0]-a[0]),Math.abs(b[1]-a[1])));
        for(int i=0;i<=steps;i++){double t=steps==0?0:(double)i/steps;int x=(int)Math.round(a[0]+(b[0]-a[0])*t),y=(int)Math.round(a[1]+(b[1]-a[1])*t);g.fill(x,y,x+width,y+width,color);}
    }
    private boolean hit(double x,double y,int rx,int ry,int w,int h) {return x>=leftPos+rx&&x<leftPos+rx+w&&y>=topPos+ry&&y<topPos+ry+h;}
    private int pickerRow(double x,double y){return hit(x,y,214,64,196,154)?(int)(y-topPos-64)/22:-1;}
    private void sendRoute(OutputMode mode,FilterMode filter,Collection<ResourceLocation> ids){ModNetwork.route(menu.containerId,selectedFace,mode,filter,ids);}
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0) {
            if(hit(x,y,8,22,404,17)){tab=x<leftPos+137?0:x<leftPos+277?1:2;updateTab();return true;}
            if(tab==0) {
                for(int i=0;i<6;i++)if(hit(x,y,8,43+i*28,197,26)) {
                    selectedSlot=i;var slot=menu.generator.generation(i);
                    if(hit(x,y,182,45+i*28,20,17))ModNetwork.generation(menu.containerId,i,null,slot.enabled);
                    else if(hit(x,y,148,45+i*28,31,17))ModNetwork.generation(menu.containerId,i,slot.resource,!slot.enabled);
                    return true;
                }
                int row=pickerRow(x,y);
                if(row>=0&&row+scroll<filtered.size()){var entry=filtered.get(row+scroll);if(entry.available())ModNetwork.generation(menu.containerId,selectedSlot,entry.item(),menu.generator.generation(selectedSlot).enabled);return true;}
            } else if(tab==1) {
                var route=menu.generator.route(selectedFace);
                if(CubeGeometry.pick(faces(),x,y)>=0||hit(x,y,8,42,196,145)){rotating=true;dragged=false;pressX=x;pressY=y;return true;}
                if(hit(x,y,214,58,196,17)){sendRoute(OutputMode.values()[(route.mode.ordinal()+1)%3],route.filter,route.custom);return true;}
                for(int i=0;i<3;i++)if(hit(x,y,214+i*66,79,64,17)){sendRoute(route.mode,FilterMode.values()[i],route.custom);return true;}
                int row=0;for(ResourceLocation id:menu.generator.activeResources())if(hit(x,y,214,114+row++*17,196,17)) {
                    Set<ResourceLocation> custom=new LinkedHashSet<>(route.filter==FilterMode.ALL_ACTIVE?menu.generator.activeResources():route.custom);
                    if(!custom.remove(id))custom.add(id);sendRoute(route.mode,FilterMode.CUSTOM,custom);return true;
                }
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(rotating&&button==0){if(Math.hypot(x-pressX,y-pressY)>3)dragged=true;if(dragged){yaw+=dx*.012;pitch+=dy*.012;}return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(rotating&&button==0){rotating=false;if(!dragged){int face=CubeGeometry.pick(faces(),x,y);if(face>=0)selectedFace=Direction.values()[face];}return true;}
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(tab==0&&hit(x,y,214,64,196,154)){scroll=Math.max(0,Math.min(Math.max(0,filtered.size()-7),scroll-(int)Math.signum(delta)*3));return true;}
        return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(tab==0&&key!=GLFW.GLFW_KEY_ESCAPE&&search.isFocused()){search.keyPressed(key,scan,modifiers);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
