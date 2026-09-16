package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Historique quotidien d'un objet, modifiable sans supprimer les coffres indexes. */
final class ItemHistoryScreen extends Screen {
    private static final Identifier PC_BASE = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier PREVIOUS_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final Identifier HISTORY_ICON = Identifier.of("tropimodclient", "guis/commons/buttons/history_button_on.png");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PANEL_W = 349;
    private static final int PANEL_H = 205;
    private static final int PAGE_SIZE = 3;
    private static final int ROW_H = 18;
    private static final int COLOR_TEXT = 0xFF17242B;
    private static final int COLOR_MUTED = 0xFF52636D;

    private final Screen parent;
    private final String itemId;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private List<TownChestIndex.ItemHistoryEntry> entries = List.of();
    private ButtonWidget clearAllButton;
    private ButtonWidget rangeButton;
    private TownChestIndex.HistoryStats stats;
    private int rangeDays = 30;
    private int left;
    private int top;
    private int page;
    private boolean confirmClearAll;
    private String hoveredAction = "";

    ItemHistoryScreen(Screen parent, String itemId) {
        super(Text.translatable("screen.tropimon_stocks_manager.history"));
        this.parent = parent;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        refresh();
        clearAllButton = addDrawableChild(new StockPcButton(left + 255, top + 28, 86, 16,
                Text.translatable("screen.tropimon_stocks_manager.history.clear_all"),
                button -> clearAll(), false));
        clearAllButton.active = !entries.isEmpty();
        rangeButton = addDrawableChild(new StockPcButton(left + 166, top + 28, 84, 16,
                Text.translatable("screen.tropimon_stocks_manager.history.range", rangeDays),
                button -> cycleRange(), false));
    }

    private String server() {
        return TownChestTracker.serverKey(client);
    }

    private void refresh() {
        if (client == null) return;
        entries = index.itemHistory(server(), itemId);
        stats = index.historyStats(server(), itemId, rangeDays);
        page = Math.max(0, Math.min(page, pages() - 1));
        if (clearAllButton != null) clearAllButton.active = !entries.isEmpty();
    }

    private void cycleRange() {
        rangeDays = rangeDays == 7 ? 30 : rangeDays == 30 ? 90 : 7;
        rangeButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.history.range", rangeDays));
        refresh();
    }

    private void clearAll() {
        if (!confirmClearAll) {
            confirmClearAll = true;
            clearAllButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.history.confirm"));
            return;
        }
        index.deleteAllItemHistory(server(), itemId);
        confirmClearAll = false;
        clearAllButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.history.clear_all"));
        refresh();
    }

