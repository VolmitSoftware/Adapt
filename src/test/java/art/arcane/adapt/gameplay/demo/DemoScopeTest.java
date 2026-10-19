package art.arcane.adapt.gameplay.demo;

import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import org.bukkit.DyeColor;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoScopeTest {
    private static final BoundingBox PLATE = new BoundingBox(-15, 40, -60, 16, 73, 11);

    @Test
    void qaPlayersOwnDemoStateAndOthersDoNot() {
        assertThat(DemoScope.ownsDemoState("AQAClient262")).isTrue();
        assertThat(DemoScope.ownsDemoState("AQAOpponent")).isTrue();
        assertThat(DemoScope.ownsDemoState("Steve")).isFalse();
        assertThat(DemoScope.ownsDemoState("aqaClient")).isFalse();
        assertThat(DemoScope.ownsDemoState(null)).isFalse();
    }

    @Test
    void nodesInsideThePlateBoxAreInScope() {
        assertThat(DemoScope.contains(PLATE, 0, 56, 0)).isTrue();
        assertThat(DemoScope.contains(PLATE, -15, 40, -60)).isTrue();
        assertThat(DemoScope.contains(PLATE, 15, 72, 10)).isTrue();
    }

    @Test
    void nodesBesideOrBeyondThePlateAreOutOfScope() {
        assertThat(DemoScope.contains(PLATE, 16, 56, 0)).isFalse();
        assertThat(DemoScope.contains(PLATE, -16, 56, 0)).isFalse();
        assertThat(DemoScope.contains(PLATE, 0, 56, -61)).isFalse();
        assertThat(DemoScope.contains(PLATE, 0, 56, 11)).isFalse();
        assertThat(DemoScope.contains(PLATE, 0, 39, 0)).isFalse();
        assertThat(DemoScope.contains(PLATE, 0, 73, 0)).isFalse();
    }

    @Test
    void theSearchSphereReachesEveryCornerOfTheBox() {
        double radius = DemoScope.searchRadius(PLATE);
        double cornerX = PLATE.getMaxX() - PLATE.getCenterX();
        double cornerY = PLATE.getMaxY() - PLATE.getCenterY();
        double cornerZ = PLATE.getMaxZ() - PLATE.getCenterZ();
        assertThat(radius).isEqualTo(Math.sqrt(cornerX * cornerX + cornerY * cornerY + cornerZ * cornerZ));
    }

    @Test
    void clearingARosterDropsItsLatticeRunesAndServantChannel() {
        GolemRoster roster = new GolemRoster();
        roster.addLattice(new GolemRune(GolemRuneType.SWIFTNESS, 1));
        roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 2));
        roster.setServantChannel(DyeColor.BLUE);

        DemoScope.clearRoster(roster);

        assertThat(roster.lattice()).isEmpty();
        assertThat(roster.servantChannel()).isEmpty();
    }

    @Test
    void resettingStoredPreferencesTouchesOnlyTheNamedAdaptationsThatHoldValues() {
        PlayerPreferenceData data = new PlayerPreferenceData();
        data.set("golem-gatherer-core", "absorb-orbs", "OFF");
        data.set("agility-wall-jump", "sound", "OFF");

        List<String> reset = DemoScope.resetStored(data, List.of("golem-gatherer-core", "golem-guardian-core"));

        assertThat(reset).containsExactly("golem-gatherer-core");
        assertThat(data.get("golem-gatherer-core", "absorb-orbs")).isNull();
        assertThat(data.get("agility-wall-jump", "sound")).isEqualTo("OFF");
    }
}
