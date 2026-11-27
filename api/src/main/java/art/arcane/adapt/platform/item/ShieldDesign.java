package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.value.DyeColor;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The banner face of a shield. {@code baseColor} is null for a shield without a banner.
 *
 * @param baseColor banner base colour, or null
 * @param patterns pattern layers, bottom first
 */
@ApiStatus.AvailableSince("1.0.0")
public record ShieldDesign(DyeColor baseColor, List<BannerLayer> patterns) {
  public ShieldDesign {
    patterns = List.copyOf(patterns);
  }
}
