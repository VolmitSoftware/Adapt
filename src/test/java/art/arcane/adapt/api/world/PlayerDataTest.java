package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    @DisplayName("an advancement grant is accepted only once")
    void advancementGrantIsAcceptedOnce() {
        PlayerData data = new PlayerData();

        assertThat(data.ensureGranted("challenge_once")).isTrue();
        assertThat(data.ensureGranted("challenge_once")).isFalse();
        assertThat(data.isGranted("challenge_once")).isTrue();
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

    @Test
    @DisplayName("stat changes notify only the currently bound runtime owner")
    void statChangesNotifyBoundRuntimeOwner() {
        PlayerData data = new PlayerData();
        AdaptPlayer firstOwner = mock(AdaptPlayer.class);
        AdaptPlayer secondOwner = mock(AdaptPlayer.class);

        data.bindRuntimeOwner(firstOwner);
        data.addStat("blocks", 1.0);
        data.bindRuntimeOwner(secondOwner);
        data.unbindRuntimeOwner(firstOwner);
        data.addStat("blocks", 1.0);
        data.unbindRuntimeOwner(secondOwner);
        data.addStat("blocks", 1.0);

        verify(firstOwner).onStatChanged("blocks");
        verify(secondOwner).onStatChanged("blocks");
        verify(firstOwner, never()).onStatChanged("other");
        assertThat(data.getStat("blocks")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("cross-skill recovery is applied once per actual skill switch")
    void crossSkillRecoveryOnlyRunsOnSwitch() {
        PlayerData data = new PlayerData();
        CountingSkillLine axes = new CountingSkillLine("axes");
        CountingSkillLine swords = new CountingSkillLine("swords");
        data.getSkillLines().put("axes", axes);
        data.getSkillLines().put("swords", swords);

        data.resetMonotonyForOtherSkills("axes");
        data.resetMonotonyForOtherSkills("axes");

        assertThat(axes.recoveryCalls).isZero();
        assertThat(swords.recoveryCalls).isEqualTo(1);

        data.resetMonotonyForOtherSkills("swords");

        assertThat(axes.recoveryCalls).isEqualTo(1);
        assertThat(swords.recoveryCalls).isEqualTo(1);
    }

    private static final class CountingSkillLine extends PlayerSkillLine {
        private int recoveryCalls;

        private CountingSkillLine(String name) {
            setLine(name);
        }

        @Override
        public void relaxStalenessForActivitySwitch() {
            recoveryCalls++;
        }
    }
}
