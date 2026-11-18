package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Objects;

/**
 * Potion data of a potion-like item (potion, splash, lingering, tipped arrow). {@code potion} is the base potion
 * type ({@code minecraft:swiftness}), null for none; {@code color} is the custom colour, null for the effect colour;
 * {@code name} is the item's custom name as Adapt markup, written without italics, null for none.
 */
public record PotionContents(Id potion, Rgb color, List<EffectInstance> customEffects, String name) {
  public static final PotionContents EMPTY = new PotionContents(null, null, List.of(), null);

  public PotionContents {
    customEffects = List.copyOf(Objects.requireNonNull(customEffects, "customEffects"));
  }
}
