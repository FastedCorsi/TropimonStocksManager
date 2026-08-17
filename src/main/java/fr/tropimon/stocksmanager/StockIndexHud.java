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
                status.detected(), status.indexed()).getString();
        String remainingLine = Text.translatable("hud.tropimon_stocks_manager.blocked_remaining",
                status.inaccessible(), status.remaining()).getString();
        int width = Math.max(Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(currentLine)),
                Math.max(client.textRenderer.getWidth(indexedLine), client.textRenderer.getWidth(remainingLine))) + 12;
        int x = (client.getWindow().getScaledWidth() - width) / 2;
        int y = 5;
        context.fill(x, y, x + width, y + 45, 0xC8182730);
        context.fill(x, y, x + 3, y + 45, 0xFF44D17A);
        context.drawTextWithShadow(client.textRenderer, title, x + 7, y + 4, 0xFF70E49A);
        context.drawTextWithShadow(client.textRenderer, currentLine, x + 7, y + 14, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, indexedLine, x + 7, y + 24, 0xFFB9DDE5);
        context.drawTextWithShadow(client.textRenderer, remainingLine, x + 7, y + 34, 0xFFB9DDE5);
    }
}
