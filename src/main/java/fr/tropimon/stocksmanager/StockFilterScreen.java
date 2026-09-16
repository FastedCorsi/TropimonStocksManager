package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtres combinables et vues nommées, sans moteur de requête supplémentaire. */
final class StockFilterScreen extends Screen {
    private static final Identifier PC = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier FILTER = Identifier.of("tropimodclient", "guis/commons/buttons/filters_button.png");
    private static final Identifier SORT = Identifier.of("tropimodclient", "guis/commons/buttons/sort_button.png");
    private static final int W = 349, H = 205;
    private final TownChestScreen parent;
    private final TownChestIndex index = TownChestIndex.get();
    private final List<ClickArea> clicks = new ArrayList<>();
    private TownChestIndex.SortOrder sort;
    private boolean lowOnly, staleOnly;
    private List<TownChestIndex.SavedView> views = List.of();
    private TextFieldWidget name;
    private StockPcButton sortButton, lowButton, staleButton;
    private int left, top;

    StockFilterScreen(TownChestScreen parent) {
        super(Text.translatable("screen.tropimon_stocks_manager.filters"));
        this.parent = parent; sort = parent.sort(); lowOnly = parent.lowOnly(); staleOnly = parent.staleOnly();
    }

    @Override protected void init() {
        left = (width - W) / 2; top = (height - H) / 2; refresh();
        sortButton = addDrawableChild(new StockPcButton(left + 33, top + 30, 101, 16,
                sortText(), button -> cycleSort(), false));
        lowButton = addDrawableChild(new StockPcButton(left + 139, top + 30, 92, 16,
                lowText(), button -> {
                    lowOnly = !lowOnly; button.setMessage(lowText()); lowButton.setSelected(lowOnly);
                }, lowOnly));
        staleButton = addDrawableChild(new StockPcButton(left + 236, top + 30, 97, 16,
                staleText(), button -> {
                    staleOnly = !staleOnly; button.setMessage(staleText()); staleButton.setSelected(staleOnly);
                }, staleOnly));
        name = addDrawableChild(new TextFieldWidget(textRenderer, left + 12, top + 157, 198, 18,
                Text.translatable("screen.tropimon_stocks_manager.views.name")));
        name.setMaxLength(32); name.setPlaceholder(Text.translatable("screen.tropimon_stocks_manager.views.name"));
        addDrawableChild(new StockPcButton(left + 215, top + 157, 118, 18,
                Text.translatable("screen.tropimon_stocks_manager.views.save"), button -> saveView(), false));
    }

    private String server() { return TownChestTracker.serverKey(client); }
    private void refresh() { if (client != null) views = index.savedViews(server()); }
    private void cycleSort() {
        TownChestIndex.SortOrder[] values = TownChestIndex.SortOrder.values();
        sort = values[(sort.ordinal() + 1) % values.length]; sortButton.setMessage(sortText());
    }
    private Text sortText() { return Text.translatable("screen.tropimon_stocks_manager.sort."
            + sort.name().toLowerCase(Locale.ROOT)); }
    private Text lowText() { return Text.translatable(lowOnly ? "screen.tropimon_stocks_manager.filter.low.on"
            : "screen.tropimon_stocks_manager.filter.low.off"); }
    private Text staleText() { return Text.translatable(staleOnly ? "screen.tropimon_stocks_manager.filter.stale.on"
            : "screen.tropimon_stocks_manager.filter.stale.off"); }
    private void saveView() {
        index.saveView(server(), new TownChestIndex.SavedView(name.getText(), parent.query(), parent.scope(),
                parent.namespace(), sort, lowOnly, staleOnly));
        name.setText(""); refresh();
    }

    @Override public void render(DrawContext context, int mx, int my, float delta) {
        renderBackground(context, mx, my, delta); clicks.clear();
        context.drawTexture(PC, left, top, W, H, 0, 0, W, H, W, H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.drawTexture(FILTER, left + 10, top + 6, 14, 14, 0, 0, 16, 16, 16, 16);
        context.drawCenteredTextWithShadow(textRenderer, title, left + W / 2, top + 13, 0xFFFFFFFF);
        drawClose(context, mx, my);
        context.drawTexture(SORT, left + 14, top + 30, 16, 16, 0, 0, 16, 16, 16, 16);
        super.render(context, mx, my, delta);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.views"),
                left + 13, top + 52, 0xFF17242B, false);
        drawViews(context, mx, my);
        String active = Text.translatable("screen.tropimon_stocks_manager.filters.active",
                parent.scope().name(), parent.namespace().name()).getString();
        context.drawText(textRenderer, textRenderer.trimToWidth(active, 315), left + 13, top + 181,
                0xFF52636D, false);
    }

    private void drawViews(DrawContext context, int mx, int my) {
        for (int row = 0; row < 4; row++) {
            int y = top + 64 + row * 22;
            context.fill(left + 10, y, left + 338, y + 21, row % 2 == 0 ? 0xFFDCE5EC : 0xFFE7EDF3);
            if (row >= views.size()) continue;
            TownChestIndex.SavedView view = views.get(row);
            context.drawText(textRenderer, textRenderer.trimToWidth(view.name(), 206), left + 16, y + 7,
                    0xFF17242B, false);
            String detail = view.sort().name() + (view.lowOnly() ? " • MIN" : "")
                    + (view.staleOnly() ? " • 24H+" : "");
            context.drawText(textRenderer, textRenderer.trimToWidth(detail, 80), left + 224, y + 7,
                    0xFF52636D, false);
            clicks.add(new ClickArea(left + 10, y, 295, 21, () -> {
                parent.applyView(view); client.setScreen(parent);
            }));
            boolean hovered = ChestManagerScreen.inside(mx, my, left + 308, y + 2, 25, 17);
            StockUi.iconButton(context, left + 308, y + 2, 25, 17, hovered, false);
            StockUi.icon(context, StockUi.Icon.CLOSE, left + 315, y + 5, hovered);
            clicks.add(new ClickArea(left + 308, y + 2, 25, 17, () -> { index.deleteView(server(), view.name()); refresh(); }));
        }
    }

    private void drawClose(DrawContext context, int mx, int my) {
        boolean hovered = ChestManagerScreen.inside(mx, my, left + 331, top + 5, 16, 16);
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, hovered, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, hovered);
        clicks.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) for (int i = clicks.size() - 1; i >= 0; i--) {
            ClickArea area = clicks.get(i);
            if (ChestManagerScreen.inside(x, y, area.x, area.y, area.w, area.h)) { area.action.run(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void close() {
        parent.applyFilters(sort, lowOnly, staleOnly);
        if (client != null) client.setScreen(parent);
    }
    @Override public boolean shouldPause() { return false; }
    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
