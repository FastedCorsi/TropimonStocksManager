package fr.tropimon.stocksmanager;

import fr.tropimon.stocksmanager.build.PrivacyCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class PrivacyCheckTest {
    // Fictional identities only. Assemble fixtures so the source itself remains shareable.
    private static PrivacyCheck scanner() { return new PrivacyCheck(List.of("Example Developer")); }
    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static byte[] zip(String name, byte[] content) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test void acceptsPublicAttributionPortablePathsAndOfficialUrls() {
        var scanner = scanner();
        scanner.inspectText("fabric.mod.json", "{\"id\":\"tropimon_stocks_manager\","
                + "\"authors\":[\"By FastedCorsi\"]}");
        scanner.inspectText("README.md", "By FastedCorsi; $env:APPDATA; System.getProperty(\"user.home\"); "
                + "https://github.com/FastedCorsi/TropimonStocksManager");
        assertTrue(scanner.findings().isEmpty());
    }

    @Test void requiresExactDeveloperAttribution() {
        var scanner = scanner();
        scanner.inspectText("fabric.mod.json", "{\"id\":\"tropimon_stocks_manager\","
                + "\"authors\":[\"FastedCorsi\"]}");
        assertEquals("developer-attribution", scanner.findings().getFirst().rule());
    }

    @Test void detectsPrivateIdentityWithoutLeakingMatchedValue() {
        var scanner = scanner();
        scanner.inspectText("Example Developer.txt", "Author: Example Developer");
        assertEquals("private-identity", scanner.findings().getFirst().rule());
        assertFalse(scanner.findings().toString().contains("Example Developer"));
    }

    @Test void detectsWindowsUnixAndEscapedPaths() {
        for (String path : List.of("C:" + "\\Users\\" + "FixtureAccount\\project",
                "C:" + "\\\\Users\\\\" + "FixtureAccount\\\\project",
                "/home" + "/FixtureAccount/project", "/Users" + "/FixtureAccount/project")) {
            var scanner = scanner();
            scanner.inspectText("example.txt", path);
            assertEquals("personal-path", scanner.findings().getFirst().rule());
        }
    }

    @Test void flagsEmailsAndSecretPatternsWithoutPrintingValues() {
        for (String value : List.of("fixture.person" + "@" + "example.invalid",
                "ghp_" + "a".repeat(36), "-----BEGIN " + "PRIVATE KEY-----",
                "api_key=\"" + "a".repeat(32) + "\"")) {
            var scanner = scanner();
            scanner.inspectText("example.txt", value);
            assertFalse(scanner.findings().isEmpty());
            assertFalse(scanner.findings().toString().contains(value));
        }
    }

    @Test void scansNestedArchivesAndCompiledConstantBytes() throws Exception {
        var scanner = scanner();
        byte[] classBytes = utf8("\u0000\u0001Example Developer\u0000");
        scanner.inspectBytes("mod.jar", zip("META-INF/jars/nested.jar", zip("Example.class", classBytes)), 0);
        assertTrue(scanner.findings().stream().anyMatch(f -> f.rule().equals("private-identity")
                && f.path().endsWith("nested.jar!/Example.class")));
    }

    @Test void scansUtf16TextAndArchiveComments() throws Exception {
        var scanner = scanner();
        scanner.inspectBytes("document.txt", "Example Developer".getBytes(StandardCharsets.UTF_16), 0);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.setComment("Example Developer");
            zip.putNextEntry(new ZipEntry("public.txt"));
            zip.write(utf8("By FastedCorsi"));
        }
        scanner.inspectBytes("mod.jar", bytes.toByteArray(), 0);
        assertEquals(2, scanner.findings().size());
    }

    @Test void rejectsPrivateAndTraversalEntries() throws Exception {
        for (String name : List.of(".env", "logs/latest.log", "config/town-chests.json", "promo/capture.png",
                "screenshots/capture.png", ".git/config", "save.backup", "../escape.txt")) {
            var scanner = scanner();
            scanner.inspectBytes("mod.jar", zip(name, utf8("fixture")), 0);
            assertFalse(scanner.findings().isEmpty(), name);
        }
    }

    @Test void rejectsUnreadableArchivesExcessiveNestingAndMissingArtifacts(@TempDir Path root) throws Exception {
        var scanner = scanner();
        scanner.inspectBytes("bad.jar", utf8("not a zip"), 0);
        scanner.inspectBytes("nested.jar", utf8("fixture"), 9);
        scanner.inspectArtifacts(root, List.of("missing"));
        assertEquals(3, scanner.findings().size());
    }

    @Test void preservesLocalOriginalsAndInspectsAllShareableFiles(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("promo"));
        Path original = root.resolve("promo/capture.png");
        Files.write(original, utf8("Example Developer"));
        Files.writeString(root.resolve("README.md"), "Example Developer");
        var scanner = scanner();
        scanner.inspectProject(root);
        assertEquals(1, scanner.findings().size());
        assertEquals("README.md", scanner.findings().getFirst().path());
        assertEquals("Example Developer", Files.readString(original));
    }

    @Test void rejectsPrivateFilesEvenWhenTrackedThenIgnored(@TempDir Path root) throws Exception {
        runGit(root, "init", "--quiet");
        Files.createDirectories(root.resolve("config"));
        Path original = root.resolve("config/town-chests.json");
        Files.writeString(original, "{}");
        runGit(root, "add", "config/town-chests.json");
        Files.writeString(root.resolve(".gitignore"), "config/\n");
        var scanner = scanner();
        scanner.inspectProject(root);
        assertTrue(scanner.findings().stream().anyMatch(f -> f.rule().equals("private-tracked-file")));
        assertEquals("{}", Files.readString(original));
    }

    private static void runGit(Path root, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, process.waitFor(), "Git fixture setup failed");
    }

    @Test void preservesThirdPartyCreditsAndFunctionalPlayerIdentifiers() throws Exception {
        var scanner = scanner();
        byte[] license = utf8("Copyright Example Foundation; Apache License, Version 2.0");
        scanner.inspectBytes("dependency.jar", zip("LICENSE", license), 0);
        scanner.inspectText("player.json", "{\"player\":\"FixturePlayer\"}");
        assertTrue(scanner.findings().isEmpty());
        assertArrayEquals(utf8("Copyright Example Foundation; Apache License, Version 2.0"), license);
    }
}
