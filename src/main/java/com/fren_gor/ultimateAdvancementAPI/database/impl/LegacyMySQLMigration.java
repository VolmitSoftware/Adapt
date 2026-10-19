package com.fren_gor.ultimateAdvancementAPI.database.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class LegacyMySQLMigration {
    private static final String MIGRATION = "generic-advancements";
    private static final String NAMESPACE_PREFIX = "adapt_skill_";
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9/._-]+");
    private static final Map<String, Map<String, Column>> COLUMNS = Map.of(
            "Teams", Map.of("ID", new Column(Types.INTEGER, 0)),
            "Players", Map.of("UUID", new Column(Types.VARCHAR, 36), "Name", new Column(Types.VARCHAR, 16), "TeamID", new Column(Types.INTEGER, 0)),
            "Advancements", Map.of("Namespace", new Column(Types.VARCHAR, 127), "Key", new Column(Types.VARCHAR, 127),
                    "TeamID", new Column(Types.INTEGER, 0), "Progression", new Column(Types.INTEGER, 0)),
            "Unredeemed", Map.of("Namespace", new Column(Types.VARCHAR, 127), "Key", new Column(Types.VARCHAR, 127),
                    "TeamID", new Column(Types.INTEGER, 0), "GiveRewards", new Column(Types.INTEGER, 0)));
    private static final Map<String, Set<String>> PRIMARY_KEYS = Map.of(
            "Teams", Set.of("ID"), "Players", Set.of("UUID"),
            "Advancements", Set.of("Namespace", "Key", "TeamID"), "Unredeemed", Set.of("Namespace", "Key", "TeamID"));
    private static final Map<String, Set<String>> FOREIGN_KEYS = Map.of(
            "Teams", Set.of(), "Players", Set.of("TeamID:Teams:ID"),
            "Advancements", Set.of("TeamID:Teams:ID"),
            "Unredeemed", Set.of("Namespace:Advancements:Namespace", "Key:Advancements:Key", "TeamID:Advancements:TeamID"));

    private final Connection connection;
    private final Logger logger;
    private final Map<String, String> tables = new HashMap<>();
    private int players;
    private int progress;
    private int rewards;

    private LegacyMySQLMigration(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    static void run(Connection connection, Logger logger) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `ADAPT_ADVANCEMENT_MIGRATIONS` (`ID` VARCHAR(64) NOT NULL PRIMARY KEY, `Completed` INTEGER NOT NULL DEFAULT 0) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");
            statement.execute("INSERT IGNORE INTO `ADAPT_ADVANCEMENT_MIGRATIONS` (`ID`, `Completed`) VALUES ('" + MIGRATION + "', 0)");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        LegacyMySQLMigration migration = new LegacyMySQLMigration(connection, logger);
        boolean imported;
        try {
            imported = migration.migrate();
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            boolean rolledBack = false;
            try {
                connection.rollback();
                rolledBack = true;
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            if (rolledBack) {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            throw exception;
        }
        connection.setAutoCommit(autoCommit);
        if (imported) {
            logger.info("Imported advancement SQL data: " + migration.players + " players, " + migration.progress + " progress rows, "
                    + migration.rewards + " pending rewards. Source tables are unchanged.");
        }
    }

    private boolean migrate() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT `Completed` FROM `ADAPT_ADVANCEMENT_MIGRATIONS` WHERE `ID`=? FOR UPDATE")) {
            statement.setString(1, MIGRATION);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Advancement migration lock row is missing.");
                }
                if (rows.getInt(1) != 0) {
                    return false;
                }
            }
        }
        if (!sourceSchemaMatches()) {
            return false;
        }
        Set<Integer> sourceTeams = sourceTeams();
        if (sourceTeams.isEmpty()) {
            return false;
        }
        Map<Integer, Set<Integer>> targetTeams = mapTeams(sourceTeams);
        copyProgress(targetTeams);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `ADAPT_ADVANCEMENT_MIGRATIONS` SET `Completed`=1 WHERE `ID`=?")) {
            statement.setString(1, MIGRATION);
            statement.executeUpdate();
        }
        return true;
    }

    private boolean sourceSchemaMatches() throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rows = metadata.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (rows.next()) {
                String table = rows.getString("TABLE_NAME");
                for (String expected : COLUMNS.keySet()) {
                    if (table.equalsIgnoreCase(expected)) {
                        if (tables.put(expected, table) != null) {
                            return schemaMismatch();
                        }
                    }
                }
            }
        }
        if (tables.isEmpty()) {
            return false;
        }
        if (tables.size() != COLUMNS.size()) {
            return schemaMismatch();
        }
        for (String table : COLUMNS.keySet()) {
            if (!columnsMatch(metadata, table) || !keysMatch(metadata, table)) {
                return schemaMismatch();
            }
        }
        return true;
    }

    private boolean schemaMismatch() {
        logger.warning("Advancement SQL import skipped: Teams, Players, Advancements and Unredeemed do not match the advancement library schema. Source tables are unchanged; import will be checked again at startup.");
        return false;
    }

    private boolean columnsMatch(DatabaseMetaData metadata, String table) throws SQLException {
        Map<String, Column> expected = COLUMNS.get(table);
        Set<String> found = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getColumns(connection.getCatalog(), connection.getSchema(), tables.get(table), "%")) {
            while (rows.next()) {
                String column = canonical(rows.getString("COLUMN_NAME"), expected.keySet());
                if (column == null) {
                    return false;
                }
                Column definition = expected.get(column);
                if (definition == null || !found.add(column) || rows.getInt("DATA_TYPE") != definition.type()
                        || rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
                        || definition.width() > 0 && rows.getInt("COLUMN_SIZE") != definition.width()) {
                    return false;
                }
            }
        }
        return found.equals(expected.keySet());
    }

    private boolean keysMatch(DatabaseMetaData metadata, String table) throws SQLException {
        Set<String> primary = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getPrimaryKeys(connection.getCatalog(), connection.getSchema(), tables.get(table))) {
            while (rows.next()) {
                primary.add(canonical(rows.getString("COLUMN_NAME"), COLUMNS.get(table).keySet()));
            }
        }
        if (!primary.equals(PRIMARY_KEYS.get(table))) {
            return false;
        }
        Set<String> foreign = new LinkedHashSet<>();
        try (ResultSet rows = metadata.getImportedKeys(connection.getCatalog(), connection.getSchema(), tables.get(table))) {
            while (rows.next()) {
                String source = canonical(rows.getString("FKCOLUMN_NAME"), COLUMNS.get(table).keySet());
                String target = canonical(rows.getString("PKTABLE_NAME"), COLUMNS.keySet());
                String schema = rows.getString("PKTABLE_SCHEM");
                if (target == null || !Objects.equals(connection.getCatalog(), rows.getString("PKTABLE_CAT"))
                        || schema != null && !Objects.equals(connection.getSchema(), schema)
                        || rows.getInt("DELETE_RULE") != DatabaseMetaData.importedKeyCascade
                        || rows.getInt("UPDATE_RULE") != DatabaseMetaData.importedKeyCascade) {
                    return false;
                }
                String column = canonical(rows.getString("PKCOLUMN_NAME"), COLUMNS.get(target).keySet());
                foreign.add(source + ":" + target + ":" + column);
            }
        }
        return foreign.equals(FOREIGN_KEYS.get(table));
    }

    private Set<Integer> sourceTeams() throws SQLException {
        Set<Integer> teams = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT DISTINCT `TeamID`, `Namespace` FROM " + table("Advancements") + " WHERE LEFT(`Namespace`, 12)=? ORDER BY `TeamID`")) {
            statement.setString(1, NAMESPACE_PREFIX);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getString(2).startsWith(NAMESPACE_PREFIX)) {
                        teams.add(rows.getInt(1));
                    }
                }
            }
        }
        return teams;
    }

    private Map<Integer, Set<Integer>> mapTeams(Set<Integer> sourceTeams) throws SQLException {
        Map<Integer, Set<Integer>> targets = new LinkedHashMap<>();
        for (int sourceTeam : sourceTeams) {
            Set<Integer> teamTargets = new LinkedHashSet<>();
            List<SourcePlayer> missing = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT `UUID`, `Name` FROM " + table("Players") + " WHERE `TeamID`=? ORDER BY `UUID`")) {
                statement.setInt(1, sourceTeam);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        SourcePlayer player = new SourcePlayer(validUuid(rows.getString(1)), rows.getString(2));
                        Integer target = existingTeam(player.uuid());
                        if (target == null) {
                            missing.add(player);
                        } else {
                            teamTargets.add(target);
                        }
                    }
                }
            }
            if (!missing.isEmpty()) {
                int team = createTeam();
                teamTargets.add(team);
                for (SourcePlayer player : missing) {
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_PLAYERS` (`UUID`, `Name`, `TeamID`) VALUES (?, ?, ?)")) {
                        statement.setString(1, player.uuid());
                        statement.setString(2, player.name());
                        statement.setInt(3, team);
                        players += statement.executeUpdate();
                    }
                }
            }
            targets.put(sourceTeam, teamTargets);
        }
        return targets;
    }

    private Integer existingTeam(String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=? FOR UPDATE")) {
            statement.setString(1, uuid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : null;
            }
        }
    }

    private int createTeam() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_TEAMS` () VALUES ()", Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            try (ResultSet rows = statement.getGeneratedKeys()) {
                if (!rows.next()) {
                    throw new SQLException("Cannot allocate an imported advancement team.");
                }
                return rows.getInt(1);
            }
        }
    }

    private void copyProgress(Map<Integer, Set<Integer>> targetTeams) throws SQLException {
        String query = "SELECT a.`Namespace`, a.`Key`, a.`TeamID`, a.`Progression`, u.`GiveRewards` FROM " + table("Advancements")
                + " a LEFT JOIN " + table("Unredeemed") + " u ON a.`Namespace`=u.`Namespace` AND a.`Key`=u.`Key` AND a.`TeamID`=u.`TeamID`"
                + " WHERE LEFT(a.`Namespace`, 12)=? ORDER BY a.`Progression` DESC,"
                + " CASE WHEN u.`GiveRewards` IS NULL THEN 2 WHEN u.`GiveRewards`=0 THEN 1 ELSE 0 END, a.`TeamID`, a.`Namespace`, a.`Key`";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, NAMESPACE_PREFIX);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String namespace = rows.getString(1);
                    if (!namespace.startsWith(NAMESPACE_PREFIX)) {
                        continue;
                    }
                    String key = rows.getString(2);
                    int progression = rows.getInt(4);
                    if (!NAMESPACE_PATTERN.matcher(namespace).matches() || !KEY_PATTERN.matcher(key).matches() || progression < 0) {
                        throw new SQLException("Invalid Adapt advancement data in source team " + rows.getInt(3) + ".");
                    }
                    Integer reward = rows.getObject(5) == null ? null : rows.getInt(5);
                    for (int target : targetTeams.getOrDefault(rows.getInt(3), Set.of())) {
                        copyAdvancement(new SourceAdvancement(namespace, key, progression, reward), target);
                    }
                }
            }
        }
    }

    private void copyAdvancement(SourceAdvancement advancement, int team) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM `ADAPT_ADVANCEMENT_PROGRESS` WHERE `Namespace`=? AND `Key`=? AND `TeamID`=? FOR UPDATE")) {
            identify(statement, advancement, team);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_PROGRESS` (`Namespace`, `Key`, `TeamID`, `Progression`) VALUES (?, ?, ?, ?)")) {
            identify(statement, advancement, team);
            statement.setInt(4, advancement.progression());
            progress += statement.executeUpdate();
        }
        if (advancement.reward() != null) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_UNREDEEMED` (`Namespace`, `Key`, `TeamID`, `GiveRewards`) VALUES (?, ?, ?, ?)")) {
                identify(statement, advancement, team);
                statement.setInt(4, advancement.reward());
                rewards += statement.executeUpdate();
            }
        }
    }

    private static void identify(PreparedStatement statement, SourceAdvancement advancement, int team) throws SQLException {
        statement.setString(1, advancement.namespace());
        statement.setString(2, advancement.key());
        statement.setInt(3, team);
    }

    private static String validUuid(String value) throws SQLException {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Noncanonical UUID");
            }
            return uuid.toString();
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid player UUID in source advancement data.", exception);
        }
    }

    private static String canonical(String value, Set<String> expected) {
        for (String candidate : expected) {
            if (candidate.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        return null;
    }

    private String table(String name) {
        return '`' + tables.get(name) + '`';
    }

    private record Column(int type, int width) {
    }

    private record SourcePlayer(String uuid, String name) {
    }

    private record SourceAdvancement(String namespace, String key, int progression, Integer reward) {
    }
}
