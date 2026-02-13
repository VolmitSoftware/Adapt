package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.util.IO;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerDataPersistenceQueue implements AutoCloseable {
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 30_000L;

    private final ExecutorService ioExecutor;
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    public PlayerDataPersistenceQueue() {
        ioExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private int tid = 0;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Adapt PlayerData IO " + (++tid));
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, e) ->
                        Adapt.warn("Uncaught async persistence exception in " + t.getName() + ": " + e.getMessage()));
                return thread;
            }
        });
    }

    public void queueSave(UUID uuid, String json, File localFile) {
        submit("save", uuid, () -> {
            if (AdaptConfig.get().isUseSql()) {
                if (Adapt.instance.getRedisSync() != null) {
                    Adapt.instance.getRedisSync().publish(uuid, json);
                }
                if (Adapt.instance.getSqlManager() != null) {
                    Adapt.instance.getSqlManager().updateData(uuid, json);
                }
                return;
            }

            IO.writeAll(localFile, json);
        });
    }

    public void queueDelete(UUID uuid, File localFile) {
        submit("delete", uuid, () -> {
            if (localFile.exists() && !localFile.delete()) {
                Adapt.verbose("Failed to delete local player data file " + localFile.getAbsolutePath());
            }

            if (AdaptConfig.get().isUseSql() && Adapt.instance.getSqlManager() != null) {
                Adapt.instance.getSqlManager().delete(uuid);
            }
        });
    }

    public void flushAndShutdown(long timeoutMs) {
        acceptingTasks.set(false);
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                Adapt.warn("Timed out waiting for player data persistence queue to drain. Forcing shutdown.");
                ioExecutor.shutdownNow();
                ioExecutor.awaitTermination(Math.max(1000, timeoutMs / 2), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Adapt.warn("Interrupted while shutting down player data persistence queue.");
            ioExecutor.shutdownNow();
        }
    }

    @Override
    public void close() {
        flushAndShutdown(DEFAULT_SHUTDOWN_TIMEOUT_MS);
    }

    private void submit(String operation, UUID uuid, ThrowingRunnable runnable) {
        if (!acceptingTasks.get()) {
            return;
        }

        try {
            ioExecutor.execute(() -> {
                try {
                    runnable.run();
                } catch (Throwable e) {
                    Adapt.warn("Failed to " + operation + " player data for " + uuid + ": " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            Adapt.verbose("Rejected player data " + operation + " task for " + uuid + " because the queue is shutting down.");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
