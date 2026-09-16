package fr.tropimon.stocksmanager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One writer, at most one waiting snapshot. T must be deeply immutable. */
final class SnapshotWriter<T> implements AutoCloseable {
    @FunctionalInterface
    interface Sink<T> { void write(T snapshot) throws Exception; }

    private record Versioned<T>(long revision, T snapshot) { }

    private final Sink<T> sink;
    private final Consumer<Exception> onFailure;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tropimon-stocks-save");
        thread.setDaemon(true);
        return thread;
    });
    private Versioned<T> latest;
    private Versioned<T> pending;
    private long savedRevision;
    private long writingRevision;
    private boolean running;
    private boolean closed;

    SnapshotWriter(Sink<T> sink, Consumer<Exception> onFailure) {
        this.sink = sink;
        this.onFailure = onFailure;
    }

    synchronized long savedRevision() { return savedRevision; }

    synchronized boolean isScheduled(long revision) {
        return revision <= savedRevision || writingRevision == revision
                || pending != null && pending.revision == revision;
    }

    synchronized void submit(long revision, T snapshot) {
        if (closed) throw new IllegalStateException("Snapshot writer is closed");
        if (revision <= savedRevision || latest != null && revision < latest.revision) return;
        latest = new Versioned<>(revision, snapshot);
        pending = latest;
        startWorker();
    }

    private void startWorker() {
        if (running) return;
        running = true;
        executor.execute(() -> {
            try {
                drain();
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    running = false;
                    writingRevision = 0L;
                    notifyAll();
                }
                throw failure;
            }
        });
    }

    private void drain() {
        while (true) {
            Versioned<T> task;
            synchronized (this) {
                task = pending;
                pending = null;
                if (task == null) {
                    running = false;
                    writingRevision = 0L;
                    notifyAll();
                    return;
                }
                writingRevision = task.revision;
            }
            try {
                sink.write(task.snapshot);
                synchronized (this) { savedRevision = task.revision; }
            } catch (Exception exception) {
                // A failed revision is never acknowledged. The caller retries after its delay.
                onFailure.accept(exception);
            } finally {
                synchronized (this) { writingRevision = 0L; }
            }
        }
    }

    @Override
    public void close() {
        boolean interrupted = false;
        synchronized (this) {
            if (closed) return;
            closed = true;
            while (running) {
                try { wait(); } catch (InterruptedException ignored) { interrupted = true; }
            }
            // Retry the newest unsaved snapshot once on shutdown, including a just-failed write.
            if (latest != null && latest.revision > savedRevision) {
                pending = latest;
                startWorker();
                while (running) {
                    try { wait(); } catch (InterruptedException ignored) { interrupted = true; }
                }
            }
            latest = null;
        }
        executor.shutdown();
        if (interrupted) Thread.currentThread().interrupt();
    }
}
