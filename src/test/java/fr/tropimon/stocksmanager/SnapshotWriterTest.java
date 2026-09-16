package fr.tropimon.stocksmanager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
final class SnapshotWriterTest {
    @Test
    void oneWorkerCoalescesPendingRevisionsWithoutLosingNewestChange() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> writes = new CopyOnWriteArrayList<>();
        Thread caller = Thread.currentThread();
        SnapshotWriter<Integer> writer = new SnapshotWriter<>(value -> {
            assertNotSame(caller, Thread.currentThread());
            if (value == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            writes.add(value);
        }, exception -> fail(exception));
        try {
            writer.submit(1, 1);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            writer.submit(2, 2);
            writer.submit(3, 3);
            writer.submit(2, 99); // an older snapshot must not overwrite the latest
            assertEquals(0, writer.savedRevision());
        } finally {
            release.countDown();
            writer.close();
        }
        assertEquals(List.of(1, 3), writes);
        assertEquals(3, writer.savedRevision());
    }

    @Test
    void failureIsNotAcknowledgedAndShutdownRetriesTheLatestSnapshot() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        SnapshotWriter<String> writer = new SnapshotWriter<>(value -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("disk unavailable");
            assertEquals("latest", value);
        }, exception -> failures.incrementAndGet());
        writer.submit(7, "latest");
        writer.close();
        assertEquals(2, attempts.get());
        assertEquals(1, failures.get());
        assertEquals(7, writer.savedRevision());
    }

    @Test
    void permanentFailureIsReportedAndDoesNotSpinForeverOnShutdown() {
        AtomicInteger failures = new AtomicInteger();
        SnapshotWriter<String> writer = new SnapshotWriter<>(value -> {
            throw new IOException("disk full");
        }, exception -> failures.incrementAndGet());
        writer.submit(1, "unsaved");
        writer.close();
        assertEquals(2, failures.get());
        assertEquals(0, writer.savedRevision());
        assertThrows(IllegalStateException.class, () -> writer.submit(2, "closed"));
    }

    @Test
    void failedRevisionCanBeResubmittedWithoutAnAdditionalChange() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        SnapshotWriter<String> writer = new SnapshotWriter<>(value -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("temporary failure");
        }, exception -> failed.countDown());
        try {
            writer.submit(1, "unchanged");
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertEquals(0, writer.savedRevision());
            writer.submit(1, "unchanged");
        } finally {
            writer.close();
        }
        assertEquals(1, writer.savedRevision());
        assertEquals(2, attempts.get());
    }
}
