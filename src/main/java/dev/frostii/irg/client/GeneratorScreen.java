package dev.frostii.irg.client;

import dev.frostii.irg.block.GeneratorBlockEntity;
import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final int ROWS = 4;
    private EditBox search;
    private List<ModNetwork.CatalogEntry> filtered = List.of();
    private int scroll;
    private int seenVersion = -1;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 252;
        inventoryLabelX = 69;
        inventoryLabelY = 160;
    }

    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        super.init();
        search = new EditBox(font, leftPos + 8, topPos + 15, 284, 16, Component.translatable("gui.infiniteresourcegenerators.search"));
        search.setMaxLength(100);
        search.setHint(Component.translatable("gui.infiniteresourcegenerators.search"));
        search.setValue(query);
        search.setResponder(value -> { scroll = 0; filter(); });
        addRenderableWidget(search);
        filter();
        ModNetwork.requestCatalog(menu.containerId);
    }

    private ItemStack stack(ModNetwork.CatalogEntry entry) {
        var item = ForgeRegistries.ITEMS.getValue(entry.item());
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private void filter() {
        if (search == null) return;
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        filtered = menu.clientEntries.stream().filter(entry -> entry.item().toString().contains(query)
                        || stack(entry).getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing((ModNetwork.CatalogEntry entry) -> !entry.available())
                        .thenComparing(entry -> stack(entry).getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.item().toString())).toList();
        scroll = Math.min(scroll, Math.max(0, filtered.size() - ROWS));
        seenVersion = menu.catalogVersion;
    }

    @Override protected void containerTick() { super.containerTick(); search.tick(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (seenVersion != menu.catalogVersion) filter();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        int row = rowAt(mouseX, mouseY);
        if (row >= 0 && row + scroll < filtered.size()) {
            var entry = filtered.get(row + scroll);
            graphics.renderComponentTooltip(font, List.of(stack(entry).getHoverName(), Component.literal(entry.item().toString()),
                    Component.translatable(entry.available() ? (entry.immediate() ? "gui.infiniteresourcegenerators.natural" : "gui.infiniteresourcegenerators.unlocked") : "gui.infiniteresourcegenerators.lock_hint")), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff373737);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xffeeeeee);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xffc6c6c6);
        g.fill(x + 7, y + 33, x + 293, y + 107, 0xff555555);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            int rowY = y + 34 + row * 18;
            if (index >= filtered.size()) break;
            var entry = filtered.get(index);
            boolean selected = menu.clientSelections.contains(entry.item());
            g.fill(x + 8, rowY, x + 285, rowY + 18, selected ? 0xff547d78 : rowAt(mouseX, mouseY) == row ? 0xffaaaaaa : 0xff8b8b8b);
            ItemStack stack = stack(entry);
            g.renderItem(stack, x + 9, rowY + 1);
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), 187);
            g.drawString(font, name, x + 29, rowY + 5, entry.available() ? 0xffffffff : 0xffdddddd, false);
            Component availability = Component.translatable(entry.available() ? "gui.infiniteresourcegenerators.ready" : "gui.infiniteresourcegenerators.locked");
            g.drawString(font, availability, x + 282 - font.width(availability), rowY + 5, entry.available() ? 0xffcaffd4 : 0xffffd3a0, false);
        }
        int maxScroll = Math.max(1, filtered.size() - ROWS);
        int thumbY = y + 34 + (int) (54.0 * scroll / maxScroll);
        g.fill(x + 287, y + 34, x + 292, y + 106, 0xff333333);
        g.fill(x + 287, thumbY, x + 292, thumbY + 18, 0xffdddddd);
        if (filtered.isEmpty()) g.drawString(font, Component.translatable(menu.catalogReady ? "gui.infiniteresourcegenerators.empty" : "gui.infiniteresourcegenerators.loading"), x + 15, y + 62, 0xffffffff, false);

        g.drawString(font, Component.translatable("gui.infiniteresourcegenerators.configured", menu.clientSelections.size(), GeneratorBlockEntity.MAX_SELECTIONS), x + 8, y + 113, 0xff333333, false);
        int iconX = x + 91;
        for (var id : menu.clientSelections.stream().limit(6).toList()) {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) g.renderItem(item.getDefaultInstance(), iconX, y + 109);
            iconX += 18;
        }
        int modeLeft = x + 205;
        boolean modeHover = mouseX >= modeLeft && mouseX < x + 293 && mouseY >= y + 109 && mouseY < y + 126;
        g.fill(modeLeft, y + 109, x + 293, y + 126, modeHover ? 0xffaaaaaa : 0xff8b8b8b);
        Component mode = Component.translatable(menu.clientMode == GeneratorBlockEntity.Mode.ROUND_ROBIN
                ? "gui.infiniteresourcegenerators.round_robin" : "gui.infiniteresourcegenerators.all_at_once");
        g.drawCenteredString(font, mode, modeLeft + 44, y + 114, 0xffffffff);
        String status = switch (menu.status()) {
            case 1 -> menu.clientMode == GeneratorBlockEntity.Mode.ALL_AT_ONCE ? "running_all" : "running_round_robin";
            case 2 -> "full"; case 3 -> "unavailable"; default -> "idle";
        };
        g.drawString(font, Component.translatable("gui.infiniteresourcegenerators." + status), x + 8, y + 128, 0xff333333, false);
        g.drawString(font, Component.translatable("gui.infiniteresourcegenerators.output"), x + 8, y + 144, 0xff333333, false);
        for (var slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xffeeeeee);
            g.fill(sx, sy, sx + 17, sy + 17, 0xff373737);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xff8b8b8b);
        }
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < leftPos + 8 || mouseX >= leftPos + 285 || mouseY < topPos + 34 || mouseY >= topPos + 106) return -1;
        return (int) (mouseY - topPos - 34) / 18;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 205 && mouseX < leftPos + 293 && mouseY >= topPos + 109 && mouseY < topPos + 126) {
            ModNetwork.setMode(menu.containerId, menu.clientMode == GeneratorBlockEntity.Mode.ROUND_ROBIN
                    ? GeneratorBlockEntity.Mode.ALL_AT_ONCE : GeneratorBlockEntity.Mode.ROUND_ROBIN);
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        if (button == 0 && row >= 0 && row + scroll < filtered.size()) {
            var entry = filtered.get(row + scroll);
            if (entry.available()) ModNetwork.select(menu.containerId, entry.item());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftPos + 7 && mouseX < leftPos + 293 && mouseY >= topPos + 33 && mouseY < topPos + 107) {
            scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - ROWS), scroll - (int) Math.signum(delta) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != GLFW.GLFW_KEY_ESCAPE && search.isFocused()) { search.keyPressed(key, scanCode, modifiers); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
