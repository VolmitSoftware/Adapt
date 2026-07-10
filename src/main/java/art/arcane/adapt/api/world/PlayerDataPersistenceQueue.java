package art.arcane.adapt.api.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerDataPersistenceQueue implements AutoCloseable {
  private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 30_000L;
  private static final long[] RETRY_BACKOFF_MILLIS = {50L, 250L, 1_000L};

  private final ExecutorService ioExecutor;
  private final RetryScheduler retryScheduler;
  private final Map<UUID, SaveRequest> pendingSaves = new ConcurrentHashMap<>();
  private final Set<UUID> drainTokens = ConcurrentHashMap.newKeySet();
  private final Object lifecycleLock = new Object();
  private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
  private final AtomicLong requestSequence = new AtomicLong();

  public PlayerDataPersistenceQueue() {
    this(createPersistenceExecutor());
  }

  private PlayerDataPersistenceQueue(ScheduledExecutorService ioExecutor) {
    this(ioExecutor, (task, delayMillis) -> ioExecutor.schedule(task, delayMillis, TimeUnit.MILLISECONDS));
  }

  PlayerDataPersistenceQueue(ExecutorService ioExecutor) {
    this(ioExecutor, (task, delayMillis) -> ioExecutor.execute(task));
  }

  PlayerDataPersistenceQueue(ExecutorService ioExecutor, RetryScheduler retryScheduler) {
    this.ioExecutor = Objects.requireNonNull(ioExecutor);
    this.retryScheduler = Objects.requireNonNull(retryScheduler);
  }

  public void queueSave(UUID uuid, String json, File localFile) {
    if (uuid == null || json == null || !acceptingTasks.get()) {
      return;
    }

    SaveTarget target;
    try {
      target = captureSaveTarget(localFile);
    } catch (RuntimeException error) {
      Adapt.warn("Failed to capture player data save target for " + uuid + ": " + error.getMessage());
      error.printStackTrace();
      return;
    }

    SaveRequest request = new SaveRequest(uuid, json, target, requestSequence.incrementAndGet(), 0);
    synchronized (lifecycleLock) {
      if (!acceptingTasks.get()) {
        return;
      }
      pendingSaves.put(uuid, request);
      scheduleDrain(uuid);
    }
  }

  String getPendingSave(UUID uuid) {
    SaveRequest request = uuid == null ? null : pendingSaves.get(uuid);
    return request == null ? null : request.json();
  }

  public void queueDelete(UUID uuid, File localFile) {
    if (uuid == null || !acceptingTasks.get()) {
      return;
    }

    File capturedFile = localFile == null ? null : localFile.getAbsoluteFile();
    File recoveryFile = capturedFile == null ? null : sqlRecoveryFile(capturedFile);
    boolean deleteSql = AdaptConfig.get().isUseSql();
    SQLManager sqlManager = Adapt.instance == null ? null : Adapt.instance.getSqlManager();
    synchronized (lifecycleLock) {
      if (!acceptingTasks.get()) {
        return;
      }
      pendingSaves.remove(uuid);
      submitOperation("delete", uuid, () -> {
        if (capturedFile != null && capturedFile.exists() && !capturedFile.delete()) {
          Adapt.verbose("Failed to delete local player data file " + capturedFile.getAbsolutePath());
        }
        if (recoveryFile != null && recoveryFile.exists() && !recoveryFile.delete()) {
          Adapt.verbose("Failed to delete SQL recovery data file " + recoveryFile.getAbsolutePath());
        }

        if (deleteSql && sqlManager != null) {
          sqlManager.delete(uuid);
        }
      });
    }
  }

  public void flushAndShutdown(long timeoutMs) {
    synchronized (lifecycleLock) {
      acceptingTasks.set(false);
      ioExecutor.shutdown();
    }
    long boundedTimeout = Math.max(0L, timeoutMs);
    boolean terminated = awaitTermination(boundedTimeout);
    if (!terminated) {
      Adapt.warn("Timed out waiting for player data persistence queue to drain with " + pendingSaves.size() + " UUID(s) remaining. Forcing shutdown.");
      ioExecutor.shutdownNow();
      terminated = awaitTermination(Math.max(1_000L, boundedTimeout / 2L));
    }

    if (terminated) {
      persistPendingFallbacksSynchronously();
    } else {
      Adapt.warn("Player data persistence executor did not terminate; skipped synchronous fallback to avoid racing newer writes.");
    }

    if (!pendingSaves.isEmpty()) {
      Adapt.warn("Player data persistence shutdown left " + pendingSaves.size() + " UUID(s) pending for recovery.");
    }
  }

  @Override
  public void close() {
    flushAndShutdown(DEFAULT_SHUTDOWN_TIMEOUT_MS);
  }

  private static ScheduledExecutorService createPersistenceExecutor() {
    ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
      private int threadId;

      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "Adapt PlayerData IO " + (++threadId));
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((current, error) -> {
          Adapt.warn("Uncaught async persistence exception in " + current.getName() + ": " + error.getMessage());
          error.printStackTrace();
        });
        return thread;
      }
    });
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private SaveTarget captureSaveTarget(File localFile) {
    Adapt plugin = Adapt.instance;
    File capturedFile = Objects.requireNonNull(localFile, "Local player data file is required").getAbsoluteFile();
    File recoveryFile = sqlRecoveryFile(capturedFile);
    if (AdaptConfig.get().isUseSql()) {
      SQLManager sqlManager = plugin == null ? null : plugin.getSqlManager();
      RedisSync redisSync = plugin == null ? null : plugin.getRedisSync();
      return new SqlSaveTarget(sqlManager, redisSync, recoveryFile);
    }

    return new LocalSaveTarget(capturedFile, recoveryFile);
  }

  private void scheduleDrain(UUID uuid) {
    if (!acceptingTasks.get() || !drainTokens.add(uuid)) {
      return;
    }

    if (!submitDrain(uuid)) {
      drainTokens.remove(uuid);
    }
  }

  private boolean submitDrain(UUID uuid) {
    try {
      ioExecutor.execute(() -> drain(uuid));
      return true;
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data save task for " + uuid + " because the queue is shutting down.");
      return false;
    }
  }

  private void drain(UUID uuid) {
    SaveRequest terminalFailure = null;
    boolean retryScheduled = false;
    try {
      while (true) {
        SaveRequest request = pendingSaves.get(uuid);
        if (request == null) {
          return;
        }

        try {
          request.target().persist(request.uuid(), request.json());
        } catch (Throwable error) {
          if (pendingSaves.get(uuid) != request) {
            continue;
          }

          logSaveFailure(request, error);
          if (acceptingTasks.get() && request.attempt() < RETRY_BACKOFF_MILLIS.length) {
            SaveRequest retry = request.retry();
            if (!pendingSaves.replace(uuid, request, retry)) {
              continue;
            }

            long delayMillis = RETRY_BACKOFF_MILLIS[request.attempt()];
            if (scheduleRetry(uuid, delayMillis)) {
              retryScheduled = true;
              return;
            }
            terminalFailure = retry;
            return;
          }

          terminalFailure = request;
          return;
        }

        if (pendingSaves.remove(uuid, request)) {
          return;
        }
      }
    } finally {
      if (!retryScheduled) {
        releaseDrain(uuid, terminalFailure);
      }
    }
  }

  private boolean scheduleRetry(UUID uuid, long delayMillis) {
    try {
      retryScheduler.schedule(() -> drain(uuid), delayMillis);
      return true;
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data save retry for " + uuid + " because the queue is shutting down.");
      return false;
    }
  }

  private void releaseDrain(UUID uuid, SaveRequest terminalFailure) {
    drainTokens.remove(uuid);
    SaveRequest latest = pendingSaves.get(uuid);
    if (latest != null && latest != terminalFailure && acceptingTasks.get()) {
      scheduleDrain(uuid);
    }
  }

  private void logSaveFailure(SaveRequest request, Throwable error) {
    int attempt = request.attempt() + 1;
    int maximum = RETRY_BACKOFF_MILLIS.length + 1;
    Adapt.warn("Failed to save player data for " + request.uuid() + " on attempt " + attempt + "/" + maximum + ": " + error.getMessage());
    error.printStackTrace();
  }

  private boolean submitOperation(String operation, UUID uuid, ThrowingRunnable runnable) {
    if (!acceptingTasks.get()) {
      return false;
    }

    try {
      ioExecutor.execute(() -> {
        try {
          runnable.run();
        } catch (Throwable error) {
          Adapt.warn("Failed to " + operation + " player data for " + uuid + ": " + error.getMessage());
          error.printStackTrace();
        }
      });
      return true;
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data " + operation + " task for " + uuid + " because the queue is shutting down.");
      return false;
    }
  }

  private boolean awaitTermination(long timeoutMs) {
    try {
      return ioExecutor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      Adapt.warn("Interrupted while shutting down player data persistence queue.");
      ioExecutor.shutdownNow();
      return ioExecutor.isTerminated();
    }
  }

  private void persistPendingFallbacksSynchronously() {
    for (SaveRequest request : List.copyOf(pendingSaves.values())) {
      UUID uuid = request.uuid();
      if (pendingSaves.get(uuid) != request) {
        continue;
      }

      try {
        request.target().persistForShutdown(request.uuid(), request.json());
        pendingSaves.remove(uuid, request);
      } catch (Throwable error) {
        Adapt.warn("Final player data fallback failed for " + uuid + ": " + error.getMessage());
        error.printStackTrace();
      }
    }
  }

  @FunctionalInterface
  interface RetryScheduler {
    void schedule(Runnable task, long delayMillis) throws RejectedExecutionException;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private interface SaveTarget {
    void persist(UUID uuid, String json) throws Exception;

    void persistForShutdown(UUID uuid, String json) throws Exception;
  }

  private record LocalSaveTarget(File file, File recoveryFile) implements SaveTarget {
    @Override
    public void persist(UUID uuid, String json) throws Exception {
      IO.writeAll(file, json);
      deleteRecoveryFile(recoveryFile);
    }

    @Override
    public void persistForShutdown(UUID uuid, String json) throws Exception {
      persist(uuid, json);
    }
  }

  private record SqlSaveTarget(SQLManager sqlManager, RedisSync redisSync, File recoveryFile) implements SaveTarget {
    @Override
    public void persist(UUID uuid, String json) {
      if (sqlManager == null) {
        throw new IllegalStateException("SQL persistence was selected without an active SQL manager");
      }
      if (!sqlManager.updateData(uuid, json)) {
        throw new IllegalStateException("SQL manager rejected the player data write");
      }
      if (redisSync != null) {
        redisSync.publish(uuid, json);
      }
      deleteRecoveryFile(recoveryFile);
    }

    @Override
    public void persistForShutdown(UUID uuid, String json) throws Exception {
      IO.writeAll(recoveryFile, json);
    }
  }

  static File sqlRecoveryFile(File localFile) {
    File absolute = Objects.requireNonNull(localFile).getAbsoluteFile();
    return new File(absolute.getParentFile(), absolute.getName() + ".pending-sql");
  }

  private static void deleteRecoveryFile(File recoveryFile) {
    if (recoveryFile.exists() && !recoveryFile.delete()) {
      Adapt.verbose("Failed to delete SQL recovery data file " + recoveryFile.getAbsolutePath());
    }
  }

  private record SaveRequest(UUID uuid, String json, SaveTarget target, long sequence, int attempt) {
    private SaveRequest retry() {
      return new SaveRequest(uuid, json, target, sequence, attempt + 1);
    }
  }
}
