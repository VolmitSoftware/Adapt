package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.GameVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturesGameRuleTest {
    private static final GameVersion V26_3 = new GameVersion(26, 3, 0);

    @Test
    void snakeCaseRulesPassThroughFrom1_21_11() {
        assertThat(Fixtures.gameRuleCommand(V26_3, "advance_weather", "false")).isEqualTo("gamerule advance_weather false");
        assertThat(Fixtures.gameRuleCommand(new GameVersion(1, 21, 11), "pvp", "true")).isEqualTo("gamerule pvp true");
    }

    @Test
    void olderVersionsUseCamelCaseNames() {
        assertThat(Fixtures.gameRuleCommand(GameVersion.V1_20_1, "advance_weather", "false")).isEqualTo("gamerule doWeatherCycle false");
        assertThat(Fixtures.gameRuleCommand(GameVersion.V1_21_1, "spawn_mobs", "false")).isEqualTo("gamerule doMobSpawning false");
        assertThat(Fixtures.gameRuleCommand(GameVersion.V1_20_1, "natural_health_regeneration", "false"))
                .isEqualTo("gamerule naturalRegeneration false");
        assertThat(Fixtures.gameRuleCommand(GameVersion.V1_21_1, "random_tick_speed", "0")).isEqualTo("gamerule randomTickSpeed 0");
    }

    @Test
    void pvpBeforeTheGameRuleIsTheServerDefault() {
        assertThat(Fixtures.gameRuleCommand(GameVersion.V1_20_1, "pvp", "true")).isNull();
        assertThatThrownBy(() -> Fixtures.gameRuleCommand(GameVersion.V1_20_1, "pvp", "false")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unmappedRulesFailLoudlyOnOlderVersions() {
        assertThatThrownBy(() -> Fixtures.gameRuleCommand(GameVersion.V1_20_1, "fire_spread_radius_around_player", "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
