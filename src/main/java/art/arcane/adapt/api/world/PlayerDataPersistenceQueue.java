package art.arcane.adapt.api.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.io.SQLManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
  private static final String DELETE_JOURNAL_HEADER = "ADAPT_DELETE_JOURNAL_V1";
  private static final String DELETE_JOURNAL_SAVE = "SAVE";
  private static final String SQL_RECOVERY_HEADER = "ADAPT_SQL_RECOVERY_V1";
  private static final int DELETE_OPERATION_LOCK_COUNT = 1_024;
  private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 30_000L;
  private static final int SQL_BATCH_SIZE = 128;
  private static final long SQL_BATCH_GATHER_MILLIS = 25L;
  private static final long[] RETRY_BACKOFF_MILLIS = {50L, 250L, 1_000L};
  private static final Object[] DELETE_OPERATION_LOCKS = createDeleteOperationLocks();

  private final ExecutorService ioExecutor;
  private final RetryScheduler retryScheduler;
  private final RetryScheduler sqlBatchScheduler;
  private final Map<UUID, SaveRequest> pendingSaves = new ConcurrentHashMap<>();
  private final Map<UUID, DeleteRequest> pendingDeletes = new ConcurrentHashMap<>();
  private final Set<CompletableFuture<SQLManager.TokenMutationResult>> pendingMutations =
      ConcurrentHashMap.newKeySet();
  private final Set<UUID> drainTokens = ConcurrentHashMap.newKeySet();
  private final Object lifecycleLock = new Object();
  private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
  private final AtomicBoolean sqlDrainScheduled = new AtomicBoolean();
  private final AtomicLong deleteRevision = new AtomicLong();

  public PlayerDataPersistenceQueue() {
    this(createPersistenceExecutor());
  }

  private PlayerDataPersistenceQueue(ScheduledExecutorService ioExecutor) {
    this(ioExecutor,
        (task, delayMillis) -> ioExecutor.schedule(task, delayMillis, TimeUnit.MILLISECONDS),
        (task, delayMillis) -> ioExecutor.schedule(task, delayMillis, TimeUnit.MILLISECONDS));
  }

  PlayerDataPersistenceQueue(ExecutorService ioExecutor) {
    this(ioExecutor,
        (task, delayMillis) -> ioExecutor.execute(task),
        (task, delayMillis) -> ioExecutor.execute(task));
  }

  PlayerDataPersistenceQueue(ExecutorService ioExecutor, RetryScheduler retryScheduler) {
    this(ioExecutor, retryScheduler, (task, delayMillis) -> ioExecutor.execute(task));
  }

  private PlayerDataPersistenceQueue(ExecutorService ioExecutor, RetryScheduler retryScheduler,
                                     RetryScheduler sqlBatchScheduler) {
    this.ioExecutor = Objects.requireNonNull(ioExecutor);
    this.retryScheduler = Objects.requireNonNull(retryScheduler);
    this.sqlBatchScheduler = Objects.requireNonNull(sqlBatchScheduler);
  }

  public int pendingCount() {
    return pendingSaves.size() + pendingDeletes.size() + pendingMutations.size();
  }

  public void queueSave(UUID uuid, String json, File localFile) {
    queueSave(uuid, json, localFile, PlayerDataPurgeGuard.generation(uuid));
  }

  void queueSave(FencedPlayerSnapshot snapshot, File localFile, long purgeGeneration) {
    Objects.requireNonNull(snapshot);
    queueSave(snapshot.playerId(), snapshot.json(), localFile, purgeGeneration, snapshot);
  }

  void queueSave(UUID uuid, String json, File localFile, long purgeGeneration) {
    queueSave(uuid, json, localFile, purgeGeneration, null);
  }

  private void queueSave(UUID uuid, String json, File localFile, long purgeGeneration,
                         FencedPlayerSnapshot fencedSnapshot) {
    if (uuid == null || json == null || !acceptingTasks.get()
        || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)) {
      return;
    }

    SaveTarget target;
    try {
      target = captureSaveTarget(localFile);
    } catch (RuntimeException error) {
      Adapt.warn("Failed to capture player data save target for " + uuid + ": " + error.getMessage());
      Adapt.error(error);
      return;
    }
    if (target instanceof SqlSaveTarget && fencedSnapshot == null) {
      Adapt.warn("Refusing unfenced SQL player data save for " + uuid);
      return;
    }

    SaveRequest request = new SaveRequest(uuid, json, fencedSnapshot, target, 0);
    Object operationLock = deleteOperationLock(uuid);
    synchronized (operationLock) {
      synchronized (lifecycleLock) {
        if (!acceptingTasks.get() || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)) {
          return;
        }
        DeleteRequest activeDelete = pendingDeletes.get(uuid);
        if (activeDelete != null) {
          if (hasInvalidDeleteJournal(activeDelete.deleteMarker())) {
            Adapt.warn("Refusing to replace invalid player data deletion journal for " + uuid + ": "
                + activeDelete.deleteMarker().getAbsolutePath());
            return;
          }
          long revision = nextDeleteRevision(activeDelete.deleteMarker(), activeDelete);
          DeleteRequest replacement = activeDelete.withSuccessor(request, revision);
          try {
            replacement.journalSuccessor();
          } catch (IOException error) {
            Adapt.warn("Failed to attach player data save behind deletion for " + uuid + ": " + error.getMessage());
            Adapt.error(error);
            pendingSaves.put(uuid, request);
            return;
          }
          pendingSaves.remove(uuid);
          if (!pendingDeletes.replace(uuid, activeDelete, replacement)) {
            Adapt.warn("Failed to replace the active player data deletion for " + uuid
                + " after its successor journal was written.");
            return;
          }
          submitDelete(replacement);
          return;
        }
        SaveRequest current = pendingSaves.get(uuid);
        if (!canReplacePendingSave(current, request)) {
          return;
        }
        pendingSaves.put(uuid, request);
        scheduleDrain(uuid);
      }
    }
  }

  boolean queueReset(UUID uuid, String json, File localFile, long purgeGeneration) {
    if (uuid == null || json == null || !acceptingTasks.get()
        || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)
        || AdaptConfig.get().getSql().isEnabled()) {
      return false;
    }

    File capturedFile = Objects.requireNonNull(localFile, "Local player data file is required").getAbsoluteFile();
    File deleteMarker = deleteMarkerFile(capturedFile);
    SaveTarget target;
    try {
      target = captureSaveTarget(capturedFile);
    } catch (RuntimeException error) {
      Adapt.warn("Failed to capture player data reset target for " + uuid + ": " + error.getMessage());
      Adapt.error(error);
      return false;
    }

    SaveRequest successor = new SaveRequest(uuid, json, null, target, 0);
    Object operationLock = deleteOperationLock(uuid);
    synchronized (operationLock) {
      synchronized (lifecycleLock) {
        if (!acceptingTasks.get() || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)) {
          return false;
        }

        DeleteRequest activeDelete = pendingDeletes.get(uuid);
        if (hasInvalidDeleteJournal(deleteMarker)) {
          Adapt.warn("Refusing to reset player data while its deletion journal is invalid: "
              + deleteMarker.getAbsolutePath());
          return false;
        }
        long revision = nextDeleteRevision(deleteMarker, activeDelete);
        DeleteRequest replacement;
        if (activeDelete == null) {
          replacement = new DeleteRequest(uuid, capturedFile, sqlRecoveryFile(capturedFile),
              deleteMarker, revision, successor, operationLock, 0);
        } else {
          replacement = activeDelete.withSuccessor(successor, revision);
        }

        try {
          replacement.journalSuccessor();
        } catch (IOException error) {
          Adapt.warn("Failed to persist the player data reset journal for " + uuid + ": " + error.getMessage());
          Adapt.error(error);
          return false;
        }

        pendingSaves.remove(uuid);
        if (activeDelete == null) {
          pendingDeletes.put(uuid, replacement);
        } else if (!pendingDeletes.replace(uuid, activeDelete, replacement)) {
          Adapt.warn("Failed to replace the active player data deletion for " + uuid
              + " after its reset journal was written.");
          return true;
        }
        submitDelete(replacement);
        return true;
      }
    }
  }

  String getPendingSave(UUID uuid) {
    SaveRequest request = uuid == null ? null : pendingSaves.get(uuid);
    if (request != null) {
      return request.json();
    }
    DeleteRequest deleteRequest = uuid == null ? null : pendingDeletes.get(uuid);
    return deleteRequest == null || deleteRequest.successor() == null
        ? null
        : deleteRequest.successor().json();
  }

  FencedPlayerSnapshot getPendingFencedSnapshot(UUID uuid) {
    SaveRequest request = uuid == null ? null : pendingSaves.get(uuid);
    if (request != null && request.fencedSnapshot() != null) {
      return request.fencedSnapshot();
    }
    DeleteRequest deleteRequest = uuid == null ? null : pendingDeletes.get(uuid);
    return deleteRequest == null || deleteRequest.successor() == null
        ? null
        : deleteRequest.successor().fencedSnapshot();
  }

  boolean hasPendingDelete(UUID uuid) {
    return uuid != null && pendingDeletes.containsKey(uuid);
  }

  void discardFencedSaves(UUID uuid, UUID currentOwnerToken, long currentEpoch) {
    if (uuid == null || currentOwnerToken == null) {
      return;
    }
    synchronized (deleteOperationLock(uuid)) {
      SaveRequest request = pendingSaves.get(uuid);
      FencedPlayerSnapshot snapshot = request == null ? null : request.fencedSnapshot();
      if (snapshot != null && !snapshot.belongsTo(currentOwnerToken, currentEpoch)) {
        pendingSaves.remove(uuid, request);
      }
    }
  }

  void discardFencedSavesBefore(UUID uuid, long minimumEpoch) {
    if (uuid == null || minimumEpoch < 1L) {
      return;
    }
    synchronized (deleteOperationLock(uuid)) {
      SaveRequest request = pendingSaves.get(uuid);
      FencedPlayerSnapshot snapshot = request == null ? null : request.fencedSnapshot();
      if (snapshot != null && snapshot.epoch() < minimumEpoch) {
        pendingSaves.remove(uuid, request);
      }
    }
  }

  void discardPredecessorSaves(UUID uuid, UUID predecessorToken, long predecessorEpoch) {
    if (uuid == null || predecessorToken == null) {
      return;
    }
    synchronized (deleteOperationLock(uuid)) {
      SaveRequest request = pendingSaves.get(uuid);
      FencedPlayerSnapshot snapshot = request == null ? null : request.fencedSnapshot();
      if (snapshot != null && snapshot.belongsTo(predecessorToken, predecessorEpoch)) {
        pendingSaves.remove(uuid, request);
      }
    }
  }

  public void queueDelete(UUID uuid, File localFile) {
    if (uuid == null || !acceptingTasks.get() || AdaptConfig.get().getSql().isEnabled()) {
      return;
    }

    File capturedFile = Objects.requireNonNull(localFile, "Local player data file is required").getAbsoluteFile();
    File recoveryFile = sqlRecoveryFile(capturedFile);
    File deleteMarker = deleteMarkerFile(capturedFile);
    Object operationLock = deleteOperationLock(uuid);
    synchronized (operationLock) {
      synchronized (lifecycleLock) {
        if (!acceptingTasks.get()) {
          return;
        }
        if (hasInvalidDeleteJournal(deleteMarker)) {
          Adapt.warn("Refusing to delete player data while its deletion journal is invalid: "
              + deleteMarker.getAbsolutePath());
          return;
        }
        long revision = nextDeleteRevision(deleteMarker, null);
        DeleteRequest request = new DeleteRequest(uuid, capturedFile, recoveryFile, deleteMarker,
            revision, null, operationLock, 0);
        try {
          writeDeleteMarker(deleteMarker, revision);
        } catch (IOException error) {
          Adapt.warn("Failed to persist player data deletion marker for " + uuid + ": " + error.getMessage());
          Adapt.error(error);
        }
        pendingSaves.remove(uuid);
        pendingDeletes.put(uuid, request);
        submitDelete(request);
      }
    }
  }

  boolean resumeDeleteThenSave(UUID uuid, File localFile, DeleteJournal journal) {
    if (uuid == null || journal == null || !journal.valid()
        || !journal.hasSuccessor() || !acceptingTasks.get()
        || AdaptConfig.get().getSql().isEnabled()) {
      return false;
    }

    SaveTarget successorTarget;
    try {
      successorTarget = captureSaveTarget(localFile);
    } catch (RuntimeException error) {
      Adapt.warn("Failed to resume delete-then-save journal for " + uuid + ": " + error.getMessage());
      Adapt.error(error);
      return false;
    }

    File capturedFile = Objects.requireNonNull(localFile, "Local player data file is required").getAbsoluteFile();
    File recoveryFile = sqlRecoveryFile(capturedFile);
    File deleteMarker = deleteMarkerFile(capturedFile);
    SaveRequest successor = new SaveRequest(uuid, journal.successorJson(), null, successorTarget, 0);
    Object operationLock = deleteOperationLock(uuid);
    DeleteRequest request = new DeleteRequest(uuid, capturedFile, recoveryFile, deleteMarker,
        journal.revision(), successor, operationLock, 0);
    deleteRevision.accumulateAndGet(journal.revision(), Math::max);

    synchronized (operationLock) {
      synchronized (lifecycleLock) {
        if (!acceptingTasks.get()) {
          return false;
        }
        DeleteRequest activeDelete = pendingDeletes.get(uuid);
        if (activeDelete != null && activeDelete.revision() >= journal.revision()) {
          return true;
        }
        pendingSaves.remove(uuid);
        if (activeDelete == null) {
          pendingDeletes.put(uuid, request);
        } else if (!pendingDeletes.replace(uuid, activeDelete, request)) {
          return false;
        }
        submitDelete(request);
        return true;
      }
    }
  }

  CompletableFuture<SQLManager.TokenMutationResult> resetFencedData(
      UUID uuid, String json, File localFile, long purgeGeneration) {
    if (uuid == null || json == null
        || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Fenced reset requires valid player data and generation"));
    }
    return submitFencedMutation(uuid, localFile, sqlManager ->
        sqlManager.resetFencedData(uuid, json));
  }

  CompletableFuture<SQLManager.TokenMutationResult> purgeFencedData(UUID uuid, File localFile) {
    if (uuid == null) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Fenced purge requires a player UUID"));
    }
    return submitFencedMutation(uuid, localFile, sqlManager ->
        sqlManager.purgeFencedData(uuid));
  }

  private CompletableFuture<SQLManager.TokenMutationResult> submitFencedMutation(
      UUID uuid, File localFile, FencedMutation mutation) {
    if (!acceptingTasks.get() || !AdaptConfig.get().getSql().isEnabled()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Fenced SQL mutation is unavailable"));
    }
    Adapt plugin = Adapt.instance;
    SQLManager sqlManager = plugin == null ? null : plugin.getSqlManager();
    if (sqlManager == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("SQL persistence is enabled without an active manager"));
    }

    File capturedFile = Objects.requireNonNull(
        localFile, "Local player data file is required").getAbsoluteFile();
    CompletableFuture<SQLManager.TokenMutationResult> completion = new CompletableFuture<>();
    pendingMutations.add(completion);
    completion.whenComplete((result, error) -> pendingMutations.remove(completion));
    executeFencedMutation(
        uuid, capturedFile, sqlManager, mutation, completion, 0, null);
    return completion;
  }

  private void executeFencedMutation(
      UUID uuid,
      File localFile,
      SQLManager sqlManager,
      FencedMutation mutation,
      CompletableFuture<SQLManager.TokenMutationResult> completion,
      int attempt,
      Throwable previousFailure
  ) {
    if (completion.isDone()) {
      return;
    }
    if (!acceptingTasks.get()) {
      completion.completeExceptionally(new IllegalStateException(
          "Persistence queue shut down during fenced SQL mutation", previousFailure));
      return;
    }
    try {
      ioExecutor.execute(() -> {
        Throwable failure = null;
        SQLManager.TokenMutationResult result = null;
        try {
          result = mutation.execute(sqlManager);
        } catch (Throwable error) {
          failure = error;
        }
        if (result != null && result.successful() && result.newToken() != null) {
          completeFencedMutation(uuid, localFile, result, completion);
          return;
        }
        Throwable terminalFailure = failure == null
            ? new IllegalStateException("SQL manager rejected the fenced mutation")
            : failure;
        retryFencedMutation(
            uuid, localFile, sqlManager, mutation, completion, attempt, terminalFailure);
      });
    } catch (RejectedExecutionException error) {
      completion.completeExceptionally(error);
    }
  }

  private void retryFencedMutation(
      UUID uuid,
      File localFile,
      SQLManager sqlManager,
      FencedMutation mutation,
      CompletableFuture<SQLManager.TokenMutationResult> completion,
      int attempt,
      Throwable failure
  ) {
    if (attempt >= RETRY_BACKOFF_MILLIS.length || !acceptingTasks.get()) {
      completion.completeExceptionally(failure);
      return;
    }
    try {
      retryScheduler.schedule(
          () -> executeFencedMutation(
              uuid, localFile, sqlManager, mutation, completion, attempt + 1, failure),
          RETRY_BACKOFF_MILLIS[attempt]
      );
    } catch (RejectedExecutionException error) {
      error.addSuppressed(failure);
      completion.completeExceptionally(error);
    }
  }

  private void completeFencedMutation(
      UUID uuid,
      File localFile,
      SQLManager.TokenMutationResult result,
      CompletableFuture<SQLManager.TokenMutationResult> completion
  ) {
    synchronized (deleteOperationLock(uuid)) {
      discardFencedSaves(uuid, result.newToken().ownerToken(), result.newToken().epoch());
      pendingDeletes.remove(uuid);
    }
    try {
      Files.deleteIfExists(localFile.toPath());
      Files.deleteIfExists(sqlRecoveryFile(localFile).toPath());
      Files.deleteIfExists(deleteMarkerFile(localFile).toPath());
    } catch (IOException error) {
      Adapt.warn("Fenced SQL mutation committed for " + uuid
          + " but stale local persistence files could not be removed: " + error.getMessage());
      Adapt.error(error);
    }
    completion.complete(result);
  }

  public void flushAndShutdown(long timeoutMs) {
    synchronized (lifecycleLock) {
      acceptingTasks.set(false);
      ioExecutor.shutdown();
    }
    long boundedTimeout = Math.max(0L, timeoutMs);
    boolean terminated = awaitTermination(boundedTimeout);
    if (!terminated) {
      Adapt.warn("Timed out waiting for player data persistence queue to drain with " + pendingCount() + " operation(s) remaining. Forcing shutdown.");
      ioExecutor.shutdownNow();
      terminated = awaitTermination(Math.max(1_000L, boundedTimeout / 2L));
    }

    if (terminated) {
      persistPendingFallbacksSynchronously();
    } else {
      Adapt.warn("Player data persistence executor did not terminate; skipped synchronous fallback to avoid racing newer writes.");
    }

    failPendingMutations();

    if (pendingCount() > 0) {
      Adapt.warn("Player data persistence shutdown left " + pendingCount() + " operation(s) pending for recovery.");
    }
  }

  private void failPendingMutations() {
    IllegalStateException failure = new IllegalStateException(
        "Adapt shut down before the fenced SQL mutation completed");
    for (CompletableFuture<SQLManager.TokenMutationResult> mutation :
        List.copyOf(pendingMutations)) {
      mutation.completeExceptionally(failure);
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
          Adapt.error(error);
        });
        return thread;
      }
    });
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private static Object[] createDeleteOperationLocks() {
    Object[] locks = new Object[DELETE_OPERATION_LOCK_COUNT];
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new Object();
    }
    return locks;
  }

  private static Object deleteOperationLock(UUID uuid) {
    int hash = uuid.hashCode();
    hash ^= hash >>> 16;
    return DELETE_OPERATION_LOCKS[hash & (DELETE_OPERATION_LOCKS.length - 1)];
  }

  private long nextDeleteRevision(File deleteMarker, DeleteRequest activeDelete) {
    long revisionFloor = activeDelete == null ? 0L : activeDelete.revision();
    DeleteJournal journal = readDeleteJournal(deleteMarker);
    if (journal.valid()) {
      revisionFloor = Math.max(revisionFloor, journal.revision());
    }
    deleteRevision.accumulateAndGet(revisionFloor, Math::max);
    return deleteRevision.incrementAndGet();
  }

  private static boolean hasInvalidDeleteJournal(File deleteMarker) {
    return deleteMarker.exists() && !readDeleteJournal(deleteMarker).valid();
  }

  private static boolean canReplacePendingSave(SaveRequest current, SaveRequest replacement) {
    if (current == null || current.fencedSnapshot() == null || replacement.fencedSnapshot() == null) {
      return true;
    }
    FencedPlayerSnapshot existing = current.fencedSnapshot();
    FencedPlayerSnapshot incoming = replacement.fencedSnapshot();
    if (!existing.belongsTo(incoming.ownerToken(), incoming.epoch())) {
      if (incoming.epoch() < existing.epoch()) {
        return false;
      }
      if (incoming.epoch() == existing.epoch()) {
        Adapt.warn("Refusing conflicting player data owners at fence epoch "
            + incoming.epoch() + " for " + incoming.playerId());
        return false;
      }
      return true;
    }
    if (incoming.sequence() < existing.sequence()) {
      return false;
    }
    if (incoming.sequence() == existing.sequence()) {
      if (!incoming.json().equals(existing.json())) {
        Adapt.warn("Refusing conflicting player data snapshots at sequence "
            + incoming.sequence() + " for " + incoming.playerId());
      }
      return false;
    }
    return true;
  }

  private SaveTarget captureSaveTarget(File localFile) {
    Adapt plugin = Adapt.instance;
    File capturedFile = Objects.requireNonNull(localFile, "Local player data file is required").getAbsoluteFile();
    File recoveryFile = sqlRecoveryFile(capturedFile);
    if (AdaptConfig.get().getSql().isEnabled()) {
      SQLManager sqlManager = plugin == null ? null : plugin.getSqlManager();
      return new SqlSaveTarget(sqlManager, recoveryFile);
    }

    return new LocalSaveTarget(capturedFile);
  }

  private void scheduleDrain(UUID uuid) {
    if (!acceptingTasks.get() || pendingDeletes.containsKey(uuid)) {
      return;
    }

    SaveRequest request = pendingSaves.get(uuid);
    if (request == null) {
      return;
    }
    if (request.target() instanceof SqlSaveTarget) {
      scheduleSqlDrain();
      return;
    }
    if (!drainTokens.add(uuid)) {
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
        if (pendingDeletes.containsKey(uuid)) {
          return;
        }

        SaveRequest request = pendingSaves.get(uuid);
        if (request == null) {
          return;
        }
        if (request.target() instanceof SqlSaveTarget) {
          scheduleSqlDrain();
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

  private void scheduleSqlDrain() {
    if (!acceptingTasks.get() || !sqlDrainScheduled.compareAndSet(false, true)) {
      return;
    }
    if (!submitSqlDrain()) {
      sqlDrainScheduled.set(false);
    }
  }

  private boolean submitSqlDrain() {
    try {
      sqlBatchScheduler.schedule(this::drainSqlBatches, SQL_BATCH_GATHER_MILLIS);
      return true;
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data SQL batch task because the queue is shutting down.");
      return false;
    }
  }

  private void drainSqlBatches() {
    boolean retryScheduled = false;
    try {
      while (true) {
        SqlBatch batch = captureSqlBatch();
        if (batch.requests().isEmpty()) {
          return;
        }

        SqlBatchOutcome outcome = persistSqlBatch(batch);
        if (outcome == SqlBatchOutcome.SUCCESS) {
          continue;
        }
        retryScheduled = outcome == SqlBatchOutcome.RETRY_SCHEDULED;
        return;
      }
    } finally {
      if (!retryScheduled) {
        releaseSqlDrain();
      }
    }
  }

  private SqlBatch captureSqlBatch() {
    List<SaveRequest> requests = new ArrayList<>(SQL_BATCH_SIZE);
    SQLManager selectedManager = null;
    boolean managerSelected = false;
    for (SaveRequest request : pendingSaves.values()) {
      UUID uuid = request.uuid();
      if (request.exhausted() || pendingDeletes.containsKey(uuid)
          || pendingSaves.get(uuid) != request
          || !(request.target() instanceof SqlSaveTarget target)) {
        continue;
      }
      if (!managerSelected) {
        selectedManager = target.sqlManager();
        managerSelected = true;
      }
      if (target.sqlManager() != selectedManager) {
        continue;
      }
      requests.add(request);
      if (requests.size() == SQL_BATCH_SIZE) {
        break;
      }
    }
    return new SqlBatch(selectedManager, requests);
  }

  private SqlBatchOutcome persistSqlBatch(SqlBatch batch) {
    List<SQLManager.FencedDataUpdate> updates = new ArrayList<>(batch.requests().size());
    for (SaveRequest request : batch.requests()) {
      FencedPlayerSnapshot snapshot = request.fencedSnapshot();
      if (snapshot == null) {
        return failSqlBatch(batch.requests(), new IllegalStateException(
            "SQL player data save is missing ownership metadata for " + request.uuid()));
      }
      updates.add(new SQLManager.FencedDataUpdate(
          snapshot.playerId(),
          snapshot.ownerToken().toString(),
          snapshot.epoch(),
          snapshot.sequence(),
          snapshot.json()
      ));
    }

    Throwable failure = null;
    List<SQLManager.FencedWriteResult> results = null;
    try {
      if (batch.sqlManager() == null) {
        throw new IllegalStateException("SQL persistence was selected without an active SQL manager");
      }
      results = batch.sqlManager().updateFencedDataBatch(updates);
    } catch (Throwable error) {
      failure = error;
    }

    if (results == null || results.size() != batch.requests().size()) {
      return failSqlBatch(batch.requests(), failure);
    }

    List<SaveRequest> retryableFailures = new ArrayList<>();
    for (int index = 0; index < batch.requests().size(); index++) {
      SaveRequest request = batch.requests().get(index);
      SQLManager.FencedWriteResult result = results.get(index);
      Object operationLock = deleteOperationLock(request.uuid());
      synchronized (operationLock) {
        if (pendingSaves.get(request.uuid()) != request) {
          continue;
        }
        if (result == null || result.status() == SQLManager.FencedWriteStatus.FAILED) {
          retryableFailures.add(request);
          continue;
        }
        SqlSaveTarget target = (SqlSaveTarget) request.target();
        try {
          if (result.status() == SQLManager.FencedWriteStatus.FENCED) {
            target.persistForShutdown(request.uuid(), request.json(), request.fencedSnapshot());
            notifyFenceLost(request.fencedSnapshot());
          } else {
            target.completeCommittedWrite(request.fencedSnapshot());
          }
          pendingSaves.remove(request.uuid(), request);
        } catch (Throwable error) {
          Adapt.warn("SQL player data post-write handling failed for " + request.uuid()
              + ": " + error.getMessage());
          Adapt.error(error);
          retryableFailures.add(request);
        }
      }
    }
    return retryableFailures.isEmpty()
        ? SqlBatchOutcome.SUCCESS
        : retrySqlRequests(retryableFailures);
  }

  private static void notifyFenceLost(FencedPlayerSnapshot snapshot) {
    Adapt plugin = Adapt.instance;
    AdaptServer server = plugin == null ? null : plugin.getAdaptServer();
    if (server != null && snapshot != null) {
      server.onPersistenceFenceLost(
          snapshot.playerId(), snapshot.ownerToken(), snapshot.epoch());
    }
  }

  private SqlBatchOutcome failSqlBatch(List<SaveRequest> requests, Throwable failure) {
    if (failure == null) {
      Adapt.warn("SQL manager rejected a player data batch containing " + requests.size() + " profile(s).");
    } else {
      Adapt.warn("Failed to save a SQL player data batch containing " + requests.size()
          + " profile(s): " + failure.getMessage());
      Adapt.error(failure);
    }

    return retrySqlRequests(requests);
  }

  private SqlBatchOutcome retrySqlRequests(List<SaveRequest> requests) {
    long retryDelayMillis = 0L;
    List<SaveRequest> retries = new ArrayList<>(requests.size());
    boolean terminalFailure = false;
    boolean recoveryRetryRequired = false;
    for (SaveRequest request : requests) {
      UUID uuid = request.uuid();
      synchronized (deleteOperationLock(uuid)) {
        if (pendingSaves.get(uuid) != request) {
          continue;
        }
        if (acceptingTasks.get() && request.attempt() < RETRY_BACKOFF_MILLIS.length) {
          SaveRequest retry = request.retry();
          if (pendingSaves.replace(uuid, request, retry)) {
            retryDelayMillis = Math.max(retryDelayMillis, RETRY_BACKOFF_MILLIS[request.attempt()]);
            retries.add(retry);
          }
        } else if (retainTerminalSqlRequestLocked(request)) {
          terminalFailure = true;
        } else {
          recoveryRetryRequired = true;
        }
      }
    }

    if (!retries.isEmpty()) {
      if (scheduleSqlRetry(retryDelayMillis)) {
        return SqlBatchOutcome.RETRY_SCHEDULED;
      }
      for (SaveRequest retry : retries) {
        synchronized (deleteOperationLock(retry.uuid())) {
          if (pendingSaves.get(retry.uuid()) != retry) {
            continue;
          }
          if (retainTerminalSqlRequestLocked(retry)) {
            terminalFailure = true;
          } else {
            recoveryRetryRequired = true;
          }
        }
      }
    }
    if (recoveryRetryRequired && acceptingTasks.get()
        && scheduleSqlRetry(RETRY_BACKOFF_MILLIS[RETRY_BACKOFF_MILLIS.length - 1])) {
      return SqlBatchOutcome.RETRY_SCHEDULED;
    }
    return terminalFailure || recoveryRetryRequired
        ? SqlBatchOutcome.TERMINAL_FAILURE
        : SqlBatchOutcome.SUCCESS;
  }

  private boolean retainTerminalSqlRequestLocked(SaveRequest request) {
    try {
      request.target().persistForShutdown(
          request.uuid(), request.json(), request.fencedSnapshot());
    } catch (Throwable error) {
      Adapt.warn("Failed to retain terminal SQL player data for " + request.uuid()
          + ": " + error.getMessage());
      Adapt.error(error);
      return false;
    }
    return pendingSaves.replace(request.uuid(), request, request.terminalFailure());
  }

  private boolean scheduleSqlRetry(long delayMillis) {
    try {
      retryScheduler.schedule(this::drainSqlBatches, delayMillis);
      return true;
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data SQL batch retry because the queue is shutting down.");
      return false;
    }
  }

  private void releaseSqlDrain() {
    synchronized (lifecycleLock) {
      sqlDrainScheduled.set(false);
      if (acceptingTasks.get() && hasDrainableSqlSave()) {
        scheduleSqlDrain();
      }
    }
  }

  private boolean hasDrainableSqlSave() {
    for (SaveRequest request : pendingSaves.values()) {
      if (!request.exhausted()
          && !pendingDeletes.containsKey(request.uuid())
          && request.target() instanceof SqlSaveTarget) {
        return true;
      }
    }
    return false;
  }

  private void submitDelete(DeleteRequest request) {
    try {
      ioExecutor.execute(() -> drainDelete(request));
    } catch (RejectedExecutionException error) {
      Adapt.verbose("Rejected player data delete task for " + request.uuid() + " because the queue is shutting down.");
    }
  }

  private void drainDelete(DeleteRequest request) {
    UUID uuid = request.uuid();
    Object operationLock = request.operationLock();
    synchronized (operationLock) {
      if (pendingDeletes.get(uuid) != request) {
        return;
      }
      try {
        request.ensureMarker();
      } catch (Throwable error) {
        handleDeleteFailureLocked(request, error);
        return;
      }
    }

    try {
      request.persistStorage();
    } catch (Throwable error) {
      synchronized (operationLock) {
        handleDeleteFailureLocked(request, error);
      }
      return;
    }

    synchronized (operationLock) {
      if (pendingDeletes.get(uuid) != request) {
        return;
      }
      try {
        request.clearMarker();
      } catch (Throwable error) {
        handleDeleteFailureLocked(request, error);
        return;
      }
      if (pendingDeletes.remove(uuid, request)) {
        scheduleDrain(uuid);
      }
    }
  }

  private void handleDeleteFailureLocked(DeleteRequest request, Throwable error) {
    UUID uuid = request.uuid();
    logDeleteFailure(request, error);
    if (pendingDeletes.get(uuid) != request
        || !acceptingTasks.get()
        || request.attempt() >= RETRY_BACKOFF_MILLIS.length) {
      return;
    }
    DeleteRequest retry = request.retry();
    if (!pendingDeletes.replace(uuid, request, retry)) {
      return;
    }
    long delayMillis = RETRY_BACKOFF_MILLIS[request.attempt()];
    try {
      retryScheduler.schedule(() -> drainDelete(retry), delayMillis);
    } catch (RejectedExecutionException rejected) {
      Adapt.verbose("Rejected player data delete retry for " + uuid + " because the queue is shutting down.");
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
    Adapt.error(error);
  }

  private void logDeleteFailure(DeleteRequest request, Throwable error) {
    int attempt = request.attempt() + 1;
    int maximum = RETRY_BACKOFF_MILLIS.length + 1;
    Adapt.warn("Failed to delete player data for " + request.uuid() + " on attempt " + attempt + "/" + maximum + ": " + error.getMessage());
    Adapt.error(error);
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
    for (DeleteRequest request : List.copyOf(pendingDeletes.values())) {
      UUID uuid = request.uuid();
      synchronized (request.operationLock()) {
        if (pendingDeletes.get(uuid) != request) {
          continue;
        }

        SaveRequest successor = pendingSaves.get(uuid);
        if (successor != null) {
          try {
            long revision = nextDeleteRevision(request.deleteMarker(), request);
            DeleteRequest replacement = request.withSuccessor(successor, revision);
            replacement.journalSuccessor();
            pendingSaves.remove(uuid, successor);
            if (pendingDeletes.replace(uuid, request, replacement)) {
              pendingDeletes.remove(uuid, replacement);
            }
          } catch (Throwable error) {
            Adapt.warn("Final player data delete-then-save journal failed for " + uuid + ": " + error.getMessage());
            Adapt.error(error);
          }
          continue;
        }

        if (request.successor() != null) {
          try {
            request.journalSuccessor();
            pendingDeletes.remove(uuid, request);
          } catch (Throwable error) {
            Adapt.warn("Final player data delete-then-save journal failed for " + uuid + ": " + error.getMessage());
            Adapt.error(error);
          }
          continue;
        }

        try {
          request.ensureMarker();
          request.persistStorage();
          request.clearMarker();
          pendingDeletes.remove(uuid, request);
        } catch (Throwable error) {
          Adapt.warn("Final player data deletion failed for " + uuid + ": " + error.getMessage());
          Adapt.error(error);
        }
      }
    }

    for (SaveRequest request : List.copyOf(pendingSaves.values())) {
      UUID uuid = request.uuid();
      if (pendingSaves.get(uuid) != request) {
        continue;
      }
      if (pendingDeletes.containsKey(uuid)) {
        continue;
      }

      try {
        request.target().persistForShutdown(
            request.uuid(), request.json(), request.fencedSnapshot());
        pendingSaves.remove(uuid, request);
      } catch (Throwable error) {
        Adapt.warn("Final player data fallback failed for " + uuid + ": " + error.getMessage());
        Adapt.error(error);
      }
    }
  }

  @FunctionalInterface
  interface RetryScheduler {
    void schedule(Runnable task, long delayMillis) throws RejectedExecutionException;
  }

  @FunctionalInterface
  private interface FencedMutation {
    SQLManager.TokenMutationResult execute(SQLManager sqlManager);
  }

  private interface SaveTarget {
    void persist(UUID uuid, String json) throws Exception;

    void persistForShutdown(UUID uuid, String json, FencedPlayerSnapshot snapshot) throws Exception;
  }

  private record LocalSaveTarget(File file) implements SaveTarget {
    @Override
    public void persist(UUID uuid, String json) throws IOException {
      writeSnapshot(file, json);
    }

    @Override
    public void persistForShutdown(UUID uuid, String json, FencedPlayerSnapshot snapshot)
        throws IOException {
      persist(uuid, json);
    }
  }

  private record SqlSaveTarget(SQLManager sqlManager, File recoveryFile) implements SaveTarget {
    @Override
    public void persist(UUID uuid, String json) throws IOException {
      throw new IOException("Fenced SQL writes require an ownership snapshot");
    }

    private void completeCommittedWrite(FencedPlayerSnapshot snapshot) throws IOException {
      deleteRecoveryFileIfOwned(recoveryFile, snapshot);
    }

    @Override
    public void persistForShutdown(UUID uuid, String json, FencedPlayerSnapshot snapshot)
        throws IOException {
      if (snapshot == null) {
        throw new IOException("Cannot retain SQL player data without ownership metadata");
      }
      writeSqlRecovery(recoveryFile, snapshot);
    }
  }

  static void writeSnapshot(File file, String json) throws IOException {
    writeSnapshot(file, json, false);
  }

  private static void writeAtomicSnapshot(File file, String json) throws IOException {
    writeSnapshot(file, json, true);
  }

  private static void writeSnapshot(File file, String json, boolean requireAtomicMove) throws IOException {
    Path target = Objects.requireNonNull(file).toPath().toAbsolutePath();
    Path parent = Objects.requireNonNull(target.getParent(), "Player data target requires a parent directory");
    Files.createDirectories(parent);
    Path temporary = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
    try {
      Files.writeString(temporary, json, StandardCharsets.UTF_8);
      try {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException error) {
        if (requireAtomicMove) {
          throw error;
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static File sqlRecoveryFile(File localFile) {
    File absolute = Objects.requireNonNull(localFile).getAbsoluteFile();
    return new File(absolute.getParentFile(), absolute.getName() + ".pending-sql");
  }

  static File deleteMarkerFile(File localFile) {
    File absolute = Objects.requireNonNull(localFile).getAbsoluteFile();
    return new File(absolute.getParentFile(), absolute.getName() + ".pending-delete");
  }

  static void writeSqlRecovery(File recoveryFile, FencedPlayerSnapshot snapshot) throws IOException {
    String envelope = SQL_RECOVERY_HEADER + "\n"
        + snapshot.playerId() + "\n"
        + snapshot.ownerToken() + "\n"
        + snapshot.epoch() + "\n"
        + snapshot.sequence() + "\n"
        + snapshot.json();
    writeAtomicSnapshot(recoveryFile, envelope);
  }

  static SqlRecoverySnapshot readSqlRecovery(File recoveryFile) {
    if (recoveryFile == null || !recoveryFile.exists()) {
      return SqlRecoverySnapshot.invalid();
    }
    try {
      String content = Files.readString(recoveryFile.toPath(), StandardCharsets.UTF_8);
      String[] fields = content.split("\n", 6);
      if (fields.length != 6 || !SQL_RECOVERY_HEADER.equals(fields[0])) {
        return SqlRecoverySnapshot.invalid();
      }
      UUID playerId = UUID.fromString(fields[1]);
      UUID ownerToken = UUID.fromString(fields[2]);
      long epoch = Long.parseLong(fields[3]);
      long sequence = Long.parseLong(fields[4]);
      return SqlRecoverySnapshot.valid(
          new FencedPlayerSnapshot(playerId, ownerToken, epoch, sequence, fields[5]));
    } catch (IOException | IllegalArgumentException error) {
      Adapt.warn("Failed to inspect SQL player data recovery file " + recoveryFile.getAbsolutePath()
          + ": " + error.getMessage());
      Adapt.error(error);
      return SqlRecoverySnapshot.invalid();
    }
  }

  private static void writeDeleteMarker(File deleteMarker, long revision) throws IOException {
    writeAtomicSnapshot(deleteMarker, Long.toString(revision));
  }

  private static void writeDeleteSaveJournal(File deleteMarker, long revision, String json) throws IOException {
    writeAtomicSnapshot(deleteMarker, DELETE_JOURNAL_HEADER + "\n" + revision + "\n"
        + DELETE_JOURNAL_SAVE + "\n" + json);
  }

  static DeleteJournal readDeleteJournal(File deleteMarker) {
    if (deleteMarker == null || !deleteMarker.exists()) {
      return DeleteJournal.invalid();
    }
    try {
      String content = Files.readString(deleteMarker.toPath(), StandardCharsets.UTF_8);
      String[] fields = content.split("\n", 4);
      if (fields.length == 4 && DELETE_JOURNAL_HEADER.equals(fields[0])
          && DELETE_JOURNAL_SAVE.equals(fields[2])) {
        long revision = Long.parseLong(fields[1]);
        if (revision > 0L && revision < Long.MAX_VALUE) {
          return DeleteJournal.save(revision, fields[3]);
        }
        return DeleteJournal.invalid();
      }
      try {
        long revision = Long.parseLong(content.trim());
        return revision > 0L && revision < Long.MAX_VALUE
            ? DeleteJournal.deleteOnly(revision)
            : DeleteJournal.invalid();
      } catch (NumberFormatException ignored) {
        return DeleteJournal.invalid();
      }
    } catch (IOException | NumberFormatException error) {
      Adapt.warn("Failed to inspect player data deletion journal " + deleteMarker.getAbsolutePath()
          + ": " + error.getMessage());
      Adapt.error(error);
      return DeleteJournal.invalid();
    }
  }

  private static boolean deleteMarkerIfOwned(File deleteMarker, long revision) throws IOException {
    if (!deleteMarker.exists()) {
      return true;
    }
    if (readDeleteJournal(deleteMarker).revision() != revision) {
      return false;
    }
    Files.deleteIfExists(deleteMarker.toPath());
    return !deleteMarker.exists();
  }

  private static void deleteRecoveryFile(File recoveryFile) throws IOException {
    Files.deleteIfExists(recoveryFile.toPath());
    if (recoveryFile.exists()) {
      throw new IOException("SQL recovery data file remains after deletion: "
          + recoveryFile.getAbsolutePath());
    }
  }

  private static boolean deleteRecoveryFileIfOwned(File recoveryFile,
                                                   FencedPlayerSnapshot snapshot) throws IOException {
    if (!recoveryFile.exists()) {
      return true;
    }
    SqlRecoverySnapshot recovery = readSqlRecovery(recoveryFile);
    if (!recovery.valid() || !snapshot.equals(recovery.snapshot())) {
      return false;
    }
    deleteRecoveryFile(recoveryFile);
    return true;
  }

  static void deleteAdoptedRecovery(File recoveryFile, UUID predecessorToken,
                                    long predecessorEpoch) throws IOException {
    if (!recoveryFile.exists()) {
      return;
    }
    SqlRecoverySnapshot recovery = readSqlRecovery(recoveryFile);
    if (recovery.valid()
        && recovery.snapshot().belongsTo(predecessorToken, predecessorEpoch)) {
      deleteRecoveryFile(recoveryFile);
    }
  }

  private static void deleteRecoveryFileBestEffort(File recoveryFile) {
    try {
      deleteRecoveryFile(recoveryFile);
    } catch (IOException error) {
      Adapt.verbose("Failed to delete superseded SQL recovery data file "
          + recoveryFile.getAbsolutePath() + ": " + error.getMessage());
    }
  }

  private record SaveRequest(UUID uuid, String json, FencedPlayerSnapshot fencedSnapshot,
                             SaveTarget target, int attempt) {
    private SaveRequest retry() {
      return new SaveRequest(uuid, json, fencedSnapshot, target, attempt + 1);
    }

    private SaveRequest terminalFailure() {
      return new SaveRequest(
          uuid, json, fencedSnapshot, target, RETRY_BACKOFF_MILLIS.length + 1);
    }

    private boolean exhausted() {
      return attempt > RETRY_BACKOFF_MILLIS.length;
    }
  }

  private record SqlBatch(SQLManager sqlManager, List<SaveRequest> requests) {
  }

  record DeleteJournal(long revision, String successorJson, boolean valid) {
    private static DeleteJournal save(long revision, String successorJson) {
      return new DeleteJournal(revision, successorJson, true);
    }

    private static DeleteJournal deleteOnly(long revision) {
      return new DeleteJournal(revision, null, true);
    }

    private static DeleteJournal invalid() {
      return new DeleteJournal(-1L, null, false);
    }

    boolean hasSuccessor() {
      return valid && successorJson != null;
    }
  }

  record SqlRecoverySnapshot(FencedPlayerSnapshot snapshot, boolean valid) {
    private static SqlRecoverySnapshot valid(FencedPlayerSnapshot snapshot) {
      return new SqlRecoverySnapshot(snapshot, true);
    }

    private static SqlRecoverySnapshot invalid() {
      return new SqlRecoverySnapshot(null, false);
    }
  }

  private enum SqlBatchOutcome {
    SUCCESS,
    RETRY_SCHEDULED,
    TERMINAL_FAILURE
  }

  private record DeleteRequest(UUID uuid, File localFile, File recoveryFile, File deleteMarker,
                               long revision, SaveRequest successor, Object operationLock, int attempt) {
    private DeleteRequest retry() {
      return new DeleteRequest(uuid, localFile, recoveryFile, deleteMarker,
          revision, successor, operationLock, attempt + 1);
    }

    private DeleteRequest withSuccessor(SaveRequest latest, long successorRevision) {
      return new DeleteRequest(uuid, localFile, recoveryFile, deleteMarker,
          successorRevision, latest, operationLock, 0);
    }

    private void journalSuccessor() throws IOException {
      if (successor == null) {
        throw new IOException("Delete-then-save journal requires a successor snapshot");
      }
      writeDeleteSaveJournal(deleteMarker, revision, successor.json());
      deleteRecoveryFileBestEffort(recoveryFile);
    }

    private void ensureMarker() throws IOException {
      if (deleteMarker.exists()) {
        DeleteJournal journal = readDeleteJournal(deleteMarker);
        if (!journal.valid()) {
          throw new IOException("Player data deletion marker is invalid and was preserved");
        }
        if (journal.revision() == revision) {
          return;
        }
      }
      if (successor == null) {
        writeDeleteMarker(deleteMarker, revision);
      } else {
        writeDeleteSaveJournal(deleteMarker, revision, successor.json());
      }
    }

    private void persistStorage() throws IOException {
      Files.deleteIfExists(localFile.toPath());
      Files.deleteIfExists(recoveryFile.toPath());
      if (successor != null) {
        try {
          successor.target().persist(uuid, successor.json());
        } catch (Exception error) {
          throw new IOException("Failed to persist player data after durable deletion", error);
        }
      }
    }

    private void clearMarker() throws IOException {
      if (!deleteMarkerIfOwned(deleteMarker, revision)) {
        throw new IOException("Player data deletion marker revision changed before completion");
      }
    }
  }
}
