package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class TownClaimScanTest {
    @Test
    void onlyLooksUpAChunkOnceWithinAScan() {
        UUID town = UUID.randomUUID();
        AtomicInteger lookups = new AtomicInteger();
        TownClaimScan scan = new TownClaimScan(town, (x, z) -> {
            lookups.incrementAndGet();
            return town;
        });
        assertTrue(scan.allows(0, 0));
        assertTrue(scan.allows(15, 15));
        assertEquals(1, lookups.get());
        assertTrue(scan.allows(-1, -1));
        assertTrue(scan.allows(-16, -16));
        assertTrue(scan.allows(16, -16));
        assertEquals(3, lookups.get());
    }

    @Test
    void newScanSeesRevokedClaimsAndChangedMembership() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        AtomicReference<UUID> claim = new AtomicReference<>(own);
        TownClaimScan.ClaimLookup lookup = (x, z) -> claim.get();
        assertTrue(new TownClaimScan(own, lookup).allows(2, 3));
        claim.set(other);
        assertFalse(new TownClaimScan(own, lookup).allows(2, 3));
        assertTrue(new TownClaimScan(other, lookup).allows(2, 3));
        claim.set(null);
        assertFalse(new TownClaimScan(other, lookup).allows(2, 3));
    }

    @Test
    void missingPlayerTownFailsClosedWithoutAnyClaimLookup() {
        TownClaimScan scan = new TownClaimScan(null, (x, z) -> {
            fail("No claims should be read without player membership");
            return null;
        });
        assertFalse(scan.allows(1, 1));
    }
}
