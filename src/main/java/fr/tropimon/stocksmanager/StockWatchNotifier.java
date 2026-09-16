package fr.tropimon.stocksmanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Alerte uniquement lors d'un passage sous le seuil, état conservé entre les reconnexions. */
final class StockWatchNotifier {
    private static String lastServer = "";
    private static long nextPeriodicCheck;

    private StockWatchNotifier() { }

    static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        String server = TownChestTracker.serverKey(client);
        long now = System.currentTimeMillis();
        if (!server.equals(lastServer) || now >= nextPeriodicCheck) {
            lastServer = server;
            nextPeriodicCheck = now + 60_000L;
            check(client, server);
        }
    }

    static void check(MinecraftClient client, String server) {
        if (client.player == null) return;
        for (TownChestIndex.WatchStatus status : TownChestIndex.get()
                .updateAlertTransitions(server, System.currentTimeMillis())) {
            client.player.sendMessage(Text.translatable(
                    "message.tropimon_stocks_manager.watch_low",
                    status.displayName(), status.current(), status.threshold()), false);
        }
    }

    static void thresholdChanged(MinecraftClient client, String server, String itemId) {
        nextPeriodicCheck = System.currentTimeMillis() + 60_000L;
        check(client, server);
    }
}
