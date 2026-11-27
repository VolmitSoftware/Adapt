package art.arcane.adapt.platform.item;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * Potion data of a potion-like item (potion, splash, lingering, tipped arrow). Start from {@link #of} or
 * {@link #EMPTY} and fill the rest with the {@code with} methods; each returns a new value.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class PotionContents {
  /** No potion, colour, effects or name. */
  public static final PotionContents EMPTY = new PotionContents(null, null, List.of(), null);

  private final Id potion;
  private final Rgb color;
  private final List<EffectInstance> customEffects;
  private final String name;

  private PotionContents(Id potion, Rgb color, List<EffectInstance> customEffects, String name) {
    this.potion = potion;
    this.color = color;
    this.customEffects = List.copyOf(Objects.requireNonNull(customEffects, "customEffects"));
    this.name = name;
  }

  /** Contents of base potion type {@code potion}, such as {@code minecraft:swiftness}; null for none. */
  public static PotionContents of(Id potion) {
    return new PotionContents(potion, null, List.of(), null);
  }

  /** These contents with another base potion type, or null for none. */
  public PotionContents withPotion(Id newPotion) {
    return new PotionContents(newPotion, color, customEffects, name);
  }

  /** These contents with a custom colour, or null for the effect colour. */
  public PotionContents withColor(Rgb newColor) {
    return new PotionContents(potion, newColor, customEffects, name);
  }

  /** These contents with other effects on top of the base potion. */
  public PotionContents withCustomEffects(List<EffectInstance> newEffects) {
    return new PotionContents(potion, color, newEffects, name);
  }

  /** These contents with a custom item name in Adapt markup, written without italics, or null for none. */
  public PotionContents withName(String newName) {
    return new PotionContents(potion, color, customEffects, newName);
  }

  /** Base potion type, or null. */
  public Id potion() {
    return potion;
  }

  /** Custom colour, or null for the effect colour. */
  public Rgb color() {
    return color;
  }

  /** Effects added on top of the base potion; never null. */
  public List<EffectInstance> customEffects() {
    return customEffects;
  }

  /** The item's custom name as Adapt markup, or null for none. */
  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PotionContents contents && Objects.equals(potion, contents.potion)
        && Objects.equals(color, contents.color) && customEffects.equals(contents.customEffects)
        && Objects.equals(name, contents.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(potion, color, customEffects, name);
  }

  @Override
  public String toString() {
    return "PotionContents[potion=" + potion + ", color=" + color + ", customEffects=" + customEffects + ", name="
        + name + "]";
  }
}
