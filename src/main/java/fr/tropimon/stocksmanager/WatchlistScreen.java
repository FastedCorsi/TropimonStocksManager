package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Liste persistante des seuils, y compris pour les objets actuellement à zéro. */
final class WatchlistScreen extends Screen {
    private static final Identifier PC_BASE = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier PREVIOUS_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final long SNOOZE_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int PANEL_W = 349;
    private static final int PANEL_H = 205;
    private static final int PAGE_SIZE = 5;
    private final Screen parent;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private List<TownChestIndex.WatchStatus> entries = List.of();
    private int left;
    private int top;
    private int page;

    WatchlistScreen(Screen parent) {
        super(Text.translatable("screen.tropimon_stocks_manager.watchlist"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        refresh();
    }

    private void refresh() {
        if (client == null) return;
        entries = index.watchStatuses(TownChestTracker.serverKey(client), System.currentTimeMillis());
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clickAreas.clear();
        context.drawTexture(PC_BASE, left, top, PANEL_W, PANEL_H,
                0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.fill(left + 5, top + 46, left + 344, top + 59, 0xFFD1DAE3);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.alerts.hint"),
                left + 13, top + 32, 0xFF52636D, false);
        context.drawCenteredTextWithShadow(textRenderer, title, left + PANEL_W / 2, top + 13, 0xFFFFFFFF);
        boolean closeHover = mouseX >= left + 331 && mouseX < left + 347
                && mouseY >= top + 5 && mouseY < top + 21;
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, closeHover, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, closeHover);
        clickAreas.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.column.item"),
                left + 39, top + 48, 0xFF17242B, false);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.column.stock"), left + 235, top + 48, 0xFF17242B);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.column.minimum"), left + 280, top + 48, 0xFF52636D);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.column.state"), left + 315, top + 48, 0xFF52636D);
        drawRows(context, mouseX, mouseY);
        drawFooter(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int entryIndex = start + row;
            int y = top + 59 + row * 23;
            context.fill(left + 7, y, left + 342, y + 23, row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC);
            context.fill(left + 7, y + 22, left + 342, y + 23, 0xFFCCD6DE);
            if (entryIndex >= entries.size()) continue;
            TownChestIndex.WatchStatus entry = entries.get(entryIndex);
            long now = System.currentTimeMillis();
            boolean snoozed = entry.snoozed(now);
            ItemStack icon = index.icon(entry.itemId());
            context.drawItem(icon, left + 14, y + 3);
            context.drawText(textRenderer, textRenderer.trimToWidth(entry.displayName(), 175),
                    left + 39, y + 3, 0xFF17242B, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(entry.itemId(), 175),
                    left + 39, y + 13, 0xFF52636D, false);
            int stateColor = snoozed ? 0xFFE39524 : entry.belowThreshold() ? 0xFFD13E4D : 0xFF36A85D;
            centered(context, Text.literal(Integer.toString(entry.current())), left + 235, y + 8, stateColor);
            centered(context, Text.literal(Integer.toString(entry.threshold())), left + 280, y + 8, 0xFF52636D);
            centered(context, Text.translatable(snoozed
                    ? "screen.tropimon_stocks_manager.state.snoozed"
                    : entry.belowThreshold() ? "screen.tropimon_stocks_manager.state.low"
                    : "screen.tropimon_stocks_manager.state.ok"), left + 309, y + 8, stateColor);
            if (mouseX >= left + 7 && mouseX < left + 342 && mouseY >= y && mouseY < y + 23) {
                context.fill(left + 7, y, left + 10, y + 23, stateColor);
            }
            clickAreas.add(new ClickArea(left + 7, y, 304, 23,
                    () -> client.setScreen(new ItemDetailScreen(this, entry.itemId()))));
            if (entry.belowThreshold()) {
                boolean snoozeHover = mouseX >= left + 319 && mouseX < left + 340
                        && mouseY >= y + 2 && mouseY < y + 21;
                StockUi.iconButton(context, left + 319, y + 2, 21, 19, snoozeHover, snoozed);
                StockUi.icon(context, StockUi.Icon.CLOCK, left + 324, y + 5,
                        snoozeHover || snoozed);
                clickAreas.add(new ClickArea(left + 319, y + 2, 21, 19, () -> {
                    index.snoozeAlert(TownChestTracker.serverKey(client), entry.itemId(),
                            snoozed ? 0L : System.currentTimeMillis() + SNOOZE_MILLIS);
                    refresh();
                }));
            }
        }
    }

    private void drawFooter(DrawContext context) {
        int y = top + 178;
        boolean previous = page > 0;
        boolean next = page + 1 < pages();
        if (previous) context.drawTexture(PREVIOUS_ICON, left + 10, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        if (next) context.drawTexture(NEXT_ICON, left + 329, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        String footer = Text.translatable("screen.tropimon_stocks_manager.page_watch",
                page + 1, pages(), StockUi.count(entries.size())).getString();
        context.drawText(textRenderer, footer, left + PANEL_W / 2 - textRenderer.getWidth(footer) / 2,
                y, 0xFF17242B, false);
        if (previous) clickAreas.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--));
        if (next) clickAreas.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++));
    }

    private int pages() { return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private void centered(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (int i = clickAreas.size() - 1; i >= 0; i--) {
                ClickArea area = clickAreas.get(i);
                if (mouseX >= area.x && mouseX < area.x + area.w && mouseY >= area.y && mouseY < area.y + area.h) {
                    area.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        page = Math.max(0, Math.min(pages() - 1, page - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
