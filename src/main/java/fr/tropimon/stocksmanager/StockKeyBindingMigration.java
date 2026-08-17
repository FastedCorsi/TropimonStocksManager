package fr.tropimon.stocksmanager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Migre une seule fois l'ancien raccourci O, très conflictuel, vers N. */
final class StockKeyBindingMigration {
    private static final Path MARKER = FabricLoader.getInstance().getConfigDir()
            .resolve("tropimon_stocks_manager")
            .resolve("keybinding-n-migrated");

    private StockKeyBindingMigration() { }

    static void run(MinecraftClient client, KeyBinding binding) {
        if (Files.exists(MARKER)) return;
        try {
            if (binding.getBoundKeyTranslationKey().equals("key.keyboard.o")) {
                binding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_N));
                KeyBinding.updateKeysByCode();
                client.options.write();
            }
            Files.createDirectories(MARKER.getParent());
            Files.writeString(MARKER, "Migrated legacy O shortcut to N.\n");
        } catch (IOException exception) {
            TropimonStocksManagerClient.LOGGER.warn("Unable to persist the stock manager key migration", exception);
        }
    }
}
