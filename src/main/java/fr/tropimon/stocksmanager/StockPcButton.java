package fr.tropimon.stocksmanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Bouton rendu directement avec l'asset de bouton du PC Cobblemon. */
final class StockPcButton extends ButtonWidget {
    private static final Identifier TEXTURE = Identifier.of("cobblemon", "textures/gui/pc/pc_release_button.png");
    private static final int TEXTURE_WIDTH = 58;
    private static final int TEXTURE_HEIGHT = 32;
    private static final int SOURCE_HEIGHT = 16;
    private static final int CAP_WIDTH = 4;
    private final boolean selected;

    StockPcButton(int x, int y, int width, int height, Text message, PressAction action, boolean selected) {
        super(x, y, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
        this.selected = selected;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int sourceY = isHovered() && !selected ? SOURCE_HEIGHT : 0;
        int cap = Math.min(CAP_WIDTH, width / 2);
        int middle = Math.max(0, width - cap * 2);
        context.drawTexture(TEXTURE, getX(), getY(), cap, height,
                0, sourceY, CAP_WIDTH, SOURCE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (middle > 0) {
            context.drawTexture(TEXTURE, getX() + cap, getY(), middle, height,
                    CAP_WIDTH, sourceY, TEXTURE_WIDTH - CAP_WIDTH * 2, SOURCE_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        context.drawTexture(TEXTURE, getX() + width - cap, getY(), cap, height,
                TEXTURE_WIDTH - CAP_WIDTH, sourceY, CAP_WIDTH, SOURCE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
        if (selected) {
            context.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0x99266376);
            context.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + 3, 0xFF9CE8F2);
            context.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 2, 0xFF163D47);
        }
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(renderer, getMessage(), getX() + width / 2,
                getY() + (height - 8) / 2, 0xFFFFFFFF);
    }
}
