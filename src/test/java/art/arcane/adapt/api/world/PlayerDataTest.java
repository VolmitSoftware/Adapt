package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.xp.XP;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void unchangedMasterXpReusesItsResolvedLevel() {
        PlayerData data = new PlayerData();
        data.setMasterXp(64D);
        AdaptConfig config = AdaptConfig.get();
        int previousMaximum = config.experienceMaxLevel;

        try (MockedStatic<XP> xp = mockStatic(XP.class)) {
            xp.when(() -> XP.getLevelForXp(64D)).thenReturn(8D, 7D);
            xp.when(() -> XP.getLevelForXp(81D)).thenReturn(9D);

            assertThat(data.getLevel()).isEqualTo(8);
            assertThat(data.getLevel()).isEqualTo(8);
            data.getMaxPower();
            xp.verify(() -> XP.getLevelForXp(64D), times(1));

            data.setMasterXp(81D);
            assertThat(data.getLevel()).isEqualTo(9);
            xp.verify(() -> XP.getLevelForXp(81D), times(1));

            data.setMasterXp(64D);
            config.experienceMaxLevel = previousMaximum - 1;
            assertThat(data.getLevel()).isEqualTo(7);
            xp.verify(() -> XP.getLevelForXp(64D), times(2));
        } finally {
            config.experienceMaxLevel = previousMaximum;
        }
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
    @DisplayName("removed Axes content is pruned from persisted player data")
    void removedAxesContentIsPrunedFromJson() {
        PlayerData data = new PlayerData();
        PlayerSkillLine axes = new PlayerSkillLine();
        axes.setLine("axes");
        axes.getAdaptations().put("axe-orchardist", adaptation("axe-orchardist", 2));
        axes.getAdaptations().put("axe-sap-tap", adaptation("axe-sap-tap", 3));
        axes.getAdaptations().put("axe-timber-mark", adaptation("axe-timber-mark", 4));
        axes.getAdaptations().put("axe-shield-splitter", adaptation("axe-shield-splitter", 1));
        data.getSkillLines().put("axes", axes);
        data.addStat("axe.orchardist.trees-replanted", 11D);
        data.addStat("axe.sap-tap.sap-drawn", 12D);
        data.addStat("axe.timber-mark.marks-felled", 13D);
        data.addStat("axes.blocks.broken", 14D);
        data.ensureGranted("adaptation_axe-orchardist");
        data.ensureGranted("adaptation_axe-sap-tap");
        data.ensureGranted("adaptation_axe-timber-mark");
        data.ensureGranted("challenge_axe_orchardist_500");
        data.ensureGranted("challenge_axe_sap_tap_500");
        data.ensureGranted("challenge_axe_timber_200");
        data.ensureGranted("challenge_axe_timber_40");
        data.ensureGranted("challenge_axe_damage_1k");

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getSkillLines().get("axes").getAdaptations())
            .containsOnlyKeys("axe-shield-splitter");
        assertThat(restored.getUsedPower()).isEqualTo(1);
        assertThat(restored.getStats())
            .hasSize(1)
            .containsEntry("axes.blocks.broken", 14D);
        assertThat(restored.getAdvancements())
            .containsOnly("challenge_axe_damage_1k");
    }

    @Test
    @DisplayName("removed Dowsing content is pruned from persisted player data")
    void removedDowsingContentIsPrunedFromJson() {
        PlayerData data = new PlayerData();
        PlayerSkillLine excavation = new PlayerSkillLine();
        excavation.setLine("excavation");
        excavation.getAdaptations().put("excavation-dowsing", adaptation("excavation-dowsing", 3));
        excavation.getAdaptations().put("excavation-burrow", adaptation("excavation-burrow", 1));
        data.getSkillLines().put("excavation", excavation);
        data.addStat("excavation.dowsing.pockets-found", 12D);
        data.addStat("excavation.blocks.broken", 14D);
        data.ensureGranted("adaptation_excavation-dowsing");
        data.ensureGranted("challenge_excavation_dowsing_200");
        data.ensureGranted("challenge_excavation_burrow_100");

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getSkillLines().get("excavation").getAdaptations())
            .containsOnlyKeys("excavation-burrow");
        assertThat(restored.getUsedPower()).isEqualTo(1);
        assertThat(restored.getStats())
            .hasSize(1)
            .containsEntry("excavation.blocks.broken", 14D);
        assertThat(restored.getAdvancements())
            .containsOnly("challenge_excavation_burrow_100");
    }

    @Test
    @DisplayName("removed Rift Step content is pruned from persisted player data")
    void removedRiftStepContentIsPrunedFromJson() {
        PlayerData data = new PlayerData();
        PlayerSkillLine rift = new PlayerSkillLine();
        rift.setLine("rift");
        rift.getAdaptations().put("rift-step", adaptation("rift-step", 4));
        rift.getAdaptations().put("rift-blink", adaptation("rift-blink", 2));
        data.getSkillLines().put("rift", rift);
        data.addStat("rift.step.saves", 12D);
        data.addStat("rift.blink.blinks", 14D);
        data.ensureGranted("adaptation_rift-step");
        data.ensureGranted("challenge_rift_step_50");
        data.ensureGranted("challenge_rift_step_500");
        data.ensureGranted("challenge_rift_blink_500");

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getSkillLines().get("rift").getAdaptations())
            .containsOnlyKeys("rift-blink");
        assertThat(restored.getUsedPower()).isEqualTo(2);
        assertThat(restored.getStats())
            .hasSize(1)
            .containsEntry("rift.blink.blinks", 14D);
        assertThat(restored.getAdvancements())
            .containsOnly("challenge_rift_blink_500");
    }

    @Test
    @DisplayName("removed Parkour Momentum content is pruned from persisted player data")
    void removedParkourMomentumContentIsPrunedFromJson() {
        PlayerData data = new PlayerData();
        PlayerSkillLine agility = new PlayerSkillLine();
        agility.setLine("agility");
        agility.getAdaptations().put("agility-parkour-momentum", adaptation("agility-parkour-momentum", 3));
        agility.getAdaptations().put("agility-roll-landing", adaptation("agility-roll-landing", 2));
        data.getSkillLines().put("agility", agility);
        data.addStat("agility.parkour-momentum.chained-landings", 501D);
        data.addStat("agility.roll-landing.damage-prevented", 30D);
        data.ensureGranted("adaptation_agility-parkour-momentum");
        data.ensureGranted("challenge_agility_parkour_500");
        data.ensureGranted("challenge_agility_roll_100");

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getSkillLines().get("agility").getAdaptations())
            .containsOnlyKeys("agility-roll-landing");
        assertThat(restored.getUsedPower()).isEqualTo(2);
        assertThat(restored.getStats())
            .hasSize(1)
            .containsEntry("agility.roll-landing.damage-prevented", 30D);
        assertThat(restored.getAdvancements())
            .containsOnly("challenge_agility_roll_100");
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
    @DisplayName("inspired popup defaults are disabled with a five-minute cooldown")
    void inspiredPopupDefaultsAreQuiet() {
        AdaptConfig.XpIntegrity integrity = new AdaptConfig().getXpIntegrity();

        assertThat(integrity.isInspiredPopupEnabled()).isFalse();
        assertThat(integrity.getInspiredCooldownMillis()).isEqualTo(300000L);
    }

    @Test
    @DisplayName("inspired activity stays assigned for the player-wide cooldown")
    void inspiredActivityStaysAssignedForCooldown() {
        PlayerData data = new PlayerData();
        CountingSkillLine axes = new CountingSkillLine("axes");
        CountingSkillLine swords = new CountingSkillLine("swords");
        CountingSkillLine crafting = new CountingSkillLine("crafting");
        axes.inspiredPressure = 40.0D;
        swords.inspiredPressure = 20.0D;
        crafting.inspiredPressure = 30.0D;
        data.getSkillLines().put("axes", axes);
        data.getSkillLines().put("swords", swords);
        data.getSkillLines().put("crafting", crafting);

        data.resetMonotonyForOtherSkills("axes");

        assertThat(data.getInspiredSkill()).isEqualTo("crafting");
        long firstAssignment = data.getInspiredAssignedAt();
        assertThat(firstAssignment).isPositive();

        crafting.inspiredPressure = 100.0D;
        data.resetMonotonyForOtherSkills("swords");

        assertThat(data.getInspiredSkill()).isEqualTo("crafting");
        assertThat(data.getInspiredAssignedAt()).isEqualTo(firstAssignment);

        axes.inspiredPressure = 40.0D;
        swords.inspiredPressure = 40.0D;
        long expiredAssignment = firstAssignment - 300000L;
        data.setInspiredAssignedAt(expiredAssignment);
        data.resetMonotonyForOtherSkills("crafting");

        assertThat(data.getInspiredSkill()).isEqualTo("axes");
        assertThat(data.getInspiredAssignedAt()).isGreaterThan(expiredAssignment);
        assertThat(axes.recoveryCalls).isEqualTo(2);
        assertThat(swords.recoveryCalls).isEqualTo(2);
        assertThat(crafting.recoveryCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("inspired assignment persists and is cleared with xp progression")
    void inspiredAssignmentPersistsAndClears() {
        PlayerData data = new PlayerData();
        data.setInspiredSkill("crafting");
        data.setInspiredAssignedAt(1234L);

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getInspiredSkill()).isEqualTo("crafting");
        assertThat(restored.getInspiredAssignedAt()).isEqualTo(1234L);

        restored.clearXp();

        assertThat(restored.getInspiredSkill()).isEmpty();
        assertThat(restored.getInspiredAssignedAt()).isZero();
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
        assertThat(restored.getInspiredSkill()).isEmpty();
        assertThat(restored.getInspiredAssignedAt()).isZero();
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

    private static PlayerAdaptation adaptation(String id, int level) {
        PlayerAdaptation adaptation = new PlayerAdaptation();
        adaptation.setId(id);
        adaptation.setLevel(level);
        return adaptation;
    }

    private static final class CountingSkillLine extends PlayerSkillLine {
        private int recoveryCalls;
        private double inspiredPressure;

        private CountingSkillLine(String name) {
            setLine(name);
        }

        @Override
        public double relaxStalenessForActivitySwitch() {
            recoveryCalls++;
            return inspiredPressure;
        }
    }
}
