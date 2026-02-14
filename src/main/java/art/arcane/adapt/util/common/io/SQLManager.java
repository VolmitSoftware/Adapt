package art.arcane.adapt.util.common.io;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class SQLManager {

    private static final String TABLE_NAME = "ADAPT_DATA";
    private static final String CREATE_TABLE_QUERY = "CREATE TABLE " + TABLE_NAME + " (UUID char(36) NOT NULL UNIQUE, DATA MEDIUMTEXT NOT NULL)";
    private static final String UPDATE_QUERY = "INSERT INTO " + TABLE_NAME + " (UUID, DATA) VALUES(?, ?) ON DUPLICATE KEY UPDATE DATA=?";
    private static final String FETCH_QUERY = "SELECT DATA FROM " + TABLE_NAME + " WHERE UUID=?";
    private static final String DELETE_QUERY = "DELETE FROM " + TABLE_NAME + " WHERE UUID=?";

    private Connection connection;

    public synchronized void establishConnection() {
        if (connection != null) {
            closeConnection();
        }

        AdaptConfig config = AdaptConfig.get();
        try {
            connection = DriverManager.getConnection(assembleUrl(config), config.getSql().getUsername(), config.getSql().getPassword());
            int verifySeconds = Math.max(1, Math.min(10, config.getSqlSecondsCheckverify()));
            if (!connection.isValid(verifySeconds)) {
                throw new SQLException("Connection timed out");
            } else {
                setupDatabase();
            }
        } catch (SQLException e) {
            handleSQLException("Failed to establish a connection to the SQL server!", e);
            connection = null;
        }
    }

    private void setupDatabase() throws SQLException {
        if (!connection.getMetaData().getTables(null, null, TABLE_NAME, new String[]{"TABLE"}).next()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_TABLE_QUERY);
            }
        }
    }

    public synchronized void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                handleSQLException("Failed to close the connection to the SQL server!", e);
            }
            connection = null;
        }
    }

    public synchronized void updateData(UUID uuid, String data) {
        executeWithRetry(conn -> {
            try (PreparedStatement statement = conn.prepareStatement(UPDATE_QUERY)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, data);
                statement.setString(3, data);
                statement.executeUpdate();
            }
        }, "Failed to write data to the SQL server!");
    }

    public synchronized void delete(UUID uuid) {
        executeWithRetry(conn -> {
            try (PreparedStatement statement = conn.prepareStatement(DELETE_QUERY)) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        }, "Failed to delete data from the SQL server!");
    }

    public synchronized String fetchData(UUID uuid) {
        try {
            checkAndReestablishConnection();
            try (PreparedStatement statement = connection.prepareStatement(FETCH_QUERY)) {
                statement.setString(1, uuid.toString());
                try (ResultSet set = statement.executeQuery()) {
                    if (!set.next()) {
                        return null;
                    }
                    return set.getString("DATA");
                }
            }
        } catch (SQLException e) {
            handleSQLException("Failed to read data from the SQL server!", e);
            return null;
        }
    }

    private void executeWithRetry(SqlAction action, String errorMessage) {
        try {
            checkAndReestablishConnection();
            action.run(connection);
        } catch (SQLException e) {
            handleSQLException(errorMessage, e);
        }
    }

    private void checkAndReestablishConnection() throws SQLException {
        if (connection == null || !connection.isValid(AdaptConfig.get().getSqlSecondsCheckverify())) { // 30 sec by default
            establishConnection();
        }
        if (connection == null) {
            throw new SQLException("No active SQL connection");
        }
    }

    private void handleSQLException(String message, SQLException e) {
        Adapt.error(message);
        Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
    }

    private String assembleUrl(AdaptConfig config) {
        long connectTimeout = Math.max(1000L, config.getSql().getConnectionTimeout());
        long socketTimeout = Math.max(connectTimeout, connectTimeout * 2L);
        return String.format(
                "jdbc:mysql://%s:%d/%s?connectTimeout=%d&socketTimeout=%d",
                config.getSql().getHost(),
                config.getSql().getPort(),
                config.getSql().getDatabase(),
                connectTimeout,
                socketTimeout
        );
    }

    @FunctionalInterface
    interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}
