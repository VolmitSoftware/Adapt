package com.fren_gor.ultimateAdvancementAPI.database.impl;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;

class LegacyMySQLMigrationTest {
    private static final Logger LOGGER = Logger.getLogger(LegacyMySQLMigrationTest.class.getName());
    private static final String FIRST = "00000000-0000-0000-0000-000000000001";
    private static final String SECOND = "00000000-0000-0000-0000-000000000002";
    private static final String THIRD = "00000000-0000-0000-0000-000000000003";
    private static final String FOURTH = "00000000-0000-0000-0000-000000000004";
    private static final List<String> SOURCES = List.of("Teams", "Players", "Advancements", "Unredeemed");

    private final JdbcDataSource dataSource = new JdbcDataSource();
    private MySQL database;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        database = mock(MySQL.class, CALLS_REAL_METHODS);
        doNothing().when(database).close();
        field("dataSource", dataSource);
        field("logger", LOGGER);
        database.setUp();
    }

    @AfterEach
    void closeDatabase() throws Exception {
        execute("SHUTDOWN");
    }

    @Test
    void importsOnlyAdaptNamespacesAndKeepsSourceRows() throws Exception {
        sourceSchema();
        execute("INSERT INTO Teams VALUES (70), (80)");
        sourcePlayer(FIRST, "Explorer", 70);
        sourcePlayer(SECOND, "Builder", 70);
        sourcePlayer(THIRD, "Unrelated", 80);
        sourceProgress("adapt_skill_mining", "ore", 70, 8, 1);
        sourceProgress("other_plugin", "ore", 70, 99, 1);
        sourceProgress("adaptXskill_mining", "ore", 80, 99, 1);
        sourceProgress("ADAPT_skill_mining", "ore", 80, 99, 1);
        migrate();
        int team = team(FIRST);
        assertThat(team).isNotEqualTo(70);
        assertThat(team(SECOND)).isEqualTo(team);
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isEqualTo(2);
        assertThat(count("ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_UNREDEEMED")).isEqualTo(1);
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(8);
        assertThat(completed()).isEqualTo(1);
    }

    @Test
    void mergesMissingKeysAcrossSplitMembershipWithoutReplacingCurrentData() throws Exception {
        sourceSchema();
        execute("INSERT INTO ADAPT_ADVANCEMENT_TEAMS VALUES (1), (10), (20)");
        targetPlayer(FIRST, "CurrentFirst", 10);
        targetPlayer(SECOND, "CurrentSecond", 20);
        targetProgress("existing", 10, 2, null);
        targetProgress("existing", 20, 3, 0);
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "OldFirst", 1);
        sourcePlayer(SECOND, "OldSecond", 1);
        sourcePlayer(THIRD, "NewThird", 1);
        sourcePlayer(FOURTH, "NewFourth", 1);
        sourceProgress("adapt_skill_mining", "existing", 1, 99, 1);
        sourceProgress("adapt_skill_mining", "missing", 1, 6, 0);
        migrate();
        int importedTeam = team(THIRD);
        assertThat(importedTeam).isNotIn(1, 10, 20);
        assertThat(team(FOURTH)).isEqualTo(importedTeam);
        assertThat(team(FIRST)).isEqualTo(10);
        assertThat(team(SECOND)).isEqualTo(20);
        assertThat(rows("SELECT Name FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='" + FIRST + "'")).containsExactly("CurrentFirst");
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE TeamID=10 AND `Key`='existing'")).isEqualTo(2);
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE TeamID=20 AND `Key`='existing'")).isEqualTo(3);
        assertThat(value("SELECT COUNT(*) FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE TeamID=10 AND `Key`='existing'")).isZero();
        assertThat(value("SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE TeamID=20 AND `Key`='existing'")).isZero();
        assertThat(value("SELECT COUNT(*) FROM ADAPT_ADVANCEMENT_PROGRESS WHERE `Key`='missing' AND Progression=6")).isEqualTo(3);
        assertThat(value("SELECT COUNT(*) FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE `Key`='missing' AND GiveRewards=0")).isEqualTo(3);
        assertThat(value("SELECT COUNT(*) FROM ADAPT_ADVANCEMENT_PROGRESS WHERE TeamID=1")).isZero();
    }

    @Test
    void mergedCanonicalTeamReceivesHighestMissingLegacyProgress() throws Exception {
        sourceSchema();
        execute("INSERT INTO ADAPT_ADVANCEMENT_TEAMS VALUES (20)");
        targetPlayer(FIRST, "First", 20);
        targetPlayer(SECOND, "Second", 20);
        execute("INSERT INTO Teams VALUES (1), (2)");
        sourcePlayer(FIRST, "First", 1);
        sourcePlayer(SECOND, "Second", 2);
        sourceProgress("adapt_skill_mining", "ore", 1, 2, 0);
        sourceProgress("adapt_skill_mining", "ore", 2, 9, 1);
        migrate();
        assertThat(count("ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(1);
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(9);
        assertThat(value("SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED")).isEqualTo(1);
    }

    @Test
    void equalProgressPreservesPendingRewardButLowerProgressCannotReplayOne() throws Exception {
        sourceSchema();
        execute("INSERT INTO ADAPT_ADVANCEMENT_TEAMS VALUES (20)");
        targetPlayer(FIRST, "First", 20);
        targetPlayer(SECOND, "Second", 20);
        targetPlayer(THIRD, "Third", 20);
        execute("INSERT INTO Teams VALUES (1), (2), (3)");
        sourcePlayer(FIRST, "First", 1);
        sourcePlayer(SECOND, "Second", 2);
        sourcePlayer(THIRD, "Third", 3);
        sourceProgress("adapt_skill_mining", "tied", 1, 10, null);
        sourceProgress("adapt_skill_mining", "tied", 2, 10, 0);
        sourceProgress("adapt_skill_mining", "tied", 3, 10, 1);
        sourceProgress("adapt_skill_mining", "higher", 1, 10, null);
        sourceProgress("adapt_skill_mining", "higher", 2, 9, 1);
        migrate();
        assertThat(count("ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(2);
        assertThat(count("ADAPT_ADVANCEMENT_UNREDEEMED")).isEqualTo(1);
        assertThat(value("SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE `Key`='tied'")).isEqualTo(1);
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE `Key`='higher'")).isEqualTo(10);
    }

    @Test
    void completionPreventsRewardProgressAndPlayerResurrection() throws Exception {
        sourceSchema();
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        migrate();
        execute("DELETE FROM ADAPT_ADVANCEMENT_UNREDEEMED");
        migrate();
        assertThat(count("ADAPT_ADVANCEMENT_UNREDEEMED")).isZero();
        execute("DELETE FROM ADAPT_ADVANCEMENT_PROGRESS");
        migrate();
        assertThat(count("ADAPT_ADVANCEMENT_PROGRESS")).isZero();
        execute("DELETE FROM ADAPT_ADVANCEMENT_TEAMS");
        migrate();
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isZero();
        assertThat(count("ADAPT_ADVANCEMENT_TEAMS")).isZero();
    }

    @Test
    void alreadyPresentDataStillCompletesMigrationWithoutReplayingReward() throws Exception {
        sourceSchema();
        execute("INSERT INTO ADAPT_ADVANCEMENT_TEAMS VALUES (20)");
        targetPlayer(FIRST, "Current", 20);
        targetProgress("ore", 20, 2, null);
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Old", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 9, 1);
        migrate();
        assertThat(completed()).isEqualTo(1);
        assertThat(value("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(2);
        assertThat(count("ADAPT_ADVANCEMENT_UNREDEEMED")).isZero();
    }

    @Test
    void missingAndUnrelatedSourcesRemainRetryable() throws Exception {
        assertThat(completed()).isZero();
        sourceSchema();
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("other_plugin", "ore", 1, 5, 1);
        migrate();
        assertThat(completed()).isZero();
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isZero();
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        migrate();
        assertThat(completed()).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isEqualTo(1);
    }

    @Test
    void rejectsSimilarSchemaWithoutLibraryForeignKeysAndRetriesAfterRepair() throws Exception {
        sourceSchema();
        execute("ALTER TABLE Players DROP CONSTRAINT legacy_players_team");
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        migrate();
        assertThat(completed()).isZero();
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isZero();
        execute("ALTER TABLE Players ADD CONSTRAINT legacy_players_team FOREIGN KEY(TeamID) REFERENCES Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE");
        migrate();
        assertThat(completed()).isEqualTo(1);
    }

    @Test
    void rejectsForeignKeysToSameNamedTablesInAnotherSchema() throws Exception {
        sourceSchema();
        execute("CREATE SCHEMA another_plugin");
        execute("CREATE TABLE another_plugin.Teams (ID INT NOT NULL PRIMARY KEY)");
        execute("INSERT INTO another_plugin.Teams VALUES (1)");
        execute("ALTER TABLE Players DROP CONSTRAINT legacy_players_team");
        execute("ALTER TABLE Players ADD FOREIGN KEY(TeamID) REFERENCES another_plugin.Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE");
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        migrate();
        assertThat(completed()).isZero();
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isZero();
    }

    @Test
    void failedCompletionRollsBackAllCopiesAndCanRetry() throws Exception {
        sourceSchema();
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        Map<String, List<String>> original = sources();
        try (Connection connection = dataSource.getConnection()) {
            Connection failing = spy(connection);
            doThrow(new SQLException("injected completion failure")).when(failing)
                    .prepareStatement("UPDATE `ADAPT_ADVANCEMENT_MIGRATIONS` SET `Completed`=1 WHERE `ID`=?");
            assertThatThrownBy(() -> LegacyMySQLMigration.run(failing, LOGGER))
                    .isInstanceOf(SQLException.class).hasMessage("injected completion failure");
        }
        assertThat(completed()).isZero();
        for (String table : List.of("TEAMS", "PLAYERS", "PROGRESS", "UNREDEEMED")) {
            assertThat(count("ADAPT_ADVANCEMENT_" + table)).isZero();
        }
        assertThat(sources()).isEqualTo(original);
        migrate();
        assertThat(completed()).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isEqualTo(1);
    }

    @Test
    void commitFailureDoesNotReportSuccessAndPreservesOriginalException() throws Exception {
        sourceSchema();
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        Logger logger = mock(Logger.class);
        try (Connection connection = dataSource.getConnection()) {
            Connection failing = spy(connection);
            doThrow(new SQLException("commit failed")).when(failing).commit();
            doThrow(new SQLException("restore failed")).when(failing).setAutoCommit(true);
            assertThatThrownBy(() -> LegacyMySQLMigration.run(failing, logger))
                    .isInstanceOf(SQLException.class).hasMessage("commit failed")
                    .satisfies(exception -> assertThat(exception.getSuppressed()).extracting(Throwable::getMessage).containsExactly("restore failed"));
        }
        verify(logger, never()).info(anyString());
        assertThat(completed()).isZero();
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isZero();
    }

    @Test
    void concurrentStartupsImportOnce() throws Exception {
        sourceSchema();
        execute("INSERT INTO Teams VALUES (1)");
        sourcePlayer(FIRST, "Explorer", 1);
        sourceProgress("adapt_skill_mining", "ore", 1, 5, 1);
        Map<String, List<String>> original = sources();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> attempts = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                attempts.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    try (Connection connection = dataSource.getConnection()) {
                        LegacyMySQLMigration.run(connection, LOGGER);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(15, TimeUnit.SECONDS);
            }
        }
        assertThat(completed()).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_TEAMS")).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_PLAYERS")).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_PROGRESS")).isEqualTo(1);
        assertThat(count("ADAPT_ADVANCEMENT_UNREDEEMED")).isEqualTo(1);
        assertThat(sources()).isEqualTo(original);
    }

    private void sourceSchema() throws SQLException {
        execute("CREATE TABLE Teams (ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY)");
        execute("CREATE TABLE Players (UUID VARCHAR(36) NOT NULL PRIMARY KEY, Name VARCHAR(16) NOT NULL, TeamID INT NOT NULL, CONSTRAINT legacy_players_team FOREIGN KEY(TeamID) REFERENCES Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE)");
        execute("CREATE TABLE Advancements (Namespace VARCHAR(127) NOT NULL, `Key` VARCHAR(127) NOT NULL, TeamID INT NOT NULL, Progression INT NOT NULL DEFAULT 0, PRIMARY KEY(Namespace, `Key`, TeamID), FOREIGN KEY(TeamID) REFERENCES Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE)");
        execute("CREATE TABLE Unredeemed (Namespace VARCHAR(127) NOT NULL, `Key` VARCHAR(127) NOT NULL, TeamID INT NOT NULL, GiveRewards INT NOT NULL, PRIMARY KEY(Namespace, `Key`, TeamID), FOREIGN KEY(Namespace, `Key`, TeamID) REFERENCES Advancements(Namespace, `Key`, TeamID) ON DELETE CASCADE ON UPDATE CASCADE)");
    }

    private void sourcePlayer(String uuid, String name, int team) throws SQLException {
        execute("INSERT INTO Players VALUES ('" + uuid + "', '" + name + "', " + team + ")");
    }

    private void targetPlayer(String uuid, String name, int team) throws SQLException {
        execute("INSERT INTO ADAPT_ADVANCEMENT_PLAYERS VALUES ('" + uuid + "', '" + name + "', " + team + ")");
    }

    private void sourceProgress(String namespace, String key, int team, int progression, Integer reward) throws SQLException {
        execute("INSERT INTO Advancements VALUES ('" + namespace + "', '" + key + "', " + team + ", " + progression + ")");
        if (reward != null) {
            execute("INSERT INTO Unredeemed VALUES ('" + namespace + "', '" + key + "', " + team + ", " + reward + ")");
        }
    }

    private void targetProgress(String key, int team, int progression, Integer reward) throws SQLException {
        execute("INSERT INTO ADAPT_ADVANCEMENT_PROGRESS VALUES ('adapt_skill_mining', '" + key + "', " + team + ", " + progression + ")");
        if (reward != null) {
            execute("INSERT INTO ADAPT_ADVANCEMENT_UNREDEEMED VALUES ('adapt_skill_mining', '" + key + "', " + team + ", " + reward + ")");
        }
    }

    private void migrate() throws SQLException {
        Map<String, List<String>> original = sources();
        database.setUp();
        assertThat(sources()).isEqualTo(original);
    }

    private Map<String, List<String>> sources() throws SQLException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : SOURCES) {
            List<String> snapshot = rows("SELECT * FROM " + table);
            snapshot.sort(String::compareTo);
            result.put(table, snapshot);
        }
        return result;
    }

    private int team(String uuid) throws SQLException {
        return value("SELECT TeamID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='" + uuid + "'");
    }

    private int completed() throws SQLException {
        return value("SELECT Completed FROM ADAPT_ADVANCEMENT_MIGRATIONS WHERE ID='generic-advancements'");
    }

    private int count(String table) throws SQLException {
        return value("SELECT COUNT(*) FROM " + table);
    }

    private int value(String query) throws SQLException {
        return Integer.parseInt(rows(query).getFirst());
    }

    private List<String> rows(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(query)) {
            List<String> result = new ArrayList<>();
            while (rows.next()) {
                List<String> values = new ArrayList<>();
                for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) {
                    values.add(rows.getString(index));
                }
                result.add(String.join("|", values));
            }
            return result;
        }
    }

    private void execute(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    private void field(String name, Object value) throws Exception {
        Field field = MySQL.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(database, value);
    }
}
