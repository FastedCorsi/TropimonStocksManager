package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class StockUi {
    enum Icon { CLOCK, CLOSE }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private StockUi() { }

    static int freshnessColor(TownChestIndex.Freshness freshness) {
        return switch (freshness) {
            case FRESH -> 0xFF36A85D;
            case AGING -> 0xFFE39524;
            case STALE -> 0xFFD34B4B;
            case UNKNOWN -> 0xFF8B969E;
        };
    }

    static String delta(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    static String date(long timestamp) {
        return timestamp <= 0L ? "—" : DATE.format(Instant.ofEpochMilli(timestamp));
    }

    static String age(long timestamp) {
        if (timestamp <= 0L) return "—";
        long minutes = Math.max(0L, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes < 60L) return minutes + " min";
        long hours = minutes / 60L;
        if (hours < 24L) return hours + " h";
        return hours / 24L + " j";
    }

    static String count(int value) {
        String raw = Integer.toString(Math.max(0, value));
        StringBuilder formatted = new StringBuilder(raw.length() + raw.length() / 3);
        for (int index = 0; index < raw.length(); index++) {
            if (index > 0 && (raw.length() - index) % 3 == 0) formatted.append(' ');
            formatted.append(raw.charAt(index));
        }
        return formatted.toString();
    }

    static void iconButton(DrawContext context, int x, int y, int width, int height,
                           boolean hovered, boolean selected) {
        StockPcButton.drawBackground(context, x, y, width, height, hovered, selected);
    }

    /** Pictogrammes 12 px homogènes, dessinés sur la palette du PC Cobblemon. */
    static void icon(DrawContext context, Icon icon, int x, int y, boolean highlighted) {
        drawIcon(context, icon, x + 1, y + 1, 0xB018252B);
        drawIcon(context, icon, x, y, highlighted ? 0xFF62E5F0 : 0xFFE7EEF1);
    }

    private static void drawIcon(DrawContext context, Icon icon, int x, int y, int color) {
        switch (icon) {
            case CLOCK -> {
                rect(context, x + 3, y + 1, 6, 1, color);
                rect(context, x + 2, y + 2, 1, 1, color);
                rect(context, x + 9, y + 2, 1, 1, color);
                rect(context, x + 1, y + 3, 1, 6, color);
                rect(context, x + 10, y + 3, 1, 6, color);
                rect(context, x + 2, y + 9, 1, 1, color);
                rect(context, x + 9, y + 9, 1, 1, color);
                rect(context, x + 3, y + 10, 6, 1, color);
                rect(context, x + 5, y + 3, 2, 4, color);
                rect(context, x + 6, y + 6, 3, 2, color);
            }
            case CLOSE -> {
                rect(context, x + 2, y + 2, 2, 2, color);
                rect(context, x + 8, y + 2, 2, 2, color);
                rect(context, x + 3, y + 3, 2, 2, color);
                rect(context, x + 7, y + 3, 2, 2, color);
                rect(context, x + 4, y + 4, 4, 4, color);
                rect(context, x + 3, y + 7, 2, 2, color);
                rect(context, x + 7, y + 7, 2, 2, color);
                rect(context, x + 2, y + 8, 2, 2, color);
                rect(context, x + 8, y + 8, 2, 2, color);
            }
        }
    }

    private static void rect(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);
    }
}
