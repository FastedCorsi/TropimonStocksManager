package fr.tropimon.stocksmanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TropimonStocksManagerClient implements ClientModInitializer {
    public static final String MOD_ID = "tropimon_stocks_manager";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static KeyBinding openStockKey;

    @Override
    public void onInitializeClient() {
        openStockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tropimon_stocks_manager.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.tropimon_stocks_manager"
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tropistock")
                    .executes(context -> {
                        MinecraftClient.getInstance().execute(TropimonStocksManagerClient::openStock);
                        return 1;
                    })
                    .then(ClientCommandManager.literal("scan").executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> TownChestAutoIndexer.toggle(client));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("stop").executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            if (TownChestAutoIndexer.isActive()) TownChestAutoIndexer.toggle(client);
                        });
                        return 1;
                    })));
            dispatcher.register(ClientCommandManager.literal("Tropistock").executes(context -> {
                MinecraftClient.getInstance().execute(TropimonStocksManagerClient::openStock);
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TownChestTracker.tick(client);
            TownChestAutoIndexer.tick(client);
            TownChestIndex.get().flushScheduledSave();
            StockWatchNotifier.tick(client);
            while (openStockKey.wasPressed()) {
                openStock();
            }
        });
        TownMenuIntegration.register();
        StockIndexHud.register();
        MinecraftClient.getInstance().execute(() -> StockKeyBindingMigration.run(
                MinecraftClient.getInstance(), openStockKey));
        LOGGER.info("TropimonStocksManager initialized");
    }

    public static void openStock() {
        openStock(null);
    }

    public static void openStock(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !(client.currentScreen instanceof TownChestScreen)) {
            client.setScreen(new TownChestScreen(parent));
        }
    }
}
