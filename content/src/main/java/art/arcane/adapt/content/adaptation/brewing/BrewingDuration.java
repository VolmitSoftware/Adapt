package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Lengthens freshly brewed potions: every lasting effect becomes {@code flatTicks + duration * multiplier}. */
public final class BrewingDuration {
  private static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = new DecimalFormat("#.##");

  static {
    ATTRIBUTE_MODIFIER_FORMAT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
  }

  private BrewingDuration() {
  }

  /** Extends every potion in the brew results in place and returns how many were extended. */
  public static int extendAll(List<ItemRef> results, double flatTicks, double multiplier, boolean customLore) {
    int extended = 0;
    for (int i = 0; i < results.size(); i++) {
      ItemRef is = results.get(i);
      if (is == null || is.isEmpty()) {
        continue;
      }
      Optional<PotionContents> p = is.potionContents();
      if (p.isEmpty()) {
        continue;
      }

      if (extend(is, p.get(), flatTicks, multiplier, customLore)) {
        extended++;
      }
      results.set(i, is);
    }
    return extended;
  }

  public static boolean extend(ItemRef is, PotionContents p, double flatTicks, double multiplier, boolean customLore) {
    Id baseType = p.potion();
    if (baseType == null) {
      return false;
    }

    RegistryLookup registries = AdaptApi.platform().host().registries();
    List<EffectInstance> effects = registries.potionEffects(baseType);
    if (effects.stream()
        .map(EffectInstance::type)
        .allMatch(registries::isInstantEffect))
      return false;

    List<EffectInstance> customEffects = new ArrayList<>();
    for (final EffectInstance effect : effects) {
      if (registries.isInstantEffect(effect.type())) {
        addCustomEffect(customEffects, effect);
        continue;
      }

      addCustomEffect(customEffects, EffectInstance.of(
          effect.type(),
          (int) (flatTicks + (effect.durationTicks() * multiplier)),
          effect.amplifier()
      ));
    }

    is.setPotionContents(PotionContents.of(p.potion()).withColor(p.color()).withCustomEffects(customEffects).withName(p.name()));
    is.hideTooltip(TooltipPart.POTION_EFFECTS);

    if (customLore) {
      List<Map<String, Object>> lore = new ArrayList<>();
      List<EffectModifier> modifiers = new ArrayList<>();
      for (EffectInstance effect : customEffects) {
        Id type = effect.type();
        Map<String, Object> name = translatable("effect." + type.namespace() + "." + type.path());
        if (effect.amplifier() > 0) {
          name = translatable("potion.withAmplifier", name,
              translatable("potion.potency." + effect.amplifier()));
        }

        if (effect.durationTicks() > 20) {
          name = translatable("potion.withDuration", name, formatDuration(effect));
        }

        lore.add(color(name, registries.effectCategory(type) == EffectCategory.HARMFUL ? "red" : "blue"));
        for (EffectModifier modifier : registries.effectModifiers(type, effect.amplifier())) {
          if (modifier.amount() != 0) {
            modifiers.add(modifier);
          }
        }
      }

      if (!modifiers.isEmpty()) {
        lore.add(text(""));
        lore.add(color(translatable("potion.whenDrank"), "dark_purple"));

        for (EffectModifier modifier : modifiers) {
          double amount = modifier.amount();
          Map<String, Object> formatted = text(ATTRIBUTE_MODIFIER_FORMAT.format(modifier.operation() == AttributeOperation.ADD_VALUE ? amount : amount * 100d));
          Map<String, Object> name = translatable(modifier.translationKey());

          if (amount > 0) {
            lore.add(color(translatable("attribute.modifier.plus." + modifier.operation().ordinal(), formatted, name), "blue"));
          } else {
            lore.add(color(translatable("attribute.modifier.take." + modifier.operation().ordinal(), formatted, name), "red"));
          }
        }
      }

      List<String> loreJson = new ArrayList<>(lore.size());
      for (Map<String, Object> line : lore) {
        line.put("italic", false);
        loreJson.add(Json.writeComponent(line));
      }
      is.setLoreJson(loreJson);
    }

    return true;
  }

  private static void addCustomEffect(List<EffectInstance> effects, EffectInstance effect) {
    for (int i = 0; i < effects.size(); i++) {
      if (effects.get(i).type().equals(effect.type())) {
        effects.set(i, effect);
        return;
      }
    }
    effects.add(effect);
  }

  private static Map<String, Object> formatDuration(EffectInstance effect) {
    if (effect.isInfinite()) {
      return translatable("effect.duration.infinite");
    } else {
      int seconds = effect.durationTicks() / 20;
      int minutes = seconds / 60;
      seconds %= 60;
      int hours = minutes / 60;
      minutes %= 60;
      return text(hours > 0 ?
          "%02d:%02d:%02d".formatted(hours, minutes, seconds) :
          "%02d:%02d".formatted(minutes, seconds));
    }
  }

  private static Map<String, Object> text(String text) {
    Map<String, Object> component = new LinkedHashMap<>();
    component.put("text", text);
    return component;
  }

  @SafeVarargs
  private static Map<String, Object> translatable(String key, Map<String, Object>... args) {
    Map<String, Object> component = new LinkedHashMap<>();
    component.put("translate", key);
    if (args.length > 0) {
      component.put("with", List.of(args));
    }
    return component;
  }

  private static Map<String, Object> color(Map<String, Object> component, String color) {
    component.put("color", color);
    return component;
  }
}
