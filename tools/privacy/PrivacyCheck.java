package fr.tropimon.stocksmanager.build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Build-only scanner. No private values or scanner classes enter the game mod. */
public final class PrivacyCheck {
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final Set<String> LOCAL_DIRECTORIES = Set.of(
            ".git", ".gradle", "build", "out", "run", ".idea", "promo", "config", "logs",
            "screenshots", "saves", "backups", "mod-archive");
    private static final Pattern HOME_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]+Users[\\\\/]+[^\\\\/\\s\"<>]+|/(?:home|Users)/[^/\\s\"<>]+)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern SECRET = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"
            + "|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}"
            + "|AKIA[A-Z0-9]{16}|xox[baprs]-[A-Za-z0-9-]{20,}"
            + "|sk-(?:proj-)?[A-Za-z0-9_-]{32,}"
            + "|(?i:(?:access[_-]?token|api[_-]?key|password|client[_-]?secret)"
            + "[\"']?\\s*[:=]\\s*[\"'][A-Za-z0-9_+/=-]{16,}[\"'])");
    private static final Pattern OWN_ID = Pattern.compile(
            "\"id\"\\s*:\\s*\"tropimon_stocks_manager(?:_smoke)?\"");
    private static final Pattern AUTHOR = Pattern.compile(
            "\"authors\"\\s*:\\s*\\[\\s*\"By FastedCorsi\"\\s*]");
    private final List<Pattern> privateTerms;
    private final List<Finding> findings = new ArrayList<>();
    private long totalBytes;
    private int inspectedFiles;

    public record Finding(String path, String rule) { }

    public PrivacyCheck(List<String> terms) {
        privateTerms = terms.stream().map(String::strip).filter(value -> value.length() >= 3)
                .distinct().map(value -> Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])"
                        + Pattern.quote(value) + "(?![\\p{L}\\p{N}_])")).toList();
    }

    public List<Finding> findings() { return List.copyOf(findings); }

    public static boolean localOnly(String path) {
        for (String part : path.replace('\\', '/').toLowerCase(Locale.ROOT).split("/")) {
            if (LOCAL_DIRECTORIES.contains(part) || part.startsWith(".env")
                    || part.endsWith(".log") || part.endsWith(".bak") || part.endsWith(".backup")
                    || part.endsWith(".private") || part.endsWith(".iml")
                    || Set.of("town-chests.json", "options.txt", "launcher_accounts.json",
                    "launcher_profiles.json").contains(part)) return true;
        }
        return false;
    }

    private void report(String path, String rule) {
        String safePath = path;
        for (Pattern term : privateTerms) safePath = term.matcher(safePath).replaceAll("[redacted]");
        safePath = EMAIL.matcher(safePath).replaceAll("[redacted]");
        safePath = HOME_PATH.matcher(safePath).replaceAll("[private-path]");
        safePath = SECRET.matcher(safePath).replaceAll("[redacted]");
        Finding finding = new Finding(safePath, rule);
        if (!findings.contains(finding)) findings.add(finding);
    }

    public void inspectText(String path, String text) {
        if (HOME_PATH.matcher(text).find()) report(path, "personal-path");
        if (EMAIL.matcher(text).find()) report(path, "email-needs-review");
        if (SECRET.matcher(text).find()) report(path, "possible-secret");
        for (Pattern term : privateTerms) {
            if (term.matcher(text).find()) { report(path, "private-identity"); break; }
        }
        if (path.endsWith("fabric.mod.json") && OWN_ID.matcher(text).find()
                && !AUTHOR.matcher(text).find()) report(path, "developer-attribution");
    }

    public void inspectBytes(String path, byte[] bytes, int depth) {
        if (depth > 8 || bytes.length > MAX_FILE_BYTES || (totalBytes += bytes.length) > MAX_TOTAL_BYTES) {
            report(path, "inspection-limit-exceeded");
            return;
        }
        inspectedFiles++;
        inspectText(path, path);
        boolean zip = path.toLowerCase(Locale.ROOT).matches(".*\\.(jar|zip)$")
                || (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K');
        if (!zip) {
            inspectText(path, new String(bytes, StandardCharsets.UTF_8));
            if (bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                    || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) {
                inspectText(path, new String(bytes, StandardCharsets.UTF_16));
            }
            return;
        }
        int entries = 0;
        // Include ZIP entry names, comments and extra fields in the identity/secret inspection.
        inspectText(path, new String(bytes, StandardCharsets.UTF_8));
        try (var archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                String name = entry.getName().replace('\\', '/');
                String label = path + "!/" + name;
                if (++entries > 20000) { report(path, "inspection-limit-exceeded"); return; }
                if (name.startsWith("/") || name.matches("^[a-zA-Z]:.*")
                        || List.of(name.split("/")).contains("..")) report(label, "unsafe-archive-path");
                if (localOnly(name)) report(label, "private-archive-entry");
                if (entry.isDirectory()) continue;
                byte[] content = archive.readNBytes(MAX_FILE_BYTES + 1);
                if (content.length > MAX_FILE_BYTES) { report(label, "inspection-limit-exceeded"); return; }
                inspectBytes(label, content, depth + 1);
                if (totalBytes > MAX_TOTAL_BYTES) return;
            }
            if (entries == 0) report(path, "empty-or-unreadable-archive");
        } catch (IOException | IllegalArgumentException failure) {
            report(path, "unreadable-archive");
        }
    }

    private void inspectFile(Path file, String label) throws IOException {
        if (Files.isSymbolicLink(file)) { report(label, "symlink-needs-review"); return; }
        if (Files.size(file) > MAX_FILE_BYTES) { report(label, "inspection-limit-exceeded"); return; }
        inspectBytes(label, Files.readAllBytes(file), 0);
    }

    public void inspectProject(Path root) throws IOException, InterruptedException {
        // Ignoring a file does not untrack it: reject private files already staged/tracked.
        if (Files.exists(root.resolve(".git"))) {
            Process git = new ProcessBuilder("git", "ls-files", "-z").directory(root.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String tracked = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (git.waitFor() != 0) throw new IOException("Cannot inspect tracked source inventory");
            for (String path : tracked.split("\u0000")) {
                if (localOnly(path)) report(path, "private-tracked-file");
            }
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && localOnly(root.relativize(dir).toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String label = root.relativize(file).toString().replace('\\', '/');
                if (!localOnly(label)) inspectFile(file, label);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void inspectArtifacts(Path root, List<String> directories) throws IOException {
        int jars = 0;
        for (String directory : directories) {
            Path dir = root.resolve(directory);
            if (Files.isRegularFile(dir)) {
                inspectFile(dir, root.relativize(dir).toString().replace('\\', '/'));
                if (dir.toString().endsWith(".jar")) jars++;
                continue;
            }
            if (!Files.isDirectory(dir)) continue;
            try (var files = Files.walk(dir)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    inspectFile(file, root.relativize(file).toString().replace('\\', '/'));
                    if (file.toString().endsWith(".jar")) jars++;
                }
            }
        }
        if (jars == 0) report("artifacts", "missing-final-jar");
    }

    private static List<String> runtimeTerms(Path root) throws IOException {
        var terms = new ArrayList<String>();
        terms.add(System.getProperty("user.name", ""));
        String extra = System.getenv("TROPIMON_PRIVACY_TERMS");
        if (extra != null) terms.addAll(extra.lines().toList());
        String privateFile = System.getenv("TROPIMON_PRIVACY_TERMS_FILE");
        if (privateFile != null && !privateFile.isBlank()) {
            Path file = Path.of(privateFile).toRealPath();
            if (file.startsWith(root.toRealPath())) throw new IOException("Private term file must be outside the project");
            terms.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
        }
        return terms;
    }

    public static void main(String[] args) {
        try {
            Path root = Path.of("").toAbsolutePath().normalize();
            PrivacyCheck scanner = new PrivacyCheck(runtimeTerms(root));
            if (args.length == 1 && args[0].equals("--sources")) scanner.inspectProject(root);
            else if (args.length > 1 && args[0].equals("--archives")) {
                scanner.inspectArtifacts(root, List.of(args).subList(1, args.length));
            } else throw new IllegalArgumentException("Expected --sources or --archives directories");
            for (Finding finding : scanner.findings) System.err.println(finding.rule + ": " + finding.path);
            if (!scanner.findings.isEmpty()) {
                System.err.println("Privacy check failed: " + scanner.findings.size() + " finding(s); values withheld.");
                System.exit(1);
            }
            System.out.println("Privacy check passed: " + scanner.inspectedFiles + " files/entries inspected.");
        } catch (Exception failure) {
            // Exception messages can contain private absolute paths; never print them.
            System.err.println("Privacy inspection failed (" + failure.getClass().getSimpleName() + "); no clearance issued.");
            System.exit(2);
        }
    }
}
