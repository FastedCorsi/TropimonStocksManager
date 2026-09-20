package fr.tropimon.stocksmanager;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Résumé compact visible pendant la marche lorsque l'indexeur est actif. */
final class StockIndexHud {
    private StockIndexHud() { }

    static void register() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!TownChestAutoIndexer.isActive() || client.player == null || client.options.hudHidden) return;
        TownChestAutoIndexer.Status status = TownChestAutoIndexer.status();
        String title = Text.translatable("hud.tropimon_stocks_manager.title").getString();
        BlockPos current = status.current();
        String currentLine = current == null
                ? Text.translatable("hud.tropimon_stocks_manager.walking").getString()
                : Text.translatable("hud.tropimon_stocks_manager.current",
                        current.getX(), current.getY(), current.getZ()).getString();
        String indexedLine = Text.translatable("hud.tropimon_stocks_manager.detected_indexed",
                status.detected(), status.indexed(), status.old()).getString();
        String remainingLine = Text.translatable("hud.tropimon_stocks_manager.blocked_remaining",
                status.inaccessible(), status.remaining()).getString();
        String[] values = {title, currentLine, indexedLine, remainingLine};
        int[] colors = {0xFF70E49A, 0xFFFFFFFF, 0xFFB9DDE5, 0xFFB9DDE5};
        int width = Math.min(client.getWindow().getScaledWidth() - 12,
                Math.max(Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(currentLine)),
                Math.max(client.textRenderer.getWidth(indexedLine), client.textRenderer.getWidth(remainingLine))) + 16);
        var lines = new java.util.ArrayList<java.util.List<net.minecraft.text.OrderedText>>();
        int lineCount = 0;
        for (String value : values) {
            var wrapped = client.textRenderer.wrapLines(Text.literal(value), width - 14);
            lines.add(wrapped);
            lineCount += wrapped.size();
        }
        int x = (client.getWindow().getScaledWidth() - width) / 2;
        int y = 5;
        context.fill(x, y, x + width, y + lineCount * 10 + 5, 0xC8182730);
        context.fill(x, y, x + 3, y + lineCount * 10 + 5, 0xFF44D17A);
        y += 4;
        for (int group = 0; group < lines.size(); group++) {
            for (var line : lines.get(group)) {
                context.drawTextWithShadow(client.textRenderer, line, x + 7, y, colors[group]);
                y += 10;
            }
        }
    }
}
