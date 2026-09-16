package fr.tropimon.stocksmanager;

import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Capture les coffres normaux/piégés ouverts, jamais l'inventaire, l'Ender Chest ou une shulker posée. */
public final class TownChestTracker {
    private static BlockPos lastChestTarget;
    private static long lastTargetTick;
    private static int activeSyncId = Integer.MIN_VALUE;
    private static long nextCaptureTick;

    private TownChestTracker() {
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            reset();
            return;
        }
        long tick = client.world.getTime();
        if (client.currentScreen == null) {
            rememberTarget(client, tick);
            activeSyncId = Integer.MIN_VALUE;
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?> handled)
                || !(handled.getScreenHandler() instanceof GenericContainerScreenHandler container)
                || isEnderChest(handled)) {
            activeSyncId = Integer.MIN_VALUE;
            return;
        }
        ScreenHandler handler = handled.getScreenHandler();
        if (activeSyncId == handler.syncId && tick < nextCaptureTick) return;
        if (lastChestTarget == null || tick - lastTargetTick > 40L
                || !(client.world.getBlockState(lastChestTarget).getBlock() instanceof ChestBlock)
                || !TropimonTownScope.isInPlayersTown(lastChestTarget)) {
            return;
        }
        if (activeSyncId != handler.syncId) {
            activeSyncId = handler.syncId;
            nextCaptureTick = tick + 2L;
        }
        if (tick < nextCaptureTick) {
            return;
        }
        // Un snapshot complet par ouverture suffit et évite de réécrire le cache en boucle.
        nextCaptureTick = Long.MAX_VALUE;

        BlockPos canonical = ChestPositions.canonical(lastChestTarget, client.world.getBlockState(lastChestTarget));
        int containerSlots = container.getRows() * 9;
        List<ItemStack> stacks = new ArrayList<>(containerSlots);
        for (int index = 0; index < Math.min(containerSlots, handler.slots.size()); index++) {
            Slot slot = handler.slots.get(index);
            // updateChest immediately converts these into this mod's immutable records.
            stacks.add(slot.getStack());
        }
        String server = serverKey(client);
        TownChestIndex.get().updateChest(server, client.world.getRegistryKey().getValue().toString(),
                canonical, handled.getTitle().getString(), stacks);
        StockWatchNotifier.check(client, server);
    }

    public static String serverKey(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            return client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        }
        return client.isInSingleplayer() ? "singleplayer" : "local";
    }

    public static void armTarget(BlockPos pos, long tick) {
        lastChestTarget = pos.toImmutable();
        lastTargetTick = tick;
    }

    private static void rememberTarget(MinecraftClient client, long tick) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (client.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
            lastChestTarget = pos.toImmutable();
            lastTargetTick = tick;
        }
    }

    private static boolean isEnderChest(HandledScreen<?> screen) {
        return screen.getTitle().getContent() instanceof TranslatableTextContent translated
                && translated.getKey().equals("container.enderchest");
    }

    private static void reset() {
        lastChestTarget = null;
        activeSyncId = Integer.MIN_VALUE;
    }
}
