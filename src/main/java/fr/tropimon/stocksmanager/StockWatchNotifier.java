package fr.tropimon.stocksmanager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

/** Alerte une seule fois lors du passage sous un seuil, puis se réarme après réapprovisionnement. */
final class StockWatchNotifier {
    private static final Set<String> ALERTED = new HashSet<>();
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
        for (TownChestIndex.WatchStatus status : TownChestIndex.get().watchStatuses(server)) {
            String key = server + '|' + status.itemId();
            if (status.belowThreshold()) {
                if (ALERTED.add(key)) {
                    client.player.sendMessage(Text.translatable(
                            "message.tropimon_stocks_manager.watch_low",
                            status.displayName(), status.current(), status.threshold()), false);
                }
            } else {
                ALERTED.remove(key);
            }
        }
    }

    static void thresholdChanged(MinecraftClient client, String server, String itemId) {
        ALERTED.remove(server + '|' + itemId);
        nextPeriodicCheck = System.currentTimeMillis() + 60_000L;
        check(client, server);
    }
}
