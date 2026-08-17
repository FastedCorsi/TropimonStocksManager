package fr.tropimon.stocksmanager;

import net.minecraft.client.gui.DrawContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class StockUi {
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

    static void iconSlot(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        context.fill(x, y, x + width, y + height, hovered ? 0x885EC8D4 : 0x554A5962);
        int border = hovered ? 0xFF9CE8F2 : 0xFF718089;
        context.fill(x, y, x + width, y + 1, border);
        context.fill(x, y + height - 1, x + width, y + height, border);
        context.fill(x, y, x + 1, y + height, border);
        context.fill(x + width - 1, y, x + width, y + height, border);
    }
}
