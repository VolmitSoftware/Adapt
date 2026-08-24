package art.arcane.adapt.util.common.io;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class SQLManager {

  private static final String TABLE_NAME = "ADAPT_DATA";
  private static final String FENCE_TABLE_NAME = "ADAPT_DATA_FENCE";
  private static final String ZERO_OWNER_TOKEN = "00000000-0000-0000-0000-000000000000";
  private static final String CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
      + " (UUID CHAR(36) NOT NULL UNIQUE, DATA MEDIUMTEXT NOT NULL) ENGINE=InnoDB";
  private static final String CREATE_FENCE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS " + FENCE_TABLE_NAME
      + " (UUID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,"
      + " OWNER_TOKEN CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
      + " FENCE_EPOCH BIGINT NOT NULL, COMMITTED_SEQUENCE BIGINT NOT NULL,"
      + " OWNER_ADOPTED BOOLEAN NOT NULL,"
      + " ADOPT_FROM_TOKEN CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,"
      + " ADOPT_FROM_EPOCH BIGINT NULL) ENGINE=InnoDB";
  private static final String VALIDATE_TABLE_ENGINES_QUERY =
      "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('"
          + TABLE_NAME + "', '" + FENCE_TABLE_NAME + "')";
  private static final String UPDATE_QUERY = "INSERT INTO " + TABLE_NAME
      + " (UUID, DATA) VALUES(?, ?) ON DUPLICATE KEY UPDATE DATA=?";
  private static final String FETCH_QUERY = "SELECT DATA FROM " + TABLE_NAME + " WHERE UUID=?";
  private static final String DELETE_QUERY = "DELETE FROM " + TABLE_NAME + " WHERE UUID=?";
  private static final String ENSURE_FENCE_QUERY = "INSERT INTO " + FENCE_TABLE_NAME
      + " (UUID, OWNER_TOKEN, FENCE_EPOCH, COMMITTED_SEQUENCE, OWNER_ADOPTED,"
      + " ADOPT_FROM_TOKEN, ADOPT_FROM_EPOCH)"
      + " VALUES(?, '" + ZERO_OWNER_TOKEN + "', 0, 0, TRUE, NULL, NULL)"
      + " ON DUPLICATE KEY UPDATE UUID=VALUES(UUID)";
  private static final String UPDATE_CLAIM_QUERY = "UPDATE " + FENCE_TABLE_NAME
      + " SET OWNER_TOKEN=?, FENCE_EPOCH=?, OWNER_ADOPTED=FALSE,"
      + " ADOPT_FROM_TOKEN=?, ADOPT_FROM_EPOCH=?"
      + " WHERE UUID=? AND OWNER_TOKEN=? AND FENCE_EPOCH=?";
  private static final String UPDATE_COMMITTED_SEQUENCE_QUERY = "UPDATE " + FENCE_TABLE_NAME
      + " SET COMMITTED_SEQUENCE=? WHERE UUID=? AND OWNER_TOKEN=? AND FENCE_EPOCH=?"
      + " AND OWNER_ADOPTED=TRUE AND COMMITTED_SEQUENCE<?";
  private static final String ADOPT_OWNER_QUERY = "UPDATE " + FENCE_TABLE_NAME
      + " SET OWNER_ADOPTED=TRUE, ADOPT_FROM_TOKEN=NULL, ADOPT_FROM_EPOCH=NULL,"
      + " COMMITTED_SEQUENCE=1"
      + " WHERE UUID=? AND OWNER_TOKEN=? AND FENCE_EPOCH=? AND OWNER_ADOPTED=FALSE";
  private static final String ROTATE_OWNER_QUERY = "UPDATE " + FENCE_TABLE_NAME
      + " SET OWNER_TOKEN=?, FENCE_EPOCH=?, COMMITTED_SEQUENCE=0,"
      + " OWNER_ADOPTED=TRUE, ADOPT_FROM_TOKEN=NULL, ADOPT_FROM_EPOCH=NULL"
      + " WHERE UUID=? AND OWNER_TOKEN=? AND FENCE_EPOCH=?";
  private static final int MAX_BATCH_SIZE = 128;
  private static final long CLAIM_GATHER_MILLIS = 25L;
  private static final long MIN_JDBC_TIMEOUT_MILLIS = 1_000L;
  private static final long MAX_JDBC_TIMEOUT_MILLIS = 5_000L;
  private static final int QUERY_TIMEOUT_SECONDS = 5;
  private static final long VALIDATION_CACHE_NANOS = TimeUnit.SECONDS.toNanos(5L);
  private static final Comparator<UUID> UUID_COMPARATOR = Comparator.comparing(UUID::toString);

  private final LongSupplier nanoTime;
  private final Supplier<UUID> tokenSupplier;
  private final ClaimScheduler claimScheduler;
  private final Object claimLock = new Object();
  private final Map<UUID, ClaimGroup> pendingClaims = new LinkedHashMap<>();
  private final Map<UUID, ClaimGroup> inFlightClaims = new HashMap<>();
  private Connection connection;
  private long lastSuccessfulValidationNanos;
  private boolean successfulValidationCached;
  private boolean claimDrainScheduled;
  private boolean shutdown;

  public SQLManager() {
    this(new RuntimeDependencies(
        System::nanoTime,
        UUID::randomUUID,
        (task, delayMillis) -> {
          Executor executor = CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS);
          executor.execute(task);
        }
    ));
  }

  SQLManager(RuntimeDependencies dependencies) {
    RuntimeDependencies required = Objects.requireNonNull(dependencies);
    nanoTime = required.nanoTime();
    tokenSupplier = required.tokenSupplier();
    claimScheduler = required.claimScheduler();
  }

  public synchronized boolean establishConnection() {
    if (shutdown) {
      return false;
    }
    if (connection != null) {
      closeActiveConnection();
    }

    AdaptConfig config = AdaptConfig.get();
    try {
      connection = DriverManager.getConnection(
          assembleUrl(config),
          config.getSql().getUsername(),
          config.getSql().getPassword()
      );
      int verifySeconds = clampValidationSeconds(config.getSql().getSecondsCheckverify());
      if (!connection.isValid(verifySeconds)) {
        throw new SQLException("Connection timed out");
      }
      setupDatabase();
      cacheSuccessfulValidation();
      return true;
    } catch (SQLException error) {
      Connection failedConnection = connection;
      connection = null;
      invalidateValidationCache();
      if (failedConnection != null) {
        try {
          failedConnection.close();
        } catch (SQLException closeError) {
          error.addSuppressed(closeError);
        }
      }
      handleSQLException("Failed to establish a connection to the SQL server!", error);
      return false;
    }
  }

  private void setupDatabase() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.executeUpdate(CREATE_TABLE_QUERY);
      statement.executeUpdate(CREATE_FENCE_TABLE_QUERY);
      validateTableEngines(statement);
    }
  }

  private void validateTableEngines(Statement statement) throws SQLException {
    Set<String> transactionalTables = new HashSet<>();
    try (ResultSet tables = statement.executeQuery(VALIDATE_TABLE_ENGINES_QUERY)) {
      while (tables.next()) {
        String tableName = tables.getString("TABLE_NAME");
        String engine = tables.getString("ENGINE");
        if ("InnoDB".equalsIgnoreCase(engine)) {
          transactionalTables.add(tableName);
        }
      }
    }
    if (!transactionalTables.contains(TABLE_NAME)
        || !transactionalTables.contains(FENCE_TABLE_NAME)) {
      throw new SQLException("Adapt SQL fencing requires InnoDB for ADAPT_DATA and"
          + " ADAPT_DATA_FENCE. Stop the server, back up the database, run"
          + " `ALTER TABLE ADAPT_DATA ENGINE=InnoDB;` and"
          + " `ALTER TABLE ADAPT_DATA_FENCE ENGINE=InnoDB;`, then restart Adapt.");
    }
  }

  public void closeConnection() {
    synchronized (claimLock) {
      synchronized (this) {
        shutdown = true;
        closeActiveConnection();
      }
      failOutstandingClaimsLocked();
    }
  }

  private void closeActiveConnection() {
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException error) {
      handleSQLException("Failed to close the connection to the SQL server!", error);
    }
    connection = null;
    invalidateValidationCache();
  }

  public synchronized FetchResult fetchData(UUID uuid) {
    Connection failedConnection = connection;
    try {
      checkAndReestablishConnection();
      failedConnection = connection;
      try (PreparedStatement statement = prepare(connection, FETCH_QUERY)) {
        statement.setString(1, Objects.requireNonNull(uuid).toString());
        try (ResultSet set = statement.executeQuery()) {
          if (!set.next()) {
            return FetchResult.success(null);
          }
          return FetchResult.success(set.getString("DATA"));
        }
      }
    } catch (SQLException error) {
      discardConnection(failedConnection, error);
      invalidateValidationCache();
      handleSQLException("Failed to read data from the SQL server!", error);
      return FetchResult.failure();
    }
  }

  public CompletableFuture<ClaimResult> claimData(UUID uuid) {
    if (uuid == null) {
      return CompletableFuture.completedFuture(ClaimResult.failed(null));
    }
    if (isShutdown()) {
      return CompletableFuture.completedFuture(ClaimResult.failed(uuid));
    }

    CompletableFuture<ClaimResult> completion = new CompletableFuture<>();
    synchronized (claimLock) {
      if (isShutdown()) {
        completion.complete(ClaimResult.failed(uuid));
        return completion;
      }
      ClaimGroup existing = pendingClaims.get(uuid);
      if (existing == null) {
        existing = inFlightClaims.get(uuid);
      }
      if (existing != null) {
        existing.completions().add(completion);
        return completion;
      }

      ClaimGroup group = new ClaimGroup(uuid, new ArrayDeque<>());
      group.completions().addLast(completion);
      pendingClaims.put(uuid, group);
      scheduleClaimDrainLocked();
    }
    return completion;
  }

  public List<FencedWriteResult> updateFencedDataBatch(List<FencedDataUpdate> updates) {
    List<FencedDataUpdate> captured = List.copyOf(Objects.requireNonNull(updates));
    if (captured.isEmpty()) {
      return List.of();
    }

    Map<UUID, List<IndexedFencedUpdate>> grouped = new HashMap<>();
    List<FencedWriteResult> results = new ArrayList<>(captured.size());
    for (int index = 0; index < captured.size(); index++) {
      FencedDataUpdate update = Objects.requireNonNull(captured.get(index));
      grouped.computeIfAbsent(update.uuid(), unused -> new ArrayList<>())
          .add(new IndexedFencedUpdate(index, update));
      results.add(null);
    }

    List<UUID> sortedIds = new ArrayList<>(grouped.keySet());
    sortedIds.sort(UUID_COMPARATOR);
    for (int start = 0; start < sortedIds.size(); start += MAX_BATCH_SIZE) {
      int end = Math.min(sortedIds.size(), start + MAX_BATCH_SIZE);
      List<IndexedFencedUpdate> batch = new ArrayList<>();
      for (int index = start; index < end; index++) {
        batch.addAll(grouped.get(sortedIds.get(index)));
      }
      List<FencedWriteResult> batchResults = updateFencedBatchTransaction(batch);
      for (int index = 0; index < batch.size(); index++) {
        results.set(batch.get(index).index(), batchResults.get(index));
      }
    }
    return List.copyOf(results);
  }

  public FencedWriteResult adoptFencedData(FencedDataUpdate update) {
    Objects.requireNonNull(update);
    if (update.sequence() != 1L) {
      return new FencedWriteResult(update.uuid(), update.sequence(), FencedWriteStatus.FAILED);
    }
    UUID ownerToken = parseToken(update.token());
    if (ownerToken == null || update.epoch() <= 0L) {
      return new FencedWriteResult(update.uuid(), update.sequence(), FencedWriteStatus.FENCED);
    }

    FencedWriteResult failure = new FencedWriteResult(
        update.uuid(), update.sequence(), FencedWriteStatus.FAILED);
    return executeTransaction(transactionConnection -> {
      Map<UUID, FenceState> states = lockFenceRows(transactionConnection, List.of(update.uuid()));
      FenceState state = states.get(update.uuid());
      if (state == null || !state.ownerToken().equals(ownerToken) || state.epoch() != update.epoch()) {
        return new FencedWriteResult(update.uuid(), update.sequence(), FencedWriteStatus.FENCED);
      }
      if (state.ownerAdopted()) {
        FencedWriteStatus status;
        if (state.committedSequence() == 1L) {
          String committedData = fetchDataRows(transactionConnection, List.of(update.uuid()))
              .get(update.uuid());
          status = update.data().equals(committedData)
              ? FencedWriteStatus.ALREADY_COMMITTED
              : FencedWriteStatus.FAILED;
        } else if (state.committedSequence() > 1L) {
          status = FencedWriteStatus.SUPERSEDED;
        } else {
          status = FencedWriteStatus.FENCED;
        }
        return new FencedWriteResult(update.uuid(), update.sequence(), status);
      }

      upsertData(transactionConnection, update.uuid(), update.data());
      try (PreparedStatement statement = prepare(transactionConnection, ADOPT_OWNER_QUERY)) {
        statement.setString(1, update.uuid().toString());
        statement.setString(2, ownerToken.toString());
        statement.setLong(3, update.epoch());
        ensureAffected(statement.executeUpdate());
      }
      return new FencedWriteResult(update.uuid(), update.sequence(), FencedWriteStatus.COMMITTED);
    }, failure, "Failed to adopt fenced player data ownership!");
  }

  public TokenMutationResult resetFencedData(UUID uuid, String defaultData) {
    Objects.requireNonNull(defaultData);
    return rotateFencedData(Objects.requireNonNull(uuid), defaultData, false);
  }

  public TokenMutationResult purgeFencedData(UUID uuid) {
    return rotateFencedData(Objects.requireNonNull(uuid), null, true);
  }

  Map<UUID, ClaimResult> claimBatch(List<UUID> requestedIds) {
    List<UUID> sortedIds = requestedIds.stream().distinct().sorted(UUID_COMPARATOR).toList();
    Map<UUID, ClaimResult> failure = failedClaims(sortedIds);
    if (sortedIds.isEmpty() || sortedIds.size() > MAX_BATCH_SIZE) {
      return failure;
    }

    return executeTransaction(transactionConnection -> {
      ensureFenceRows(transactionConnection, sortedIds);
      Map<UUID, FenceState> states = lockFenceRows(transactionConnection, sortedIds);
      if (states.size() != sortedIds.size()) {
        throw new SQLException("Failed to lock every requested player data fence row");
      }
      Map<UUID, String> data = fetchDataRows(transactionConnection, sortedIds);
      Map<UUID, ClaimResult> claimed = new LinkedHashMap<>();
      try (PreparedStatement statement = prepare(transactionConnection, UPDATE_CLAIM_QUERY)) {
        for (UUID uuid : sortedIds) {
          FenceState state = states.get(uuid);
          UUID newOwnerToken = nextOwnerToken();
          long newEpoch = nextEpoch(state.epoch());
          SqlToken previousToken = isZeroToken(state.ownerToken())
              ? null
              : storedSqlToken(state.ownerToken(), state.epoch(), "OWNER_TOKEN");
          SqlToken effectivePredecessor = state.ownerAdopted()
              && !isZeroToken(state.ownerToken())
              ? previousToken
              : state.adoptFrom();
          statement.setString(1, newOwnerToken.toString());
          statement.setLong(2, newEpoch);
          if (effectivePredecessor == null) {
            statement.setNull(3, Types.CHAR);
            statement.setNull(4, Types.BIGINT);
          } else {
            statement.setString(3, effectivePredecessor.ownerToken().toString());
            statement.setLong(4, effectivePredecessor.epoch());
          }
          statement.setString(5, uuid.toString());
          statement.setString(6, state.ownerToken().toString());
          statement.setLong(7, state.epoch());
          statement.addBatch();
          claimed.put(uuid, ClaimResult.claimed(
              uuid,
              previousToken,
              effectivePredecessor,
              state.committedSequence(),
              new SqlToken(newOwnerToken, newEpoch),
              data.get(uuid)
          ));
        }
        ensureBatchAffectedAll(statement.executeBatch(), sortedIds.size());
      }
      return claimed;
    }, failure, "Failed to claim fenced player data ownership batch!");
  }

  private void scheduleClaimDrainLocked() {
    if (claimDrainScheduled || pendingClaims.isEmpty()) {
      return;
    }
    claimDrainScheduled = true;
    try {
      claimScheduler.schedule(this::drainClaims, CLAIM_GATHER_MILLIS);
    } catch (RuntimeException error) {
      claimDrainScheduled = false;
      List<ClaimGroup> failed = new ArrayList<>(pendingClaims.values());
      pendingClaims.clear();
      for (ClaimGroup group : failed) {
        completeClaimGroup(group, ClaimResult.failed(group.uuid()));
      }
    }
  }

  private void drainClaims() {
    List<ClaimGroup> captured;
    synchronized (claimLock) {
      captured = new ArrayList<>(pendingClaims.values());
      pendingClaims.clear();
      claimDrainScheduled = false;
      for (ClaimGroup group : captured) {
        inFlightClaims.put(group.uuid(), group);
      }
    }

    captured.sort(Comparator.comparing(ClaimGroup::uuid, UUID_COMPARATOR));
    for (int start = 0; start < captured.size(); start += MAX_BATCH_SIZE) {
      int end = Math.min(captured.size(), start + MAX_BATCH_SIZE);
      List<ClaimGroup> batch = captured.subList(start, end);
      List<UUID> batchIds = new ArrayList<>(batch.size());
      for (ClaimGroup group : batch) {
        batchIds.add(group.uuid());
      }
      Map<UUID, ClaimResult> results;
      try {
        results = claimBatch(batchIds);
      } catch (Throwable error) {
        results = failedClaims(batchIds);
      }
      for (ClaimGroup group : batch) {
        ClaimResult result = results.getOrDefault(group.uuid(), ClaimResult.failed(group.uuid()));
        completeInFlightClaimGroup(group, result);
      }
    }

    synchronized (claimLock) {
      scheduleClaimDrainLocked();
    }
  }

  private void completeClaimGroup(ClaimGroup group, ClaimResult result) {
    List<CompletableFuture<ClaimResult>> completions = List.copyOf(group.completions());
    for (CompletableFuture<ClaimResult> completion : completions) {
      completion.complete(result);
    }
  }

  private void completeInFlightClaimGroup(ClaimGroup group, ClaimResult result) {
    while (true) {
      synchronized (claimLock) {
        CompletableFuture<ClaimResult> completion = group.completions().pollFirst();
        if (completion == null) {
          inFlightClaims.remove(group.uuid(), group);
          return;
        }
        completion.complete(result);
      }
    }
  }

  private List<FencedWriteResult> updateFencedBatchTransaction(List<IndexedFencedUpdate> indexedUpdates) {
    List<FencedWriteResult> failure = new ArrayList<>(indexedUpdates.size());
    for (IndexedFencedUpdate indexed : indexedUpdates) {
      FencedDataUpdate update = indexed.update();
      failure.add(new FencedWriteResult(update.uuid(), update.sequence(), FencedWriteStatus.FAILED));
    }

    return executeTransaction(transactionConnection -> {
      List<UUID> ids = indexedUpdates.stream()
          .map(indexed -> indexed.update().uuid())
          .distinct()
          .sorted(UUID_COMPARATOR)
          .toList();
      Map<UUID, FenceState> states = lockFenceRows(transactionConnection, ids);
      List<UUID> equalSequenceIds = equalSequenceIds(indexedUpdates, states);
      Map<UUID, String> committedData = equalSequenceIds.isEmpty()
          ? Map.of()
          : fetchDataRows(transactionConnection, equalSequenceIds);
      List<FencedWriteResult> classified = new ArrayList<>(indexedUpdates.size());
      for (int index = 0; index < indexedUpdates.size(); index++) {
        classified.add(null);
      }
      List<IndexedFencedUpdate> winners = classifyFencedUpdates(
          indexedUpdates, states, committedData, classified);
      if (!winners.isEmpty()) {
        try (PreparedStatement dataStatement = prepare(transactionConnection, UPDATE_QUERY);
             PreparedStatement sequenceStatement = prepare(
                 transactionConnection, UPDATE_COMMITTED_SEQUENCE_QUERY)) {
          for (IndexedFencedUpdate winner : winners) {
            FencedDataUpdate update = winner.update();
            bindDataUpdate(dataStatement, update.uuid(), update.data());
            dataStatement.addBatch();
            sequenceStatement.setLong(1, update.sequence());
            sequenceStatement.setString(2, update.uuid().toString());
            sequenceStatement.setString(3, update.token());
            sequenceStatement.setLong(4, update.epoch());
            sequenceStatement.setLong(5, update.sequence());
            sequenceStatement.addBatch();
          }
          ensureBatchSucceeded(dataStatement.executeBatch(), winners.size());
          ensureBatchAffectedAll(sequenceStatement.executeBatch(), winners.size());
        }
      }
      return classified;
    }, List.copyOf(failure), "Failed to write fenced player data batch!");
  }

  private List<IndexedFencedUpdate> classifyFencedUpdates(
      List<IndexedFencedUpdate> indexedUpdates,
      Map<UUID, FenceState> states,
      Map<UUID, String> committedData,
      List<FencedWriteResult> results
  ) {
    Map<FencedSequence, String> firstPayloads = new HashMap<>();
    Set<FencedSequence> conflictingPayloads = new HashSet<>();
    for (IndexedFencedUpdate indexed : indexedUpdates) {
      FencedDataUpdate update = indexed.update();
      FencedSequence sequence = new FencedSequence(update.uuid(), update.sequence());
      String firstPayload = firstPayloads.putIfAbsent(sequence, update.data());
      if (firstPayload != null && !firstPayload.equals(update.data())) {
        conflictingPayloads.add(sequence);
      }
    }

    Map<UUID, List<Integer>> eligibleByUuid = new HashMap<>();
    for (int index = 0; index < indexedUpdates.size(); index++) {
      FencedDataUpdate update = indexedUpdates.get(index).update();
      FenceState state = states.get(update.uuid());
      UUID ownerToken = parseToken(update.token());
      FencedWriteStatus immediate = null;
      if (conflictingPayloads.contains(new FencedSequence(update.uuid(), update.sequence()))) {
        immediate = FencedWriteStatus.FAILED;
      } else if (state == null || ownerToken == null || update.epoch() <= 0L
          || !state.ownerToken().equals(ownerToken) || state.epoch() != update.epoch()
          || !state.ownerAdopted()) {
        immediate = FencedWriteStatus.FENCED;
      } else if (update.sequence() < state.committedSequence()) {
        immediate = FencedWriteStatus.SUPERSEDED;
      } else if (update.sequence() == state.committedSequence()) {
        immediate = update.data().equals(committedData.get(update.uuid()))
            ? FencedWriteStatus.ALREADY_COMMITTED
            : FencedWriteStatus.FAILED;
      }
      if (immediate != null) {
        results.set(index, new FencedWriteResult(update.uuid(), update.sequence(), immediate));
      } else {
        eligibleByUuid.computeIfAbsent(update.uuid(), unused -> new ArrayList<>()).add(index);
      }
    }

    List<IndexedFencedUpdate> winners = new ArrayList<>(eligibleByUuid.size());
    for (Map.Entry<UUID, List<Integer>> entry : eligibleByUuid.entrySet()) {
      int winnerIndex = entry.getValue().getFirst();
      for (int candidateIndex : entry.getValue()) {
        FencedDataUpdate candidate = indexedUpdates.get(candidateIndex).update();
        FencedDataUpdate winner = indexedUpdates.get(winnerIndex).update();
        if (candidate.sequence() > winner.sequence()) {
          winnerIndex = candidateIndex;
        }
      }
      for (int candidateIndex : entry.getValue()) {
        FencedDataUpdate update = indexedUpdates.get(candidateIndex).update();
        FencedDataUpdate winner = indexedUpdates.get(winnerIndex).update();
        FencedWriteStatus status;
        if (candidateIndex == winnerIndex) {
          status = FencedWriteStatus.COMMITTED;
        } else if (update.sequence() == winner.sequence() && update.data().equals(winner.data())) {
          status = FencedWriteStatus.ALREADY_COMMITTED;
        } else {
          status = FencedWriteStatus.SUPERSEDED;
        }
        results.set(candidateIndex, new FencedWriteResult(update.uuid(), update.sequence(), status));
      }
      winners.add(indexedUpdates.get(winnerIndex));
    }
    winners.sort(Comparator.comparing(indexed -> indexed.update().uuid(), UUID_COMPARATOR));
    return winners;
  }

  private List<UUID> equalSequenceIds(
      List<IndexedFencedUpdate> indexedUpdates,
      Map<UUID, FenceState> states
  ) {
    Set<UUID> equalSequenceIds = new HashSet<>();
    for (IndexedFencedUpdate indexed : indexedUpdates) {
      FencedDataUpdate update = indexed.update();
      FenceState state = states.get(update.uuid());
      if (state != null && state.ownerAdopted()
          && state.ownerToken().toString().equals(update.token())
          && state.epoch() == update.epoch()
          && state.committedSequence() == update.sequence()) {
        equalSequenceIds.add(update.uuid());
      }
    }
    List<UUID> sortedIds = new ArrayList<>(equalSequenceIds);
    sortedIds.sort(UUID_COMPARATOR);
    return sortedIds;
  }

  private TokenMutationResult rotateFencedData(UUID uuid, String data, boolean purge) {
    TokenMutationResult failure = TokenMutationResult.failed(uuid);
    return executeTransaction(transactionConnection -> {
      ensureFenceRows(transactionConnection, List.of(uuid));
      Map<UUID, FenceState> states = lockFenceRows(transactionConnection, List.of(uuid));
      FenceState state = states.get(uuid);
      if (state == null) {
        throw new SQLException("Failed to lock player data fence row for " + uuid);
      }
      UUID newOwnerToken = nextOwnerToken();
      long newEpoch = nextEpoch(state.epoch());
      if (purge) {
        try (PreparedStatement statement = prepare(transactionConnection, DELETE_QUERY)) {
          statement.setString(1, uuid.toString());
          statement.executeUpdate();
        }
      } else {
        upsertData(transactionConnection, uuid, data);
      }
      try (PreparedStatement statement = prepare(transactionConnection, ROTATE_OWNER_QUERY)) {
        statement.setString(1, newOwnerToken.toString());
        statement.setLong(2, newEpoch);
        statement.setString(3, uuid.toString());
        statement.setString(4, state.ownerToken().toString());
        statement.setLong(5, state.epoch());
        ensureAffected(statement.executeUpdate());
      }
      return TokenMutationResult.committed(uuid, new SqlToken(newOwnerToken, newEpoch));
    }, failure, purge
        ? "Failed to purge fenced player data!"
        : "Failed to reset fenced player data!");
  }

  private void ensureFenceRows(Connection transactionConnection, List<UUID> sortedIds)
      throws SQLException {
    try (PreparedStatement statement = prepare(transactionConnection, ENSURE_FENCE_QUERY)) {
      for (UUID uuid : sortedIds) {
        statement.setString(1, uuid.toString());
        statement.addBatch();
      }
      ensureBatchSucceeded(statement.executeBatch(), sortedIds.size());
    }
  }

  private Map<UUID, FenceState> lockFenceRows(Connection transactionConnection, List<UUID> ids)
      throws SQLException {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UUID> sortedIds = ids.stream().distinct().sorted(UUID_COMPARATOR).toList();
    String query = "SELECT UUID, OWNER_TOKEN, FENCE_EPOCH, COMMITTED_SEQUENCE,"
        + " OWNER_ADOPTED, ADOPT_FROM_TOKEN, ADOPT_FROM_EPOCH FROM " + FENCE_TABLE_NAME
        + " WHERE UUID IN (" + placeholders(sortedIds.size()) + ") ORDER BY UUID FOR UPDATE";
    Map<UUID, FenceState> states = new LinkedHashMap<>();
    try (PreparedStatement statement = prepare(transactionConnection, query)) {
      bindUuids(statement, sortedIds);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          UUID uuid = parseStoredToken(rows.getString("UUID"), "UUID");
          UUID ownerToken = parseStoredToken(rows.getString("OWNER_TOKEN"), "OWNER_TOKEN");
          String predecessorText = rows.getString("ADOPT_FROM_TOKEN");
          SqlToken predecessor = predecessorText == null
              ? null
              : storedSqlToken(
                  parseStoredToken(predecessorText, "ADOPT_FROM_TOKEN"),
                  readRequiredLong(rows, "ADOPT_FROM_EPOCH"),
                  "ADOPT_FROM_TOKEN"
              );
          states.put(uuid, new FenceState(
              uuid,
              ownerToken,
              rows.getLong("FENCE_EPOCH"),
              rows.getLong("COMMITTED_SEQUENCE"),
              rows.getBoolean("OWNER_ADOPTED"),
              predecessor
          ));
        }
      }
    }
    return states;
  }

  private Map<UUID, String> fetchDataRows(Connection transactionConnection, List<UUID> sortedIds)
      throws SQLException {
    String query = "SELECT UUID, DATA FROM " + TABLE_NAME + " WHERE UUID IN ("
        + placeholders(sortedIds.size()) + ") ORDER BY UUID";
    Map<UUID, String> data = new HashMap<>();
    try (PreparedStatement statement = prepare(transactionConnection, query)) {
      bindUuids(statement, sortedIds);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          data.put(parseStoredToken(rows.getString("UUID"), "UUID"), rows.getString("DATA"));
        }
      }
    }
    return data;
  }

  private void upsertData(Connection transactionConnection, UUID uuid, String data)
      throws SQLException {
    try (PreparedStatement statement = prepare(transactionConnection, UPDATE_QUERY)) {
      bindDataUpdate(statement, uuid, data);
      statement.executeUpdate();
    }
  }

  private static void bindDataUpdate(PreparedStatement statement, UUID uuid, String data)
      throws SQLException {
    statement.setString(1, Objects.requireNonNull(uuid).toString());
    statement.setString(2, Objects.requireNonNull(data));
    statement.setString(3, data);
  }

  private static void bindUuids(PreparedStatement statement, List<UUID> sortedIds)
      throws SQLException {
    for (int index = 0; index < sortedIds.size(); index++) {
      statement.setString(index + 1, sortedIds.get(index).toString());
    }
  }

  private static String placeholders(int count) {
    StringBuilder placeholders = new StringBuilder(Math.max(0, count * 2 - 1));
    for (int index = 0; index < count; index++) {
      if (index > 0) {
        placeholders.append(',');
      }
      placeholders.append('?');
    }
    return placeholders.toString();
  }

  private static PreparedStatement prepare(Connection transactionConnection, String query)
      throws SQLException {
    PreparedStatement statement = transactionConnection.prepareStatement(query);
    try {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      return statement;
    } catch (SQLException error) {
      try {
        statement.close();
      } catch (SQLException closeError) {
        error.addSuppressed(closeError);
      }
      throw error;
    }
  }

  private synchronized <T> T executeTransaction(
      SqlTransaction<T> transaction,
      T failureResult,
      String errorMessage
  ) {
    Connection transactionConnection = null;
    boolean transactionActive = false;
    boolean restoreAutoCommit = false;
    try {
      checkAndReestablishConnection();
      transactionConnection = connection;
      restoreAutoCommit = transactionConnection.getAutoCommit();
      if (restoreAutoCommit) {
        transactionConnection.setAutoCommit(false);
      }
      transactionActive = true;
      T result = transaction.run(transactionConnection);
      transactionConnection.commit();
      transactionActive = false;
      if (restoreAutoCommit) {
        try {
          transactionConnection.setAutoCommit(true);
        } catch (SQLException restoreError) {
          discardConnection(transactionConnection, restoreError);
          invalidateValidationCache();
          handleSQLException(errorMessage, restoreError);
        }
      }
      return result;
    } catch (SQLException error) {
      if (transactionActive && transactionConnection != null) {
        try {
          transactionConnection.rollback();
        } catch (SQLException rollbackError) {
          error.addSuppressed(rollbackError);
        }
      }
      discardConnection(transactionConnection, error);
      invalidateValidationCache();
      handleSQLException(errorMessage, error);
      return failureResult;
    }
  }

  void checkAndReestablishConnection() throws SQLException {
    if (shutdown) {
      throw new SQLException("SQL manager is shut down");
    } else if (connection == null) {
      establishConnection();
    } else if (connection.isClosed()) {
      reestablishConnection();
    } else if (!hasFreshSuccessfulValidation()) {
      int verifySeconds = clampValidationSeconds(AdaptConfig.get().getSql().getSecondsCheckverify());
      if (!connection.isValid(verifySeconds)) {
        reestablishConnection();
      } else {
        cacheSuccessfulValidation();
      }
    }
    if (connection == null) {
      throw new SQLException("No active SQL connection");
    }
  }

  private void reestablishConnection() {
    closeActiveConnection();
    establishConnection();
  }

  private synchronized boolean isShutdown() {
    return shutdown;
  }

  private void failOutstandingClaimsLocked() {
    List<ClaimGroup> groups = new ArrayList<>();
    groups.addAll(pendingClaims.values());
    groups.addAll(inFlightClaims.values());
    pendingClaims.clear();
    inFlightClaims.clear();
    claimDrainScheduled = false;
    for (ClaimGroup group : groups) {
      CompletableFuture<ClaimResult> completion;
      while ((completion = group.completions().pollFirst()) != null) {
        completion.complete(ClaimResult.failed(group.uuid()));
      }
    }
  }

  private void discardConnection(Connection failedConnection, SQLException failure) {
    if (failedConnection == null) {
      return;
    }
    try {
      failedConnection.close();
    } catch (SQLException closeError) {
      failure.addSuppressed(closeError);
    } finally {
      if (connection == failedConnection) {
        connection = null;
      }
    }
  }

  static int clampValidationSeconds(int seconds) {
    return Math.max(1, Math.min(5, seconds));
  }

  static long saturatingDouble(long value) {
    if (value <= 0L) {
      return 0L;
    }
    return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
  }

  static long clampJdbcTimeout(long timeoutMillis) {
    return Math.max(MIN_JDBC_TIMEOUT_MILLIS, Math.min(MAX_JDBC_TIMEOUT_MILLIS, timeoutMillis));
  }

  private static void ensureBatchSucceeded(int[] updateCounts, int expectedCount)
      throws SQLException {
    if (updateCounts == null || updateCounts.length != expectedCount) {
      throw new SQLException("SQL batch returned an unexpected update result count");
    }
    for (int updateCount : updateCounts) {
      if (updateCount == Statement.EXECUTE_FAILED) {
        throw new SQLException("SQL batch reported a failed update");
      }
    }
  }

  private static void ensureBatchAffectedAll(int[] updateCounts, int expectedCount)
      throws SQLException {
    ensureBatchSucceeded(updateCounts, expectedCount);
    for (int updateCount : updateCounts) {
      if (updateCount == 0) {
        throw new SQLException("SQL batch failed to affect every locked row");
      }
    }
  }

  private static void ensureAffected(int updateCount) throws SQLException {
    if (updateCount == 0 || updateCount == Statement.EXECUTE_FAILED) {
      throw new SQLException("SQL update did not affect the locked row");
    }
  }

  private UUID nextOwnerToken() throws SQLException {
    for (int attempt = 0; attempt < 16; attempt++) {
      UUID token = tokenSupplier.get();
      if (token != null && !isZeroToken(token)) {
        return token;
      }
    }
    throw new SQLException("Owner token generator did not produce a nonzero UUID");
  }

  private static long nextEpoch(long epoch) throws SQLException {
    if (epoch < 0L || epoch == Long.MAX_VALUE) {
      throw new SQLException("Player data fence epoch cannot be advanced");
    }
    return epoch + 1L;
  }

  private static boolean isZeroToken(UUID token) {
    return token.getMostSignificantBits() == 0L && token.getLeastSignificantBits() == 0L;
  }

  private static UUID parseToken(String token) {
    if (token == null) {
      return null;
    }
    try {
      return UUID.fromString(token);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static UUID parseStoredToken(String token, String column) throws SQLException {
    UUID parsed = parseToken(token);
    if (parsed == null) {
      throw new SQLException("Invalid UUID in " + FENCE_TABLE_NAME + "." + column);
    }
    return parsed;
  }

  private static SqlToken storedSqlToken(UUID token, long epoch, String column) throws SQLException {
    try {
      return new SqlToken(token, epoch);
    } catch (IllegalArgumentException error) {
      throw new SQLException("Invalid ownership token in " + FENCE_TABLE_NAME + "." + column, error);
    }
  }

  private static long readRequiredLong(ResultSet rows, String column) throws SQLException {
    long value = rows.getLong(column);
    if (rows.wasNull()) {
      throw new SQLException("Missing value in " + FENCE_TABLE_NAME + "." + column);
    }
    return value;
  }

  private static Map<UUID, ClaimResult> failedClaims(List<UUID> ids) {
    Map<UUID, ClaimResult> failed = new LinkedHashMap<>();
    for (UUID uuid : ids) {
      failed.put(uuid, ClaimResult.failed(uuid));
    }
    return failed;
  }

  private boolean hasFreshSuccessfulValidation() {
    if (!successfulValidationCached) {
      return false;
    }
    long elapsed = nanoTime.getAsLong() - lastSuccessfulValidationNanos;
    return elapsed >= 0L && elapsed < VALIDATION_CACHE_NANOS;
  }

  private void cacheSuccessfulValidation() {
    lastSuccessfulValidationNanos = nanoTime.getAsLong();
    successfulValidationCached = true;
  }

  private void invalidateValidationCache() {
    successfulValidationCached = false;
  }

  private void handleSQLException(String message, SQLException error) {
    Adapt.error(message);
    Adapt.error("\t" + error.getClass().getSimpleName()
        + (error.getMessage() != null ? ": " + error.getMessage() : ""));
    Adapt.error(error);
  }

  String assembleUrl(AdaptConfig config) {
    long connectTimeout = clampJdbcTimeout(config.getSql().getConnectionTimeout());
    long socketTimeout = clampJdbcTimeout(saturatingDouble(connectTimeout));
    return String.format(
        "jdbc:mysql://%s:%d/%s?connectTimeout=%d&socketTimeout=%d&rewriteBatchedStatements=true",
        config.getSql().getHost(),
        config.getSql().getPort(),
        config.getSql().getDatabase(),
        connectTimeout,
        socketTimeout
    );
  }

  @FunctionalInterface
  interface SqlTransaction<T> {
    T run(Connection transactionConnection) throws SQLException;
  }

  @FunctionalInterface
  interface ClaimScheduler {
    void schedule(Runnable task, long delayMillis);
  }

  record RuntimeDependencies(
      LongSupplier nanoTime,
      Supplier<UUID> tokenSupplier,
      ClaimScheduler claimScheduler
  ) {
    RuntimeDependencies {
      Objects.requireNonNull(nanoTime);
      Objects.requireNonNull(tokenSupplier);
      Objects.requireNonNull(claimScheduler);
    }
  }

  public record FetchResult(String data, boolean successful) {
    public static FetchResult success(String data) {
      return new FetchResult(data, true);
    }

    public static FetchResult failure() {
      return new FetchResult(null, false);
    }

    public boolean found() {
      return successful && data != null;
    }
  }

  public record SqlToken(UUID ownerToken, long epoch) {
    public SqlToken {
      Objects.requireNonNull(ownerToken);
      if (isZeroToken(ownerToken) || epoch <= 0L) {
        throw new IllegalArgumentException("SQL ownership token and epoch must be positive");
      }
    }
  }

  public record FencedDataUpdate(
      UUID uuid,
      String token,
      long epoch,
      long sequence,
      String data
  ) {
    public FencedDataUpdate {
      Objects.requireNonNull(uuid);
      Objects.requireNonNull(token);
      Objects.requireNonNull(data);
      UUID parsedToken = parseToken(token);
      if (parsedToken == null || isZeroToken(parsedToken) || epoch <= 0L || sequence <= 0L) {
        throw new IllegalArgumentException("Fenced update token, epoch, and sequence must be positive");
      }
      token = parsedToken.toString();
    }
  }

  public record FencedWriteResult(UUID uuid, long sequence, FencedWriteStatus status) {
    public FencedWriteResult {
      Objects.requireNonNull(uuid);
      Objects.requireNonNull(status);
    }

    public boolean successful() {
      return status != FencedWriteStatus.FAILED && status != FencedWriteStatus.FENCED;
    }
  }

  public enum FencedWriteStatus {
    COMMITTED,
    ALREADY_COMMITTED,
    SUPERSEDED,
    FENCED,
    FAILED
  }

  public record ClaimResult(
      UUID uuid,
      ClaimStatus status,
      SqlToken previousToken,
      SqlToken effectivePredecessor,
      long previousCommittedSequence,
      SqlToken newToken,
      String committedData
  ) {
    private static ClaimResult claimed(
        UUID uuid,
        SqlToken previousToken,
        SqlToken effectivePredecessor,
        long previousCommittedSequence,
        SqlToken newToken,
        String committedData
    ) {
      return new ClaimResult(
          uuid,
          ClaimStatus.CLAIMED,
          previousToken,
          effectivePredecessor,
          previousCommittedSequence,
          newToken,
          committedData
      );
    }

    private static ClaimResult failed(UUID uuid) {
      return new ClaimResult(uuid, ClaimStatus.FAILED, null, null, 0L, null, null);
    }

    public boolean successful() {
      return status == ClaimStatus.CLAIMED;
    }

    public boolean found() {
      return successful() && committedData != null;
    }
  }

  public enum ClaimStatus {
    CLAIMED,
    FAILED
  }

  public record TokenMutationResult(
      UUID uuid,
      TokenMutationStatus status,
      SqlToken newToken
  ) {
    private static TokenMutationResult committed(UUID uuid, SqlToken token) {
      return new TokenMutationResult(uuid, TokenMutationStatus.COMMITTED, token);
    }

    private static TokenMutationResult failed(UUID uuid) {
      return new TokenMutationResult(uuid, TokenMutationStatus.FAILED, null);
    }

    public boolean successful() {
      return status == TokenMutationStatus.COMMITTED;
    }
  }

  public enum TokenMutationStatus {
    COMMITTED,
    FAILED
  }

  private record FenceState(
      UUID uuid,
      UUID ownerToken,
      long epoch,
      long committedSequence,
      boolean ownerAdopted,
      SqlToken adoptFrom
  ) {
  }

  private record ClaimGroup(UUID uuid, Deque<CompletableFuture<ClaimResult>> completions) {
  }

  private record IndexedFencedUpdate(int index, FencedDataUpdate update) {
  }

  private record FencedSequence(UUID uuid, long sequence) {
  }
}
