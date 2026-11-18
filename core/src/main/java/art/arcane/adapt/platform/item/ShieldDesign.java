package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.value.DyeColor;

import java.util.List;

/** The banner face of a shield. {@code baseColor} is null for a shield without a banner. */
public record ShieldDesign(DyeColor baseColor, List<BannerLayer> patterns) {
  public ShieldDesign {
    patterns = List.copyOf(patterns);
  }
}
