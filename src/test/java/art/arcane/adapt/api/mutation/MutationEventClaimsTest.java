package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationEventClaimsTest {
  @Test
  void everyClaimCanBeConsumedOnlyOncePerEvent() {
    MutationEventClaims claims = new MutationEventClaims();

    for (MutationClaim claim : MutationClaim.values()) {
      assertThat(claims.tryClaim(claim)).as(claim.name()).isTrue();
      assertThat(claims.tryClaim(claim)).as(claim.name()).isFalse();
      assertThat(claims.isClaimed(claim)).as(claim.name()).isTrue();
    }
  }

  @Test
  void newEventsDoNotShareClaims() {
    MutationEventClaims first = new MutationEventClaims();
    MutationEventClaims second = new MutationEventClaims();

    assertThat(first.tryClaim(MutationClaim.UTILITY_ECHO)).isTrue();
    assertThat(second.tryClaim(MutationClaim.UTILITY_ECHO)).isTrue();
  }
}
