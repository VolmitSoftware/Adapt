package com.fren_gor.ultimateAdvancementAPI.database.impl;

import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.database.IDatabase;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.fren_gor.ultimateAdvancementAPI.exceptions.IllegalKeyException;
import com.fren_gor.ultimateAdvancementAPI.exceptions.UserNotRegisteredException;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;
import com.fren_gor.ultimateAdvancementAPI.libs.net.byteflux.libby.Library;
import com.fren_gor.ultimateAdvancementAPI.libs.net.byteflux.libby.LibraryManager;
import com.fren_gor.ultimateAdvancementAPI.libs.net.byteflux.libby.classloader.IsolatedClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MySQL implements IDatabase {

    private final Logger logger;
    private final IsolatedClassLoader classLoader;
    private final DataSource dataSource;
    private final Method close;

    public MySQL(@NotNull AdvancementMain main, @NotNull String username, @NotNull String password,
                 @NotNull String databaseName, @NotNull String host, int port, int poolSize,
                 long connectionTimeout) throws Exception {
        Objects.requireNonNull(main, "AdvancementMain is null.");
        ConnectionSettings settings = new ConnectionSettings(username, password, databaseName, host, port, poolSize, connectionTimeout);
        logger = main.getLogger();
        LibraryManager manager = main.getLibbyManager();

        classLoader = new IsolatedClassLoader();
        classLoader.addPath(manager.downloadLibrary(Library.builder().groupId("org.slf4j").artifactId("slf4j-api").version("1.7.36").checksum("0+9XXj5JeWeNwBvx3M5RAhSTtNEft/G+itmCh3wWocA=").build()));
        classLoader.addPath(manager.downloadLibrary(Library.builder().groupId("org.slf4j").artifactId("slf4j-nop").version("1.7.36").checksum("whSViweBbLRBKzDHvb1DCP/ca6KoN2e486kinL2SdNY=").build()));
        classLoader.addPath(manager.downloadLibrary(Library.builder().groupId("com.zaxxer").artifactId("HikariCP").version("5.1.0").checksum("pHpu5iN5aU7lLDADbwkxty+a7iqAHVkDQe2CvYOeITQ=").build()));

        Class<?> hikariConfig = classLoader.loadClass("com.zaxxer.hikari.HikariConfig");
        Class<?> hikariDataSource = classLoader.loadClass("com.zaxxer.hikari.HikariDataSource");

        close = hikariDataSource.getDeclaredMethod("close");

        Properties props = new Properties();
        props.put("jdbcUrl", "jdbc:mysql://" + settings.host() + ":" + settings.port() + '/' + settings.databaseName());
        props.put("driverClassName", "com.mysql.jdbc.Driver");
        props.put("username", settings.username());
        props.put("password", settings.password());
        props.put("minimumIdle", settings.poolSize());
        props.put("maximumPoolSize", settings.poolSize());
        props.put("connectionTimeout", settings.connectionTimeout());
        props.put("poolName", "AdaptAdvancements");
        props.put("dataSource.useSSL", false);
        props.put("dataSource.cachePrepStmts", true);
        props.put("dataSource.prepStmtCacheSize", 250);
        props.put("dataSource.prepStmtCacheSqlLimit", 2048);
        props.put("dataSource.useServerPrepStmts", true);
        props.put("dataSource.useLocalSessionState", true);
        props.put("dataSource.rewriteBatchedStatements", true);
        props.put("dataSource.cacheResultSetMetadata", true);
        props.put("dataSource.cacheServerConfiguration", true);
        props.put("dataSource.maintainTimeStats", false);

        Object config = hikariConfig.getConstructor(Properties.class).newInstance(props);
        this.dataSource = (DataSource) hikariDataSource.getConstructor(hikariConfig).newInstance(config);
        try (Connection ignored = openConnection()) {
        } catch (SQLException e) {
            throw new SQLException("An exception occurred while testing the established connection.", e);
        }
    }

    @Override
    public void setUp() throws SQLException {
        try (Connection conn = openConnection(); Statement statement = conn.createStatement()) {
            statement.addBatch("CREATE TABLE IF NOT EXISTS `ADAPT_ADVANCEMENT_TEAMS` (`ID` INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;");
            statement.addBatch("CREATE TABLE IF NOT EXISTS `ADAPT_ADVANCEMENT_PLAYERS` (`UUID` VARCHAR(36) NOT NULL, `Name` VARCHAR(16) NOT NULL, `TeamID` INTEGER NOT NULL, PRIMARY KEY(`UUID`), CONSTRAINT `ADAPT_ADVANCEMENT_PLAYERS_TEAM` FOREIGN KEY(`TeamID`) REFERENCES `ADAPT_ADVANCEMENT_TEAMS`(`ID`) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;");
            statement.addBatch("CREATE TABLE IF NOT EXISTS `ADAPT_ADVANCEMENT_PROGRESS` (`Namespace` VARCHAR(127) NOT NULL, `Key` VARCHAR(127) NOT NULL, `TeamID` INTEGER NOT NULL, `Progression` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`Namespace`,`Key`,`TeamID`), CONSTRAINT `ADAPT_ADVANCEMENT_PROGRESS_TEAM` FOREIGN KEY(`TeamID`) REFERENCES `ADAPT_ADVANCEMENT_TEAMS`(`ID`) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;");
            statement.addBatch("CREATE TABLE IF NOT EXISTS `ADAPT_ADVANCEMENT_UNREDEEMED` (`Namespace` VARCHAR(127) NOT NULL, `Key` VARCHAR(127) NOT NULL, `TeamID` INTEGER NOT NULL, `GiveRewards` INTEGER NOT NULL, PRIMARY KEY(`Namespace`,`Key`,`TeamID`), CONSTRAINT `ADAPT_ADVANCEMENT_UNREDEEMED_PROGRESS` FOREIGN KEY(`Namespace`, `Key`, `TeamID`) REFERENCES `ADAPT_ADVANCEMENT_PROGRESS`(`Namespace`, `Key`, `TeamID`) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;");
            statement.executeBatch();
            LegacyMySQLMigration.run(conn, logger);
        } catch (SQLException | RuntimeException exception) {
            try {
                close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    @Override
    public Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() throws SQLException {
        try {
            close.invoke(dataSource);
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Cannot close HikariDataSource.", e);
        } finally {
            try {
                classLoader.close();
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "Cannot close the advancement database class loader.", exception);
            }
        }
    }

    @Override
    public int getTeamId(@NotNull UUID uuid) throws SQLException, UserNotRegisteredException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("SELECT `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=?;")) {
            ps.setString(1, uuid.toString());
            ResultSet r = ps.executeQuery();
            if (r.next()) {
                return r.getInt(1);
            } else {
                throw new UserNotRegisteredException("No user " + uuid + " has been found.");
            }
        }
    }

    @Override
    public List<UUID> getTeamMembers(int teamId) throws SQLException {
        try (Connection conn = openConnection()) {
            return getTeamMembers(conn, teamId);
        }
    }

    @Override
    public Map<AdvancementKey, Integer> getTeamAdvancements(int teamId) throws SQLException {
        try (Connection conn = openConnection()) {
            return getTeamAdvancements(conn, teamId);
        }
    }

    @Override
    public Entry<TeamProgression, Boolean> loadOrRegisterPlayer(@NotNull UUID uuid, @NotNull String name) throws SQLException {
        int teamId;
        ResultSet r;
        try (Connection conn = openConnection()) {
            try (PreparedStatement psTeamId = conn.prepareStatement("SELECT `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=?;")) {
                psTeamId.setString(1, uuid.toString());

                r = psTeamId.executeQuery();
                if (!r.next()) {
                    try (PreparedStatement psInsert = conn.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_TEAMS` () VALUES ();", Statement.RETURN_GENERATED_KEYS); PreparedStatement psInsertPl = conn.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_PLAYERS` (`UUID`, `Name`, `TeamID`) VALUES (?, ?, ?);")) {
                        psInsert.executeUpdate();
                        r = psInsert.getGeneratedKeys();
                        if (!r.next()) {
                            throw new SQLException("Cannot insert default values into ADAPT_ADVANCEMENT_TEAMS table.");
                        }
                        teamId = r.getInt(1);
                        psInsertPl.setString(1, uuid.toString());
                        psInsertPl.setString(2, name);
                        psInsertPl.setInt(3, teamId);
                        psInsertPl.execute();
                        return new SimpleEntry<>(new TeamProgression(teamId, uuid), true);
                    }
                }

                teamId = r.getInt(1);
            }
            List<UUID> list = getTeamMembers(conn, teamId);
            Map<AdvancementKey, Integer> map = getTeamAdvancements(conn, teamId);
            return new SimpleEntry<>(new TeamProgression(map, teamId, list), false);
        }
    }

    @Override
    public TeamProgression loadUUID(@NotNull UUID uuid) throws SQLException, UserNotRegisteredException {
        int teamID = Integer.MIN_VALUE;
        List<UUID> list = new LinkedList<>();
        try (Connection conn = openConnection()) {
            try (PreparedStatement psTeamId = conn.prepareStatement("SELECT `UUID`, `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `TeamID`=(SELECT `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=? LIMIT 1);")) {
                psTeamId.setString(1, uuid.toString());
                ResultSet r = psTeamId.executeQuery();
                while (r.next()) {
                    list.add(UUID.fromString(r.getString(1)));
                    if (teamID == Integer.MIN_VALUE) {
                        teamID = r.getInt(2);
                    }
                }
            }

            if (teamID == Integer.MIN_VALUE) {
                throw new UserNotRegisteredException("No user " + uuid + " has been found.");
            }

            try (PreparedStatement psAdv = conn.prepareStatement("SELECT `Namespace`, `Key`, `Progression` FROM `ADAPT_ADVANCEMENT_PROGRESS` WHERE `TeamID`=?;")) {
                Map<AdvancementKey, Integer> map = new HashMap<>();
                psAdv.setInt(1, teamID);
                ResultSet r = psAdv.executeQuery();
                while (r.next()) {
                    String namespace = r.getString(1);
                    String key = r.getString(2);
                    int progression = r.getInt(3);
                    try {
                        map.put(new AdvancementKey(namespace, key), progression);
                    } catch (IllegalKeyException e) {
                        logger.log(Level.WARNING, "Invalid advancement key " + namespace + ':' + key + " in ADAPT_ADVANCEMENT_PROGRESS.", e);
                    }
                }
                return new TeamProgression(map, teamID, list);
            }
        }
    }

    @Override
    public void updateAdvancement(@NotNull AdvancementKey key, int teamId, @Range(from = 0, to = Integer.MAX_VALUE) int progression) throws SQLException {
        try (Connection conn = openConnection()) {
            if (progression <= 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM `ADAPT_ADVANCEMENT_PROGRESS` WHERE `Namespace`=? AND `Key`=? AND `TeamID`=?;")) {
                    ps.setString(1, key.getNamespace());
                    ps.setString(2, key.getKey());
                    ps.setInt(3, teamId);
                    ps.execute();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_PROGRESS` (`Namespace`, `Key`, `TeamID`, `Progression`) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE `Progression`=VALUES(`Progression`);")) {
                    ps.setString(1, key.getNamespace());
                    ps.setString(2, key.getKey());
                    ps.setInt(3, teamId);
                    ps.setInt(4, progression);
                    ps.execute();
                }
            }
        }
    }

    @Override
    public List<Entry<AdvancementKey, Boolean>> getUnredeemed(int teamId) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("SELECT `Namespace`, `Key`, `GiveRewards` FROM `ADAPT_ADVANCEMENT_UNREDEEMED` WHERE `TeamID`=?;")) {
            ps.setInt(1, teamId);
            ResultSet r = ps.executeQuery();
            List<Entry<AdvancementKey, Boolean>> list = new LinkedList<>();
            while (r.next()) {
                String namespace = r.getString(1);
                String key = r.getString(2);
                boolean giveRewards = r.getInt(3) != 0;
                try {
                    list.add(new SimpleEntry<>(new AdvancementKey(namespace, key), giveRewards));
                } catch (IllegalKeyException e) {
                    logger.log(Level.WARNING, "Invalid advancement key " + namespace + ':' + key + " in ADAPT_ADVANCEMENT_UNREDEEMED.", e);
                }
            }
            return list;
        }
    }

    @Override
    public void setUnredeemed(@NotNull AdvancementKey key, boolean giveRewards, int teamId) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO `ADAPT_ADVANCEMENT_UNREDEEMED` (`Namespace`, `Key`, `TeamID`, `GiveRewards`) VALUES (?, ?, ?, ?);")) {
            ps.setString(1, key.getNamespace());
            ps.setString(2, key.getKey());
            ps.setInt(3, teamId);
            ps.setInt(4, giveRewards ? 1 : 0);
            ps.execute();
        }
    }

    @Override
    public boolean isUnredeemed(@NotNull AdvancementKey key, int teamId) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("SELECT Count(*) FROM `ADAPT_ADVANCEMENT_UNREDEEMED` WHERE `Namespace`=? AND `Key`=? AND `TeamID`=?;")) {
            ps.setString(1, key.getNamespace());
            ps.setString(2, key.getKey());
            ps.setInt(3, teamId);
            ResultSet r = ps.executeQuery();
            return r.next() && r.getInt(1) > 0;
        }
    }

    @Override
    public void unsetUnredeemed(@NotNull AdvancementKey key, int teamId) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM `ADAPT_ADVANCEMENT_UNREDEEMED` WHERE `Namespace`=? AND `Key`=? AND `TeamID`=?;")) {
            ps.setString(1, key.getNamespace());
            ps.setString(2, key.getKey());
            ps.setInt(3, teamId);
            ps.execute();
        }
    }

    @Override
    public void unsetUnredeemed(@NotNull List<Entry<AdvancementKey, Boolean>> keyList, int teamId) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM `ADAPT_ADVANCEMENT_UNREDEEMED` WHERE `Namespace`=? AND `Key`=? AND `TeamID`=?;")) {
            for (Entry<AdvancementKey, ?> key : keyList) {
                ps.setString(1, key.getKey().getNamespace());
                ps.setString(2, key.getKey().getKey());
                ps.setInt(3, teamId);
                ps.execute();
            }
        }
    }

    @Override
    public void unregisterPlayer(@NotNull UUID uuid) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement stDelete = conn.prepareStatement("DELETE FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=?;")) {
            stDelete.setString(1, uuid.toString());
            stDelete.execute();
        }
    }

    @Override
    public void movePlayer(@NotNull UUID uuid, int newTeamId) throws SQLException {
        try (Connection conn = openConnection()) {
            movePlayer(conn, uuid, newTeamId);
        }
    }

    @Override
    public TeamProgression movePlayerInNewTeam(@NotNull UUID uuid) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement psInsert = conn.prepareStatement("INSERT INTO `ADAPT_ADVANCEMENT_TEAMS` () VALUES ();", Statement.RETURN_GENERATED_KEYS)) {
            psInsert.executeUpdate();
            ResultSet r = psInsert.getGeneratedKeys();
            if (!r.next()) {
                throw new SQLException("Cannot insert default values into ADAPT_ADVANCEMENT_TEAMS table.");
            }
            int teamId = r.getInt(1);
            movePlayer(conn, uuid, teamId);
            return new TeamProgression(teamId, uuid);
        }
    }

    @Override
    public List<UUID> getPlayersByName(@NotNull String name) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("SELECT `UUID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `Name`=?;")) {
            ps.setString(1, name);
            ResultSet r = ps.executeQuery();
            List<UUID> list = new LinkedList<>();
            while (r.next()) {
                list.add(UUID.fromString(r.getString(1)));
            }
            return list;
        }
    }

    @Override
    public String getPlayerName(@NotNull UUID uuid) throws SQLException, UserNotRegisteredException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("SELECT `Name` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `UUID`=? LIMIT 1;")) {
            ps.setString(1, uuid.toString());
            ResultSet r = ps.executeQuery();
            if (!r.next()) {
                throw new UserNotRegisteredException("No user " + uuid + " has been found.");
            }
            return r.getString(1);
        }
    }

    @Override
    public void updatePlayerName(@NotNull UUID uuid, @NotNull String name) throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE `ADAPT_ADVANCEMENT_PLAYERS` SET `Name`=? WHERE `UUID`=?;")) {
            ps.setString(1, name);
            ps.setString(2, uuid.toString());
            ps.execute();
        }
    }

    @Override
    public void clearUpTeams() throws SQLException {
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM `ADAPT_ADVANCEMENT_TEAMS` WHERE `ID` NOT IN (SELECT `TeamID` FROM `ADAPT_ADVANCEMENT_PLAYERS` GROUP BY `TeamID`);")) {
            ps.execute();
        }
    }
    private List<UUID> getTeamMembers(Connection connection, int teamId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT `UUID` FROM `ADAPT_ADVANCEMENT_PLAYERS` WHERE `TeamID`=?;")) {
            ps.setInt(1, teamId);
            ResultSet r = ps.executeQuery();
            List<UUID> list = new LinkedList<>();
            while (r.next()) {
                list.add(UUID.fromString(r.getString(1)));
            }
            return list;
        }
    }

    private Map<AdvancementKey, Integer> getTeamAdvancements(Connection connection, int teamId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT `Namespace`,`Key`,`Progression` FROM `ADAPT_ADVANCEMENT_PROGRESS` WHERE `TeamID`=?;")) {
            ps.setInt(1, teamId);
            ResultSet r = ps.executeQuery();
            Map<AdvancementKey, Integer> map = new HashMap<>();
            while (r.next()) {
                String namespace = r.getString(1);
                String key = r.getString(2);
                int progression = r.getInt(3);
                try {
                    map.put(new AdvancementKey(namespace, key), progression);
                } catch (IllegalKeyException e) {
                    logger.log(Level.WARNING, "Invalid advancement key " + namespace + ':' + key + " in ADAPT_ADVANCEMENT_PROGRESS.", e);
                }
            }
            return map;
        }
    }

    private void movePlayer(Connection connection, @NotNull UUID uuid, int newTeamId) throws SQLException {
        try (PreparedStatement stUpdate = connection.prepareStatement("UPDATE `ADAPT_ADVANCEMENT_PLAYERS` SET `TeamID`=? WHERE `UUID`=?;")) {
            stUpdate.setInt(1, newTeamId);
            stUpdate.setString(2, uuid.toString());
            stUpdate.execute();
        }
    }

    private record ConnectionSettings(String username, String password, String databaseName, String host,
                                     int port, int poolSize, long connectionTimeout) {
        private ConnectionSettings {
            Objects.requireNonNull(username, "Username is null.");
            Objects.requireNonNull(password, "Password is null.");
            Objects.requireNonNull(databaseName, "Database name is null.");
            Objects.requireNonNull(host, "Host is null.");
            if (port <= 0 || poolSize <= 0 || connectionTimeout < 250) {
                throw new IllegalArgumentException("Port and pool size must be positive; connection timeout must be at least 250 ms.");
            }
        }
    }
}
