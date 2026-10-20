package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LumberFellingsTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000023a1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000023a2");

  private final LumberFellings fellings = new LumberFellings();
  private final LumberTrees.Tree tree = LumberTrees.survey(LumberTreesTest.oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 64)).tree();

  @Test
  void aStartedFellingIsFoundByItsWorldAndBase() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);

    assertThat(fellings.get(WORLD, 0, 64, 0)).containsSame(felling);
    assertThat(fellings.get(WORLD, 0, 65, 0)).isEmpty();
    assertThat(fellings.get(OTHER, 0, 64, 0)).isEmpty();
    assertThat(fellings.pending()).isTrue();
    assertThat(felling.tree()).isSameAs(tree);
    assertThat(felling.cursor()).isZero();
    assertThat(felling.felled()).isFalse();
  }

  @Test
  void advancingPastEveryPartFellsTheTreeAndSettlingRemembersTheLeaves() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);
    List<LumberTrees.Part> leaves = List.of(new LumberTrees.Part(1, 66, 0, Material.OAK_LEAVES));

    felling.advance(5);
    felling.settle(3_000L, leaves);

    assertThat(felling.felled()).isTrue();
    assertThat(felling.settling()).isTrue();
    assertThat(felling.settleUntil()).isEqualTo(3_000L);
    assertThat(felling.leaves()).isEqualTo(leaves);
  }

  @Test
  void finishingForgetsTheFelling() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);

    fellings.finish(WORLD, felling);

    assertThat(fellings.get(WORLD, 0, 64, 0)).isEmpty();
    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void fellingsNobodyTouchesForAMinuteAreDropped() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);

    felling.touch(30_000L);
    fellings.sweep(89_999L);
    assertThat(fellings.get(WORLD, 0, 64, 0)).isPresent();
    fellings.sweep(90_000L);
    assertThat(fellings.get(WORLD, 0, 64, 0)).isEmpty();
  }

  @Test
  void replantRetriesWaitFiveSeconds() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);
    felling.retryReplant(2_000L);

    assertThat(felling.replantReady(6_999L)).isFalse();
    assertThat(felling.replantReady(7_000L)).isTrue();

    felling.retryReplant(7_000L);

    assertThat(felling.replantReady(11_999L)).isFalse();
    assertThat(felling.replantReady(12_000L)).isTrue();
  }

  @Test
  void replantLifetimeDoesNotExtendWhenANodeKeepsTheStumpOrAnotherRetryRuns() {
    LumberFellings.Felling felling = fellings.start(WORLD, tree, 1_000L);
    felling.retryReplant(2_000L);
    felling.retryReplant(200_000L);
    felling.touch(300_000L);
    fellings.sweep(301_999L);

    assertThat(felling.replantReady(301_999L)).isTrue();
    assertThat(felling.replantReady(302_000L)).isFalse();

    fellings.sweep(302_000L);

    assertThat(fellings.pending()).isFalse();
  }

  @Test
  void forgettingDropsEveryFelling() {
    fellings.start(WORLD, tree, 1_000L);
    fellings.start(OTHER, tree, 1_000L);

    fellings.forget();

    assertThat(fellings.pending()).isFalse();
  }
}
