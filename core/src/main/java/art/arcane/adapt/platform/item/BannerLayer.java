package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** One banner pattern layer: a colour and a banner pattern id such as {@code minecraft:border}. */
public record BannerLayer(DyeColor color, Id pattern) {
  public BannerLayer {
    Objects.requireNonNull(color, "color");
    Objects.requireNonNull(pattern, "pattern");
  }
}
