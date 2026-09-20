package fr.tropimon.stocksmanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** Test-only entrypoint: offline UI/cache/persistence smoke, then clean shutdown. */
public final class StockSmokeClient implements ClientModInitializer {
    private boolean started;
    private int ticks;
    private TownChestScreen root;

    @Override
    public void onInitializeClient() {
        if (Boolean.getBoolean("tropimon.gui.smoke")) {
            new GuiScaleSmoke().onInitializeClient();
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> started = true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!started || client.getOverlay() != null) return;
            try {
                switch (++ticks) {
                    case 1 -> prepare(client);
                    case 15 -> client.setScreen(new ItemDetailScreen(null, "minecraft:stone"));
                    case 27 -> client.setScreen(new ItemHistoryScreen(root, "minecraft:stone"));
                    case 39 -> client.setScreen(new WatchlistScreen(root));
                    case 51 -> client.setScreen(new CoverageScreen(root));
                    case 63 -> client.setScreen(new ChestManagerScreen(root));
                    case 75 -> client.setScreen(new StockFilterScreen(root));
                    case 90 -> {
                        TropimonStocksManagerClient.LOGGER.info("STOCK_SMOKE_OK: startup, simplified UI, claims fail-closed, stocks, graph and retained stock data");
                        client.scheduleStop();
                    }
                    default -> { }
                }
            } catch (Throwable exception) {
                TropimonStocksManagerClient.LOGGER.error("STOCK_SMOKE_FAILED", exception);
                client.scheduleStop();
            }
        });
    }

    private void prepare(MinecraftClient client) {
        if (client.player != null) throw new AssertionError("Smoke test must remain offline");
        if (TropimonTownScope.beginScan().allows(0, 0)) throw new AssertionError("Missing player town was accepted");
        TownChestIndex index = TownChestIndex.get();
        var stone = new TownChestIndex.StoredItem("minecraft:stone", "Stone", 64, false, List.of());
        var box = new TownChestIndex.StoredItem("minecraft:shulker_box", "Shulker Box", 1, true, List.of(stone));
        index.updateStoredChest("local", "minecraft:overworld", 1, 64, 2, "Smoke Chest",
                List.of(stone, box), System.currentTimeMillis());
        index.setWatchThreshold("local", "minecraft:stone", 200);
        index.configureChest("local|minecraft:overworld|1,64,2", "Smoke Storage", "fixture, blocks", true);
        index.saveView("local", new TownChestIndex.SavedView("Low blocks", "stone",
                TownChestIndex.Scope.ALL, TownChestIndex.Namespace.MINECRAFT,
                TownChestIndex.SortOrder.ALERT, true, false));
        var result = index.search("local", "smoke", TownChestIndex.Scope.ALL, TownChestIndex.Namespace.ALL);
        if (result.size() != 1 || result.getFirst().total() != 128 || !result.getFirst().belowThreshold()) {
            throw new AssertionError("Stock calculation mismatch");
        }
        root = new TownChestScreen(null);
        client.setScreen(root);
    }
}
