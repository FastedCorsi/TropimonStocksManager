package fr.tropimon.stocksmanager;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.UUID;

/** Lit les claims déjà synchronisés par TropimodClient, sans requête ni dépendance compilée. */
public final class TropimonTownScope {
    private static boolean warned;

    private TropimonTownScope() {
    }

    public static boolean isInPlayersTown(BlockPos pos) {
        try {
            Class<?> clientClass = Class.forName("fr.erusel.tropimodclient.client.TropimodClient");
            Object playerData = clientClass.getMethod("getCachedPlayerData").invoke(null);
            if (playerData == null) {
                return false;
            }
            Object townInfo = playerData.getClass().getMethod("getTownInfo").invoke(playerData);
            if (townInfo == null) {
                return false;
            }
            Object cityIdValue = townInfo.getClass().getMethod("getCityId").invoke(townInfo);
            if (!(cityIdValue instanceof UUID cityId)) {
                return false;
            }

            Class<?> managerClass = Class.forName("fr.erusel.tropimodclient.data.TownManager");
            Method getTown = managerClass.getMethod("getTown", int.class, int.class);
            Object claimedTown = getTown.invoke(null, pos.getX() >> 4, pos.getZ() >> 4);
            if (claimedTown == null) {
                return false;
            }
            Object claimedTownId = claimedTown.getClass().getMethod("id").invoke(claimedTown);
            return cityId.equals(claimedTownId);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warned) {
                warned = true;
                TropimonStocksManagerClient.LOGGER.warn(
                        "TropimodClient town claims are unavailable; chest capture remains disabled", exception);
            }
            return false;
        }
    }
}
