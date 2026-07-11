package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationPairingRulesTest {
  @Test
  void allOneHundredFiveUnorderedPairsResolveSymmetrically() {
    MutationPairingRules rules = new MutationPairingRules();
    MutationType[] mutations = MutationType.values();
    Set<String> keys = new HashSet<>();
    int evaluated = 0;

    for (int first = 0; first < mutations.length; first++) {
      for (int second = first + 1; second < mutations.length; second++) {
        MutationType left = mutations[first];
        MutationType right = mutations[second];
        MutationPairingRules.PairResolution forward = rules.evaluate(left, right);
        MutationPairingRules.PairResolution reverse = rules.evaluate(right, left);
        assertThat(forward).as(left.id() + " + " + right.id()).isEqualTo(reverse);
        assertThat(forward.compatible()).isTrue();
        assertThat(forward.policy()).isNotBlank();
        assertThat(keys.add(left.id() + "+" + right.id())).isTrue();
        evaluated++;
      }
    }

    assertThat(evaluated).isEqualTo(105);
    assertThat(keys).hasSize(105);
  }

  @Test
  void highRiskPairsExposeTheirExclusiveInteractionClaims() {
    MutationPairingRules rules = new MutationPairingRules();

    assertThat(rules.evaluate(MutationType.UMBRAL_ECHO, MutationType.RESONANT_FORMULA).exclusiveClaims())
        .containsExactly(MutationClaim.UTILITY_ECHO);
    assertThat(rules.evaluate(MutationType.TEMPERBOUND, MutationType.MASTERWORK_BOND).exclusiveClaims())
        .containsExactly(MutationClaim.ITEM_PRESERVATION);
    assertThat(rules.evaluate(MutationType.PACKMIND, MutationType.MYCELIAL_NERVE).exclusiveClaims())
        .containsExactly(MutationClaim.COOPERATIVE_LINK);
    assertThat(rules.evaluate(MutationType.LIVING_LATTICE, MutationType.GRAVEBLOOM).exclusiveClaims())
        .containsExactly(MutationClaim.WORLD_STATE);
    assertThat(rules.evaluate(MutationType.DEEPBLOOD, MutationType.GRAVEBLOOM).exclusiveClaims())
        .containsExactly(MutationClaim.RECOVERY);
  }
}
