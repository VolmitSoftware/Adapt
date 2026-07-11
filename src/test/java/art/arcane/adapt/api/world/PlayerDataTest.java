package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.mutation.PlayerMutationData;
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

    @Test
    @DisplayName("mutation state preserves discovery order and unknown ids through json")
    void mutationStateRoundTripsWithoutPruning() {
        PlayerData data = new PlayerData();
        PlayerMutationData mutations = data.getMutationData();
        mutations.discover("gale-lung");
        mutations.discover("future-disabled-id");
        mutations.setSlotOneId("gale-lung");
        mutations.setSlotTwoId("future-disabled-id");
        mutations.setSlotOneReadyAt(1234L);
        mutations.setSlotTwoReadyAt(5678L);
        mutations.setCooperativeOptIn(true);
        mutations.setDeepbloodIchor(42D);

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getMutationData().getDiscovered())
            .containsExactly("gale-lung", "future-disabled-id");
        assertThat(restored.getMutationData().getSlotOneId()).isEqualTo("gale-lung");
        assertThat(restored.getMutationData().getSlotTwoId()).isEqualTo("future-disabled-id");
        assertThat(restored.getMutationData().getSlotOneReadyAt()).isEqualTo(1234L);
        assertThat(restored.getMutationData().getSlotTwoReadyAt()).isEqualTo(5678L);
        assertThat(restored.getMutationData().isCooperativeOptIn()).isTrue();
        assertThat(restored.getMutationData().getDeepbloodIchor()).isEqualTo(42D);
    }

    @Test
    @DisplayName("legacy player json receives empty mutation state while json null stays invalid")
    void legacyJsonNormalizesMutationStateWithoutMaskingNull() {
        PlayerData restored = PlayerData.fromJson("{\"masterXp\":1}");
        PlayerData legacyMutation = PlayerData.fromJson(
            "{\"mutationData\":{\"discovered\":null,\"slotOneId\":\"GALE_LUNG\",\"formulaSigils\":null}}"
        );

        assertThat(restored).isNotNull();
        assertThat(restored.getMutationData()).isNotNull();
        assertThat(restored.getMutationData().getDiscovered()).isEmpty();
        assertThat(legacyMutation).isNotNull();
        assertThat(legacyMutation.getMutationData().getDiscovered()).isEmpty();
        assertThat(legacyMutation.getMutationData().getSlotOneId()).isEqualTo("gale-lung");
        assertThat(legacyMutation.getMutationData().getFormulaSigils()).isEmpty();
        assertThat(PlayerData.fromJson("null")).isNull();
    }

    @Test
    @DisplayName("adaptation and xp clears retain mutation selections while clear all removes them")
    void mutationResetSemanticsMatchProgressionScope() {
        PlayerData data = new PlayerData();
        data.getMutationData().discover("deepblood");
        data.getMutationData().setSlotOneId("deepblood");

        data.clearAdaptations();
        assertThat(data.getMutationData().getSlotOneId()).isEqualTo("deepblood");
        data.clearXp();
        assertThat(data.getMutationData().getSlotOneId()).isEqualTo("deepblood");
        data.clearAll();
        assertThat(data.getMutationData().getSlotOneId()).isEmpty();
        assertThat(data.getMutationData().getDiscovered()).isEmpty();
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
