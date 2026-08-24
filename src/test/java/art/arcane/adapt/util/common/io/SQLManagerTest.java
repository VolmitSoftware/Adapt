package art.arcane.adapt.util.common.io;

import art.arcane.adapt.AdaptConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SQLManagerTest {
  private AdaptConfig previousConfig;

  @BeforeEach
  void installConfig() throws Exception {
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
  }

  @AfterEach
  void restoreConfig() throws Exception {
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(null, previousConfig);
  }

  @Test
  void validationTimeoutIsAlwaysWithinJdbcBounds() {
    assertThat(SQLManager.clampValidationSeconds(Integer.MIN_VALUE)).isOne();
    assertThat(SQLManager.clampValidationSeconds(0)).isOne();
    assertThat(SQLManager.clampValidationSeconds(5)).isEqualTo(5);
    assertThat(SQLManager.clampValidationSeconds(30)).isEqualTo(5);
    assertThat(SQLManager.clampValidationSeconds(Integer.MAX_VALUE)).isEqualTo(5);
  }

  @Test
  void socketTimeoutDoublingSaturatesAtLongMaximum() {
    assertThat(SQLManager.saturatingDouble(0L)).isZero();
    assertThat(SQLManager.saturatingDouble(5_000L)).isEqualTo(10_000L);
    assertThat(SQLManager.saturatingDouble(Long.MAX_VALUE / 2L)).isEqualTo(Long.MAX_VALUE - 1L);
    assertThat(SQLManager.saturatingDouble(Long.MAX_VALUE / 2L + 1L)).isEqualTo(Long.MAX_VALUE);
    assertThat(SQLManager.saturatingDouble(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void jdbcTimeoutsStayBelowPersistenceShutdownRecoveryWindow() {
    assertThat(SQLManager.clampJdbcTimeout(Long.MIN_VALUE)).isEqualTo(1_000L);
    assertThat(SQLManager.clampJdbcTimeout(5_000L)).isEqualTo(5_000L);
    assertThat(SQLManager.clampJdbcTimeout(Long.MAX_VALUE)).isEqualTo(5_000L);
    long maximumRetryWallMillis = (4L * SQLManager.clampJdbcTimeout(Long.MAX_VALUE))
        + 50L + 250L + 1_000L;
    assertThat(maximumRetryWallMillis).isLessThan(30_000L);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sqlSettings);
    when(sqlSettings.getHost()).thenReturn("database");
    when(sqlSettings.getPort()).thenReturn(3307);
    when(sqlSettings.getDatabase()).thenReturn("profiles");
    when(sqlSettings.getConnectionTimeout()).thenReturn(Long.MAX_VALUE);

    assertThat(new SQLManager().assembleUrl(config)).isEqualTo(
        "jdbc:mysql://database:3307/profiles?connectTimeout=5000&socketTimeout=5000&rewriteBatchedStatements=true");
  }

  @Test
  void successfulValidationIsCachedForOnlyTheBoundedWindow() throws Exception {
    AtomicLong clock = new AtomicLong(100L);
    SQLManager manager = managerWith(clock::get, UUID::randomUUID, (task, delayMillis) -> task.run());
    Connection connection = mock(Connection.class);
    when(connection.isClosed()).thenReturn(false);
    when(connection.isValid(5)).thenReturn(true);
    setConnection(manager, connection);

    manager.checkAndReestablishConnection();
    manager.checkAndReestablishConnection();
    clock.addAndGet(TimeUnit.SECONDS.toNanos(5L) - 1L);
    manager.checkAndReestablishConnection();

    verify(connection, times(1)).isValid(5);

    clock.incrementAndGet();
    manager.checkAndReestablishConnection();

    verify(connection, times(2)).isValid(5);
  }

  @Test
  void closedConnectionIsDiscardedBeforeReconnect() throws Exception {
    SQLManager manager = new SQLManager();
    Connection closed = mock(Connection.class);
    Connection replacement = validReplacementConnection();
    when(closed.isClosed()).thenReturn(true);
    setConnection(manager, closed);

    try (MockedStatic<DriverManager> driverManager = mockDriverConnection(replacement)) {
      manager.checkAndReestablishConnection();
    }

    verify(closed).close();
    assertThat(getConnection(manager)).isSameAs(replacement);
  }

  @Test
  void invalidConnectionIsDiscardedBeforeReconnect() throws Exception {
    SQLManager manager = new SQLManager();
    Connection invalid = mock(Connection.class);
    Connection replacement = validReplacementConnection();
    when(invalid.isClosed()).thenReturn(false);
    when(invalid.isValid(5)).thenReturn(false);
    setConnection(manager, invalid);

    try (MockedStatic<DriverManager> driverManager = mockDriverConnection(replacement)) {
      manager.checkAndReestablishConnection();
    }

    verify(invalid).close();
    assertThat(getConnection(manager)).isSameAs(replacement);
  }

  @Test
  void rejectedReplacementConnectionIsClosed() throws Exception {
    SQLManager manager = new SQLManager();
    Connection replacement = mock(Connection.class);
    when(replacement.isValid(5)).thenReturn(false);

    try (MockedStatic<DriverManager> driverManager = mockDriverConnection(replacement)) {
      assertThat(manager.establishConnection()).isFalse();
    }

    verify(replacement).close();
    assertThat(getConnection(manager)).isNull();
  }

  @Test
  void startupAlwaysCreatesCanonicalDataAndFenceTables() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(connection.isValid(5)).thenReturn(true);
    when(connection.createStatement()).thenReturn(statement);
    stubTableEngines(statement, "InnoDB", "InnoDB");

    try (MockedStatic<DriverManager> driverManager = mockDriverConnection(connection)) {
      assertThat(manager.establishConnection()).isTrue();
    }

    verify(statement).executeUpdate(
        "CREATE TABLE IF NOT EXISTS ADAPT_DATA"
            + " (UUID CHAR(36) NOT NULL UNIQUE, DATA MEDIUMTEXT NOT NULL) ENGINE=InnoDB");
    verify(statement).executeUpdate(
        "CREATE TABLE IF NOT EXISTS ADAPT_DATA_FENCE"
            + " (UUID CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,"
            + " OWNER_TOKEN CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,"
            + " FENCE_EPOCH BIGINT NOT NULL, COMMITTED_SEQUENCE BIGINT NOT NULL,"
            + " OWNER_ADOPTED BOOLEAN NOT NULL,"
            + " ADOPT_FROM_TOKEN CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,"
            + " ADOPT_FROM_EPOCH BIGINT NULL) ENGINE=InnoDB");
    verify(statement).setQueryTimeout(5);
    verify(statement).executeQuery(
        "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES"
            + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN"
            + " ('ADAPT_DATA', 'ADAPT_DATA_FENCE')");
  }

  @Test
  void startupRejectsLegacyNonTransactionalDataTable() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(connection.isValid(5)).thenReturn(true);
    when(connection.createStatement()).thenReturn(statement);
    stubTableEngines(statement, "MyISAM", "InnoDB");

    try (MockedStatic<DriverManager> driverManager = mockDriverConnection(connection)) {
      assertThat(manager.establishConnection()).isFalse();
    }

    verify(connection).close();
    assertThat(getConnection(manager)).isNull();
  }

  @Test
  void fetchDistinguishesMissingRowsFromReadFailures() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = validatedConnection();
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(false);
    setConnection(manager, connection);

    SQLManager.FetchResult missing = manager.fetchData(UUID.randomUUID());

    assertThat(missing.successful()).isTrue();
    assertThat(missing.found()).isFalse();

    when(connection.prepareStatement(anyString())).thenThrow(new SQLException("read failed"));
    SQLManager.FetchResult failed = manager.fetchData(UUID.randomUUID());

    assertThat(failed.successful()).isFalse();
    assertThat(failed.found()).isFalse();
    verify(connection).close();
    assertThat(getConnection(manager)).isNull();
  }

  @Test
  void validationExceptionDuringFetchDiscardsWedgedConnection() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = mock(Connection.class);
    when(connection.isClosed()).thenReturn(false);
    when(connection.isValid(5)).thenThrow(new SQLException("validation failed"));
    setConnection(manager, connection);

    SQLManager.FetchResult result = manager.fetchData(UUID.randomUUID());

    assertThat(result.successful()).isFalse();
    verify(connection).close();
    assertThat(getConnection(manager)).isNull();
  }

  @Test
  void fetchReturnsFoundRowWithoutConflatingItWithSuccessState() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = validatedConnection();
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true);
    when(rows.getString("DATA")).thenReturn("profile");
    setConnection(manager, connection);

    SQLManager.FetchResult found = manager.fetchData(UUID.randomUUID());

    assertThat(found.successful()).isTrue();
    assertThat(found.found()).isTrue();
    assertThat(found.data()).isEqualTo("profile");
  }

  @Test
  void preparedStatementIsClosedWhenQueryTimeoutSetupFails() throws Exception {
    SQLManager manager = new SQLManager();
    Connection connection = validatedConnection();
    PreparedStatement statement = mock(PreparedStatement.class);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    doThrow(new SQLException("timeout setup failed")).when(statement).setQueryTimeout(5);
    setConnection(manager, connection);

    SQLManager.FetchResult result = manager.fetchData(UUID.randomUUID());

    assertThat(result.successful()).isFalse();
    verify(statement).close();
    verify(connection).close();
  }

  @Test
  void abandonedClaimCarriesOriginalEffectivePredecessorTokenAndEpoch() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID abandonedOwner = UUID.randomUUID();
    UUID effectiveOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, abandonedOwner, 7L, 12L, false,
        new SQLManager.SqlToken(effectiveOwner, 4L));
    fixture.stubDataRow(playerId, "committed");
    when(fixture.ensureFence.executeBatch()).thenReturn(new int[]{1});
    when(fixture.updateClaim.executeBatch()).thenReturn(new int[]{1});
    SQLManager manager = managerWith(
        System::nanoTime,
        () -> newOwner,
        (task, delayMillis) -> task.run()
    );
    setConnection(manager, fixture.connection);

    SQLManager.ClaimResult result = manager.claimBatch(List.of(playerId)).get(playerId);

    assertThat(result.status()).isEqualTo(SQLManager.ClaimStatus.CLAIMED);
    assertThat(result.previousToken()).isEqualTo(new SQLManager.SqlToken(abandonedOwner, 7L));
    assertThat(result.effectivePredecessor()).isEqualTo(new SQLManager.SqlToken(effectiveOwner, 4L));
    assertThat(result.previousCommittedSequence()).isEqualTo(12L);
    assertThat(result.newToken()).isEqualTo(new SQLManager.SqlToken(newOwner, 8L));
    assertThat(result.committedData()).isEqualTo("committed");
    verify(fixture.updateClaim).setString(3, effectiveOwner.toString());
    verify(fixture.updateClaim).setLong(4, 4L);
    verify(fixture.connection).commit();
  }

  @Test
  void thousandConcurrentClaimsCoalesceIntoEightSortedPhysicalBatches() {
    CapturingClaimScheduler scheduler = new CapturingClaimScheduler();
    AtomicLong tokenSequence = new AtomicLong();
    SQLManager manager = spy(managerWith(
        System::nanoTime,
        () -> new UUID(1L, tokenSequence.incrementAndGet()),
        scheduler
    ));
    AtomicReference<CompletableFuture<SQLManager.ClaimResult>> inFlightDuplicate =
        new AtomicReference<>();
    AtomicReference<UUID> inFlightTarget = new AtomicReference<>();
    AtomicInteger physicalBatches = new AtomicInteger();
    doAnswer(invocation -> {
      List<UUID> ids = invocation.getArgument(0);
      Map<UUID, SQLManager.ClaimResult> results = new LinkedHashMap<>();
      for (UUID id : ids) {
        UUID token = new UUID(2L, tokenSequence.incrementAndGet());
        results.put(id, new SQLManager.ClaimResult(
            id,
            SQLManager.ClaimStatus.CLAIMED,
            null,
            null,
            0L,
            new SQLManager.SqlToken(token, 1L),
            null
        ));
      }
      if (physicalBatches.getAndIncrement() == 0) {
        inFlightTarget.set(ids.getFirst());
        inFlightDuplicate.set(manager.claimData(inFlightTarget.get()));
      }
      return results;
    }).when(manager).claimBatch(any());

    List<UUID> ids = new ArrayList<>(1_000);
    List<CompletableFuture<SQLManager.ClaimResult>> claims = new ArrayList<>(1_000);
    Map<UUID, CompletableFuture<SQLManager.ClaimResult>> originalClaims =
        new LinkedHashMap<>(1_000);
    for (int index = 1_000; index > 0; index--) {
      UUID playerId = new UUID(0L, index);
      ids.add(playerId);
      CompletableFuture<SQLManager.ClaimResult> claim = manager.claimData(playerId);
      claims.add(claim);
      originalClaims.put(playerId, claim);
    }
    CompletableFuture<SQLManager.ClaimResult> gatheredDuplicate = manager.claimData(ids.getFirst());
    AtomicReference<CompletableFuture<SQLManager.ClaimResult>> deliveryDuplicate =
        new AtomicReference<>();
    claims.getFirst().thenAccept(result ->
        deliveryDuplicate.set(manager.claimData(ids.getFirst())));

    assertThat(scheduler.delays).containsExactly(25L);
    assertThat(scheduler.tasks).hasSize(1);
    scheduler.runNext();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<UUID>> batches = ArgumentCaptor.forClass(List.class);
    verify(manager, times(8)).claimBatch(batches.capture());
    assertThat(batches.getAllValues()).allSatisfy(batch -> {
      assertThat(batch).hasSizeLessThanOrEqualTo(128);
      assertThat(batch).isSortedAccordingTo(Comparator.comparing(UUID::toString));
    });
    assertThat(batches.getAllValues().stream().mapToInt(List::size).sum()).isEqualTo(1_000);
    assertThat(claims).allSatisfy(claim -> assertThat(claim).isDone());
    assertThat(gatheredDuplicate).isDone();
    assertThat(inFlightDuplicate.get()).isDone();
    assertThat(deliveryDuplicate.get()).isDone();
    assertThat(originalClaims.get(inFlightTarget.get()).join().newToken())
        .isEqualTo(inFlightDuplicate.get().join().newToken());
    assertThat(gatheredDuplicate.join().newToken())
        .isEqualTo(deliveryDuplicate.get().join().newToken());
    assertThat(scheduler.tasks).isEmpty();
  }

  @Test
  void fencedBatchClassifiesCommittedAlreadyCommittedSupersededAndFenced() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 4L, 5L, true, null);
    fixture.stubDataRow(playerId, "already");
    when(fixture.dataUpdate.executeBatch()).thenReturn(new int[]{1});
    when(fixture.updateSequence.executeBatch()).thenReturn(new int[]{1});
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);
    List<SQLManager.FencedDataUpdate> updates = List.of(
        fencedUpdate(playerId, owner, 4L, 6L, "committed"),
        fencedUpdate(playerId, owner, 4L, 5L, "already"),
        fencedUpdate(playerId, owner, 4L, 4L, "superseded"),
        fencedUpdate(playerId, UUID.randomUUID(), 4L, 7L, "fenced")
    );

    List<SQLManager.FencedWriteResult> results = manager.updateFencedDataBatch(updates);

    assertThat(results).extracting(SQLManager.FencedWriteResult::status).containsExactly(
        SQLManager.FencedWriteStatus.COMMITTED,
        SQLManager.FencedWriteStatus.ALREADY_COMMITTED,
        SQLManager.FencedWriteStatus.SUPERSEDED,
        SQLManager.FencedWriteStatus.FENCED
    );
    verify(fixture.dataUpdate, times(1)).addBatch();
    verify(fixture.updateSequence, times(1)).addBatch();
    verify(fixture.connection).commit();
  }

  @Test
  void normalSaveIsFencedUntilNewOwnerAdopts() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 8L, 14L, false,
        new SQLManager.SqlToken(UUID.randomUUID(), 5L));
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    List<SQLManager.FencedWriteResult> results = manager.updateFencedDataBatch(List.of(
        fencedUpdate(playerId, owner, 8L, 15L, "blocked")
    ));

    assertThat(results).extracting(SQLManager.FencedWriteResult::status)
        .containsExactly(SQLManager.FencedWriteStatus.FENCED);
    verify(fixture.dataUpdate, never()).executeBatch();
    verify(fixture.connection).commit();
  }

  @Test
  void conflictingPayloadsForSameUuidAndSequenceAreRejectedWithoutLastWins() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 3L, 1L, true, null);
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    List<SQLManager.FencedWriteResult> results = manager.updateFencedDataBatch(List.of(
        fencedUpdate(playerId, owner, 3L, 2L, "first"),
        fencedUpdate(playerId, owner, 3L, 2L, "different")
    ));

    assertThat(results).extracting(SQLManager.FencedWriteResult::status).containsExactly(
        SQLManager.FencedWriteStatus.FAILED,
        SQLManager.FencedWriteStatus.FAILED
    );
    verify(fixture.dataUpdate, never()).executeBatch();
    verify(fixture.updateSequence, never()).executeBatch();
  }

  @Test
  void differentPayloadForPreviouslyCommittedSequenceIsRejected() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 3L, 2L, true, null);
    fixture.stubDataRow(playerId, "committed");
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    List<SQLManager.FencedWriteResult> results = manager.updateFencedDataBatch(List.of(
        fencedUpdate(playerId, owner, 3L, 2L, "different")
    ));

    assertThat(results).extracting(SQLManager.FencedWriteResult::status)
        .containsExactly(SQLManager.FencedWriteStatus.FAILED);
    verify(fixture.dataUpdate, never()).executeBatch();
    verify(fixture.updateSequence, never()).executeBatch();
  }

  @Test
  void ownershipValueTypesRejectZeroOrNonpositiveCredentials() {
    UUID owner = UUID.randomUUID();

    assertThat(catchThrowable(
        () -> new SQLManager.SqlToken(new UUID(0L, 0L), 1L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(catchThrowable(
        () -> new SQLManager.SqlToken(owner, 0L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(catchThrowable(
        () -> new SQLManager.FencedDataUpdate(
            UUID.randomUUID(), "invalid", 1L, 1L, "data")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(catchThrowable(
        () -> new SQLManager.FencedDataUpdate(
            UUID.randomUUID(), new UUID(0L, 0L).toString(), 1L, 1L, "data")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(catchThrowable(
        () -> new SQLManager.FencedDataUpdate(
            UUID.randomUUID(), owner.toString(), 0L, 1L, "data")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(catchThrowable(
        () -> new SQLManager.FencedDataUpdate(UUID.randomUUID(), owner.toString(), 1L, 0L, "data")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fencedUpdateCanonicalizesValidOwnerTokenText() {
    UUID owner = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    SQLManager.FencedDataUpdate update = new SQLManager.FencedDataUpdate(
        UUID.randomUUID(), owner.toString().toUpperCase(), 1L, 1L, "data");

    assertThat(update.token()).isEqualTo(owner.toString());
  }

  @Test
  void closeFailsPendingClaimsAndDelayedDrainCannotReopenSql() {
    CapturingClaimScheduler scheduler = new CapturingClaimScheduler();
    SQLManager manager = spy(managerWith(System::nanoTime, UUID::randomUUID, scheduler));
    UUID playerId = UUID.randomUUID();
    CompletableFuture<SQLManager.ClaimResult> claim = manager.claimData(playerId);

    manager.closeConnection();
    scheduler.runNext();

    assertThat(claim.join().status()).isEqualTo(SQLManager.ClaimStatus.FAILED);
    assertThat(manager.claimData(UUID.randomUUID()).join().status())
        .isEqualTo(SQLManager.ClaimStatus.FAILED);
    verify(manager, never()).claimBatch(any());
  }

  @Test
  void closeDuringClaimDeliveryFailsEveryUndeliveredDuplicate() {
    CapturingClaimScheduler scheduler = new CapturingClaimScheduler();
    SQLManager manager = spy(managerWith(System::nanoTime, UUID::randomUUID, scheduler));
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    SQLManager.ClaimResult claimed = new SQLManager.ClaimResult(
        playerId,
        SQLManager.ClaimStatus.CLAIMED,
        null,
        null,
        0L,
        new SQLManager.SqlToken(owner, 1L),
        null
    );
    doReturn(Map.of(playerId, claimed)).when(manager).claimBatch(any());
    CompletableFuture<SQLManager.ClaimResult> first = manager.claimData(playerId);
    CompletableFuture<SQLManager.ClaimResult> second = manager.claimData(playerId);
    first.thenRun(manager::closeConnection);

    scheduler.runNext();

    assertThat(first.join().status()).isEqualTo(SQLManager.ClaimStatus.CLAIMED);
    assertThat(second.join().status()).isEqualTo(SQLManager.ClaimStatus.FAILED);
  }

  @Test
  void adoptionCommitsSequenceOneAndClearsAbandonedPredecessor() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 9L, 27L, false,
        new SQLManager.SqlToken(UUID.randomUUID(), 4L));
    when(fixture.adoptOwner.executeUpdate()).thenReturn(1);
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    SQLManager.FencedWriteResult result = manager.adoptFencedData(
        fencedUpdate(playerId, owner, 9L, 1L, "selected"));

    assertThat(result.status()).isEqualTo(SQLManager.FencedWriteStatus.COMMITTED);
    verify(fixture.dataUpdate).executeUpdate();
    verify(fixture.adoptOwner).executeUpdate();
    verify(fixture.connection).commit();
  }

  @Test
  void adoptionRetryIsAlreadyCommittedWithoutRewritingData() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 9L, 1L, true, null);
    fixture.stubDataRow(playerId, "selected");
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    SQLManager.FencedWriteResult result = manager.adoptFencedData(
        fencedUpdate(playerId, owner, 9L, 1L, "selected"));

    assertThat(result.status()).isEqualTo(SQLManager.FencedWriteStatus.ALREADY_COMMITTED);
    verify(fixture.dataUpdate, never()).executeUpdate();
    verify(fixture.adoptOwner, never()).executeUpdate();
  }

  @Test
  void adoptionRetryWithDifferentPayloadIsRejected() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 9L, 1L, true, null);
    fixture.stubDataRow(playerId, "selected");
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    SQLManager.FencedWriteResult result = manager.adoptFencedData(
        fencedUpdate(playerId, owner, 9L, 1L, "different"));

    assertThat(result.status()).isEqualTo(SQLManager.FencedWriteStatus.FAILED);
    verify(fixture.dataUpdate, never()).executeUpdate();
    verify(fixture.adoptOwner, never()).executeUpdate();
  }

  @Test
  void failedFencedSequenceCasRollsBackWholeBatchAndReturnsFailed() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, owner, 4L, 1L, true, null);
    when(fixture.dataUpdate.executeBatch()).thenReturn(new int[]{1});
    when(fixture.updateSequence.executeBatch()).thenReturn(new int[]{Statement.EXECUTE_FAILED});
    SQLManager manager = new SQLManager();
    setConnection(manager, fixture.connection);

    List<SQLManager.FencedWriteResult> results = manager.updateFencedDataBatch(List.of(
        fencedUpdate(playerId, owner, 4L, 2L, "profile")
    ));

    assertThat(results).extracting(SQLManager.FencedWriteResult::status)
        .containsExactly(SQLManager.FencedWriteStatus.FAILED);
    verify(fixture.connection).rollback();
    verify(fixture.connection).close();
    assertThat(getConnection(manager)).isNull();
  }

  @Test
  void resetAtomicallyRotatesAdoptedOwnerAndWritesDefaultAtSequenceZero() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID oldOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, oldOwner, 11L, 40L, false,
        new SQLManager.SqlToken(UUID.randomUUID(), 6L));
    when(fixture.ensureFence.executeBatch()).thenReturn(new int[]{1});
    when(fixture.rotateOwner.executeUpdate()).thenReturn(1);
    SQLManager manager = managerWith(
        System::nanoTime,
        () -> newOwner,
        (task, delayMillis) -> task.run()
    );
    setConnection(manager, fixture.connection);

    SQLManager.TokenMutationResult result = manager.resetFencedData(playerId, "default");

    assertThat(result.status()).isEqualTo(SQLManager.TokenMutationStatus.COMMITTED);
    assertThat(result.newToken()).isEqualTo(new SQLManager.SqlToken(newOwner, 12L));
    verify(fixture.dataUpdate).executeUpdate();
    verify(fixture.rotateOwner).setString(1, newOwner.toString());
    verify(fixture.rotateOwner).setLong(2, 12L);
    verify(fixture.connection).commit();
  }

  @Test
  void purgeDeletesOnlyDataAndRetainsRotatedFenceTombstone() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID oldOwner = UUID.randomUUID();
    UUID newOwner = UUID.randomUUID();
    FenceFixture fixture = new FenceFixture();
    fixture.stubFenceRow(playerId, oldOwner, 2L, 8L, true, null);
    when(fixture.ensureFence.executeBatch()).thenReturn(new int[]{1});
    when(fixture.rotateOwner.executeUpdate()).thenReturn(1);
    SQLManager manager = managerWith(
        System::nanoTime,
        () -> newOwner,
        (task, delayMillis) -> task.run()
    );
    setConnection(manager, fixture.connection);

    SQLManager.TokenMutationResult result = manager.purgeFencedData(playerId);

    assertThat(result.status()).isEqualTo(SQLManager.TokenMutationStatus.COMMITTED);
    assertThat(result.newToken()).isEqualTo(new SQLManager.SqlToken(newOwner, 3L));
    verify(fixture.deleteData).executeUpdate();
    assertThat(fixture.preparedQueries).noneMatch(query -> query.startsWith("DELETE FROM ADAPT_DATA_FENCE"));
    verify(fixture.rotateOwner).executeUpdate();
    verify(fixture.connection).commit();
  }

  private static SQLManager.FencedDataUpdate fencedUpdate(
      UUID uuid,
      UUID token,
      long epoch,
      long sequence,
      String data
  ) {
    return new SQLManager.FencedDataUpdate(uuid, token.toString(), epoch, sequence, data);
  }

  private static final class CapturingClaimScheduler implements SQLManager.ClaimScheduler {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<Long> delays = new ArrayList<>();

    @Override
    public void schedule(Runnable task, long delayMillis) {
      tasks.addLast(task);
      delays.add(delayMillis);
    }

    private void runNext() {
      tasks.removeFirst().run();
    }
  }

  private static final class FenceFixture {
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement ensureFence = mock(PreparedStatement.class);
    private final PreparedStatement lockFence = mock(PreparedStatement.class);
    private final PreparedStatement fetchData = mock(PreparedStatement.class);
    private final PreparedStatement updateClaim = mock(PreparedStatement.class);
    private final PreparedStatement dataUpdate = mock(PreparedStatement.class);
    private final PreparedStatement updateSequence = mock(PreparedStatement.class);
    private final PreparedStatement adoptOwner = mock(PreparedStatement.class);
    private final PreparedStatement rotateOwner = mock(PreparedStatement.class);
    private final PreparedStatement deleteData = mock(PreparedStatement.class);
    private final ResultSet fenceRows = mock(ResultSet.class);
    private final ResultSet dataRows = mock(ResultSet.class);
    private final List<String> preparedQueries = new ArrayList<>();

    private FenceFixture() throws Exception {
      when(connection.isClosed()).thenReturn(false);
      when(connection.isValid(5)).thenReturn(true);
      when(connection.getAutoCommit()).thenReturn(true);
      when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
        String query = invocation.getArgument(0);
        preparedQueries.add(query);
        if (query.startsWith("INSERT INTO ADAPT_DATA_FENCE")) {
          return ensureFence;
        }
        if (query.startsWith("SELECT UUID, OWNER_TOKEN")) {
          return lockFence;
        }
        if (query.startsWith("SELECT UUID, DATA")) {
          return fetchData;
        }
        if (query.contains("OWNER_ADOPTED=FALSE, ADOPT_FROM_TOKEN=?")) {
          return updateClaim;
        }
        if (query.startsWith("INSERT INTO ADAPT_DATA")) {
          return dataUpdate;
        }
        if (query.contains("SET COMMITTED_SEQUENCE=? WHERE")) {
          return updateSequence;
        }
        if (query.contains("SET OWNER_TOKEN=?, FENCE_EPOCH=?, COMMITTED_SEQUENCE=0")) {
          return rotateOwner;
        }
        if (query.contains("SET OWNER_ADOPTED=TRUE")) {
          return adoptOwner;
        }
        if (query.startsWith("DELETE FROM ADAPT_DATA")) {
          return deleteData;
        }
        throw new SQLException("Unexpected query: " + query);
      });
      when(lockFence.executeQuery()).thenReturn(fenceRows);
      when(fetchData.executeQuery()).thenReturn(dataRows);
    }

    private void stubFenceRow(
        UUID uuid,
        UUID ownerToken,
        long epoch,
        long committedSequence,
        boolean adopted,
        SQLManager.SqlToken predecessor
    ) throws Exception {
      when(fenceRows.next()).thenReturn(true, false);
      when(fenceRows.getString("UUID")).thenReturn(uuid.toString());
      when(fenceRows.getString("OWNER_TOKEN")).thenReturn(ownerToken.toString());
      when(fenceRows.getLong("FENCE_EPOCH")).thenReturn(epoch);
      when(fenceRows.getLong("COMMITTED_SEQUENCE")).thenReturn(committedSequence);
      when(fenceRows.getBoolean("OWNER_ADOPTED")).thenReturn(adopted);
      when(fenceRows.getString("ADOPT_FROM_TOKEN")).thenReturn(
          predecessor == null ? null : predecessor.ownerToken().toString());
      when(fenceRows.getLong("ADOPT_FROM_EPOCH")).thenReturn(
          predecessor == null ? 0L : predecessor.epoch());
      when(fenceRows.wasNull()).thenReturn(predecessor == null);
    }

    private void stubDataRow(UUID uuid, String data) throws Exception {
      when(dataRows.next()).thenReturn(true, false);
      when(dataRows.getString("UUID")).thenReturn(uuid.toString());
      when(dataRows.getString("DATA")).thenReturn(data);
    }
  }

  private static Connection validReplacementConnection() throws Exception {
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(connection.isValid(5)).thenReturn(true);
    when(connection.createStatement()).thenReturn(statement);
    stubTableEngines(statement, "InnoDB", "InnoDB");
    return connection;
  }

  private static void stubTableEngines(
      Statement statement, String dataEngine, String fenceEngine) throws Exception {
    ResultSet tables = mock(ResultSet.class);
    when(statement.executeQuery(anyString())).thenReturn(tables);
    when(tables.next()).thenReturn(true, true, false);
    when(tables.getString("TABLE_NAME")).thenReturn("ADAPT_DATA", "ADAPT_DATA_FENCE");
    when(tables.getString("ENGINE")).thenReturn(dataEngine, fenceEngine);
  }

  private static Connection validatedConnection() throws Exception {
    Connection connection = mock(Connection.class);
    when(connection.isClosed()).thenReturn(false);
    when(connection.isValid(5)).thenReturn(true);
    return connection;
  }

  private static MockedStatic<DriverManager> mockDriverConnection(Connection connection) {
    MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class);
    driverManager.when(() -> DriverManager.getConnection(
        "jdbc:mysql://localhost:3306/adapt?connectTimeout=5000&socketTimeout=5000&rewriteBatchedStatements=true",
        "user",
        "password"
    )).thenReturn(connection);
    return driverManager;
  }

  private static Connection getConnection(SQLManager manager) throws Exception {
    Field connectionField = SQLManager.class.getDeclaredField("connection");
    connectionField.setAccessible(true);
    return (Connection) connectionField.get(manager);
  }

  private static void setConnection(SQLManager manager, Connection connection) throws Exception {
    Field connectionField = SQLManager.class.getDeclaredField("connection");
    connectionField.setAccessible(true);
    connectionField.set(manager, connection);
  }

  private static SQLManager managerWith(
      LongSupplier nanoTime,
      Supplier<UUID> tokenSupplier,
      SQLManager.ClaimScheduler claimScheduler
  ) {
    return new SQLManager(new SQLManager.RuntimeDependencies(nanoTime, tokenSupplier, claimScheduler));
  }
}
