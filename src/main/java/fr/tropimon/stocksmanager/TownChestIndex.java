package fr.tropimon.stocksmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Index local des seuls coffres effectivement ouverts par le joueur. */
public final class TownChestIndex implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SHULKER_DEPTH = 4;
    private static final int MAX_HISTORY_DAYS = 90;
    private static final long SAVE_INTERVAL_MILLIS = 2_000L;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    public static final long FRESH_MILLIS = 2L * 60L * 60L * 1000L;
    public static final long STALE_MILLIS = 24L * 60L * 60L * 1000L;

    private final Path path;
    private final SnapshotWriter<IndexFile> writer;
    private final Map<String, ChestSnapshot> chests = new LinkedHashMap<>();
    private final List<DailySnapshot> history = new ArrayList<>();
    private final Map<String, Map<String, Integer>> watchThresholds = new LinkedHashMap<>();
    private final Map<String, ChestMetadata> chestMetadata = new LinkedHashMap<>();
    private final Map<String, List<SavedView>> savedViews = new LinkedHashMap<>();
    private final Map<String, Long> alertSnoozes = new LinkedHashMap<>();
    private final Map<String, Boolean> alertLowStates = new LinkedHashMap<>();
    private final Map<String, ServerCache> serverCaches = new HashMap<>();
    private boolean loaded;
    private boolean closed;
    private long revision;
    private long lastSaveAttempt;

    private TownChestIndex() {
        this(FabricLoader.getInstance().getConfigDir().resolve("tropimon_stocks_manager")
                .resolve("town-chests.json"));
    }

    TownChestIndex(Path path) {
        this(path, snapshot -> writeSnapshot(path, snapshot));
    }

    TownChestIndex(Path path, SnapshotWriter.Sink<IndexFile> sink) {
        this.path = path;
        writer = new SnapshotWriter<>(sink, exception -> TropimonStocksManagerClient.LOGGER.warn(
                "Unable to save the local town chest index; changes remain pending", exception));
    }

    public static TownChestIndex get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final TownChestIndex INSTANCE = new TownChestIndex();
    }

    public synchronized boolean updateChest(String server, String dimension, BlockPos pos, String title,
                                            List<ItemStack> stacks) {
        ensureLoaded();
        long now = System.currentTimeMillis();
        String safeTitle = title == null || title.isBlank() ? "Coffre" : title;
        List<StoredItem> items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) items.add(store(stack, 0));
        }
        return updateStoredChest(server, dimension, pos.getX(), pos.getY(), pos.getZ(), safeTitle, items, now);
    }

    synchronized boolean updateStoredChest(String server, String dimension, int x, int y, int z,
                                            String title, List<StoredItem> items, long now) {
        ensureLoaded();
        String id = chestId(server, dimension, x, y, z);
        String safeTitle = title == null || title.isBlank() ? "Coffre" : title;
        String fingerprint = fingerprint(items);
        ChestSnapshot previous = chests.get(id);
        ChestMetadata metadata = chestMetadata.get(id);
        if (metadata != null && metadata.missingPasses > 0) {
            chestMetadata.put(id, new ChestMetadata(metadata.customName, metadata.tags,
                    metadata.favorite, 0, 0L));
        }
        boolean changed = previous == null
                || !previous.fingerprint.equals(fingerprint)
                || !previous.title.equals(safeTitle);
        if (changed) {
            chests.put(id, new ChestSnapshot(id, server, dimension, x, y, z,
                    safeTitle, now, now, fingerprint, items));
        } else {
            chests.put(id, new ChestSnapshot(previous.id, previous.server, previous.dimension,
                    previous.x, previous.y, previous.z, previous.title, previous.updatedAt,
                    now, previous.fingerprint, previous.items));
        }
        // Rechecks invalidate freshness too, even when quantities are unchanged.
        serverCaches.remove(server);
        recordDailySnapshot(server, now);
        requestSave(now);
        return changed;
    }

    /** Dernière lecture effective du coffre, conservée entre les sessions de jeu. */
    public synchronized long lastCheckedAt(String server, String dimension, BlockPos pos) {
        ensureLoaded();
        ChestSnapshot snapshot = chests.get(chestId(server, dimension, pos));
        return snapshot == null ? 0L : checkedAt(snapshot);
    }

    public synchronized List<ItemAggregate> search(String server, String query, Scope scope, Namespace namespace) {
        return search(server, query, scope, namespace, SortOrder.QUANTITY, false, false);
    }

    public synchronized List<ItemAggregate> search(String server, String query, Scope scope, Namespace namespace,
                                                    SortOrder sort, boolean lowOnly, boolean staleOnly) {
        ensureLoaded();
        String normalizedQuery = normalize(query);
        DailySnapshot previous = previousDailySnapshot(server);
        List<ItemAggregate> result = new ArrayList<>();
        for (ItemAggregate base : cache(server).ordered(scope)) {
            if (base.total(scope) <= 0 || !namespace.accepts(base.itemId)) continue;
            if (!normalizedQuery.isBlank() && !base.searchText().contains(normalizedQuery)) continue;
            ItemAggregate item = base.view(scope);
            applyMetadata(server, item, previous);
            if (lowOnly && !item.belowThreshold()) continue;
            if (staleOnly && item.freshness() != Freshness.STALE) continue;
            result.add(item);
        }
        if (sort != SortOrder.QUANTITY) result.sort(itemComparator(sort));
        return result;
    }

    private static Comparator<ItemAggregate> itemComparator(SortOrder sort) {
        Comparator<ItemAggregate> name = Comparator.comparing(ItemAggregate::displayName,
                String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case QUANTITY -> Comparator.comparingInt((ItemAggregate item) -> item.total()).reversed()
                    .thenComparing(name);
            case NAME -> name;
            case VARIATION -> Comparator.comparingInt(ItemAggregate::dailyDelta).thenComparing(name);
            case FRESHNESS -> Comparator.comparingLong(ItemAggregate::oldestCheckedAt).thenComparing(name);
            case ALERT -> Comparator.comparing(ItemAggregate::belowThreshold).reversed()
                    .thenComparingInt(ItemAggregate::total).thenComparing(name);
        };
    }

    public synchronized ItemAggregate findItem(String server, String itemId) {
        ensureLoaded();
        ItemAggregate item = aggregate(server).get(itemId);
        if (item == null) {
            item = new ItemAggregate(itemId, displayName(itemId));
        }
        item = item.view(Scope.ALL);
        applyMetadata(server, item, previousDailySnapshot(server));
        return item;
    }

    public synchronized List<ItemHistoryEntry> itemHistory(String server, String itemId) {
        ensureLoaded();
        List<DailySnapshot> snapshots = history.stream()
                .filter(value -> value.server.equals(server)
                        && value.totals != null
                        && value.totals.containsKey(itemId))
                .sorted(Comparator.comparing(value -> value.date))
                .toList();
        List<ItemHistoryEntry> result = new ArrayList<>();
        Integer previous = null;
        for (DailySnapshot snapshot : snapshots) {
            int total = snapshot.totals.getOrDefault(itemId, 0);
            result.add(new ItemHistoryEntry(snapshot.date, snapshot.recordedAt, total,
                    previous != null, previous == null ? 0 : total - previous));
            previous = total;
        }
        result.sort(Comparator.comparing(ItemHistoryEntry::date).reversed());
        return result;
    }

    public synchronized HistoryStats historyStats(String server, String itemId, int days) {
        int safeDays = days == 7 || days == 30 || days == 90 ? days : 30;
        List<ItemHistoryEntry> chronological = new ArrayList<>(itemHistory(server, itemId));
        Collections.reverse(chronological);
        if (chronological.size() > safeDays) {
            chronological = new ArrayList<>(chronological.subList(chronological.size() - safeDays,
                    chronological.size()));
        }
        long consumed = 0L;
        int intervals = 0;
        for (int index = 1; index < chronological.size(); index++) {
            consumed += Math.max(0, chronological.get(index - 1).total - chronological.get(index).total);
            intervals++;
        }
        double dailyAverage = intervals == 0 ? 0D : (double) consumed / intervals;
        ItemAggregate currentItem = chronological.isEmpty() ? findItem(server, itemId) : null;
        int current = chronological.isEmpty() ? currentItem.directCount + currentItem.shulkerCount
                : chronological.getLast().total;
        int estimatedDays = dailyAverage <= 0D ? -1 : Math.max(1, (int) Math.ceil(current / dailyAverage));
        return new HistoryStats(safeDays, List.copyOf(chronological), dailyAverage, estimatedDays);
    }

    public synchronized void deleteItemHistoryEntry(String server, String itemId, String date) {
        ensureLoaded();
        if (deleteItemHistory(history, server, itemId, date)) {
            revision++;
            saveNow();
        }
    }

    public synchronized void deleteAllItemHistory(String server, String itemId) {
        ensureLoaded();
        if (deleteItemHistory(history, server, itemId, null)) {
            revision++;
            saveNow();
        }
    }

    static boolean deleteItemHistory(List<DailySnapshot> entries, String server, String itemId, String date) {
        boolean changed = false;
        for (int index = entries.size() - 1; index >= 0; index--) {
            DailySnapshot snapshot = entries.get(index);
            if (!snapshot.server.equals(server) || (date != null && !snapshot.date.equals(date))
                    || snapshot.totals == null || !snapshot.totals.containsKey(itemId)) continue;
            Map<String, Integer> totals = new LinkedHashMap<>(snapshot.totals);
            totals.remove(itemId);
            changed = true;
            if (totals.isEmpty()) entries.remove(index);
            else entries.set(index, new DailySnapshot(snapshot.server, snapshot.date, snapshot.recordedAt, totals));
        }
        return changed;
    }

    public synchronized List<WatchStatus> watchStatuses(String server) {
        return watchStatuses(server, System.currentTimeMillis());
    }

    public synchronized List<WatchStatus> watchStatuses(String server, long now) {
        ensureLoaded();
        Map<String, ItemAggregate> totals = aggregate(server);
        List<WatchStatus> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : watchThresholds
                .getOrDefault(server, Map.of()).entrySet()) {
            ItemAggregate item = totals.get(entry.getKey());
            int current = item == null ? 0 : item.directCount + item.shulkerCount;
            String name = item == null ? displayName(entry.getKey()) : item.displayName;
            long snoozedUntil = alertSnoozes.getOrDefault(alertKey(server, entry.getKey()), 0L);
            result.add(new WatchStatus(entry.getKey(), name, current, entry.getValue(),
                    current < entry.getValue(), snoozedUntil));
        }
        result.sort(Comparator.comparing(WatchStatus::belowThreshold).reversed()
                .thenComparing(WatchStatus::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized int activeAlertCount(String server) {
        long now = System.currentTimeMillis();
        return (int) watchStatuses(server, now).stream()
                .filter(status -> status.belowThreshold && status.snoozedUntil <= now).count();
    }

    public synchronized void snoozeAlert(String server, String itemId, long until) {
        ensureLoaded();
        String key = alertKey(server, itemId);
        if (until <= System.currentTimeMillis()) alertSnoozes.remove(key);
        else alertSnoozes.put(key, until);
        revision++;
        saveNow();
    }

    /** Returns only genuine low-stock transitions and persists them across reconnects. */
    public synchronized List<WatchStatus> updateAlertTransitions(String server, long now) {
        ensureLoaded();
        List<WatchStatus> notifications = new ArrayList<>();
        boolean changed = false;
        for (WatchStatus status : watchStatuses(server, now)) {
            String key = alertKey(server, status.itemId);
            boolean previous = alertLowStates.getOrDefault(key, false);
            if (status.belowThreshold != previous) {
                alertLowStates.put(key, status.belowThreshold);
                changed = true;
                if (status.belowThreshold && status.snoozedUntil <= now) notifications.add(status);
            }
            if (!status.belowThreshold && alertSnoozes.remove(key) != null) changed = true;
        }
        if (changed) requestSave(now);
        return notifications;
    }

    private static String alertKey(String server, String itemId) { return server + '|' + itemId; }

    public synchronized int watchThreshold(String server, String itemId) {
        ensureLoaded();
        return watchThresholds.getOrDefault(server, Map.of()).getOrDefault(itemId, 0);
    }

    public synchronized void setWatchThreshold(String server, String itemId, int threshold) {
        ensureLoaded();
        Map<String, Integer> serverThresholds = watchThresholds.computeIfAbsent(server,
                ignored -> new LinkedHashMap<>());
        if (threshold <= 0) serverThresholds.remove(itemId);
        else serverThresholds.put(itemId, threshold);
        if (serverThresholds.isEmpty()) watchThresholds.remove(server);
        alertLowStates.remove(alertKey(server, itemId));
        if (threshold <= 0) alertSnoozes.remove(alertKey(server, itemId));
        revision++;
        saveNow();
    }

    public synchronized List<SavedView> savedViews(String server) {
        ensureLoaded();
        return savedViews.getOrDefault(server, List.of());
    }

    public synchronized void saveView(String server, SavedView view) {
        ensureLoaded();
        if (view.name == null || view.name.isBlank()) return;
        List<SavedView> values = new ArrayList<>(savedViews.getOrDefault(server, List.of()));
        values.removeIf(candidate -> candidate.name.equalsIgnoreCase(view.name));
        values.add(view.sanitized());
        while (values.size() > 4) values.removeFirst();
        savedViews.put(server, List.copyOf(values));
        revision++;
        saveNow();
    }

    public synchronized void deleteView(String server, String name) {
        ensureLoaded();
        List<SavedView> values = new ArrayList<>(savedViews.getOrDefault(server, List.of()));
        if (!values.removeIf(candidate -> candidate.name.equals(name))) return;
        if (values.isEmpty()) savedViews.remove(server); else savedViews.put(server, List.copyOf(values));
        revision++;
        saveNow();
    }

    public synchronized void flushScheduledSave() {
        if (!closed && revision > writer.savedRevision()
                && System.currentTimeMillis() - lastSaveAttempt >= SAVE_INTERVAL_MILLIS) saveNow();
    }

    public synchronized Summary summary(String server) {
        ensureLoaded();
        int chestCount = 0;
        long checkedAt = 0L;
        for (ChestSnapshot chest : chests.values()) {
            if (chest.server.equals(server)) {
                chestCount++;
                checkedAt = Math.max(checkedAt, checkedAt(chest));
            }
        }
        List<ItemAggregate> all = search(server, "", Scope.ALL, Namespace.ALL);
        return new Summary(chestCount, all.size(), all.stream().mapToInt(ItemAggregate::total).sum(), checkedAt);
    }

    public synchronized List<ChestInfo> chests(String server) {
        ensureLoaded();
        List<ChestInfo> result = new ArrayList<>();
        for (ChestSnapshot chest : chests.values()) {
            if (!chest.server.equals(server)) continue;
            ChestMetadata metadata = chestMetadata.getOrDefault(chest.id, ChestMetadata.EMPTY);
            result.add(chestInfo(chest, metadata));
        }
        result.sort(Comparator.comparing(ChestInfo::favorite).reversed()
                .thenComparing(ChestInfo::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized ChestInfo chest(String chestId) {
        ensureLoaded();
        ChestSnapshot chest = chests.get(chestId);
        return chest == null ? null : chestInfo(chest,
                chestMetadata.getOrDefault(chest.id, ChestMetadata.EMPTY));
    }

    public synchronized List<ChestLocation> chestLocationsNear(String server, String dimension,
                                                               BlockPos origin, int horizontal, int vertical) {
        ensureLoaded();
        List<ChestLocation> result = new ArrayList<>();
        for (ChestSnapshot chest : chests.values()) {
            if (!chest.server.equals(server) || !chest.dimension.equals(dimension)
                    || Math.abs(chest.x - origin.getX()) > horizontal
                    || Math.abs(chest.y - origin.getY()) > vertical
                    || Math.abs(chest.z - origin.getZ()) > horizontal) continue;
            result.add(new ChestLocation(chest.id, new BlockPos(chest.x, chest.y, chest.z)));
        }
        return result;
    }

    public synchronized void noteChestPresent(String chestId) {
        ensureLoaded();
        ChestMetadata metadata = chestMetadata.get(chestId);
        if (metadata == null || metadata.missingPasses == 0) return;
        chestMetadata.put(chestId, new ChestMetadata(metadata.customName, metadata.tags,
                metadata.favorite, 0, 0L));
        serverCaches.remove(chests.get(chestId).server);
        requestSave(System.currentTimeMillis());
    }

    public synchronized void noteChestMissing(String chestId, long now) {
        ensureLoaded();
        if (!chests.containsKey(chestId)) return;
        ChestMetadata metadata = chestMetadata.getOrDefault(chestId, ChestMetadata.EMPTY);
        chestMetadata.put(chestId, new ChestMetadata(metadata.customName, metadata.tags,
                metadata.favorite, Math.min(99, metadata.missingPasses + 1), now));
        requestSave(now);
    }

    public synchronized void configureChest(String chestId, String customName, String tags, boolean favorite) {
        ensureLoaded();
        ChestSnapshot chest = chests.get(chestId);
        if (chest == null) return;
        LinkedHashSet<String> cleanTags = new LinkedHashSet<>();
        if (tags != null) for (String value : tags.split(",")) {
            String clean = value.strip();
            if (!clean.isBlank()) cleanTags.add(clean.substring(0, Math.min(24, clean.length())));
            if (cleanTags.size() == 8) break;
        }
        ChestMetadata previous = chestMetadata.getOrDefault(chestId, ChestMetadata.EMPTY);
        String cleanName = customName == null ? "" : customName.strip();
        if (cleanName.length() > 48) cleanName = cleanName.substring(0, 48);
        chestMetadata.put(chestId, new ChestMetadata(cleanName, List.copyOf(cleanTags), favorite,
                previous.missingPasses, previous.lastMissingAt));
        serverCaches.remove(chest.server);
        revision++;
        saveNow();
    }

    public synchronized boolean forgetChest(String chestId) {
        ensureLoaded();
        ChestSnapshot removed = chests.remove(chestId);
        if (removed == null) return false;
        chestMetadata.remove(chestId);
        serverCaches.remove(removed.server);
        recordDailySnapshot(removed.server, System.currentTimeMillis());
        revision++;
        saveNow();
        return true;
    }

    public synchronized int cleanupMissing(String server) {
        ensureLoaded();
        List<String> removable = chests.values().stream()
                .filter(chest -> chest.server.equals(server))
                .filter(chest -> chestMetadata.getOrDefault(chest.id, ChestMetadata.EMPTY).missingPasses >= 2)
                .map(ChestSnapshot::id).toList();
        if (removable.isEmpty()) return 0;
        removable.forEach(id -> { chests.remove(id); chestMetadata.remove(id); });
        serverCaches.remove(server);
        recordDailySnapshot(server, System.currentTimeMillis());
        revision++;
        saveNow();
        return removable.size();
    }

    private static ChestInfo chestInfo(ChestSnapshot chest, ChestMetadata metadata) {
        String display = metadata.customName.isBlank() ? chest.title : metadata.customName;
        return new ChestInfo(chest.id, display, chest.title, chest.dimension, chest.x, chest.y, chest.z,
                checkedAt(chest), metadata.tags, metadata.favorite, metadata.missingPasses,
                metadata.lastMissingAt);
    }

    public ItemStack icon(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(id));
    }

    public static Freshness freshness(long checkedAt, long now) {
        if (checkedAt <= 0L) return Freshness.UNKNOWN;
        long age = Math.max(0L, now - checkedAt);
        if (age < FRESH_MILLIS) return Freshness.FRESH;
        if (age < STALE_MILLIS) return Freshness.AGING;
        return Freshness.STALE;
    }

    private Map<String, ItemAggregate> aggregate(String server) {
        return cache(server).items;
    }

    private ServerCache cache(String server) {
        return serverCaches.computeIfAbsent(server, this::buildCache);
    }

    private ServerCache buildCache(String server) {
        Map<String, ItemAggregate> totals = new LinkedHashMap<>();
        for (ChestSnapshot chest : chests.values()) {
            if (!chest.server.equals(server)) continue;
            ChestMetadata metadata = chestMetadata.getOrDefault(chest.id, ChestMetadata.EMPTY);
            for (StoredItem item : chest.items) add(totals, item, chest, metadata, false, 1);
        }
        return new ServerCache(totals);
    }

    private void applyMetadata(String server, ItemAggregate item, DailySnapshot previous) {
        if (previous != null) {
            item.previousTotal = previous.totals.getOrDefault(item.itemId, 0);
            item.dailyDelta = item.directCount + item.shulkerCount - item.previousTotal;
            item.comparisonDate = previous.date;
            item.hasDailyComparison = true;
        }
        item.watchThreshold = watchThresholds.getOrDefault(server, Map.of()).getOrDefault(item.itemId, 0);
    }

    private void recordDailySnapshot(String server, long now) {
        String today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString();
        upsertDailySnapshot(history, server, today, now, currentTotals(server));
        history.sort(Comparator.comparing((DailySnapshot value) -> value.date).reversed());
        while (history.size() > MAX_HISTORY_DAYS) history.remove(history.size() - 1);
    }

    static void upsertDailySnapshot(List<DailySnapshot> entries, String server, String date,
                                    long recordedAt, Map<String, Integer> totals) {
        DailySnapshot snapshot = new DailySnapshot(server, date, recordedAt, totals);
        for (int index = 0; index < entries.size(); index++) {
            DailySnapshot candidate = entries.get(index);
            if (candidate.server.equals(server) && candidate.date.equals(date)) {
                entries.set(index, snapshot);
                return;
            }
        }
        entries.add(snapshot);
    }

    private DailySnapshot previousDailySnapshot(String server) {
        String today = LocalDate.now().toString();
        return history.stream()
                .filter(value -> value.server.equals(server) && value.date.compareTo(today) < 0)
                .max(Comparator.comparing(value -> value.date))
                .orElse(null);
    }

    private Map<String, Integer> currentTotals(String server) {
        return cache(server).totals;
    }

    private static void add(Map<String, ItemAggregate> totals, StoredItem item, ChestSnapshot chest,
                            ChestMetadata metadata, boolean insideShulker, int multiplier) {
        // Une shulker remplie est un stockage : son contenu compte, pas la boîte elle-même.
        if (item.shulker && item.contents != null && !item.contents.isEmpty()) {
            for (StoredItem child : item.contents) {
                add(totals, child, chest, metadata, true, Math.max(1, multiplier * item.count));
            }
            return;
        }
        int count = Math.max(0, item.count * multiplier);
        ItemAggregate aggregate = totals.computeIfAbsent(item.itemId,
                ignored -> new ItemAggregate(item.itemId, item.displayName));
        MutableSource source = aggregate.sources.computeIfAbsent(chest.id,
                ignored -> new MutableSource(chest, metadata));
        if (insideShulker) {
            aggregate.shulkerCount += count;
            source.shulkerCount += count;
        } else {
            aggregate.directCount += count;
            source.directCount += count;
        }
        long checkedAt = checkedAt(chest);
        if (aggregate.oldestCheckedAt == 0L || checkedAt < aggregate.oldestCheckedAt) {
            aggregate.oldestCheckedAt = checkedAt;
        }
    }

    private static String chestId(String server, String dimension, BlockPos pos) {
        return chestId(server, dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    private static String chestId(String server, String dimension, int x, int y, int z) {
        return server + "|" + dimension + "|" + x + "," + y + "," + z;
    }

    private static long checkedAt(ChestSnapshot chest) {
        return chest.checkedAt > 0L ? chest.checkedAt : chest.updatedAt;
    }

    private static String sourceLabel(MutableSource source) {
        String title = source.metadata.customName.isBlank() ? source.chest.title : source.metadata.customName;
        String tags = source.metadata.tags.isEmpty() ? "" : "  #" + String.join(" #", source.metadata.tags);
        return title + tags + "  [" + source.chest.x + ", " + source.chest.y + ", " + source.chest.z + "]";
    }

    private static String displayName(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            return new ItemStack(Registries.ITEM.get(id)).getName().getString();
        }
        return itemId;
    }

    private static StoredItem store(ItemStack stack, int depth) {
        boolean shulker = isShulker(stack);
        List<StoredItem> contents = new ArrayList<>();
        if (shulker && depth < MAX_SHULKER_DEPTH) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack child : container.iterateNonEmpty()) contents.add(store(child, depth + 1));
            }
        }
        return new StoredItem(Registries.ITEM.getId(stack.getItem()).toString(),
                stack.getName().getString(), stack.getCount(), shulker, contents);
    }

    private static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static String fingerprint(List<StoredItem> items) {
        StringBuilder value = new StringBuilder();
        appendFingerprint(value, items);
        return Integer.toHexString(value.toString().hashCode()) + ":" + value.length();
    }

    private static void appendFingerprint(StringBuilder target, List<StoredItem> items) {
        for (StoredItem item : items) {
            target.append(item.itemId).append('=').append(item.count).append('{');
            appendFingerprint(target, item.contents);
            target.append('}');
        }
    }

    static String normalize(String value) {
        return value == null ? "" : COMBINING_MARKS.matcher(
                java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            IndexFile file = GSON.fromJson(reader, IndexFile.class);
            if (file == null) return;
            if (file.chests != null) {
                for (ChestSnapshot chest : file.chests) {
                    if (chest != null && chest.id != null) chests.put(chest.id, chest);
                }
            }
            if (file.history != null) history.addAll(file.history);
            if (file.watchThresholds != null) file.watchThresholds.forEach((server, values) ->
                    watchThresholds.put(server, new LinkedHashMap<>(values)));
            if (file.chestMetadata != null) chestMetadata.putAll(file.chestMetadata);
            if (file.savedViews != null) file.savedViews.forEach((server, values) ->
                    savedViews.put(server, List.copyOf(values)));
            if (file.alertSnoozes != null) alertSnoozes.putAll(file.alertSnoozes);
            if (file.alertLowStates != null) alertLowStates.putAll(file.alertLowStates);
        } catch (Exception exception) {
            TropimonStocksManagerClient.LOGGER.warn("Unable to read the local town chest index", exception);
        }
    }

    private void requestSave(long now) {
        revision++;
        if (lastSaveAttempt == 0L || now - lastSaveAttempt >= SAVE_INTERVAL_MILLIS) saveNow();
    }

    private void saveNow() {
        if (closed || writer.isScheduled(revision)) return;
        // Only copy collection roots here: all chest/item/day values are immutable records.
        IndexFile snapshot = new IndexFile(3, Instant.now().toString(),
                new ArrayList<>(chests.values()), history, watchThresholds, chestMetadata,
                savedViews, alertSnoozes, alertLowStates);
        lastSaveAttempt = System.currentTimeMillis();
        writer.submit(revision, snapshot);
    }

    static void writeSnapshot(Path path, IndexFile snapshot) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer output = Files.newBufferedWriter(temporary)) {
            GSON.toJson(snapshot, output);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        long finalRevision;
        synchronized (this) {
            if (closed) return;
            saveNow();
            closed = true;
            finalRevision = revision;
        }
        // Never wait with the index lock held. The game only blocks here, during shutdown.
        writer.close();
        if (writer.savedRevision() < finalRevision) {
            TropimonStocksManagerClient.LOGGER.error(
                    "Town chest index shutdown left unsaved changes (saved revision {}, requested {})",
                    writer.savedRevision(), finalRevision);
        }
    }

    private static final class ServerCache {
        final Map<String, ItemAggregate> items;
        final Map<String, Integer> totals;
        final Map<Scope, List<ItemAggregate>> ordered = new EnumMap<>(Scope.class);

        ServerCache(Map<String, ItemAggregate> items) {
            this.items = items;
            Map<String, Integer> counts = new LinkedHashMap<>();
            items.forEach((id, item) -> counts.put(id, item.directCount + item.shulkerCount));
            totals = immutableMap(counts);
        }

        List<ItemAggregate> ordered(Scope scope) {
            return ordered.computeIfAbsent(scope, value -> {
                List<ItemAggregate> result = new ArrayList<>(items.values());
                result.forEach(ItemAggregate::searchText);
                result.sort(Comparator.comparingInt((ItemAggregate item) -> item.total(value)).reversed()
                        .thenComparing(ItemAggregate::displayName, String.CASE_INSENSITIVE_ORDER));
                return List.copyOf(result);
            });
        }
    }

    public enum Scope { ALL, DIRECT, SHULKER }

    public enum SortOrder { QUANTITY, NAME, VARIATION, FRESHNESS, ALERT }

    public enum Namespace {
        ALL(""), MINECRAFT("minecraft"), COBBLEMON("cobblemon"), TROPIMON("tropimon");

        private final String prefix;

        Namespace(String prefix) { this.prefix = prefix; }

        boolean accepts(String itemId) {
            if (this == ALL) return true;
            String itemNamespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "minecraft";
            return this == TROPIMON
                    ? itemNamespace.equals("tropimon") || itemNamespace.equals("tropimod")
                    : itemNamespace.equals(prefix);
        }
    }

    public enum Freshness { FRESH, AGING, STALE, UNKNOWN }

    public static final class ItemAggregate {
        private final String itemId;
        private final String displayName;
        private final Map<String, MutableSource> sources;
        private Map<String, Integer> cachedSourceCounts;
        private List<ItemSource> cachedSourceDetails;
        private String searchText;
        private int directCount;
        private int shulkerCount;
        private int total;
        private long oldestCheckedAt;
        private boolean hasDailyComparison;
        private int previousTotal;
        private int dailyDelta;
        private String comparisonDate = "";
        private int watchThreshold;

        private ItemAggregate(String itemId, String displayName) {
            this(itemId, displayName, new LinkedHashMap<>());
        }

        private ItemAggregate(String itemId, String displayName, Map<String, MutableSource> sources) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.sources = sources;
        }

        private int total(Scope scope) {
            return switch (scope) {
                case ALL -> directCount + shulkerCount;
                case DIRECT -> directCount;
                case SHULKER -> shulkerCount;
            };
        }

        private String searchText() {
            if (searchText == null) searchText = normalize(displayName + " " + itemId + " "
                    + String.join(" ", sourceCounts().keySet()));
            return searchText;
        }

        private ItemAggregate view(Scope scope) {
            ItemAggregate result = new ItemAggregate(itemId, displayName, sources);
            result.cachedSourceCounts = sourceCounts();
            result.cachedSourceDetails = sourceDetails();
            result.directCount = directCount;
            result.shulkerCount = shulkerCount;
            result.oldestCheckedAt = oldestCheckedAt;
            result.total = total(scope);
            return result;
        }

        public String itemId() { return itemId; }
        public String displayName() { return displayName; }
        public int directCount() { return directCount; }
        public int shulkerCount() { return shulkerCount; }
        public int total() { return total; }
        public long oldestCheckedAt() { return oldestCheckedAt; }
        public Freshness freshness() { return TownChestIndex.freshness(oldestCheckedAt, System.currentTimeMillis()); }
        public boolean hasDailyComparison() { return hasDailyComparison; }
        public int previousTotal() { return previousTotal; }
        public int dailyDelta() { return dailyDelta; }
        public String comparisonDate() { return comparisonDate; }
        public int watchThreshold() { return watchThreshold; }
        public boolean belowThreshold() { return watchThreshold > 0 && directCount + shulkerCount < watchThreshold; }

        public Map<String, Integer> sourceCounts() {
            if (cachedSourceCounts != null) return cachedSourceCounts;
            Map<String, Integer> result = new LinkedHashMap<>();
            for (MutableSource source : sources.values()) {
                result.put(sourceLabel(source), source.directCount + source.shulkerCount);
            }
            cachedSourceCounts = Map.copyOf(result);
            return cachedSourceCounts;
        }

        public List<ItemSource> sourceDetails() {
            if (cachedSourceDetails != null) return cachedSourceDetails;
            List<ItemSource> result = new ArrayList<>();
            for (MutableSource source : sources.values()) {
                ChestSnapshot chest = source.chest;
                String title = source.metadata.customName.isBlank() ? chest.title : source.metadata.customName;
                result.add(new ItemSource(chest.id, title, chest.dimension,
                        chest.x, chest.y, chest.z, source.directCount, source.shulkerCount, checkedAt(chest),
                        source.metadata.tags, source.metadata.favorite));
            }
            result.sort(Comparator.comparingInt(ItemSource::total).reversed()
                    .thenComparing(ItemSource::title, String.CASE_INSENSITIVE_ORDER));
            cachedSourceDetails = List.copyOf(result);
            return cachedSourceDetails;
        }
    }

    public record ItemSource(String chestId, String title, String dimension, int x, int y, int z,
                             int directCount, int shulkerCount, long checkedAt, List<String> tags,
                             boolean favorite) {
        public int total() { return directCount + shulkerCount; }
        public Freshness freshness() { return TownChestIndex.freshness(checkedAt, System.currentTimeMillis()); }
    }

    public record WatchStatus(String itemId, String displayName, int current, int threshold,
                              boolean belowThreshold, long snoozedUntil) {
        public boolean snoozed(long now) { return snoozedUntil > now; }
    }

    public record ItemHistoryEntry(String date, long recordedAt, int total,
                                   boolean hasPrevious, int delta) { }

    public record HistoryStats(int days, List<ItemHistoryEntry> entries, double averageDailyConsumption,
                               int estimatedDaysRemaining) { }

    public record Summary(int chestCount, int itemTypes, int itemCount, long updatedAt) { }

    public record ChestInfo(String id, String displayName, String originalTitle, String dimension,
                            int x, int y, int z, long checkedAt, List<String> tags, boolean favorite,
                            int missingPasses, long lastMissingAt) {
        public Freshness freshness() { return TownChestIndex.freshness(checkedAt, System.currentTimeMillis()); }
        public boolean suspectedMissing() { return missingPasses > 0; }
        public boolean removable() { return missingPasses >= 2; }
    }

    public record ChestLocation(String id, BlockPos pos) { }

    private static final class MutableSource {
        final ChestSnapshot chest;
        final ChestMetadata metadata;
        int directCount;
        int shulkerCount;

        MutableSource(ChestSnapshot chest, ChestMetadata metadata) {
            this.chest = chest;
            this.metadata = metadata;
        }
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    record IndexFile(int version, String savedAt, List<ChestSnapshot> chests,
                     List<DailySnapshot> history, Map<String, Map<String, Integer>> watchThresholds,
                     Map<String, ChestMetadata> chestMetadata, Map<String, List<SavedView>> savedViews,
                     Map<String, Long> alertSnoozes, Map<String, Boolean> alertLowStates) {
        IndexFile(int version, String savedAt, List<ChestSnapshot> chests,
                  List<DailySnapshot> history, Map<String, Map<String, Integer>> watchThresholds) {
            this(version, savedAt, chests, history, watchThresholds, Map.of(), Map.of(), Map.of(), Map.of());
        }
        IndexFile {
            chests = chests == null ? List.of() : List.copyOf(chests);
            history = history == null ? List.of() : List.copyOf(history);
            Map<String, Map<String, Integer>> thresholds = new LinkedHashMap<>();
            if (watchThresholds != null) watchThresholds.forEach((server, values) ->
                    thresholds.put(server, immutableMap(values)));
            watchThresholds = immutableMap(thresholds);
            chestMetadata = immutableMap(chestMetadata);
            Map<String, List<SavedView>> views = new LinkedHashMap<>();
            if (savedViews != null) savedViews.forEach((server, values) ->
                    views.put(server, values == null ? List.of() : List.copyOf(values)));
            savedViews = immutableMap(views);
            alertSnoozes = immutableMap(alertSnoozes);
            alertLowStates = immutableMap(alertLowStates);
        }
    }

    public record SavedView(String name, String query, Scope scope, Namespace namespace, SortOrder sort,
                            boolean lowOnly, boolean staleOnly) {
        SavedView sanitized() {
            String cleanName = name == null ? "" : name.strip();
            if (cleanName.length() > 32) cleanName = cleanName.substring(0, 32);
            String cleanQuery = query == null ? "" : query.strip();
            if (cleanQuery.length() > 80) cleanQuery = cleanQuery.substring(0, 80);
            return new SavedView(cleanName, cleanQuery, scope == null ? Scope.ALL : scope,
                    namespace == null ? Namespace.ALL : namespace,
                    sort == null ? SortOrder.QUANTITY : sort, lowOnly, staleOnly);
        }
    }

    record ChestMetadata(String customName, List<String> tags, boolean favorite,
                         int missingPasses, long lastMissingAt) {
        static final ChestMetadata EMPTY = new ChestMetadata("", List.of(), false, 0, 0L);
        ChestMetadata {
            customName = customName == null ? "" : customName;
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    record DailySnapshot(String server, String date, long recordedAt, Map<String, Integer> totals) {
        DailySnapshot { totals = immutableMap(totals); }
    }

    record ChestSnapshot(String id, String server, String dimension, int x, int y, int z, String title,
                         long updatedAt, long checkedAt, String fingerprint, List<StoredItem> items) {
        ChestSnapshot { items = items == null ? List.of() : List.copyOf(items); }
    }

    record StoredItem(String itemId, String displayName, int count, boolean shulker, List<StoredItem> contents) {
        StoredItem { contents = contents == null ? List.of() : List.copyOf(contents); }
    }
}
