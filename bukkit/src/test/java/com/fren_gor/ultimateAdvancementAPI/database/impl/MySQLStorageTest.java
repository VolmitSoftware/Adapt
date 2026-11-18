package com.fren_gor.ultimateAdvancementAPI.database.impl;

import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.fren_gor.ultimateAdvancementAPI.exceptions.UserNotRegisteredException;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementKey;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class MySQLStorageTest {
    private static final List<String> OTHER_PLUGIN_TABLES = List.of("Teams", "Players", "Advancements", "Unredeemed");

    private final JdbcDataSource dataSource = new JdbcDataSource();
    private MySQL database;
    private MockedConstruction<AdvancementKey> keys;

    @BeforeEach
    void openDatabase() throws Exception {
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        database = mock(MySQL.class, CALLS_REAL_METHODS);
        keys = mockConstruction(AdvancementKey.class, (key, context) -> {
            when(key.getNamespace()).thenReturn((String) context.arguments().get(0));
            when(key.getKey()).thenReturn((String) context.arguments().get(1));
        });
        setField("dataSource", dataSource);
        setField("logger", Logger.getLogger(MySQLStorageTest.class.getName()));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : OTHER_PLUGIN_TABLES) {
                statement.execute("CREATE TABLE `" + table + "` (`Owner` VARCHAR(32) PRIMARY KEY)");
                statement.execute("INSERT INTO `" + table + "` VALUES ('other-plugin')");
            }
        }
        database.setUp();
        database.setUp();
    }

    @AfterEach
    void closeDatabase() throws Exception {
        try (MockedConstruction<AdvancementKey> ignored = keys;
             Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : OTHER_PLUGIN_TABLES) {
                try (ResultSet rows = statement.executeQuery("SELECT `Owner` FROM `" + table + "`")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("other-plugin");
                    assertThat(rows.next()).isFalse();
                }
            }
            statement.execute("SHUTDOWN");
        }
    }

    @Test
    void playerAndAdvancementOperationsUseOnlyAdaptTables() throws Exception {
        UUID player = UUID.randomUUID();
        Map.Entry<TeamProgression, Boolean> created = database.loadOrRegisterPlayer(player, "Explorer");
        int teamId = created.getKey().getTeamId();
        assertThat(created.getValue()).isTrue();
        assertThat(database.getTeamId(player)).isEqualTo(teamId);
        assertThat(database.getTeamMembers(teamId)).containsExactly(player);
        assertThat(database.loadUUID(player).getMembersCopy()).containsExactly(player);
        assertThat(database.loadOrRegisterPlayer(player, "Explorer").getValue()).isFalse();
        assertThat(database.getPlayersByName("Explorer")).containsExactly(player);
        assertThat(database.getPlayerName(player)).isEqualTo("Explorer");
        database.updatePlayerName(player, "Builder");
        assertThat(database.getPlayerName(player)).isEqualTo("Builder");
        assertThat(database.getPlayersByName("Explorer")).isEmpty();

        AdvancementKey key = new AdvancementKey("adapt", "mining");
        database.updateAdvancement(key, teamId, 2);
        database.updateAdvancement(key, teamId, 3);
        assertThat(database.getTeamAdvancements(teamId).entrySet())
                .extracting(entry -> entry.getKey().getNamespace(), entry -> entry.getKey().getKey(), Map.Entry::getValue)
                .containsExactly(tuple("adapt", "mining", 3));
        database.setUnredeemed(key, true, teamId);
        database.setUnredeemed(key, false, teamId);
        assertThat(database.isUnredeemed(key, teamId)).isTrue();
        assertThat(database.getUnredeemed(teamId))
                .extracting(entry -> entry.getKey().getNamespace(), entry -> entry.getKey().getKey(), Map.Entry::getValue)
                .containsExactly(tuple("adapt", "mining", true));
        database.unsetUnredeemed(key, teamId);
        assertThat(database.isUnredeemed(key, teamId)).isFalse();
        database.setUnredeemed(key, false, teamId);
        database.unsetUnredeemed(database.getUnredeemed(teamId), teamId);
        assertThat(database.getUnredeemed(teamId)).isEmpty();
        database.setUnredeemed(key, true, teamId);
        database.updateAdvancement(key, teamId, 0);
        assertThat(database.getTeamAdvancements(teamId)).isEmpty();
        assertThat(database.getUnredeemed(teamId)).isEmpty();
    }

    @Test
    void teamMovesAndCleanupCascadeOnlyThroughAdaptTables() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        int firstTeam = database.loadOrRegisterPlayer(first, "Explorer").getKey().getTeamId();
        int secondTeam = database.loadOrRegisterPlayer(second, "Builder").getKey().getTeamId();
        AdvancementKey key = new AdvancementKey("adapt", "mining");
        database.updateAdvancement(key, firstTeam, 5);
        database.setUnredeemed(key, true, firstTeam);
        database.movePlayer(second, firstTeam);
        assertThat(database.getTeamMembers(firstTeam)).containsExactlyInAnyOrder(first, second);
        database.clearUpTeams();
        assertThat(database.getTeamMembers(secondTeam)).isEmpty();

        TeamProgression separated = database.movePlayerInNewTeam(second);
        assertThat(separated.getTeamId()).isNotEqualTo(firstTeam);
        assertThat(database.getTeamMembers(firstTeam)).containsExactly(first);
        assertThat(database.getTeamMembers(separated.getTeamId())).containsExactly(second);
        database.unregisterPlayer(first);
        database.clearUpTeams();
        assertThat(database.getTeamAdvancements(firstTeam)).isEmpty();
        assertThat(database.getUnredeemed(firstTeam)).isEmpty();
        assertThat(database.getTeamId(second)).isEqualTo(separated.getTeamId());
        assertThatThrownBy(() -> database.getTeamId(first)).isInstanceOf(UserNotRegisteredException.class);
        assertThatThrownBy(() -> database.loadUUID(first)).isInstanceOf(UserNotRegisteredException.class);
        assertThatThrownBy(() -> database.getPlayerName(first)).isInstanceOf(UserNotRegisteredException.class);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MySQL.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(database, value);
    }
}