    private void deleteEntry(String date) {
        index.deleteItemHistoryEntry(server(), itemId, date);
        confirmClearAll = false;
        clearAllButton.setMessage(Text.translatable("screen.tropimon_stocks_manager.history.clear_all"));
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clickAreas.clear();
        hoveredAction = "";
        context.drawTexture(PC_BASE, left, top, PANEL_W, PANEL_H,
                0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.fill(left + 5, top + 96, left + 344, top + 109, 0xFFD1DAE3);
        context.fill(left + 5, top + 24, left + 344, top + 26, 0xFF8B969E);
        super.render(context, mouseX, mouseY, delta);
        drawTitle(context, mouseX, mouseY);
        drawGraph(context);
        drawHeaders(context);
        drawRows(context, mouseX, mouseY);
        drawFooter(context);
        if (!hoveredAction.isBlank()) {
            context.drawTooltip(textRenderer, Text.literal(hoveredAction), mouseX, mouseY);
        }
    }

    private void drawTitle(DrawContext context, int mouseX, int mouseY) {
        TownChestIndex.ItemAggregate item = index.findItem(server(), itemId);
        ItemStack icon = index.icon(itemId);
        context.drawItem(icon, left + 10, top + 5);
        context.drawTexture(HISTORY_ICON, left + 28, top + 6, 14, 14, 0, 0, 16, 16, 16, 16);
        String value = Text.translatable("screen.tropimon_stocks_manager.history.title",
                item.displayName()).getString();
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(value, 255),
                left + PANEL_W / 2, top + 13, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.history.latest"),
                left + 13, top + 32, COLOR_MUTED, false);

        boolean closeHover = inside(mouseX, mouseY, left + 331, top + 5, 16, 16);
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, closeHover, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, closeHover);
        clickAreas.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    private void drawHeaders(DrawContext context) {
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.history.column.date"),
                left + 14, top + 98, COLOR_TEXT, false);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.column.total"),
                left + 212, top + 98, COLOR_TEXT);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.history.column.delta"),
                left + 274, top + 98, COLOR_MUTED);
    }

    private void drawGraph(DrawContext context) {
        int x = left + 13, y = top + 53, graphW = 206, graphH = 37;
        context.fill(x, y, x + graphW, y + graphH, 0xFFD1DAE3);
        context.fill(x, y + graphH - 1, x + graphW, y + graphH, 0xFF8B969E);
        if (stats != null && !stats.entries().isEmpty()) {
            int max = stats.entries().stream().mapToInt(TownChestIndex.ItemHistoryEntry::total).max().orElse(1);
            int count = stats.entries().size();
            int barW = Math.max(1, graphW / Math.max(rangeDays, count));
            int startX = x + graphW - count * barW;
            for (int i = 0; i < count; i++) {
                int height = Math.max(1, stats.entries().get(i).total() * (graphH - 3) / Math.max(1, max));
                context.fill(startX + i * barW, y + graphH - 1 - height,
                        startX + (i + 1) * barW, y + graphH - 1, 0xFF3BA4BC);
            }
        }
        String average = Text.translatable("screen.tropimon_stocks_manager.history.average",
                stats == null ? "0" : String.format(java.util.Locale.ROOT, "%.1f",
                        stats.averageDailyConsumption())).getString();
        context.drawText(textRenderer, average, left + 227, top + 58, COLOR_TEXT, false);
        String remaining = stats == null || stats.estimatedDaysRemaining() < 0
                ? Text.translatable("screen.tropimon_stocks_manager.history.estimate.none").getString()
                : Text.translatable("screen.tropimon_stocks_manager.history.estimate",
                        stats.estimatedDaysRemaining()).getString();
        context.drawText(textRenderer, textRenderer.trimToWidth(remaining, 105), left + 227, top + 74,
                COLOR_MUTED, false);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int entryIndex = start + row;
            int y = top + 109 + row * ROW_H;
            context.fill(left + 7, y, left + 342, y + ROW_H,
                    row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC);
            context.fill(left + 7, y + ROW_H - 1, left + 342, y + ROW_H, 0xFFCCD6DE);
            if (entryIndex >= entries.size()) continue;

            TownChestIndex.ItemHistoryEntry entry = entries.get(entryIndex);
            context.drawText(textRenderer, displayDate(entry.date()), left + 14, y + 2, COLOR_TEXT, false);
            context.drawText(textRenderer, StockUi.date(entry.recordedAt()), left + 88, y + 2, COLOR_MUTED, false);
            centered(context, Text.literal(StockUi.count(entry.total())), left + 212, y + 5, COLOR_TEXT);
            String delta = entry.hasPrevious() ? StockUi.delta(entry.delta()) : "—";
            int deltaColor = entry.delta() < 0 ? 0xFFC33B4A : entry.delta() > 0 ? 0xFF268B4D : COLOR_MUTED;
            centered(context, Text.literal(delta), left + 274, y + 5, deltaColor);

            boolean deleteHover = inside(mouseX, mouseY, left + 317, y + 1, 17, 15);
            StockUi.iconButton(context, left + 317, y + 1, 17, 15, deleteHover, false);
            StockUi.icon(context, StockUi.Icon.CLOSE, left + 320, y + 3, deleteHover);
            if (deleteHover) hoveredAction = Text.translatable(
                    "screen.tropimon_stocks_manager.history.delete_day").getString();
            clickAreas.add(new ClickArea(left + 317, y + 1, 17, 15,
                    () -> deleteEntry(entry.date())));
        }
        if (entries.isEmpty()) {
            Text empty = Text.translatable("screen.tropimon_stocks_manager.history.empty");
            centered(context, empty, left + PANEL_W / 2, top + 130, COLOR_MUTED);
        }
    }

    private void drawFooter(DrawContext context) {
        boolean previous = page > 0;
        boolean next = page + 1 < pages();
        if (previous) context.drawTexture(PREVIOUS_ICON, left + 10, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        if (next) context.drawTexture(NEXT_ICON, left + 329, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        String footer = Text.translatable("screen.tropimon_stocks_manager.history.page",
                page + 1, pages(), StockUi.count(entries.size())).getString();
        context.drawText(textRenderer, footer, left + PANEL_W / 2 - textRenderer.getWidth(footer) / 2,
                top + 178, COLOR_TEXT, false);
        if (previous) clickAreas.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--));
        if (next) clickAreas.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++));
    }

    private static String displayDate(String value) {
        try {
            return DISPLAY_DATE.format(LocalDate.parse(value));
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private int pages() { return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (int i = clickAreas.size() - 1; i >= 0; i--) {
                ClickArea area = clickAreas.get(i);
                if (inside((int) mouseX, (int) mouseY, area.x, area.y, area.w, area.h)) {
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

    private void centered(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
