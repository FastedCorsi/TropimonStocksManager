package fr.tropimon.stocksmanager;

import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** Lit les claims déjà synchronisés par TropimodClient, sans requête ni dépendance compilée. */
public final class TropimonTownScope {
    private static boolean warned;
    private static final ClassValue<Optional<Method>> TOWN_INFO = getter("getTownInfo");
    private static final ClassValue<Optional<Method>> CITY_ID = getter("getCityId");
    private static final ClassValue<Optional<Method>> CLAIM_ID = getter("id");

    private TropimonTownScope() {
    }

    public static boolean isInPlayersTown(BlockPos pos) {
        return beginScan().allows(pos.getX(), pos.getZ());
    }

    static TownClaimScan beginScan() {
        // Cache only reflection metadata globally, never a profile, UUID or permission result.
        Api api = ApiHolder.API;
        if (api == null) return new TownClaimScan(null, (x, z) -> null);
        try {
            Object playerData = api.playerData.invoke(null);
            Object townInfo = invoke(TOWN_INFO, playerData);
            Object cityId = invoke(CITY_ID, townInfo);
            return new TownClaimScan(cityId instanceof UUID id ? id : null, api::townAt);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(exception);
            return new TownClaimScan(null, (x, z) -> null);
        }
    }

    private static ClassValue<Optional<Method>> getter(String name) {
        return new ClassValue<>() {
            @Override protected Optional<Method> computeValue(Class<?> type) {
                try { return Optional.of(type.getMethod(name)); }
                catch (NoSuchMethodException exception) { return Optional.empty(); }
            }
        };
    }

    private static Object invoke(ClassValue<Optional<Method>> methods, Object receiver)
            throws ReflectiveOperationException {
        if (receiver == null) return null;
        return methods.get(receiver.getClass()).orElseThrow(NoSuchMethodException::new).invoke(receiver);
    }

    private static void warn(Throwable exception) {
        if (warned) return;
        warned = true;
        TropimonStocksManagerClient.LOGGER.warn(
                "TropimodClient town claims are unavailable; chest capture remains disabled", exception);
    }

    private static final class ApiHolder {
        private static final Api API = load();

        private static Api load() {
            try {
                Class<?> client = Class.forName("fr.erusel.tropimodclient.client.TropimodClient");
                Class<?> manager = Class.forName("fr.erusel.tropimodclient.data.TownManager");
                return new Api(client.getMethod("getCachedPlayerData"), manager.getMethod("getTown", int.class, int.class));
            } catch (ReflectiveOperationException | LinkageError exception) {
                warn(exception);
                return null;
            }
        }
    }

    private record Api(Method playerData, Method getTown) {
        UUID townAt(int chunkX, int chunkZ) {
            try {
                Object id = invoke(CLAIM_ID, getTown.invoke(null, chunkX, chunkZ));
                return id instanceof UUID uuid ? uuid : null;
            } catch (ReflectiveOperationException | LinkageError exception) {
                warn(exception);
                return null;
            }
        }
    }
}
