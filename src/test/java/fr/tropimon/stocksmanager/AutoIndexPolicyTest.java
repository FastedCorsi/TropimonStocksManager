package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AutoIndexPolicyTest {
    @Test
    void preservesTwentyMinuteRecheckBoundaryAndClockRollbackHandling() {
        long checked = 100_000_000L;
        assertTrue(TownChestAutoIndexer.withinRecheckDelay(checked, checked));
        assertTrue(TownChestAutoIndexer.withinRecheckDelay(checked, checked + 1_199_999L));
        assertFalse(TownChestAutoIndexer.withinRecheckDelay(checked, checked + 1_200_000L));
        assertFalse(TownChestAutoIndexer.withinRecheckDelay(checked, checked - 1));
        assertFalse(TownChestAutoIndexer.withinRecheckDelay(0, checked));
    }
}
