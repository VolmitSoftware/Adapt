package art.arcane.adapt.engine.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.engine.xp.XP;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

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
    @DisplayName("the golem roster survives a json round trip")
    void golemRosterSurvivesJsonRoundTrip() {
        PlayerData data = new PlayerData();
        UUID golem = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        data.section(GolemRoster.section()).add(new GolemRoster.Binding(golem, GolemBody.FROST, GolemCoreType.HARVESTER, world, GolemRoster.chunkKey(2, -3), 99L));
        data.section(GolemRoster.section()).raiseSighting(golem, 12L);
        data.section(GolemRoster.section()).transition(golem, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

        PlayerData back = PlayerData.fromJson(data.toJson(false));

        GolemRoster.GolemEntry entry = back.section(GolemRoster.section()).golem(golem).orElseThrow();
        assertThat(entry.body()).isEqualTo(GolemBody.FROST);
        assertThat(entry.core()).isEqualTo(GolemCoreType.HARVESTER);
        assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
        assertThat(entry.world()).isEqualTo(world);
        assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(2, -3));
        assertThat(entry.sighting()).isEqualTo(12L);
    }

    @Test
    @DisplayName("every golem roster field survives a json round trip together")
    void everyGolemRosterFieldSurvivesJsonRoundTripTogether() {
        PlayerData data = new PlayerData();
        GolemRoster roster = data.section(GolemRoster.section());
        UUID packed = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        roster.add(new GolemRoster.Binding(packed, GolemBody.IRON, GolemCoreType.GUARDIAN, world, GolemRoster.chunkKey(4, -7), 31L));
        roster.add(new GolemRoster.Binding(active, GolemBody.COPPER, GolemCoreType.SORTER, world, GolemRoster.chunkKey(-1, 2), 44L));
        roster.markMasterwork(packed, true);
        roster.raiseSighting(packed, 9L);
        roster.stow(packed, GolemRoster.Status.PACKED, "{\"dormant\":1}");
        roster.raiseSighting(active, 3L);
        roster.setServantChannel("LIME");
        roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 2));
        roster.addLattice(new GolemRune(GolemRuneType.EMBER, 3));

        GolemRoster back = PlayerData.fromJson(data.toJson(false)).section(GolemRoster.section());

        GolemRoster.GolemEntry stowed = back.golem(packed).orElseThrow();
        assertThat(stowed.status()).isEqualTo(GolemRoster.Status.PACKED);
        assertThat(stowed.masterwork()).isTrue();
        assertThat(stowed.sighting()).isEqualTo(9L);
        assertThat(stowed.holds("{\"dormant\":1}")).isTrue();
        assertThat(stowed.lastSeen()).isEqualTo(31L);
        GolemRoster.GolemEntry bound = back.golem(active).orElseThrow();
        assertThat(bound.status()).isEqualTo(GolemRoster.Status.ACTIVE);
        assertThat(bound.masterwork()).isFalse();
        assertThat(bound.sighting()).isEqualTo(3L);
        assertThat(bound.core()).isEqualTo(GolemCoreType.SORTER);
        assertThat(back.servantChannel()).contains("LIME");
        assertThat(back.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2), new GolemRune(GolemRuneType.EMBER, 3));
    }

    @Test
    @DisplayName("player data without a roster reads an empty one")
    void missingGolemRosterReadsEmpty() {
        PlayerData legacy = PlayerData.fromJson("{\"wisdom\":3}");
        PlayerData explicitNull = PlayerData.fromJson("{\"golemancy\":null}");

        assertThat(legacy.section(GolemRoster.section())).isNotNull();
        assertThat(legacy.section(GolemRoster.section()).golemCount()).isZero();
        assertThat(explicitNull.section(GolemRoster.section())).isNotNull();
        assertThat(explicitNull.section(GolemRoster.section()).golemCount()).isZero();
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
    @DisplayName("elapsed recovery is checked once per actual skill switch")
    void crossSkillRecoveryOnlyRunsOnSwitch() {
        PlayerData data = new PlayerData();
        CountingSkillLine axes = new CountingSkillLine("adapt:axes");
        CountingSkillLine swords = new CountingSkillLine("adapt:swords");
        data.getSkillLines().put("adapt:axes", axes);
        data.getSkillLines().put("adapt:swords", swords);

        data.noteActivity("adapt:axes");
        data.noteActivity("adapt:axes");

        assertThat(axes.recoveryCalls).isZero();
        assertThat(swords.recoveryCalls).isEqualTo(1);

        data.noteActivity("adapt:swords");

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
        CountingSkillLine axes = new CountingSkillLine("adapt:axes");
        CountingSkillLine swords = new CountingSkillLine("adapt:swords");
        CountingSkillLine crafting = new CountingSkillLine("adapt:crafting");
        axes.inspiredPressure = 40.0D;
        swords.inspiredPressure = 20.0D;
        crafting.inspiredPressure = 30.0D;
        data.getSkillLines().put("adapt:axes", axes);
        data.getSkillLines().put("adapt:swords", swords);
        data.getSkillLines().put("adapt:crafting", crafting);

        data.noteActivity("adapt:axes");

        assertThat(data.getInspiredSkill()).isEqualTo("adapt:crafting");
        long firstAssignment = data.getInspiredAssignedAt();
        assertThat(firstAssignment).isPositive();

        crafting.inspiredPressure = 100.0D;
        data.noteActivity("adapt:swords");

        assertThat(data.getInspiredSkill()).isEqualTo("adapt:crafting");
        assertThat(data.getInspiredAssignedAt()).isEqualTo(firstAssignment);

        axes.inspiredPressure = 40.0D;
        swords.inspiredPressure = 40.0D;
        long expiredAssignment = firstAssignment - 300000L;
        data.setInspiredAssignedAt(expiredAssignment);
        data.noteActivity("adapt:crafting");

        assertThat(data.getInspiredSkill()).isEqualTo("adapt:axes");
        assertThat(data.getInspiredAssignedAt()).isGreaterThan(expiredAssignment);
        assertThat(axes.recoveryCalls).isEqualTo(2);
        assertThat(swords.recoveryCalls).isEqualTo(2);
        assertThat(crafting.recoveryCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("inspired assignment persists and is cleared with xp progression")
    void inspiredAssignmentPersistsAndClears() {
        PlayerData data = new PlayerData();
        data.setInspiredSkill("adapt:crafting");
        data.setInspiredAssignedAt(1234L);

        PlayerData restored = PlayerData.fromJson(data.toJson(false));

        assertThat(restored).isNotNull();
        assertThat(restored.getInspiredSkill()).isEqualTo("adapt:crafting");
        assertThat(restored.getInspiredAssignedAt()).isEqualTo(1234L);

        restored.resetMasterXp();

        assertThat(restored.getInspiredSkill()).isEmpty();
        assertThat(restored.getInspiredAssignedAt()).isZero();
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
        public double recoverStaleness(long now) {
            recoveryCalls++;
            return inspiredPressure;
        }
    }
}
