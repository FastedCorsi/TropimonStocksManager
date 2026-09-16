package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tableau de stocks basé sur l'interface du PC Cobblemon utilisée par Team Saver. */
public final class TownChestScreen extends Screen {
    private static final Identifier PC_BASE = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier PREVIOUS_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT_ICON = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final int PANEL_W = 349;
    private static final int PANEL_H = 205;
    private static final int PAGE_SIZE = 5;
    private static final int ROW_H = 23;
    private static final int COLOR_TEXT = 0xFF17242B;
    private static final int COLOR_MUTED = 0xFF52636D;
    private static final int COLOR_CHEST = 0xFF167C92;
    private static final int COLOR_SHULKER = 0xFF8B3DB0;
    private static final int COLOR_TOTAL = 0xFF17242B;

    private final Screen parent;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clickAreas = new ArrayList<>();
    private TownChestIndex.Scope scope = TownChestIndex.Scope.ALL;
    private TownChestIndex.Namespace namespace = TownChestIndex.Namespace.ALL;
    private TownChestIndex.SortOrder sort = TownChestIndex.SortOrder.QUANTITY;
    private boolean lowOnly;
    private boolean staleOnly;
    private List<TownChestIndex.ItemAggregate> results = List.of();
    private TextFieldWidget search;
    private int page;
    private int selectedIndex = -1;
    private int left;
    private int top;
    private TownChestIndex.ItemAggregate hoveredItem;
    private String hoveredAction = "";
    private String query = "";
    private StockPcButton indexButton;

    public TownChestScreen(Screen parent) {
        super(Text.translatable("screen.tropimon_stocks_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        indexButton = addDrawableChild(new StockPcButton(left + 6, top + 5, 60, 16,
                indexButtonText(), button -> toggleIndexing(), TownChestAutoIndexer.isActive()));

        addScopeButton(left + 8, 42, TownChestIndex.Scope.ALL, "filter.all");
        addScopeButton(left + 52, 58, TownChestIndex.Scope.DIRECT, "filter.direct_short");
        addScopeButton(left + 112, 64, TownChestIndex.Scope.SHULKER, "filter.shulker_short");
        addDrawableChild(new StockPcButton(left + 178, top + 28, 68, 16,
                namespaceButtonText(), button -> cycleNamespace(), false));

        search = new TextFieldWidget(textRenderer, left + 248, top + 28, 93, 16,
                Text.translatable("screen.tropimon_stocks_manager.search"));
        search.setPlaceholder(Text.translatable("screen.tropimon_stocks_manager.search_short"));
        search.setMaxLength(80);
        search.setText(query);
        search.setChangedListener(value -> {
            query = value;
            page = 0;
            refresh();
        });
        addDrawableChild(search);
        refresh();
    }

    private void addScopeButton(int x, int buttonWidth, TownChestIndex.Scope value, String key) {
        StockPcButton button = new StockPcButton(x, top + 28, buttonWidth, 16,
                Text.translatable("screen.tropimon_stocks_manager." + key), ignored -> setScope(value), scope == value);
        addDrawableChild(button);
    }

    void refresh() {
        if (client == null) return;
        results = index.search(TownChestTracker.serverKey(client), query, scope, namespace,
                sort, lowOnly, staleOnly);
        page = Math.max(0, Math.min(page, pages() - 1));
        selectedIndex = Math.min(selectedIndex, results.size() - 1);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        clickAreas.clear();
        hoveredItem = null;
        hoveredAction = "";
        context.drawTexture(PC_BASE, left, top, PANEL_W, PANEL_H,
                0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        drawTitleBar(context, mouseX, mouseY);
        drawTableBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawHeaders(context);
        drawRows(context, mouseX, mouseY);
        drawFooter(context, mouseX, mouseY);
        drawTooltip(context, mouseX, mouseY);
    }

    private void drawTitleBar(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer, title, left + PANEL_W / 2, top + 13, 0xFFFFFFFF);
        boolean closeHover = inside(mouseX, mouseY, left + 331, top + 5, 16, 16);
        if (closeHover) hoveredAction = Text.translatable("screen.tropimon_stocks_manager.close").getString();
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, closeHover, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, closeHover);
        clickAreas.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    private void drawTableBackground(DrawContext context) {
        int x = left + 5;
        int y = top + 24;
        context.fill(x, y, x + 339, top + 189, 0xFFE7EDF3);
        context.fill(x, y, x + 339, y + 2, 0xFF8B969E);
        context.fill(x, top + 46, x + 339, top + 59, 0xFFD1DAE3);
        drawOutline(context, x, y, 339, 165, 0xFF20282D);
    }

