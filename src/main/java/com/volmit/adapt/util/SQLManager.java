package com.volmit.adapt.util;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SQLManager {

    private static final String TABLE_NAME = "ADAPT_DATA";
    private static final String CREATE_TABLE_QUERY = "CREATE TABLE " + TABLE_NAME + " (UUID char(36) NOT NULL UNIQUE, DATA MEDIUMTEXT NOT NULL)";
    private static final String UPDATE_QUERY = "INSERT INTO " + TABLE_NAME + " (UUID, DATA) VALUES('%s', '%s') ON DUPLICATE KEY UPDATE DATA='%s'";
    private static final String FETCH_QUERY = "SELECT DATA FROM " + TABLE_NAME + " WHERE UUID='%s'";
    private static final String DELETE_QUERY = "DELETE FROM " + TABLE_NAME + " WHERE UUID='%s'";

    private Connection connection;

    public void establishConnection() {
        if (connection != null) {
            closeConnection();
        }

        AdaptConfig config = AdaptConfig.get();
        try {
            connection = DriverManager.getConnection(assembleUrl(config), config.getSql().getUsername(), config.getSql().getPassword());
            if (!connection.isValid(30)) {
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
        AdaptConfig config = AdaptConfig.get();
        if (!connection.getMetaData().getTables(null, null, TABLE_NAME, new String[]{"TABLE"}).next()) {
            connection.createStatement().executeUpdate(CREATE_TABLE_QUERY);
        }
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                handleSQLException("Failed to close the connection to the SQL server!", e);
            }
            connection = null;
        }
    }

    public void updateData(UUID uuid, String data) {
        executeWithRetry(() -> connection.createStatement().executeUpdate(String.format(UPDATE_QUERY, uuid.toString(), data, data)), "Failed to write data to the SQL server!");
    }

    public void delete(UUID uuid) {
        executeWithRetry(() -> connection.createStatement().executeUpdate(String.format(DELETE_QUERY, uuid.toString())), "Failed to delete data from the SQL server!");
    }

    public String fetchData(UUID uuid) {
        try {
            checkAndReestablishConnection();
            ResultSet set = connection.prepareStatement(String.format(FETCH_QUERY, uuid.toString())).executeQuery();
            if (!set.next()) return null;
            return set.getString("DATA");
        } catch (SQLException e) {
            handleSQLException("Failed to read data from the SQL server!", e);
            return null;
        }
    }

    private void executeWithRetry(RunnableWithException action, String errorMessage) {
        try {
            checkAndReestablishConnection();
            action.run();
        } catch (SQLException e) {
            handleSQLException(errorMessage, e);
        }
    }

    private void checkAndReestablishConnection() throws SQLException {
        if (connection == null || !connection.isValid(AdaptConfig.get().getSqlSecondsCheckverify())) { // 30 sec by default
            establishConnection();
        }
    }

    private void handleSQLException(String message, SQLException e) {
        Adapt.error(message);
        Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
    }

    private String assembleUrl(AdaptConfig config) {
        return String.format("jdbc:mysql://%s:%d/%s", config.getSql().getHost(), config.getSql().getPort(), config.getSql().getDatabase());
    }

    @FunctionalInterface
    interface RunnableWithException {
        void run() throws SQLException;
    }
}