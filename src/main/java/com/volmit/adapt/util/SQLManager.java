package com.volmit.adapt.util;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SQLManager {

    private static final String TABLE_NAME = "ADAPT_DATA";
    private static final String CHECK_TABLE_QUERY = "SELECT 1 FROM " + TABLE_NAME + " LIMIT 1";
    private static final String CREATE_TABLE_QUERY =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    "UUID char(36) NOT NULL UNIQUE, " +
                    "DATA MEDIUMTEXT NOT NULL, " +
                    "TIME BIGINT NOT NULL)";
    private static final String ALTER_TABLE_QUERY =
            "ALTER TABLE " + TABLE_NAME + " " +
                    "ADD COLUMN TIME BIGINT NOT NULL";
    private static final String UPDATE_QUERY =
            "INSERT INTO " + TABLE_NAME + " (UUID, DATA, TIME) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE DATA=?, TIME=?";
    private static final String UPDATE_TIME_QUERY =
            "UPDATE " + TABLE_NAME + " SET TIME=? WHERE UUID=?";
    private static final String FETCH_QUERY = "SELECT DATA FROM " + TABLE_NAME + " WHERE UUID=?";
    private static final String FETCH_TIME_QUERY = "SELECT TIME FROM " + TABLE_NAME + " WHERE UUID=?";
    private static final String DELETE_QUERY = "DELETE FROM " + TABLE_NAME + " WHERE UUID=?";

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
        boolean tableExists;
        try (PreparedStatement stmt = connection.prepareStatement(CHECK_TABLE_QUERY)) {
            stmt.executeQuery();
            tableExists = true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146) {
                tableExists = false;
            } else {
                throw e;
            }
        }

        if (!tableExists) {
            executeUpdate(CREATE_TABLE_QUERY, "Failed to create table!");
        } else {
            // Check if the TIME column exists
            boolean timeColumnExists;
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, TABLE_NAME, "TIME")) {
                timeColumnExists = rs.next();
            }

            if (!timeColumnExists) {
                executeUpdate(ALTER_TABLE_QUERY, "Failed to add TIME column!");
            }
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
        long time = System.currentTimeMillis();
        if (data.equals("null") || data.isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty!");
        }
        executeUpdate(UPDATE_QUERY, "Failed to write data to the SQL server!", uuid.toString(), data, time, data, time);
    }

    public void updateTime(UUID uuid, long time) {
        executeUpdate(UPDATE_TIME_QUERY, "Failed to update time for UUID in the SQL server!", time, uuid.toString());
    }

    public void delete(UUID uuid) {
        executeUpdate(DELETE_QUERY, "Failed to delete data from the SQL server!", uuid.toString());
    }

    public String fetchData(UUID uuid) {
        try {
            checkAndReestablishConnection();
            try (PreparedStatement stmt = connection.prepareStatement(FETCH_QUERY)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet set = stmt.executeQuery()) {
                    if (!set.next()) return null;
                    return set.getString("DATA");
                }
            }
        } catch (SQLException e) {
            handleSQLException("Failed to read data from the SQL server!", e);
            return null;
        }
    }

    public Long fetchTime(UUID uuid) {
        try {
            checkAndReestablishConnection();
            try (PreparedStatement stmt = connection.prepareStatement(FETCH_TIME_QUERY)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet set = stmt.executeQuery()) {
                    if (!set.next()) return null;
                    return set.getLong("TIME");
                }
            }
        } catch (SQLException e) {
            handleSQLException("Failed to read time from the SQL server!", e);
            return null;
        }
    }

    private void executeUpdate(String query, String errorMessage, Object... params) {
        try {
            checkAndReestablishConnection();
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            }
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
}