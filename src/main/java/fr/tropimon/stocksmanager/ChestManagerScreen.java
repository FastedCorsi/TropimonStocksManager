package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Organisation locale et nettoyage prudent des coffres mémorisés. */
final class ChestManagerScreen extends Screen {
    private static final Identifier PC = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier TOWN = Identifier.of("tropimodclient", "guis/town/town_favicon.png");
    private static final Identifier STAR = Identifier.of("tropimodclient", "guis/commons/icons/star_icon.png");
    private static final Identifier PREVIOUS = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final int W = 349, H = 205, PAGE = 5, ROW = 23;
    private final Screen parent;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clicks = new ArrayList<>();
    private List<TownChestIndex.ChestInfo> entries = List.of();
    private int left, top, page;
    private ButtonWidget cleanButton;
    private boolean confirmClean;

    ChestManagerScreen(Screen parent) {
        super(Text.translatable("screen.tropimon_stocks_manager.chests"));
        this.parent = parent;
    }

    @Override protected void init() {
        left = (width - W) / 2; top = (height - H) / 2;
        refresh();
        cleanButton = addDrawableChild(new StockPcButton(left + 218, top + 28, 123, 16,
                Text.translatable("screen.tropimon_stocks_manager.chests.clean"), button -> clean(), false));
        cleanButton.active = entries.stream().anyMatch(TownChestIndex.ChestInfo::removable);
    }

    private String server() { return TownChestTracker.serverKey(client); }
    private void refresh() {
        if (client == null) return;
        entries = index.chests(server());
        page = Math.max(0, Math.min(page, pages() - 1));
        if (cleanButton != null) cleanButton.active = entries.stream().anyMatch(TownChestIndex.ChestInfo::removable);
    }

    private void clean() {
        if (!confirmClean) {
            confirmClean = true;
            cleanButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.confirm"));
            return;
        }
        index.cleanupMissing(server());
        confirmClean = false;
        cleanButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.chests.clean"));
        refresh();
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta); clicks.clear();
        context.drawTexture(PC, left, top, W, H, 0, 0, W, H, W, H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.fill(left + 5, top + 46, left + 344, top + 59, 0xFFD1DAE3);
        context.drawTexture(TOWN, left + 10, top + 6, 14, 14, 0, 0, 14, 14, 14, 14);
        context.drawCenteredTextWithShadow(textRenderer, title, left + W / 2, top + 13, 0xFFFFFFFF);
        drawClose(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.chests.column.name"),
                left + 39, top + 48, 0xFF17242B, false);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.chests.column.state"),
                left + 258, top + 48, 0xFF52636D, false);
        drawRows(context, mouseX, mouseY); drawFooter(context);
    }

    private void drawClose(DrawContext context, int mx, int my) {
        boolean hovered = inside(mx, my, left + 331, top + 5, 16, 16);
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, hovered, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, hovered);
        clicks.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    private void drawRows(DrawContext context, int mx, int my) {
        for (int row = 0; row < PAGE; row++) {
            int i = page * PAGE + row, y = top + 59 + row * ROW;
            context.fill(left + 7, y, left + 342, y + ROW, row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC);
            context.fill(left + 7, y + ROW - 1, left + 342, y + ROW, 0xFFCCD6DE);
            if (i >= entries.size()) continue;
            TownChestIndex.ChestInfo chest = entries.get(i);
            boolean hovered = inside(mx, my, left + 7, y, 335, ROW);
            if (hovered) context.fill(left + 7, y, left + 10, y + ROW, 0xFF3BA4BC);
            context.drawItem(new ItemStack(Items.CHEST), left + 14, y + 3);
            if (chest.favorite()) context.drawTexture(STAR, left + 28, y + 2, 9, 9,
                    0, 0, 14, 14, 14, 14);
            context.drawText(textRenderer, textRenderer.trimToWidth(chest.displayName(), 205),
                    left + 39, y + 3, 0xFF17242B, false);
            String details = chest.x() + ", " + chest.y() + ", " + chest.z()
                    + (chest.tags().isEmpty() ? "" : "  #" + String.join(" #", chest.tags()));
            context.drawText(textRenderer, textRenderer.trimToWidth(details, 205), left + 39, y + 13,
                    0xFF52636D, false);
            boolean old = chest.freshness() == TownChestIndex.Freshness.STALE;
            Text state = Text.translatable(chest.removable()
                    ? "screen.tropimon_stocks_manager.chests.missing"
                    : chest.suspectedMissing() ? "screen.tropimon_stocks_manager.chests.suspect"
                    : old ? "screen.tropimon_stocks_manager.chests.old"
                    : "screen.tropimon_stocks_manager.chests.present");
            int color = chest.removable() ? 0xFFC33B4A
                    : chest.suspectedMissing() || old ? 0xFFE39524 : 0xFF268B4D;
            context.drawText(textRenderer, state, left + 258, y + 8, color, false);
            clicks.add(new ClickArea(left + 7, y, 335, ROW,
                    () -> client.setScreen(new ChestEditScreen(this, chest.id()))));
        }
    }

    private void drawFooter(DrawContext context) {
        if (page > 0) { context.drawTexture(PREVIOUS, left + 10, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28); clicks.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--)); }
        if (page + 1 < pages()) { context.drawTexture(NEXT, left + 329, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28); clicks.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++)); }
        String value = Text.translatable("screen.tropimon_stocks_manager.chests.page",
                page + 1, pages(), entries.size()).getString();
        context.drawText(textRenderer, value, left + W / 2 - textRenderer.getWidth(value) / 2,
                top + 178, 0xFF17242B, false);
    }

    private int pages() { return Math.max(1, (entries.size() + PAGE - 1) / PAGE); }
    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) for (int i = clicks.size() - 1; i >= 0; i--) {
            ClickArea area = clicks.get(i); if (inside(x, y, area.x, area.y, area.w, area.h)) {
                area.action.run(); return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
