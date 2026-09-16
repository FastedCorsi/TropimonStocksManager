package fr.tropimon.stocksmanager;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Indexeur continu limité aux interactions de coffre normales et validées par le serveur. */
public final class TownChestAutoIndexer {
    private static final double MAX_DISTANCE_SQUARED = 20.25D;
    private static final long MIN_RECHECK_MILLIS = 20L * 60L * 1000L;
    private static final long FAILED_RETRY_MILLIS = 20L * 1000L;
    private static final Map<ChestKey, Long> LAST_CHECKED_AT = new HashMap<>();
    private static final Map<ChestKey, Long> RETRY_AFTER = new HashMap<>();
    private static final Set<ChestKey> DETECTED = new HashSet<>();
    private static final Set<ChestKey> INDEXED = new HashSet<>();
    private static final Set<ChestKey> INACCESSIBLE = new HashSet<>();
    private static final Set<String> MISSING_REPORTED = new HashSet<>();
    private static final Map<ChestKey, CoverageEntry> COVERAGE = new LinkedHashMap<>();
    private static boolean active;
    private static BlockPos pending;
    private static long interactionTick;
    private static long nextActionTick;
    private static int openTicks;
    private static String server = "";
    private static long nextMissingScanTick;

    private TownChestAutoIndexer() { }

    public static void start(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        RETRY_AFTER.clear();
        DETECTED.clear();
        INDEXED.clear();
        INACCESSIBLE.clear();
        MISSING_REPORTED.clear();
        COVERAGE.clear();
        pending = null;
        openTicks = 0;
        server = TownChestTracker.serverKey(client);
        active = true;
        nextActionTick = client.world.getTime() + 2L;
        nextMissingScanTick = client.world.getTime();
        client.setScreen(null);
        client.player.sendMessage(Text.translatable("message.tropimon_stocks_manager.index_started"), true);
    }

    public static void toggle(MinecraftClient client) {
        if (active) stop(client);
        else start(client);
    }

    public static boolean isActive() { return active; }

    public static Status status() {
        int upToDate = (int) COVERAGE.values().stream().filter(CoverageEntry::upToDate).count();
        int old = (int) COVERAGE.values().stream().filter(CoverageEntry::old).count();
        int inaccessible = (int) COVERAGE.values().stream().filter(CoverageEntry::inaccessible).count();
        return new Status(active, pending, COVERAGE.size(), upToDate, old, inaccessible,
                remaining(), List.copyOf(COVERAGE.values()));
    }

