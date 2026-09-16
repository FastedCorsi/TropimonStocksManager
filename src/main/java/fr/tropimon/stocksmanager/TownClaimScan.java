package fr.tropimon.stocksmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived authorization view: never retain across scans or capture ticks. */
final class TownClaimScan {
    @FunctionalInterface
    interface ClaimLookup { UUID townAt(int chunkX, int chunkZ); }

    private final UUID playerTown;
    private final ClaimLookup lookup;
    private final Map<Long, Boolean> chunks = new HashMap<>(4);

    TownClaimScan(UUID playerTown, ClaimLookup lookup) {
        this.playerTown = playerTown;
        this.lookup = lookup;
    }

    boolean allows(int blockX, int blockZ) {
        if (playerTown == null) return false;
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long key = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
        Boolean cached = chunks.get(key);
        if (cached != null) return cached;
        boolean allowed = playerTown.equals(lookup.townAt(chunkX, chunkZ));
        chunks.put(key, allowed);
        return allowed;
    }
}
