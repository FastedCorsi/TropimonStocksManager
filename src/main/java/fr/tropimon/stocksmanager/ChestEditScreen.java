package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Fiche locale d'un coffre : nom, étiquettes, favori et oubli confirmé. */
final class ChestEditScreen extends FittedScreen {
    private static final Identifier PC = Identifier.of("cobblemon", "textures/gui/pc/pc_base.png");
    private static final Identifier STAR = Identifier.of("tropimodclient", "guis/commons/icons/star_icon.png");
    private static final Identifier TOWN = Identifier.of("tropimodclient", "guis/town/town_favicon.png");
    private static final int W = 349, H = 205;
    private final Screen parent;
    private final String chestId;
    private final TownChestIndex index = TownChestIndex.get();
    private TextFieldWidget nameField, tagsField;
    private StockPcButton favoriteButton;
    private ButtonWidget forgetButton;
    private boolean favorite, confirmForget;
    private int left, top;

    ChestEditScreen(Screen parent, String chestId) {
        super(Text.translatable("screen.tropimon_stocks_manager.chest.edit"), 357, 213);
        this.parent = parent; this.chestId = chestId;
    }

    @Override protected void initContent() {
        left = (width - W) / 2; top = (height - H) / 2;
        TownChestIndex.ChestInfo chest = index.chest(chestId);
        if (chest == null) { close(); return; }
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, left + 91, top + 54, 238, 18,
                Text.translatable("screen.tropimon_stocks_manager.chest.name")));
        nameField.setMaxLength(48);
        nameField.setText(chest.displayName().equals(chest.originalTitle()) ? "" : chest.displayName());
        tagsField = addDrawableChild(new TextFieldWidget(textRenderer, left + 91, top + 86, 238, 18,
                Text.translatable("screen.tropimon_stocks_manager.chest.tags")));
        tagsField.setMaxLength(160); tagsField.setText(String.join(", ", chest.tags()));
        favorite = chest.favorite();
        favoriteButton = addDrawableChild(new StockPcButton(left + 91, top + 115, 110, 18,
                favoriteText(), button -> {
                    favorite = !favorite; button.setMessage(favoriteText()); favoriteButton.setSelected(favorite);
                }, favorite));
        addDrawableChild(new StockPcButton(left + 207, top + 115, 122, 18,
                Text.translatable("screen.tropimon_stocks_manager.save"), button -> save(), false));
        forgetButton = addDrawableChild(new StockPcButton(left + 207, top + 142, 122, 18,
                Text.translatable("screen.tropimon_stocks_manager.chest.forget"), button -> forget(), false));
    }

    private Text favoriteText() { return Text.translatable(favorite
            ? "screen.tropimon_stocks_manager.chest.favorite.on"
            : "screen.tropimon_stocks_manager.chest.favorite.off"); }
    private void save() { index.configureChest(chestId, nameField.getText(), tagsField.getText(), favorite); close(); }
    private void forget() {
        if (!confirmForget) { confirmForget = true; forgetButton.setMessage(Text.translatable(
                "screen.tropimon_stocks_manager.confirm")); return; }
        index.forgetChest(chestId); close();
    }

    @Override public void renderContent(DrawContext context, int mx, int my, float delta) {
        context.drawTexture(PC, left, top, W, H, 0, 0, W, H, W, H);
        context.fill(left + 5, top + 24, left + 344, top + 189, 0xFFE7EDF3);
        TownChestIndex.ChestInfo chest = index.chest(chestId);
        context.drawTexture(TOWN, left + 10, top + 6, 14, 14, 0, 0, 14, 14, 14, 14);
        context.drawCenteredTextWithShadow(textRenderer, title, left + W / 2, top + 13, 0xFFFFFFFF);
        if (chest != null) {
            context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.chest.name"),
                    left + 16, top + 59, 0xFF17242B, false);
            context.drawText(textRenderer, Text.translatable("screen.tropimon_stocks_manager.chest.tags"),
                    left + 16, top + 91, 0xFF17242B, false);
            context.drawText(textRenderer, chest.originalTitle() + " • " + chest.x() + ", " + chest.y() + ", " + chest.z(),
                    left + 16, top + 35, 0xFF52636D, false);
            context.drawTexture(STAR, left + 71, top + 117, 14, 14, 0, 0, 14, 14, 14, 14);
            String safety = Text.translatable(chest.removable()
                    ? "screen.tropimon_stocks_manager.chest.forget.safe"
                    : "screen.tropimon_stocks_manager.chest.forget.warning").getString();
            context.drawText(textRenderer, textRenderer.trimToWidth(safety, 310), left + 16, top + 169,
                    chest.removable() ? 0xFF268B4D : 0xFFC33B4A, false);
        }
        renderWidgets(context, mx, my, delta);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
