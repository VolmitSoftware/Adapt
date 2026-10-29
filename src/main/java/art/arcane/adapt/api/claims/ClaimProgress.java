package art.arcane.adapt.api.claims;

import java.util.Objects;

public record ClaimProgress(ClaimBlock block, String blockState, double breaking, double placement) {
  public ClaimProgress {
    Objects.requireNonNull(block);
    Objects.requireNonNull(blockState);
    if (!Double.isFinite(breaking) || !Double.isFinite(placement)
        || breaking < 0 || breaking > 1 || placement < 0 || placement > 1) {
      throw new IllegalArgumentException("Raid progress must be normalized between zero and one");
    }
  }

  public double progress(ClaimAction action) {
    return action == ClaimAction.BREAK ? breaking : placement;
  }

  public ClaimProgress advance(ClaimAction action, int required) {
    double advanced = progress(action) + 1D / required;
    double value = advanced >= 1D - 1E-10 ? 1D : advanced;
    return action == ClaimAction.BREAK
        ? new ClaimProgress(block, blockState, value, placement)
        : new ClaimProgress(block, blockState, breaking, value);
  }
}
