package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.io.Json;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemRosterServantsTest {
  @Test
  void theServantChannelStartsEmptyAndCanBeSetAndCleared() {
    GolemRoster roster = new GolemRoster();

    assertThat(roster.servantChannel()).isEmpty();
    roster.setServantChannel(DyeColor.LIME);
    assertThat(roster.servantChannel()).contains(DyeColor.LIME);
    roster.setServantChannel(null);
    assertThat(roster.servantChannel()).isEmpty();
  }

  @Test
  void theLatticeHoldsOneRunePerAspectInSlotOrder() {
    GolemRoster roster = new GolemRoster();

    assertThat(roster.lattice()).isEmpty();
    assertThat(roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 2))).isTrue();
    assertThat(roster.addLattice(new GolemRune(GolemRuneType.EMBER, 1))).isTrue();
    assertThat(roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 3))).isFalse();

    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2), new GolemRune(GolemRuneType.EMBER, 1));
  }

  @Test
  void removingNeedsTheSameRuneAtThatSlotSoASecondTakeFails() {
    GolemRoster roster = new GolemRoster();
    roster.addLattice(new GolemRune(GolemRuneType.SWIFTNESS, 1));
    roster.addLattice(new GolemRune(GolemRuneType.BULWARK, 3));

    assertThat(roster.removeLattice(0, new GolemRune(GolemRuneType.BULWARK, 3))).isFalse();
    assertThat(roster.removeLattice(1, new GolemRune(GolemRuneType.BULWARK, 2))).isFalse();
    assertThat(roster.removeLattice(1, new GolemRune(GolemRuneType.BULWARK, 3))).isTrue();
    assertThat(roster.removeLattice(1, new GolemRune(GolemRuneType.BULWARK, 3))).isFalse();
    assertThat(roster.removeLattice(5, new GolemRune(GolemRuneType.SWIFTNESS, 1))).isFalse();
    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.SWIFTNESS, 1));
  }

  @Test
  void theLatticeAndChannelSurviveGson() {
    GolemRoster roster = new GolemRoster();
    roster.setServantChannel(DyeColor.PURPLE);
    roster.addLattice(new GolemRune(GolemRuneType.EMBER, 2));
    roster.addLattice(new GolemRune(GolemRuneType.SWIFTNESS, 3));

    GolemRoster back = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    assertThat(back.servantChannel()).contains(DyeColor.PURPLE);
    assertThat(back.lattice()).containsExactly(new GolemRune(GolemRuneType.EMBER, 2), new GolemRune(GolemRuneType.SWIFTNESS, 3));
  }

  @Test
  void unreadableStoredRunesAndChannelsAreSkipped() {
    GolemRoster back = Json.fromJson("{\"servantChannel\":\"MAUVE\",\"lattice\":[{\"aspect\":\"might\",\"tier\":2},"
        + "{\"aspect\":\"glitter\",\"tier\":1},{\"aspect\":\"ember\",\"tier\":7},null]}", GolemRoster.class);

    assertThat(back.servantChannel()).isEmpty();
    assertThat(back.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
  }

  @Test
  void anOlderRosterWithoutServantDataReadsAsEmpty() {
    GolemRoster back = Json.fromJson("{\"golems\":{},\"nodes\":{}}", GolemRoster.class);

    assertThat(back.servantChannel()).isEmpty();
    assertThat(back.lattice()).isEmpty();
    assertThat(back.addLattice(new GolemRune(GolemRuneType.MIGHT, 1))).isTrue();
  }
}
