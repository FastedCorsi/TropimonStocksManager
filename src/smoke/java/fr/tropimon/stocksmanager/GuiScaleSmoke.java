package fr.tropimon.stocksmanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Optional offline matrix for installed release JARs. This class never ships in a mod. */
public final class GuiScaleSmoke implements ClientModInitializer {
    private interface Factory { Screen create() throws Exception; }
    private record Case(String name, Factory factory) {}
    private final List<Case> cases = new ArrayList<>();
    private boolean ready;
    private int tick, current, failures, scale = 1;
    private boolean caseValid;

    @Override public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(c -> ready = true);
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!ready || c.getOverlay() != null) return;
            try {
                if (c.player != null || c.world != null) throw new AssertionError("UI matrix must stay offline");
                if (++tick == 1) {
                    fixtures();
                    open(c);
                } else if (tick % 12 == 10) {
                    if (caseValid) inspect(c);
                } else if (tick % 12 == 0) {
                    if (++current == cases.size()) { current = 0; scale++; }
                    if (scale > 4) {
                        System.out.println((failures == 0 ? "GUI_MATRIX_OK" : "GUI_MATRIX_INCOMPLETE")
                                + " screens=" + cases.size() + " scales=1,2,3,4 failures=" + failures);
                        ready = false;
                        c.scheduleStop();
                    } else open(c);
                }
            } catch (Throwable e) {
                System.err.println("GUI_MATRIX_FAILED case=" + current + " gui=" + scale);
                e.printStackTrace();
                failures++;
                caseValid = false;
            }
        });
    }

    private void fixtures() throws Exception {
        var index = TownChestIndex.get();
        var stone = new TownChestIndex.StoredItem("minecraft:stone", "Stone", 64, false, List.of());
        index.updateStoredChest("local", "minecraft:overworld", 1, 64, 2, "Example storage",
                List.of(stone), System.currentTimeMillis());
        index.configureChest("local|minecraft:overworld|1,64,2", "A long example storage name for layout", "blocks, fixture", true);
        index.setWatchThreshold("local", "minecraft:stone", 200);
        cases.add(new Case("stocks-main", () -> new TownChestScreen(null)));
        cases.add(new Case("stocks-item", () -> new ItemDetailScreen(null, "minecraft:stone")));
        cases.add(new Case("stocks-history", () -> new ItemHistoryScreen(null, "minecraft:stone")));
        cases.add(new Case("stocks-watchlist", () -> new WatchlistScreen(null)));
        cases.add(new Case("stocks-coverage", () -> new CoverageScreen(null)));
        cases.add(new Case("stocks-chests", () -> new ChestManagerScreen(null)));
        cases.add(new Case("stocks-filter", () -> new StockFilterScreen(new TownChestScreen(null))));
        cases.add(new Case("stocks-edit", () -> new ChestEditScreen(null, "local|minecraft:overworld|1,64,2")));
        optional("damage", "fr.tropimon.damagecalc.DamageCalcScreen", () -> screen("fr.tropimon.damagecalc.DamageCalcScreen", make("fr.tropimon.damagecalc.DamageCalcState")));
        optional("damage-stats", "fr.tropimon.damagecalc.DamageCalcScreen", () -> {
            Screen s = screen("fr.tropimon.damagecalc.DamageCalcScreen", make("fr.tropimon.damagecalc.DamageCalcState"));
            set(s, "compactStatsVisible", true); return s;
        });
        optional("teams", "fr.tropimon.teamsaver.client.TeamManagerScreen", () -> screen("fr.tropimon.teamsaver.client.TeamManagerScreen", pc(), true));
        optional("teams-edit", "fr.tropimon.teamsaver.client.TeamManagerScreen", () -> {
            Screen s = screen("fr.tropimon.teamsaver.client.TeamManagerScreen", pc(), true);
            set(s, "creating", true); return s;
        });
        optional("teams-paste", "fr.tropimon.teamsaver.client.PasteImportScreen", () -> screen("fr.tropimon.teamsaver.client.PasteImportScreen",
                make("fr.tropimon.teamsaver.client.TeamManagerScreen", pc(), true)));
        optional("builder", "fr.tropimon.cobblemonbuilder.BuilderScreen", () -> screen("fr.tropimon.cobblemonbuilder.BuilderScreen",
                make("fr.tropimon.cobblemonbuilder.BuilderController", make("fr.tropimon.cobblemonbuilder.BuilderPreferences"))));
        optional("battle-chat", "fr.tropimon.battleui.BattleUiSettingsScreen", () -> screen("fr.tropimon.battleui.BattleUiSettingsScreen", (Object) null));
        optional("battle-tooltips", "fr.tropimon.battleui.BattleUiSettingsScreen", () -> {
            Screen s = screen("fr.tropimon.battleui.BattleUiSettingsScreen", (Object) null); set(s, "tooltips", true); return s;
        });
        optional("events", "fr.tropimon.events.EventsScreen", () -> screen("fr.tropimon.events.EventsScreen"));
        try { Class.forName("fr.tropimon.casino.CasinoScreen"); } catch (ClassNotFoundException absent) { return; }
        String p = "fr.tropimon.casino.";
        Object config = make(p + "CasinoConfig");
        // Authentication fails locally before any HTTP request. No real account or transaction.
        set(config, "loadError", "OFFLINE_UI_FIXTURE");
        Object api = make(p + "CasinoApi", config);
        Object settings = make(p + "CasinoSettings", true, "ExampleBank", 1, 1000, 100000);
        Object role = Class.forName(p + "CasinoRole").getField("SUPER_ADMIN").get(null);
        Object profile = make(p + "RemoteProfile", new UUID(0, 1), new UUID(0, 2), "ExamplePlayer", role, 123456L);
        Object snapshot = make(p + "CasinoSnapshot", profile, settings, List.of(), List.of());
        Screen machine = screen(p + "CasinoScreen", api); set(machine, "snapshot", snapshot);
        cases.add(new Case("casino", () -> machine));
        cases.add(new Case("casino-menu", () -> screen(p + "CasinoMenuScreen", machine, api)));
        cases.add(new Case("casino-history", () -> screen(p + "CasinoHistoryScreen", machine, snapshot)));
        cases.add(new Case("casino-paytable", () -> screen(p + "CasinoPaytableScreen", machine, machine)));
        cases.add(new Case("casino-deposit", () -> screen(p + "CasinoTransactionScreen", machine, machine, api, true)));
        cases.add(new Case("casino-withdraw", () -> screen(p + "CasinoTransactionScreen", machine, machine, api, false)));
        cases.add(new Case("casino-settings", () -> screen(p + "CasinoSettingsScreen", machine, api, settings, (Runnable) () -> {})));
        cases.add(new Case("casino-admin", () -> screen(p + "CasinoAdminScreen", machine, machine, api)));
    }

    private void optional(String label, String className, Factory factory) throws Exception {
        try { Class.forName(className); cases.add(new Case(label, factory)); }
        catch (ClassNotFoundException absent) { System.out.println("GUI_MATRIX_ABSENT " + label); }
    }

    private void open(MinecraftClient c) throws Exception {
        caseValid = false;
        c.options.getGuiScale().setValue(scale);
        c.onResolutionChanged();
        c.setScreen(cases.get(current).factory.create());
        caseValid = true;
    }

    private void inspect(MinecraftClient c) throws Exception {
        Screen s = c.currentScreen;
        if (s == null) throw new AssertionError("Screen unexpectedly closed");
        int vw = c.getWindow().getScaledWidth(), vh = c.getWindow().getScaledHeight();
        String label = cases.get(current).name + "-gui" + scale;
        var folder = c.runDirectory.toPath().resolve("captures");
        Files.createDirectories(folder);
        try (var picture = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) { picture.writeTo(folder.resolve(label + ".png")); }
        double factor = Math.min((double) vw / s.width, (double) vh / s.height);
        for (var child : s.children()) {
            if (!(child instanceof ClickableWidget w) || !w.visible) continue;
            if (w.getX() < 0 || w.getY() < 0 || (w.getX() + w.getWidth()) * factor > vw + 1
                    || (w.getY() + w.getHeight()) * factor > vh + 1) {
                throw new AssertionError("Offscreen widget: " + w.getMessage().getString() + " rect="
                        + w.getX() + "," + w.getY() + "," + w.getWidth() + "," + w.getHeight());
            }
            if (w instanceof TextFieldWidget && w.active) {
                s.mouseClicked(0, 0, 0);
                s.mouseReleased(0, 0, 0);
                s.mouseClicked((w.getX() + w.getWidth() / 2.0) * factor, (w.getY() + w.getHeight() / 2.0) * factor, 0);
                if (!w.isFocused()) throw new AssertionError("Field hitbox does not match rendered position: "
                        + w.getMessage().getString() + " at " + w.getX() + "," + w.getY());
                s.mouseReleased((w.getX() + 5) * factor, (w.getY() + 5) * factor, 0);
            }
        }
        System.out.println("GUI_MATRIX_SCREEN " + label + " viewport=" + vw + "x" + vh + " effective=" + c.getWindow().getScaleFactor());
    }

    private static Object pc() throws Exception {
        String p = "com.cobblemon.mod.common.";
        return make(p + "client.gui.pc.PCGUI", make(p + "client.storage.ClientPC", new UUID(0, 3), 2),
                make(p + "client.storage.ClientParty", new UUID(0, 2), 6),
                make(p + "client.gui.pc.PCGUIConfiguration"), 0, new java.util.HashSet<>());
    }

    private static Screen screen(String name, Object... arguments) throws Exception { return (Screen) make(name, arguments); }
    private static Object make(String name, Object... arguments) throws Exception {
        for (Constructor<?> constructor : Class.forName(name).getDeclaredConstructors()) {
            if (constructor.getParameterCount() != arguments.length) continue;
            constructor.setAccessible(true);
            try { return constructor.newInstance(arguments); } catch (IllegalArgumentException wrongOverload) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