    private void drawHeaders(DrawContext context) {
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.column.item"),
                left + 39, top + 48, COLOR_TEXT, false);
        drawCentered(context, Text.translatable("screen.tropimon_stocks_manager.column.chest"), left + 213, top + 48, COLOR_CHEST);
        drawCentered(context, Text.translatable("screen.tropimon_stocks_manager.column.shulker"), left + 268, top + 48, COLOR_SHULKER);
        drawCentered(context, Text.translatable("screen.tropimon_stocks_manager.column.total"), left + 322, top + 48, COLOR_TOTAL);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int indexInResults = start + row;
            int y = top + 59 + row * ROW_H;
            boolean hovered = inside(mouseX, mouseY, left + 7, y, 335, ROW_H);
            boolean selected = indexInResults == selectedIndex;
            int color = selected ? 0xFFC6E2EA : row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC;
            context.fill(left + 7, y, left + 342, y + ROW_H, color);
            context.fill(left + 7, y + ROW_H - 1, left + 342, y + ROW_H, 0xFFCCD6DE);
            if (hovered) context.fill(left + 7, y, left + 10, y + ROW_H, 0xFF3BA4BC);
            if (indexInResults >= results.size()) continue;

            TownChestIndex.ItemAggregate item = results.get(indexInResults);
            int freshness = StockUi.freshnessColor(item.freshness());
            context.fill(left + 8, y + 1, left + 11, y + ROW_H - 1, freshness);
            ItemStack icon = this.index.icon(item.itemId());
            context.drawItem(icon, left + 14, y + 3);
            context.drawText(textRenderer, textRenderer.trimToWidth(item.displayName(), 151),
                    left + 39, y + 3, COLOR_TEXT, false);
            context.drawText(textRenderer, textRenderer.trimToWidth(item.itemId(), 151),
                    left + 39, y + 13, COLOR_MUTED, false);
            drawCentered(context, Text.literal(Integer.toString(item.directCount())), left + 213, y + 8, COLOR_CHEST);
            drawCentered(context, Text.literal(Integer.toString(item.shulkerCount())), left + 268, y + 8, COLOR_SHULKER);
            drawCentered(context, Text.literal(Integer.toString(item.total())), left + 322, y + 3, COLOR_TOTAL);
            String delta = item.hasDailyComparison() ? StockUi.delta(item.dailyDelta()) : "—";
            drawCentered(context, Text.literal(delta), left + 322, y + 13,
                    item.dailyDelta() < 0 ? 0xFFC33B4A : item.dailyDelta() > 0 ? 0xFF268B4D : COLOR_MUTED);
            if (item.belowThreshold()) {
                context.drawTextWithShadow(textRenderer, "!", left + 184, y + 3, 0xFFFF5252);
            }
            clickAreas.add(new ClickArea(left + 7, y, 335, ROW_H,
                    () -> client.setScreen(new ItemDetailScreen(this, item.itemId()))));
            if (hovered) hoveredItem = item;
        }
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        int y = top + 178;
        boolean previous = page > 0;
        boolean next = page + 1 < pages();
        if (previous) context.drawTexture(PREVIOUS_ICON, left + 10, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        if (next) context.drawTexture(NEXT_ICON, left + 329, top + 175, 7, 14,
                0, 0, 14, 28, 14, 28);
        String footer = Text.translatable("screen.tropimon_stocks_manager.page",
                page + 1, pages(), StockUi.count(results.size())).getString();
        context.drawText(textRenderer, footer, left + PANEL_W / 2 - textRenderer.getWidth(footer) / 2,
                y, COLOR_TEXT, false);
        if (previous) clickAreas.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--));
        if (next) clickAreas.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++));
    }

    private void drawTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!hoveredAction.isBlank()) {
            context.drawTooltip(textRenderer, Text.literal(hoveredAction), mouseX, mouseY);
            return;
        }
        if (hoveredItem == null) return;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(hoveredItem.displayName()));
        lines.add(Text.translatable("screen.tropimon_stocks_manager.direct_short", hoveredItem.directCount()));
        lines.add(Text.translatable("screen.tropimon_stocks_manager.shulker_short", hoveredItem.shulkerCount()));
        lines.add(Text.translatable("screen.tropimon_stocks_manager.total_short", hoveredItem.total()));
        lines.add(Text.translatable("screen.tropimon_stocks_manager.freshness_age",
                StockUi.age(hoveredItem.oldestCheckedAt())));
        if (hoveredItem.hasDailyComparison()) {
            lines.add(Text.translatable("screen.tropimon_stocks_manager.daily_change",
                    hoveredItem.previousTotal(), hoveredItem.directCount() + hoveredItem.shulkerCount(),
                    StockUi.delta(hoveredItem.dailyDelta())));
        }
        if (hoveredItem.watchThreshold() > 0) {
            lines.add(Text.translatable("screen.tropimon_stocks_manager.watch_threshold",
                    hoveredItem.watchThreshold()));
        }
        hoveredItem.sourceCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(6)
                .forEach(source -> lines.add(Text.literal(source.getKey() + " ×" + source.getValue())));
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private void setScope(TownChestIndex.Scope value) {
        scope = value;
        page = 0;
        clearAndInit();
    }

    TownChestIndex.Scope scope() { return scope; }
    TownChestIndex.Namespace namespace() { return namespace; }
    TownChestIndex.SortOrder sort() { return sort; }
    boolean lowOnly() { return lowOnly; }
    boolean staleOnly() { return staleOnly; }
    String query() { return query; }

    void applyView(TownChestIndex.SavedView view) {
        scope = view.scope(); namespace = view.namespace(); sort = view.sort();
        lowOnly = view.lowOnly(); staleOnly = view.staleOnly(); query = view.query(); page = 0;
    }

    void applyFilters(TownChestIndex.SortOrder sort, boolean lowOnly, boolean staleOnly) {
        this.sort = sort; this.lowOnly = lowOnly; this.staleOnly = staleOnly; page = 0;
    }

    private void cycleNamespace() {
        TownChestIndex.Namespace[] values = TownChestIndex.Namespace.values();
        namespace = values[(namespace.ordinal() + 1) % values.length];
        page = 0;
        clearAndInit();
    }

    private String namespaceLabel() {
        return Text.translatable("screen.tropimon_stocks_manager.namespace_short."
                + namespace.name().toLowerCase(Locale.ROOT)).getString();
    }

    private Text namespaceButtonText() {
        return Text.translatable("screen.tropimon_stocks_manager.namespace_button", namespaceLabel());
    }

    private Text indexButtonText() {
        return Text.translatable(TownChestAutoIndexer.isActive()
                ? "screen.tropimon_stocks_manager.index_stop_short"
                : "screen.tropimon_stocks_manager.index_start_short");
    }

    private void toggleIndexing() {
        TownChestAutoIndexer.toggle(client);
        indexButton.setMessage(indexButtonText());
        indexButton.setSelected(TownChestAutoIndexer.isActive());
    }

    private int pages() { return Math.max(1, (results.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

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
        int direction = -(int) Math.signum(verticalAmount);
        page = Math.max(0, Math.min(pages() - 1, page + direction));
        return true;
    }

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
    @Override
    public boolean shouldPause() { return false; }

    private void drawCentered(DrawContext context, Text text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    private static void drawOutline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