    public static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.player == null || client.world == null
                || !server.equals(TownChestTracker.serverKey(client))) {
            stop(client);
            return;
        }
        long tick = client.world.getTime();

        if (pending != null && client.currentScreen instanceof HandledScreen<?> handled
                && handled.getScreenHandler() instanceof GenericContainerScreenHandler) {
            openTicks++;
            // TownChestTracker capture le contenu au troisième tick d'écran, juste avant cet appel.
            if (openTicks >= 3) {
                ChestKey key = chestKey(client, pending);
                LAST_CHECKED_AT.put(key, System.currentTimeMillis());
                INDEXED.add(key);
                INACCESSIBLE.remove(key);
                coverage(key, BlockPos.fromLong(key.position), CoverageReason.UP_TO_DATE,
                        System.currentTimeMillis());
                client.player.closeHandledScreen();
                pending = null;
                openTicks = 0;
                nextActionTick = tick + 2L;
            }
            return;
        }
        if (client.currentScreen != null) return;
        if (pending != null) {
            if (tick - interactionTick > 40L) {
                ChestKey key = chestKey(client, pending);
                RETRY_AFTER.put(key, System.currentTimeMillis() + FAILED_RETRY_MILLIS);
                INACCESSIBLE.add(key);
                coverage(key, pending, CoverageReason.SERVER_REFUSED,
                        TownChestIndex.get().lastCheckedAt(key.server, key.dimension, pending));
                pending = null;
                nextActionTick = tick + 2L;
            }
            return;
        }
        if (tick < nextActionTick) return;
        if (client.player.isSneaking()) {
            client.player.sendMessage(Text.translatable("message.tropimon_stocks_manager.index_sneaking"), true);
            nextActionTick = tick + 20L;
            return;
        }

        Target target = nearestVisibleChest(client);
        if (target == null) {
            nextActionTick = tick + 5L;
            return;
        }
        pending = target.pos;
        TownChestTracker.armTarget(target.pos, tick);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, target.hit);
        if (result.isAccepted()) {
            if (result.shouldSwingHand()) client.player.swingHand(Hand.MAIN_HAND);
            interactionTick = tick;
            openTicks = 0;
        } else {
            ChestKey key = target.key;
            RETRY_AFTER.put(key, System.currentTimeMillis() + FAILED_RETRY_MILLIS);
            INACCESSIBLE.add(key);
            coverage(key, target.pos, CoverageReason.SERVER_REFUSED,
                    TownChestIndex.get().lastCheckedAt(key.server, key.dimension, target.pos));
            pending = null;
            nextActionTick = tick + 2L;
        }
    }

    private static Target nearestVisibleChest(MinecraftClient client) {
        BlockPos origin = client.player.getBlockPos();
        Vec3d eyes = client.player.getEyePos();
        Target best = null;
        Set<ChestKey> processed = new HashSet<>();
        String dimension = client.world.getRegistryKey().getValue().toString();
        TownClaimScan claims = null;
        long now = System.currentTimeMillis();
        if (client.world.getTime() >= nextMissingScanTick) {
            detectMissingKnownChests(client, origin, dimension, now);
            nextMissingScanTick = client.world.getTime() + 40L;
        }
        for (BlockPos mutable : BlockPos.iterate(origin.add(-5, -4, -5), origin.add(5, 4, 5))) {
            BlockState state = client.world.getBlockState(mutable);
            if (!(state.getBlock() instanceof ChestBlock)) continue;
            BlockPos pos = mutable.toImmutable();
            BlockPos canonical = ChestPositions.canonical(pos, state);
            ChestKey key = new ChestKey(server, dimension, canonical.asLong());
            DETECTED.add(key);
            TownChestIndex.get().noteChestPresent(chestId(key));
            double distance = eyes.squaredDistanceTo(mutable.getX() + 0.5D,
                    mutable.getY() + 0.5D, mutable.getZ() + 0.5D);
            long checkedAt = TownChestIndex.get().lastCheckedAt(server, dimension, canonical);
            if (distance > MAX_DISTANCE_SQUARED) {
                coverage(key, canonical, CoverageReason.OUT_OF_REACH, checkedAt);
                INACCESSIBLE.add(key);
                continue;
            }
            if (claims == null) claims = TropimonTownScope.beginScan();
            if (!claims.allows(mutable.getX(), mutable.getZ())) {
                coverage(key, canonical, CoverageReason.PERMISSION, checkedAt);
                INACCESSIBLE.add(key);
                continue;
            }
            if (isRecentlyChecked(canonical, key, now)) {
                INDEXED.add(key);
                INACCESSIBLE.remove(key);
                coverage(key, canonical, CoverageReason.COOLDOWN, checkedAt);
                continue;
            }
            if (RETRY_AFTER.getOrDefault(key, 0L) > now) {
                INACCESSIBLE.add(key);
                coverage(key, canonical, CoverageReason.SERVER_REFUSED, checkedAt);
                continue;
            }
            if (processed.contains(key)) continue;

            HitResult ray = client.world.raycast(new RaycastContext(eyes, pos.toCenterPos(),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            if (!(ray instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || !hit.getBlockPos().equals(pos)) {
                INACCESSIBLE.add(key);
                coverage(key, canonical, CoverageReason.OBSTACLE, checkedAt);
                continue;
            }
            processed.add(key);
            INACCESSIBLE.remove(key);
            CoverageReason available = checkedAt > 0L
                    && TownChestIndex.freshness(checkedAt, now) != TownChestIndex.Freshness.FRESH
                    ? CoverageReason.OLD : CoverageReason.PENDING;
            coverage(key, canonical, available, checkedAt);
            if (best == null || distance < best.distance) best = new Target(pos, hit, distance, key);
        }
        return best;
    }

    private static void detectMissingKnownChests(MinecraftClient client, BlockPos origin,
                                                 String dimension, long now) {
        for (TownChestIndex.ChestLocation known : TownChestIndex.get()
                .chestLocationsNear(server, dimension, origin, 5, 4)) {
            BlockPos pos = known.pos();
            if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (client.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                TownChestIndex.get().noteChestPresent(known.id());
            } else if (MISSING_REPORTED.add(known.id())) {
                TownChestIndex.get().noteChestMissing(known.id(), now);
            }
        }
    }

    private static String chestId(ChestKey key) {
        BlockPos pos = BlockPos.fromLong(key.position);
        return key.server + "|" + key.dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static void coverage(ChestKey key, BlockPos pos, CoverageReason reason, long checkedAt) {
        COVERAGE.put(key, new CoverageEntry(pos.toImmutable(), reason, checkedAt));
    }

    private static boolean isRecentlyChecked(BlockPos canonical, ChestKey key, long now) {
        long persisted = TownChestIndex.get().lastCheckedAt(key.server, key.dimension, canonical);
        long checkedAt = Math.max(LAST_CHECKED_AT.getOrDefault(key, 0L), persisted);
        return withinRecheckDelay(checkedAt, now);
    }

    static boolean withinRecheckDelay(long checkedAt, long now) {
        if (checkedAt <= 0L) return false;
        long elapsed = now - checkedAt;
        return elapsed >= 0L && elapsed < MIN_RECHECK_MILLIS;
    }

    private static int remaining() {
        return (int) COVERAGE.values().stream()
                .filter(entry -> entry.reason == CoverageReason.PENDING || entry.reason == CoverageReason.OLD)
                .count();
    }

    private static ChestKey chestKey(MinecraftClient client, BlockPos pos) {
        String dimension = client.world == null ? "unknown" : client.world.getRegistryKey().getValue().toString();
        return new ChestKey(server, dimension, ChestPositions.canonical(pos, client.world.getBlockState(pos)).asLong());
    }

    private static void stop(MinecraftClient client) {
        active = false;
        pending = null;
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(
                    "message.tropimon_stocks_manager.index_complete", INDEXED.size()), true);
        }
    }

    public enum CoverageReason {
        UP_TO_DATE, OLD, OUT_OF_REACH, OBSTACLE, PERMISSION, COOLDOWN, SERVER_REFUSED, PENDING
    }

    public record CoverageEntry(BlockPos pos, CoverageReason reason, long checkedAt) {
        public boolean upToDate() { return reason == CoverageReason.UP_TO_DATE || reason == CoverageReason.COOLDOWN; }
        public boolean old() { return reason == CoverageReason.OLD; }
        public boolean inaccessible() {
            return reason == CoverageReason.OUT_OF_REACH || reason == CoverageReason.OBSTACLE
                    || reason == CoverageReason.PERMISSION || reason == CoverageReason.SERVER_REFUSED;
        }
    }

    public record Status(boolean active, BlockPos current, int detected, int indexed, int old,
                         int inaccessible, int remaining, List<CoverageEntry> entries) { }

    private record ChestKey(String server, String dimension, long position) { }

    private record Target(BlockPos pos, BlockHitResult hit, double distance, ChestKey key) { }
}
