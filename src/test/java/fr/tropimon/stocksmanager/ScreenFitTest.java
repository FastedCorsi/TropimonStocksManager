package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenFitTest {
    @Test void fourGuiSettingsFitAndKeepMouseTargetsAligned() {
        for (int[] window : new int[][]{{1920,1080},{1280,960},{1280,720},{960,600},{2560,1440},{3840,2160}}) {
            for (int requested = 1; requested <= 4; requested++) {
                int effective = Math.max(1, Math.min(requested, Math.min(window[0] / 320, window[1] / 240)));
                int width = (int) Math.ceil(window[0] / (double) effective);
                int height = (int) Math.ceil(window[1] / (double) effective);
                var fit = ScreenFit.of(width, height, 357, 213);
                assertTrue(fit.width() * fit.scale() <= width + .001);
                assertTrue(fit.height() * fit.scale() <= height + .001);
                assertTrue(fit.width() >= 357 - 1 && fit.height() >= 213 - 1);
                assertTrue(fit.scale() * effective >= 1, "Never smaller than the native one-pixel font in this matrix");
                for (double value : new double[]{0, 8, 20, fit.width() / 2., fit.width() - 8})
                    assertEquals(value, fit.local(value * fit.scale()), .0001);
                assertTrue(fit.local(-.5) < 0, "An outside click must remain outside");
            }
        }
    }

    @Test void aPanelThatAlreadyFitsKeepsNativeSize() {
        var fit = ScreenFit.of(1920, 1080, 357, 213);
        assertEquals(1F, fit.scale());
        assertEquals(1920, fit.width());
        assertEquals(1080, fit.height());
    }
}
