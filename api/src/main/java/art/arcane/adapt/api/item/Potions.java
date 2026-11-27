package art.arcane.adapt.api.item;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Potion stacks for brewing recipes, and the ledger of effects Adapt itself put on a player. */
@ApiStatus.Experimental
public final class Potions {
  private Potions() {
  }

  /** A builder for one potion stack of {@code type}. */
  public static Builder of(Type type) {
    return new Builder(type);
  }

  /** A plain vanilla potion: {@code type} with the base potion {@code potion} (for example {@code minecraft:awkward}). */
  public static ItemRef vanilla(Type type, Id potion) {
    return of(type).setBaseType(potion).build();
  }

  /**
   * Records that Adapt applied the effect type to the player, so a skill reset strips it and leaves potions the player
   * drank alone. Call it after adding an effect directly to the player; component potion helpers record on their own.
   */
  public static void recordApplied(PlayerRef player, Id effect) {
    Engine.bridge().items().recordAppliedEffect(player.uuid(), effect);
  }

  /**
   * The bottle a potion comes in.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Type {
    /** A drinkable potion. */
    REGULAR(Id.minecraft("potion")),
    /** A thrown splash potion. */
    SPLASH(Id.minecraft("splash_potion")),
    /** A thrown lingering potion. */
    LINGERING(Id.minecraft("lingering_potion"));

    private final Id material;

    Type(Id material) {
      this.material = material;
    }

    /** The item id of the bottle. */
    public Id material() {
      return material;
    }
  }

  /** Collects the contents of one potion stack. */
  public static final class Builder {
    private final Type type;
    private final List<EffectInstance> effects = new ArrayList<>();
    private @Nullable String name;
    private @Nullable Rgb color;
    private @Nullable Id baseType;

    private Builder(Type type) {
      this.type = Objects.requireNonNull(type, "type");
    }

    /** The liquid colour; unset keeps the colour the effects give. */
    public Builder setColor(Rgb color) {
      this.color = color;
      return this;
    }

    /** Adds a custom effect. */
    public Builder addEffect(EffectInstance effect) {
      effects.add(Objects.requireNonNull(effect, "effect"));
      return this;
    }

    /** Adds a custom effect from its parts; {@code duration} is in ticks. */
    public Builder addEffect(Id type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
      return addEffect(EffectInstance.of(type, duration, amplifier).withAmbient(ambient).withParticles(particles).withIcon(icon));
    }

    /** The custom item name of the stack. */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    /** The vanilla base potion, for example {@code minecraft:swiftness}. */
    public Builder setBaseType(Id baseType) {
      this.baseType = baseType;
      return this;
    }

    /** A new stack of one potion. Needs a running core. */
    public ItemRef build() {
      ItemRef item = AdaptApi.platform().host().items().create(type.material(), 1);
      item.setPotionContents(PotionContents.of(baseType).withColor(color).withCustomEffects(effects).withName(name));
      return item;
    }
  }
}
