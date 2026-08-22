package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationRuntimePolicyTest {
  @Test
  void everyMutationHasBoundedWork() {
    for (MutationType type : MutationType.values()) {
      assertThat(MutationRuntimePolicy.maxEntities(type)).as("%s entity ceiling", type).isBetween(1, 16);
      assertThat(MutationRuntimePolicy.maxBlocks(type)).as("%s block ceiling", type).isBetween(1, 16);
    }
  }

  @Test
  void copiedDurationsNeverExceedSourceOrCeiling() {
    assertThat(MutationRuntimePolicy.copiedDuration(200, 0.5D, 400)).isEqualTo(100);
    assertThat(MutationRuntimePolicy.copiedDuration(200, 2D, 80)).isEqualTo(80);
    assertThat(MutationRuntimePolicy.copiedDuration(0, 0.5D, 80)).isZero();
  }

  @Test
  void anglesWrapIntoStableBuckets() {
    assertThat(MutationRuntimePolicy.angleBucket(-1D, 45)).isEqualTo(7);
    assertThat(MutationRuntimePolicy.angleBucket(0D, 45)).isZero();
    assertThat(MutationRuntimePolicy.angleBucket(359.99D, 45)).isEqualTo(7);
    assertThat(MutationRuntimePolicy.angleBucket(360D, 45)).isZero();
  }

  @Test
  void deepbloodDecayUsesARealHalfLife() {
    assertThat(MutationRuntimePolicy.decayHalfLife(100D, 300_000L, 300_000L)).isEqualTo(50D);
    assertThat(MutationRuntimePolicy.decayHalfLife(100D, 600_000L, 300_000L)).isEqualTo(25D);
    assertThat(MutationRuntimePolicy.decayHalfLife(100D, -1L, 300_000L)).isEqualTo(100D);
  }

  @Test
  void formulaOnlyAcceptsUtilityEchoClaims() {
    for (MutationClaim claim : MutationClaim.values()) {
      assertThat(MutationRuntimePolicy.canFormulaEcho(claim)).isEqualTo(claim == MutationClaim.UTILITY_ECHO);
    }
  }
}
