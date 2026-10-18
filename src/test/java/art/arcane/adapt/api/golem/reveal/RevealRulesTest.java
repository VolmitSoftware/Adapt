package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.item.GolemItemKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RevealRulesTest {
  @Test
  void theCrookNodesCoresAndDormantGolemsAreRevealItems() {
    Set<GolemItemKind> revealing = EnumSet.noneOf(GolemItemKind.class);
    for (GolemItemKind kind : GolemItemKind.values()) {
      if (RevealRules.reveals(kind)) {
        revealing.add(kind);
      }
    }

    assertThat(revealing).containsExactlyInAnyOrder(
        GolemItemKind.CROOK, GolemItemKind.NODE, GolemItemKind.BLANK_CORE, GolemItemKind.CORE, GolemItemKind.DORMANT_GOLEM);
  }

  @Test
  void eitherHandHoldingARevealItemMakesTheViewerEligible() {
    assertThat(RevealRules.eligible(GolemItemKind.CROOK, null)).isTrue();
    assertThat(RevealRules.eligible(null, GolemItemKind.NODE)).isTrue();
    assertThat(RevealRules.eligible(GolemItemKind.RUNE, GolemItemKind.CORE)).isTrue();
    assertThat(RevealRules.eligible(GolemItemKind.RUNE, GolemItemKind.SHATTERED_CORE)).isFalse();
    assertThat(RevealRules.eligible(null, null)).isFalse();
  }

  @Test
  void swappingHandsKeepsEligibilityWhileSwappingToAnotherItemDropsIt() {
    assertThat(RevealRules.eligible(GolemItemKind.CROOK, null)).isEqualTo(RevealRules.eligible(null, GolemItemKind.CROOK));
    assertThat(RevealRules.eligible(GolemItemKind.MASTERWORK_HEART, null)).isFalse();
  }

  @Test
  void rangeIsInclusiveAndCompareSquaredDistances() {
    assertThat(RevealRules.withinRange(32D * 32D, 32D)).isTrue();
    assertThat(RevealRules.withinRange(32D * 32D + 0.01D, 32D)).isFalse();
    assertThat(RevealRules.withinRange(0D, 1D)).isTrue();
  }
}
