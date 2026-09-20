package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Détail de la dernière couverture observée pendant l'indexation en marche. */
final class CoverageScreen extends FittedScreen {
    private static final Identifier PC = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier CLOCK = Identifier.of("tropimodclient", "guis/commons/icons/clock_icon.png");
    private static final Identifier CROSS = Identifier.of("tropimodclient", "guis/commons/icons/validation_cross_icon.png");
    private static final Identifier RELOAD = Identifier.of("tropimodclient", "guis/pokeradar/pokeradar_reload_button.png");
    private static final Identifier PREVIOUS = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_previous.png");
    private static final Identifier NEXT = Identifier.of("cobblemon", "textures/gui/pc/pc_arrow_next.png");
    private static final int W = 349, H = 205, PAGE = 5, ROW = 23;
    private final Screen parent;
    private final List<ClickArea> clicks = new ArrayList<>();
    private int left, top, page;

    CoverageScreen(Screen parent) {
        super(Text.translatable("screen.tropimon_stocks_manager.coverage"), 357, 213);
        this.parent = parent;
    }

    @Override protected void initContent() { left = (width - W) / 2; top = (height - H) / 2; }

    @Override public void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        clicks.clear();
        TownChestAutoIndexer.Status status = TownChestAutoIndexer.status();
        List<TownChestAutoIndexer.CoverageEntry> entries = status.entries();
        page = Math.max(0, Math.min(page, pages(entries) - 1));
        context.drawTexture(PC, left, top, W, H, 0, 0, W, H, W, H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        context.fill(left + 5, top + 46, left + 344, top + 59, 0xFFD1DAE3);
        context.drawCenteredTextWithShadow(textRenderer, title, left + W / 2, top + 13, 0xFFFFFFFF);
        drawClose(context, mouseX, mouseY);
        String summary = Text.translatable("screen.tropimon_stocks_manager.coverage.summary",
                status.detected(), status.indexed(), status.old(), status.inaccessible()).getString();
        context.drawText(textRenderer, textRenderer.trimToWidth(summary, 318), left + 13, top + 32,
                0xFF52636D, false);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.coverage.column.position"),
                left + 39, top + 48, 0xFF17242B, false);
        context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.coverage.column.state"),
                left + 185, top + 48, 0xFF17242B, false);
        drawRows(context, entries);
        drawFooter(context, entries);
        renderWidgets(context, mouseX, mouseY, delta);
    }

    private void drawClose(DrawContext context, int mx, int my) {
        boolean hovered = inside(mx, my, left + 331, top + 5, 16, 16);
        StockUi.iconButton(context, left + 331, top + 5, 16, 16, hovered, false);
        StockUi.icon(context, StockUi.Icon.CLOSE, left + 333, top + 7, hovered);
        clicks.add(new ClickArea(left + 331, top + 5, 16, 16, this::close));
    }

    private void drawRows(DrawContext context, List<TownChestAutoIndexer.CoverageEntry> entries) {
        for (int row = 0; row < PAGE; row++) {
            int index = page * PAGE + row;
            int y = top + 59 + row * ROW;
            context.fill(left + 7, y, left + 342, y + ROW, row % 2 == 0 ? 0xFFE7EDF3 : 0xFFDCE5EC);
            context.fill(left + 7, y + ROW - 1, left + 342, y + ROW, 0xFFCCD6DE);
            if (index >= entries.size()) continue;
            TownChestAutoIndexer.CoverageEntry entry = entries.get(index);
            Identifier icon = entry.inaccessible() ? CROSS
                    : entry.reason() == TownChestAutoIndexer.CoverageReason.PENDING ? RELOAD : CLOCK;
            int size = icon == CLOCK ? 10 : 12;
            int source = icon == CLOCK ? 10 : 16;
            context.drawTexture(icon, left + 14, y + 6, size, size, 0, 0, source, source, source, source);
            String coordinates = entry.pos().getX() + ", " + entry.pos().getY() + ", " + entry.pos().getZ();
            context.drawText(textRenderer, coordinates, left + 39, y + 4, 0xFF17242B, false);
            context.drawText(textRenderer, entry.checkedAt() <= 0 ? "—" : StockUi.age(entry.checkedAt()),
                    left + 39, y + 14, 0xFF52636D, false);
            Text reason = Text.translatable("screen.tropimon_stocks_manager.coverage.reason."
                    + entry.reason().name().toLowerCase(Locale.ROOT));
            int color = entry.inaccessible() ? 0xFFC33B4A : entry.old() ? 0xFFE39524
                    : entry.upToDate() ? 0xFF268B4D : 0xFF167C92;
            context.drawText(textRenderer, textRenderer.trimToWidth(reason.getString(), 145),
                    left + 185, y + 9, color, false);
        }
    }

    private void drawFooter(DrawContext context, List<?> entries) {
        int pages = pages(entries);
        if (page > 0) {
            context.drawTexture(PREVIOUS, left + 10, top + 175, 7, 14, 0, 0, 14, 28, 14, 28);
            clicks.add(new ClickArea(left + 8, top + 175, 24, 14, () -> page--));
        }
        if (page + 1 < pages) {
            context.drawTexture(NEXT, left + 329, top + 175, 7, 14, 0, 0, 14, 28, 14, 28);
            clicks.add(new ClickArea(left + 317, top + 175, 24, 14, () -> page++));
        }
        String text = Text.translatable("screen.tropimon_stocks_manager.coverage.page",
                page + 1, pages, entries.size()).getString();
        context.drawText(textRenderer, text, left + W / 2 - textRenderer.getWidth(text) / 2,
                top + 178, 0xFF17242B, false);
    }

    private static int pages(List<?> entries) { return Math.max(1, (entries.size() + PAGE - 1) / PAGE); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    @Override public boolean clickContent(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) for (int i = clicks.size() - 1; i >= 0; i--) {
            ClickArea area = clicks.get(i);
            if (inside(x, y, area.x, area.y, area.w, area.h)) { area.action.run(); return true; }
        }
        return super.clickContent(x, y, button);
    }
    @Override public boolean scrollContent(double x, double y, double h, double vertical) {
        page = Math.max(0, Math.min(pages(TownChestAutoIndexer.status().entries()) - 1,
                page - (int) Math.signum(vertical))); return true;
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
    private record ClickArea(int x, int y, int w, int h, Runnable action) { }
}
