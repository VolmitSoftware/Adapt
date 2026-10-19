package art.arcane.adapt.api.golem;

import java.util.Objects;

public record GolemRune(GolemRuneType aspect, int tier) {
  public GolemRune {
    Objects.requireNonNull(aspect);
    if (!GolemRuneType.isTier(tier)) {
      throw new IllegalArgumentException("Rune tier must be " + GolemRuneType.MIN_TIER + " to " + GolemRuneType.MAX_TIER + ", found " + tier);
    }
  }
}
