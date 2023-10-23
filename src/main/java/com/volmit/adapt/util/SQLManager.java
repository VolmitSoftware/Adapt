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
        AdaptConfig config = AdaptConfig.get();
        try {
            connection = DriverManager.getConnection(assembleUrl(config), config.getSql().getUsername(), config.getSql().getPassword());
            if (!connection.isValid(30)) {
                Adapt.error("Timeout while trying to establish a connection to the SQL server!");
                connection.close();
                connection = null;
            } else {
                Adapt.info(String.format("Connected to SQL Database \"%s\" at %s:%d.", config.getSql().getDatabase(), config.getSql().getHost(), config.getSql().getPort()));
                if (!connection.getMetaData().getTables(null, null, TABLE_NAME, new String[]{"TABLE"}).next()) {
                    Adapt.info("\tAdapt Table does not exist, creating...");
                    connection.createStatement().executeUpdate(CREATE_TABLE_QUERY);
                    Adapt.info("\tCreated Table \"" + TABLE_NAME + "\" on database " + config.getSql().getDatabase() + ".");
                }
            }
        } catch (SQLException e) {
            Adapt.error("Failed to establish a connection to the SQL server!");
            Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            connection = null;
        }
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                Adapt.error("Failed to close the connection to the SQL server!");
                Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }
        }
    }

    public void updateData(UUID uuid, String data) {
        J.a(() -> {
            try {
                connection.createStatement().executeUpdate(String.format(UPDATE_QUERY, uuid.toString(), data, data));
            } catch (SQLException e) {
                Adapt.error("Failed to write data to the SQL server!");
                Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }
        });
    }

    public void delete(UUID uuid) {
        J.a(() -> {
            try {
                connection.createStatement().executeUpdate(String.format(DELETE_QUERY, uuid.toString()));
            } catch (SQLException e) {
                Adapt.error("Failed to delete data from the SQL server!");
                Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            }
        });
    }

    public String fetchData(UUID uuid) {
        try {
            ResultSet set = connection.prepareStatement(String.format(FETCH_QUERY, uuid.toString())).executeQuery();
            if (!set.next())
                return null;
            return set.getString("DATA");
        } catch (SQLException e) {
            Adapt.error("Failed to read data from the SQL server!");
            Adapt.error("\t" + e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            return null;
        }
    }

    public boolean useSql() {
        return AdaptConfig.get().isUseSql() && connection != null;
    }

    private String assembleUrl(AdaptConfig config) {
        return String.format("jdbc:mysql://%s:%d/%s", config.getSql().getHost(), config.getSql().getPort(), config.getSql().getDatabase());
    }
}