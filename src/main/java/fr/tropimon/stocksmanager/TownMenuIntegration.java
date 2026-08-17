package fr.tropimon.stocksmanager;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Ajoute un cinquième raccourci sans dépendance binaire directe sur TropimodClient. */
public final class TownMenuIntegration {
    private static final String TOWN_HOME_PACKAGE = "fr.erusel.tropimodclient.client.gui.town.home.";
    private static final String TOWN_BANK_SCREEN = "fr.erusel.tropimodclient.client.gui.town.bank.TownBankScreen";

    private TownMenuIntegration() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isTownMenu(screen)) {
                return;
            }
            // Les quatre raccourcis Tropimod occupent centre+108 à centre+138, par pas de 10.
            int x = scaledWidth / 2 + 148;
            int y = scaledHeight / 2 - 89;
            Screens.getButtons(screen).add(new TownStockButton(x, y, screen));
        });
    }

    private static boolean isTownMenu(Screen screen) {
        String name = screen.getClass().getName();
        return name.startsWith(TOWN_HOME_PACKAGE) || name.equals(TOWN_BANK_SCREEN);
    }

    private static final class TownStockButton extends PressableWidget {
        private static final Identifier COIN_ICON = Identifier.of(
                "cobblemon", "textures/item/relic_coin.png");
        private final Screen parent;

        private TownStockButton(int x, int y, Screen parent) {
            super(x, y, 8, 8, Text.translatable("button.tropimon_stocks_manager.town_menu"));
            this.parent = parent;
            setTooltip(Tooltip.of(Text.translatable("button.tropimon_stocks_manager.town_menu")));
        }

        @Override
        public void onPress() {
            TropimonStocksManagerClient.openStock(parent);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            if (isHovered()) {
                context.fill(getX() - 1, getY() - 1, getX() + 9, getY() + 9, 0x8858DDE1);
            }
            context.drawTexture(COIN_ICON, getX(), getY(), 8, 8,
                    0, 0, 16, 16, 16, 16);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

        @Override
        public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                super.playDownSound(soundManager);
            }
        }
    }
}
