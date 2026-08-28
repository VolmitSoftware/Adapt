/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.ItemFlags;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.inventorygui.Element;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class BrewingLingering extends SimpleAdaptation<BrewingLingering.Config> {
  private static final Function<PotionEffectType, TextColor> getColor;
  private static final Function<PotionEffectType, Map<Attribute, AttributeModifier>> getEffectAttributes;
  private static final Function3<PotionEffectType, Attribute, Integer, Double> getAttributeModifierAmount;
  private static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = new DecimalFormat("#.##");

  static {
    java.lang.invoke.MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle getCategory;
    try {
      java.lang.reflect.Method method = PotionEffectType.class.getDeclaredMethod("getCategory");
      getCategory = lookup.unreflect(method);
    } catch (Throwable ignored) {
      getCategory = null;
    }

    MethodHandle modifiersHandle;
    MethodHandle amountHandle;
    try {
      modifiersHandle = lookup.findVirtual(PotionEffectType.class, "getEffectAttributes", MethodType.methodType(Map.class));
      amountHandle = lookup.findVirtual(PotionEffectType.class, "getAttributeModifierAmount", MethodType.methodType(double.class, Attribute.class, int.class));
    } catch (Throwable ignored) {
      Adapt.verbose("Failed to find attributes for potion effect type");
      modifiersHandle = null;
      amountHandle = null;
    }

    if (getCategory != null) {
      MethodHandle handle = getCategory;
      getColor = type -> {
        try {
          return ((Enum<?>) handle.invoke(type)).ordinal() == 1 ? NamedTextColor.RED : NamedTextColor.BLUE;
        } catch (Throwable err) {
          throw new RuntimeException(err);
        }
      };
    } else getColor = $ -> NamedTextColor.BLUE;

    if (modifiersHandle != null) {
      MethodHandle handle = modifiersHandle;
      getEffectAttributes = type -> {
        try {
          return (Map<Attribute, AttributeModifier>) handle.invoke(type);
        } catch (Throwable err) {
          throw new RuntimeException(err);
        }
      };
    } else getEffectAttributes = $ -> Map.of();

    if (amountHandle != null) {
      MethodHandle handle = amountHandle;
      getAttributeModifierAmount = (type, attribute, level) -> {
        try {
          return (double) handle.invoke(type, attribute, level);
        } catch (Throwable err) {
          throw new RuntimeException(err);
        }
      };
    } else getAttributeModifierAmount = ($, $$, $$$) -> 0d;

    ATTRIBUTE_MODIFIER_FORMAT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
  }

  public BrewingLingering() {
    super("brewing-lingering");
    registerConfiguration(Config.class);
    setIcon(Material.DRAGON_BREATH);
    setInterval(4788);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LINGERING_POTION)
        .key("challenge_brewing_lingering_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_BREATH)
            .key("challenge_brewing_lingering_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewing_lingering_200", "brewing.lingering.potions-extended", 200, 300);
    registerMilestone("challenge_brewing_lingering_5k", "brewing.lingering.potions-extended", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration((long) getDurationBoost(getLevelPercent(level)), 0), 1);
    statLore(v, Form.pc(getPercentBoost(getLevelPercent(level)), 0), 2);
  }

  public double getDurationBoost(double factor) {
    return (getConfig().durationBoostFactorTicks * factor) + getConfig().baseDurationBoostTicks;
  }

  public double getPercentBoost(double factor) {
    return 1 + ((factor * factor * getConfig().durationMultiplierFactor) + getConfig().baseDurationMultiplier);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BrewEvent e) {
    if (!e.getBlock().getType().equals(Material.BREWING_STAND)) {
      return;
    }
    BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).get(e.getBlock(), BrewingStandOwner.class);

    if (owner == null) {
      Adapt.verbose("No Owner");
      return;
    }

    int level = getServer().getOnlineAdaptationLevel(owner.getOwner(), getSkill().getName(), getName());
    if (level <= 0) {
      return;
    }

    java.util.List<org.bukkit.inventory.ItemStack> results = e.getResults();
    int enhancedPotions = 0;
    for (int i = 0; i < results.size(); i++) {
      ItemStack is = results.get(i);

      if (is == null || is.getItemMeta() == null || !(is.getItemMeta() instanceof PotionMeta p)) {
        continue;
      }

      if (enhance(getLevelPercent(level), is, p)) {
        enhancedPotions++;
      }
      results.set(i, is);
    }

    if (enhancedPotions > 0) {
      getServer().addStat(owner.getOwner(), "brewing.lingering.potions-extended", enhancedPotions);
      Location loc = e.getBlock().getLocation().add(0.5D, 0.6D, 0.5D);
      Particle.DustTransition transition = new Particle.DustTransition(Color.fromRGB(0x8A, 0x2B, 0xE2), Color.fromRGB(0xFF, 0x77, 0xFF), 1.2F);
      timeline(loc)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .cullRadius(24.0D)
          .frame((f, tick, progress) -> {
            f.ring(Particle.DUST_COLOR_TRANSITION, 0.4D + (0.6D * progress), 10, 0.3D, transition);
            if (tick == 0) {
              f.particle(Particle.DRAGON_BREATH, 16, 0, 0.2D, 0, 0.4D, 0.01D)
                  .chord(Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 0.75F, Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.75F, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3F, 1.2F)
                  .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.2F);
            }
            if (tick == 3) {
              f.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
            }
          })
          .start();
    }
  }

  private boolean enhance(double factor, ItemStack is, PotionMeta p) {
    org.bukkit.potion.PotionType baseType = p.getBasePotionType();
    if (baseType == null) {
      return false;
    }

    java.util.List<org.bukkit.potion.PotionEffect> effects = baseType.getPotionEffects();
    if (effects.stream()
        .map(PotionEffect::getType)
        .allMatch(PotionEffectType::isInstant))
      return false;

    p.clearCustomEffects();
    for (final PotionEffect effect : effects) {
      if (effect.getType().isInstant()) {
        p.addCustomEffect(effect, true);
        continue;
      }

      p.addCustomEffect(new PotionEffect(
          effect.getType(),
          (int) (getDurationBoost(factor) + (effect.getDuration() * getPercentBoost(factor))),
          effect.getAmplifier()
      ), true);
    }

    p.addItemFlags(ItemFlags.HIDE_POTION_EFFECTS);
    is.setItemMeta(p);

    if (getConfig().useCustomLore) {
      KList<Component> lore = new KList<>();
      KList<Modifier> modifiers = new KList<>();
      for (org.bukkit.potion.PotionEffect effect : p.getCustomEffects()) {
        org.bukkit.potion.PotionEffectType type = effect.getType();
        org.bukkit.NamespacedKey key = type.getKey();
        net.kyori.adventure.text.TranslatableComponent name = Component.translatable("effect." + key.getNamespace() + "." + key.getKey());
        if (effect.getAmplifier() > 0) {
          name = Component.translatable("potion.withAmplifier", name,
              Component.translatable("potion.potency." + effect.getAmplifier()));
        }

        if (effect.getDuration() > 20) {
          name = Component.translatable("potion.withDuration", name, formatDuration(effect));
        }

        lore.add(name.color(getColor.apply(type)));
        getEffectAttributes.apply(type)
            .entrySet()
            .stream()
            .map(Modifier::new)
            .map(m -> m.adjust(type, effect.getAmplifier()))
            .filter(m -> m.amount != 0)
            .forEach(modifiers::add);
      }

      if (!modifiers.isEmpty()) {
        lore.add(Component.empty());
        lore.add(Component.translatable("potion.whenDrank").color(NamedTextColor.DARK_PURPLE));

        for (Modifier modifier : modifiers) {
          double amount = modifier.amount;
          net.kyori.adventure.text.TextComponent formatted = Component.text(ATTRIBUTE_MODIFIER_FORMAT.format(modifier.operation == AttributeModifier.Operation.ADD_NUMBER ? amount : amount * 100d));
          net.kyori.adventure.text.TranslatableComponent name = Component.translatable("attribute.name." + modifier.attribute.getKey().getKey());

          if (amount > 0) {
            lore.add(Component.translatable("attribute.modifier.plus." + modifier.operation.ordinal(), formatted, name)
                .color(NamedTextColor.BLUE));
          } else {
            lore.add(Component.translatable("attribute.modifier.take." + modifier.operation.ordinal(), formatted, name)
                .color(NamedTextColor.RED));
          }
        }
      }
      lore.replaceAll(c -> c.decoration(TextDecoration.ITALIC, false));

      Adapt.platform.editItem(is)
          .lore(lore)
          .build();
    }

    return true;
  }

  private Component formatDuration(PotionEffect effect) {
    if (effect.isInfinite()) {
      return Component.translatable("effect.duration.infinite");
    } else {
      int seconds = effect.getDuration() / 20;
      int minutes = seconds / 60;
      seconds %= 60;
      int hours = minutes / 60;
      minutes %= 60;
      return Component.text(hours > 0 ?
          "%02d:%02d:%02d".formatted(hours, minutes, seconds) :
          "%02d:%02d".formatted(minutes, seconds));
    }
  }


  @ConfigDescription("Brewed potions last longer.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Duration Boost Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDurationBoostTicks = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Boost Factor Ticks for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationBoostFactorTicks = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Multiplier Factor for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationMultiplierFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Duration Multiplier for the Brewing Lingering adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDurationMultiplier = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Custom Lore for the Brewing Lingering adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useCustomLore = true;

    public Config() {
      baseCost = 3;
      costFactor = 0.75;
      initialCost = 5;
    }
  }

  private record Modifier(Attribute attribute,
                          AttributeModifier.Operation operation,
                          double amount) {
    private Modifier(Map.Entry<Attribute, AttributeModifier> entry) {
      this(entry.getKey(), entry.getValue());
    }

    private Modifier(Attribute attribute, AttributeModifier modifier) {
      this(attribute, modifier.getOperation(), modifier.getAmount());
    }

    private Modifier adjust(PotionEffectType type, int amplifier) {
      return new Modifier(
          attribute,
          operation,
          getAttributeModifierAmount.apply(type, attribute, amplifier)
      );
    }
  }
}
