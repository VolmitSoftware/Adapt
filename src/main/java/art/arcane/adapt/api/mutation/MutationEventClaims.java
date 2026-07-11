package art.arcane.adapt.api.mutation;

import java.util.EnumSet;

public final class MutationEventClaims {
  private final EnumSet<MutationClaim> claims = EnumSet.noneOf(MutationClaim.class);

  public synchronized boolean tryClaim(MutationClaim claim) {
    return claim != null && claims.add(claim);
  }

  public synchronized boolean isClaimed(MutationClaim claim) {
    return claim != null && claims.contains(claim);
  }
}
