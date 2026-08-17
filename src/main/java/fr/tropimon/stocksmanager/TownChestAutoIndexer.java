package fr.tropimon.stocksmanager;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
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
import java.util.Map;
import java.util.Set;

/** Indexeur continu limité aux interactions de coffre normales et validées par le serveur. */
public final class TownChestAutoIndexer {
    private static final double MAX_DISTANCE_SQUARED = 20.25D;
    private static final long MIN_RECHECK_MILLIS = 20L * 60L * 1000L;
    private static final long FAILED_RETRY_MILLIS = 20L * 1000L;
    private static final Map<String, Long> LAST_CHECKED_AT = new HashMap<>();
    private static final Map<String, Long> RETRY_AFTER = new HashMap<>();
    private static final Set<String> DETECTED = new HashSet<>();
    private static final Set<String> INDEXED = new HashSet<>();
    private static final Set<String> INACCESSIBLE = new HashSet<>();
    private static boolean active;
    private static BlockPos pending;
    private static long interactionTick;
    private static long nextActionTick;
    private static int openTicks;
    private static String server = "";

    private TownChestAutoIndexer() { }

    public static void start(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        RETRY_AFTER.clear();
        DETECTED.clear();
        INDEXED.clear();
        INACCESSIBLE.clear();
        pending = null;
        openTicks = 0;
        server = TownChestTracker.serverKey(client);
        active = true;
        nextActionTick = client.world.getTime() + 2L;
        client.setScreen(null);
        client.player.sendMessage(Text.translatable("message.tropimon_stocks_manager.index_started"), true);
    }

    public static void toggle(MinecraftClient client) {
        if (active) stop(client);
        else start(client);
    }

    public static boolean isActive() { return active; }

    public static Status status() {
        return new Status(active, pending, DETECTED.size(), INDEXED.size(), INACCESSIBLE.size(), remaining());
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
                String key = chestKey(client, pending);
                LAST_CHECKED_AT.put(key, System.currentTimeMillis());
                INDEXED.add(key);
                INACCESSIBLE.remove(key);
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
                String key = chestKey(client, pending);
                RETRY_AFTER.put(key, System.currentTimeMillis() + FAILED_RETRY_MILLIS);
                INACCESSIBLE.add(key);
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
            String key = chestKey(client, target.pos);
            RETRY_AFTER.put(key, System.currentTimeMillis() + FAILED_RETRY_MILLIS);
            INACCESSIBLE.add(key);
            pending = null;
            nextActionTick = tick + 2L;
        }
    }

    private static Target nearestVisibleChest(MinecraftClient client) {
        BlockPos origin = client.player.getBlockPos();
        Vec3d eyes = client.player.getEyePos();
        Target best = null;
        Set<String> processed = new HashSet<>();
        long now = System.currentTimeMillis();
        for (BlockPos mutable : BlockPos.iterate(origin.add(-5, -4, -5), origin.add(5, 4, 5))) {
            BlockPos pos = mutable.toImmutable();
            BlockState state = client.world.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock) || !TropimonTownScope.isInPlayersTown(pos)) continue;
            Vec3d center = pos.toCenterPos();
            double distance = eyes.squaredDistanceTo(center);
            if (distance > MAX_DISTANCE_SQUARED) continue;

            String key = chestKey(client, pos);
            DETECTED.add(key);
            if (isRecentlyChecked(client, pos, key, now)) {
                INDEXED.add(key);
                INACCESSIBLE.remove(key);
                continue;
            }
            if (RETRY_AFTER.getOrDefault(key, 0L) > now) {
                INACCESSIBLE.add(key);
                continue;
            }
            if (processed.contains(key)) continue;

            HitResult ray = client.world.raycast(new RaycastContext(eyes, center,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
            if (!(ray instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                    || !hit.getBlockPos().equals(pos)) {
                INACCESSIBLE.add(key);
                continue;
            }
            processed.add(key);
            INACCESSIBLE.remove(key);
            if (best == null || distance < best.distance) best = new Target(pos, hit, distance);
        }
        return best;
    }

    private static boolean isRecentlyChecked(MinecraftClient client, BlockPos pos, String key, long now) {
        BlockPos canonical = canonicalChestPos(client, pos);
        String dimension = client.world.getRegistryKey().getValue().toString();
        long persisted = TownChestIndex.get().lastCheckedAt(server, dimension, canonical);
        long checkedAt = Math.max(LAST_CHECKED_AT.getOrDefault(key, 0L), persisted);
        if (checkedAt <= 0L) return false;
        long elapsed = now - checkedAt;
        return elapsed >= 0L && elapsed < MIN_RECHECK_MILLIS;
    }

    private static int remaining() {
        int count = 0;
        for (String key : DETECTED) {
            if (!INDEXED.contains(key) && !INACCESSIBLE.contains(key)) count++;
        }
        return count;
    }

    private static String chestKey(MinecraftClient client, BlockPos pos) {
        String dimension = client.world == null ? "unknown" : client.world.getRegistryKey().getValue().toString();
        return server + '|' + dimension + '|' + canonicalChestPos(client, pos).asLong();
    }

    private static BlockPos canonicalChestPos(MinecraftClient client, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
            return pos;
        }
        BlockPos other = pos.offset(ChestBlock.getFacing(state));
        if (other.getX() < pos.getX()
                || other.getX() == pos.getX() && other.getY() < pos.getY()
                || other.getX() == pos.getX() && other.getY() == pos.getY() && other.getZ() < pos.getZ()) {
            return other;
        }
        return pos;
    }

    private static void stop(MinecraftClient client) {
        active = false;
        pending = null;
        if (client.player != null) {
            client.player.sendMessage(Text.translatable(
                    "message.tropimon_stocks_manager.index_complete", INDEXED.size()), true);
        }
    }

    public record Status(boolean active, BlockPos current, int detected, int indexed,
                         int inaccessible, int remaining) { }

    private record Target(BlockPos pos, BlockHitResult hit, double distance) { }
}
