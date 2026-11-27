package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * One banner pattern layer: a colour and a banner pattern id such as {@code minecraft:border}.
 *
 * @param color layer colour
 * @param pattern banner pattern id
 */
@ApiStatus.AvailableSince("1.0.0")
public record BannerLayer(DyeColor color, Id pattern) {
  public BannerLayer {
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(pattern, "pattern");
  }
}
