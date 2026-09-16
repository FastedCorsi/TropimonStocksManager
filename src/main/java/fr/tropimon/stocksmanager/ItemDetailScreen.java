package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Fiche complète d'un objet avec répartition par coffre et seuil d'alerte. */
final class ItemDetailScreen extends Screen {
    private static final Identifier PC_BASE = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier PREVIOUS_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final int PANEL_W = 349;
    private static final int PANEL_H = 205;
    private static final int PAGE_SIZE = 5;
    private static final int ROW_H = 23;
    private static final int COLOR_TEXT = 0xFF17242B;
    private static final int COLOR_MUTED = 0xFF52636D;

    private final Screen parent;
    private final String itemId;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private TownChestIndex.ItemAggregate item;
    private List<TownChestIndex.ItemSource> sources = List.of();
    private TextFieldWidget thresholdField;
    private int left;
    private int top;
    private int page;

    ItemDetailScreen(Screen parent, String itemId) {
        super(Text.translatable("screen.tropimon_stocks_manager.detail"));
        this.parent = parent;
        this.itemId = itemId;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        refresh();
        addDrawableChild(new StockPcButton(left + 8, top + 28, 78, 16,
                Text.translatable("screen.tropimon_stocks_manager.history"),
                button -> client.setScreen(new ItemHistoryScreen(this, itemId)), false));
        thresholdField = new TextFieldWidget(textRenderer, left + 172, top + 28, 50, 16,
                Text.translatable("screen.tropimon_stocks_manager.watch_threshold_label"));
        thresholdField.setMaxLength(9);
        thresholdField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        thresholdField.setText(item.watchThreshold() <= 0 ? "" : Integer.toString(item.watchThreshold()));
        addDrawableChild(thresholdField);
        addDrawableChild(new StockPcButton(left + 224, top + 28, 54, 16,
                Text.translatable("screen.tropimon_stocks_manager.save"), button -> saveThreshold(), false));
        addDrawableChild(new StockPcButton(left + 280, top + 28, 61, 16,
                Text.translatable("screen.tropimon_stocks_manager.remove"), button -> clearThreshold(), false));
    }

    private void refresh() {
        if (client == null) return;
        item = index.findItem(TownChestTracker.serverKey(client), itemId);
        sources = item.sourceDetails();
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    private void saveThreshold() {
        int value = 0;
        try {
            if (!thresholdField.getText().isBlank()) value = Integer.parseInt(thresholdField.getText());
        } catch (NumberFormatException ignored) { }
        index.setWatchThreshold(TownChestTracker.serverKey(client), itemId, value);
        StockWatchNotifier.thresholdChanged(client, TownChestTracker.serverKey(client), itemId);
        refresh();
    }

    private void clearThreshold() {
        thresholdField.setText("");
        index.setWatchThreshold(TownChestTracker.serverKey(client), itemId, 0);
        StockWatchNotifier.thresholdChanged(client, TownChestTracker.serverKey(client), itemId);
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clickAreas.clear();
        context.drawTexture(PC_BASE, left, top, PANEL_W, PANEL_H,
                0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        drawBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawTitle(context, mouseX, mouseY);
        drawHeaders(context);
        drawRows(context, mouseX, mouseY);
        drawFooter(context);
    }

    private void drawBackground(DrawContext context) {
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.fill(left + 5, top + 46, left + 344, top + 59, 0xFFD1DAE3);
        context.fill(left + 5, top + 24, left + 344, top + 26, 0xFF8B969E);
    }

    private void drawTitle(DrawContext context, int mouseX, int mouseY) {
        ItemStack icon = index.icon(itemId);
        context.drawItem(icon, left + 10, top + 5);
        String titleValue = item.displayName();
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(titleValue, 255),
                left + PANEL_W / 2, top + 13, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.watch_min"),
                left + 91, top + 32, COLOR_MUTED, false);
        boolean closeHover = mouseX >= left + 331 && mouseX < left + 347
                && mouseY >= top + 5 && mouseY < top + 21;
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, closeHover, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, closeHover);
        clickAreas.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    private void drawHeaders(DrawContext context) {
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.column.chest_name"),
                left + 13, top + 48, COLOR_TEXT, false);
        centered(context, Text.literal("C"), left + 209, top + 48, 0xFF167C92);
        centered(context, Text.literal("S"), left + 239, top + 48, 0xFF8B3DB0);
        centered(context, Text.literal("T"), left + 268, top + 48, COLOR_TEXT);
        centered(context, Text.translatable("screen.tropimon_stocks_manager.column.checked"),
                left + 310, top + 48, COLOR_MUTED);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int sourceIndex = start + row;
            int y = top + 59 + row * ROW_H;
            context.fill(left + 7, y, left + 342, y + ROW_H,
                    row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC);
            context.fill(left + 7, y + ROW_H - 1, left + 342, y + ROW_H, 0xFFCCD6DE);
            if (sourceIndex >= sources.size()) continue;
            TownChestIndex.ItemSource source = sources.get(sourceIndex);
            int freshness = StockUi.freshnessColor(source.freshness());
            context.fill(left + 8, y + 1, left + 11, y + ROW_H - 1, freshness);
            context.drawText(textRenderer, textRenderer.trimToWidth(source.title(), 174),
                    left + 14, y + 3, COLOR_TEXT, false);
            String coords = source.x() + ", " + source.y() + ", " + source.z() + " • " + source.dimension();
            context.drawText(textRenderer, textRenderer.trimToWidth(coords, 174),
                    left + 14, y + 13, COLOR_MUTED, false);
            centered(context, Text.literal(Integer.toString(source.directCount())), left + 209, y + 8, 0xFF167C92);
            centered(context, Text.literal(Integer.toString(source.shulkerCount())), left + 239, y + 8, 0xFF8B3DB0);
            centered(context, Text.literal(Integer.toString(source.total())), left + 268, y + 8, COLOR_TEXT);
            centered(context, Text.literal(StockUi.date(source.checkedAt())), left + 310, y + 8, freshness);
            if (mouseX >= left + 7 && mouseX < left + 342 && mouseY >= y && mouseY < y + ROW_H) {
                context.fill(left + 7, y, left + 10, y + ROW_H, 0xFF3BA4BC);
                context.drawTooltip(textRenderer, List.of(
                        Text.literal(source.title()),
                        Text.literal(coords),
                        Text.translatable("screen.tropimon_stocks_manager.direct_short", source.directCount()),
                        Text.translatable("screen.tropimon_stocks_manager.shulker_short", source.shulkerCount()),
                        Text.translatable("screen.tropimon_stocks_manager.freshness_age", StockUi.age(source.checkedAt()))
                ), mouseX, mouseY);
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
        String footer = Text.translatable("screen.tropimon_stocks_manager.page_sources",
                page + 1, pages(), StockUi.count(sources.size())).getString();
        context.drawText(textRenderer, footer, left + PANEL_W / 2 - textRenderer.getWidth(footer) / 2,
                y, COLOR_TEXT, false);
        if (previous) clickAreas.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--));
        if (next) clickAreas.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++));
    }

    private int pages() { return Math.max(1, (sources.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

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

    private void centered(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
