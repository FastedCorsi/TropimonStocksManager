package fr.tropimon.stocksmanager;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class ModIndependenceTest {
    @Test
    void manifestRequiresOnlyOfficialDependencies() throws Exception {
        var metadata = JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json")))
                .getAsJsonObject();
        assertEquals(Set.of("fabricloader", "minecraft", "fabric-api", "cobblemon", "tropimodclient"),
                metadata.getAsJsonObject("depends").keySet());
    }

    @Test
    void noPeerPackageImportsOrReflectiveAccessAndOnlyOfficialResourceNamespaces() throws Exception {
        Pattern peer = Pattern.compile("fr[./]tropimon[./](?!stocksmanager(?:[./]|\\b))[\\w./]+");
        Pattern resources = Pattern.compile("Identifier\\.of\\(\\s*\"([^\"]+)\"");
        Pattern imports = Pattern.compile("import\\s+(?:static\\s+)?([\\w.]+)");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(peer.matcher(source).find(), "Peer mod reference in " + file);
                var matcher = resources.matcher(source);
                while (matcher.find()) {
                    assertTrue(Set.of("minecraft", "cobblemon", "tropimodclient", "tropimon_stocks_manager")
                            .contains(matcher.group(1)), "Unexpected asset dependency in " + file);
                }
                matcher = imports.matcher(source);
                while (matcher.find()) {
                    String name = matcher.group(1);
                    assertTrue(name.startsWith("java.") || name.startsWith("net.minecraft.")
                            || name.startsWith("net.fabricmc.") || name.startsWith("com.google.gson.")
                            || name.startsWith("org.lwjgl.") || name.startsWith("org.slf4j."),
                            "Unexpected binary dependency: " + name);
                }
            }
        }
    }
}
