package art.arcane.adapt.api.potion;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PotionBuilder {

  private final List<EffectInstance> effects = new ArrayList<>();
  private final Type type;

  private String name;
  private Rgb color;
  private Id baseType;

  private PotionBuilder(Type type) {
    this.type = type;
  }

  public static ItemRef vanilla(Type type, Id potion) {
    return of(type)
        .setBaseType(potion)
        .build();
  }

  public static PotionBuilder of(Type type) {
    return new PotionBuilder(type);
  }

  public PotionBuilder setColor(Rgb color) {
    this.color = color;
    return this;
  }

  public PotionBuilder addEffect(EffectInstance effect) {
    effects.add(effect);
    return this;
  }

  public PotionBuilder addEffect(Id type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
    effects.add(new EffectInstance(type, duration, amplifier, ambient, particles, icon));
    return this;
  }

  public PotionBuilder setName(String name) {
    this.name = name;
    return this;
  }

  public PotionBuilder setBaseType(Id baseType) {
    this.baseType = baseType;
    return this;
  }

  public ItemRef build() {
    ItemRef item = AdaptCore.platform().host().items().create(type.getMaterial(), 1);
    item.setPotionContents(new PotionContents(baseType, color, effects, name));
    return item;
  }

  @Getter
  @AllArgsConstructor
  public enum Type {
    REGULAR(Id.minecraft("potion")),
    SPLASH(Id.minecraft("splash_potion")),
    LINGERING(Id.minecraft("lingering_potion"));

    private final Id material;
  }
}
