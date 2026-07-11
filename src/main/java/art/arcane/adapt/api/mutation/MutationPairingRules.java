package art.arcane.adapt.api.mutation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class MutationPairingRules {
  public PairResolution evaluate(MutationType first, MutationType second) {
    if (first == null || second == null) {
      return new PairResolution(false, Set.of(), "Both Mutation slots must resolve to known catalog entries");
    }
    if (first == second) {
      return new PairResolution(false, Set.of(), "The same Mutation cannot occupy both slots");
    }

    EnumSet<MutationClaim> claims = EnumSet.noneOf(MutationClaim.class);
    String policy = "Compatible through the shared two-slot event policy";
    if (matches(first, second, MutationType.UMBRAL_ECHO, MutationType.RESONANT_FORMULA)) {
      claims.add(MutationClaim.UTILITY_ECHO);
      policy = "Only the first legal utility Echo may be scheduled";
    } else if (matches(first, second, MutationType.TEMPERBOUND, MutationType.MASTERWORK_BOND)) {
      claims.add(MutationClaim.ITEM_PRESERVATION);
      policy = "Only one item-preservation result may apply to a durability event";
    } else if (matches(first, second, MutationType.PACKMIND, MutationType.MYCELIAL_NERVE)) {
      claims.add(MutationClaim.COOPERATIVE_LINK);
      policy = "Every recipient consents independently and propagation never chains";
    } else if (matches(first, second, MutationType.LIVING_LATTICE, MutationType.GRAVEBLOOM)) {
      claims.add(MutationClaim.WORLD_STATE);
      policy = "Temporary world state remains separately owned and bounded";
    } else if (matches(first, second, MutationType.GALE_LUNG, MutationType.BASTION_SPINE)) {
      claims.add(MutationClaim.MOVEMENT);
      claims.add(MutationClaim.POSTURE);
      policy = "The most recent deliberate movement or posture action owns the result";
    } else if (matches(first, second, MutationType.DEEPBLOOD, MutationType.GRAVEBLOOM)) {
      claims.add(MutationClaim.RECOVERY);
      policy = "Recovery evaluates once in deterministic slot order";
    } else if (matches(first, second, MutationType.PARADOX_SCAR, MutationType.UMBRAL_ECHO)) {
      claims.add(MutationClaim.MOVEMENT);
      claims.add(MutationClaim.UTILITY_ECHO);
      policy = "One authoritative movement destination is resolved before control Echoes";
    }
    return new PairResolution(true, claims, policy);
  }

  private boolean matches(
      MutationType first,
      MutationType second,
      MutationType expectedFirst,
      MutationType expectedSecond
  ) {
    return (first == expectedFirst && second == expectedSecond)
        || (first == expectedSecond && second == expectedFirst);
  }

  public record PairResolution(boolean compatible, Set<MutationClaim> exclusiveClaims, String policy) {
    public PairResolution {
      if (exclusiveClaims == null || exclusiveClaims.isEmpty()) {
        exclusiveClaims = Collections.emptySet();
      } else {
        exclusiveClaims = Collections.unmodifiableSet(EnumSet.copyOf(exclusiveClaims));
      }
      policy = policy == null ? "" : policy;
    }
  }
}
