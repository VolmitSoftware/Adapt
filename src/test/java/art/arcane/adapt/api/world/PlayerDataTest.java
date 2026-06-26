package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerDataTest extends AdaptTestBase {

    @Test
    @DisplayName("addStat accumulates by sum")
    void addStatAccumulates() {
        PlayerData d = new PlayerData();
        d.addStat("blocks", 3.0);
        d.addStat("blocks", 4.0);
        assertThat(d.getStat("blocks")).isEqualTo(7.0);
    }

    @Test
    @DisplayName("an absent stat reads as zero")
    void absentStatReadsZero() {
        assertThat(new PlayerData().getStat("nope")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("stats survive a json round trip")
    void statsSurviveJsonRoundTrip() {
        PlayerData d = new PlayerData();
        d.addStat("mined", 10.0);
        d.addStat("crafted", 2.5);
        PlayerData back = PlayerData.fromJson(d.toJson(false));
        assertThat(back.getStat("mined")).isEqualTo(10.0);
        assertThat(back.getStat("crafted")).isEqualTo(2.5);
    }

    @Test
    @DisplayName("globalXPMultiplier registers a multiplier")
    void globalMultiplierIsRegistered() {
        PlayerData d = new PlayerData();
        int before = d.getMultipliers().size();
        d.globalXPMultiplier(0.5, 60000);
        assertThat(d.getMultipliers().size()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("granting master xp raises the player level")
    void masterXpRaisesLevel() {
        PlayerData d = new PlayerData();
        int start = d.getLevel();
        d.giveMasterXp(250000.0);
        assertThat(d.getLevel()).isGreaterThanOrEqualTo(start);
        assertThat(d.getMasterXp()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("clearStats empties the stat map")
    void clearStatsEmpties() {
        PlayerData d = new PlayerData();
        d.addStat("a", 1.0);
        d.clearStats();
        assertThat(d.getStat("a")).isEqualTo(0.0);
    }
}
