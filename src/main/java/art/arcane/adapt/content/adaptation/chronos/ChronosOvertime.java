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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChronosOvertime extends SimpleAdaptation<ChronosOvertime.Config> {
  private final Map<UUID, Boolean> reapplying = playerState();

  public ChronosOvertime() {
    super("chronos-overtime");
    registerConfiguration(Config.class);
    setIcon(Material.GLISTERING_MELON_SLICE);
    setInterval(60000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLISTERING_MELON_SLICE)
        .key("challenge_chronos_overtime_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_overtime_1k", "chronos.overtime.seconds-extended", 1000, 750);
  }

  // Held in a holder so the potion registry is only touched on first use, never during class init.
  private static final class Beneficial {
    private static final Set<PotionEffectType> TYPES = buildBeneficialSet();
  }

  private static Set<PotionEffectType> buildBeneficialSet() {
    Set<PotionEffectType> set = new HashSet<>();
    addIfPresent(set, PotionEffectType.SPEED);
    addIfPresent(set, PotionEffectType.JUMP_BOOST);
    addIfPresent(set, PotionEffectType.REGENERATION);
    addIfPresent(set, PotionEffectType.RESISTANCE);
    addIfPresent(set, PotionEffectType.FIRE_RESISTANCE);
    addIfPresent(set, PotionEffectType.WATER_BREATHING);
    addIfPresent(set, PotionEffectType.INVISIBILITY);
    addIfPresent(set, PotionEffectType.NIGHT_VISION);
    addIfPresent(set, PotionEffectType.HEALTH_BOOST);
    addIfPresent(set, PotionEffectType.ABSORPTION);
    addIfPresent(set, PotionEffectType.SATURATION);
    addIfPresent(set, PotionEffectType.LUCK);
    addIfPresent(set, PotionEffectType.SLOW_FALLING);
    addIfPresent(set, PotionEffectType.DOLPHINS_GRACE);
    addIfPresent(set, PotionEffectType.HERO_OF_THE_VILLAGE);
    addIfPresent(set, PotionEffectTypes.FAST_DIGGING);
    addIfPresent(set, PotionEffectTypes.INCREASE_DAMAGE);
    return set;
  }

  private static void addIfPresent(Set<PotionEffectType> set, PotionEffectType type) {
    if (type != null) {
      set.add(type);
    }
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Math.round(getExtensionPercent(level) * 100D) + "%", 1);
    statLore(v, C.YELLOW, "+ ", (getConfig().maxBonusTicks / 20) + "s", 2);
    statLore(v, C.RED, "* ",
        getConfig().halveHarmfulEffectsAtMaxLevel && level >= getMaxLevel()
            ? Form.pc(getHarmfulDurationMultiplier(), 0)
            : "-",
        ChronosMessages.OVERTIME_MAX_LEVEL_LORE);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.OVERTIME_LORE3));
  }

  private double getExtensionPercent(int level) {
    return Math.min(getConfig().maxExtensionPercent,
        getConfig().baseExtensionPercent + (Math.max(1, level) * getConfig().extensionPercentPerLevel));
  }

  private double getHarmfulDurationMultiplier() {
    double multiplier = getConfig().maxLevelHarmfulDurationMultiplier;
    return Double.isFinite(multiplier) ? Math.max(0D, Math.min(1D, multiplier)) : 1D;
  }

  static boolean shortensHarmfulEffect(boolean enabled, boolean atMaxLevel, PotionEffectTypeCategory category,
                                       EntityPotionEffectEvent.Action action) {
    return enabled
        && atMaxLevel
        && category == PotionEffectTypeCategory.HARMFUL
        && (action == EntityPotionEffectEvent.Action.ADDED || action == EntityPotionEffectEvent.Action.CHANGED);
  }

  static int shortenedHarmfulDurationTicks(int duration, double multiplier) {
    if (duration <= 0) {
      return 0;
    }

    if (!Double.isFinite(multiplier)) {
      return duration;
    }

    double clamped = Math.max(0D, Math.min(1D, multiplier));
    return Math.max(1, Math.min(duration, (int) Math.round(duration * clamped)));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    reapplying.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onHarmfulEffect(EntityPotionEffectEvent e) {
    PotionEffect incoming = e.getNewEffect();
    if (incoming == null || incoming.getType().isInstant()) {
      return;
    }

    if (!(PaperCompat.livingEntity(e) instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (reapplying.containsKey(id)) {
      return;
    }

    int level = getActiveLevel(p);
    if (!shortensHarmfulEffect(getConfig().halveHarmfulEffectsAtMaxLevel, level > 0 && level >= getMaxLevel(),
        incoming.getType().getCategory(), e.getAction())) {
      return;
    }

    int duration = incoming.getDuration();
    int shortened = shortenedHarmfulDurationTicks(duration, getHarmfulDurationMultiplier());
    if (shortened >= duration) {
      return;
    }

    // Cancel the vanilla application and re-apply the shortened copy under a guard so the
    // nested EntityPotionEffectEvent from our own addPotionEffect is not shortened again.
    e.setCancelled(true);
    PotionEffect halved = new PotionEffect(incoming.getType(), shortened, incoming.getAmplifier(),
        incoming.isAmbient(), incoming.hasParticles(), incoming.hasIcon());

    reapplying.put(id, true);
    try {
      p.addPotionEffect(halved);
    } finally {
      reapplying.remove(id);
    }

    addStat(p, "chronos.overtime.seconds-shortened", (duration - shortened) / 20D);
    fx(p.getLocation(), FxPriority.TRANSITION)
        .particle(Particle.WAX_OFF, 4, 0, 0.9D, 0, 0.3D, 0.02D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 0.8F);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPotionEffectEvent e) {
    EntityPotionEffectEvent.Action action = e.getAction();
    if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) {
      return;
    }

    if (!(PaperCompat.livingEntity(e) instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (reapplying.containsKey(id)) {
      return;
    }

    PotionEffect newEffect = e.getNewEffect();
    if (newEffect == null) {
      return;
    }

    int duration = newEffect.getDuration();
    if (duration < getConfig().minimumDurationTicks || duration > getConfig().maximumBaseDurationTicks) {
      return;
    }

    PotionEffectType type = newEffect.getType();
    if (!Beneficial.TYPES.contains(type)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int bonus = (int) Math.min(getConfig().maxBonusTicks, Math.round(duration * getExtensionPercent(level)));
    if (bonus <= 0) {
      return;
    }

    int amplifier = newEffect.getAmplifier();
    int targetDuration = duration + bonus;

    Runnable extend = () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }

      PotionEffect current = p.getPotionEffect(type);
      if (current == null
          || current.getAmplifier() != amplifier
          || current.getDuration() <= 0
          || current.getDuration() >= targetDuration) {
        return;
      }

      reapplying.put(id, true);
      try {
        p.addPotionEffect(new PotionEffect(type, targetDuration, amplifier,
            current.isAmbient(), current.hasParticles(), current.hasIcon()), true);
      } finally {
        reapplying.remove(id);
      }

      addStat(p, "chronos.overtime.seconds-extended", bonus / 20D);
      xpSilent(p, Math.min(getConfig().maxXpPerExtension, (bonus / 20D) * getConfig().xpPerBonusSecond), "chronos:overtime");

      Location body = p.getLocation();
      FxEmitter shimmer = fx(body, FxPriority.TRANSITION)
          .particle(Particle.WAX_ON, 5, 0, 0.6D, 0, 0.25D, 0.03D)
          .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F, Sound.BLOCK_BREWING_STAND_BREW, 0.3F, 1.3F);
      if (bonus >= getConfig().maxBonusTicks * 0.6D) {
        shimmer.ring(Particles.ENCHANTMENT_TABLE, 0.9D, 8, 0.1D);
      }
    };

    if (J.isFoliaThreading()) {
      J.runEntity(p, extend, 1);
    } else {
      J.s(extend, 1);
    }
  }


  @ConfigDescription("Beneficial potion effects applied to you last longer, scaled by adaptation level. At max level, harmful effects applied to you last half as long.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base duration extension as a fraction of the original duration.", impact = "Higher values extend beneficial effects more.")
    double baseExtensionPercent = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra extension fraction per adaptation level.", impact = "Higher values make leveling extend effects faster.")
    double extensionPercentPerLevel = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the duration extension fraction.", impact = "Higher values allow larger total extensions.")
    double maxExtensionPercent = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum bonus ticks added to any single effect application.", impact = "Higher values let long potions gain bigger absolute extensions.")
    int maxBonusTicks = 2400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum original duration in ticks for an effect to be extended.", impact = "Higher values ignore short pulsing effects entirely.")
    int minimumDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum original duration in ticks eligible for extension.", impact = "Lower values stop near permanent effects from being extended.")
    int maximumBaseDurationTicks = 72000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per bonus second of effect duration.", impact = "Higher values grant more skill XP per extension.")
    double xpPerBonusSecond = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum XP granted by a single extension.", impact = "Higher values allow bigger XP payouts per potion.")
    double maxXpPerExtension = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shorten harmful potion effects applied to you once this adaptation is at max level.", impact = "True enables this behavior and false disables it.")
    boolean halveHarmfulEffectsAtMaxLevel = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the original duration kept by harmful effects at max level.", impact = "Lower values shorten harmful effects more; 1 leaves them untouched.")
    double maxLevelHarmfulDurationMultiplier = 0.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.38;
      initialCost = 4;
    }
  }
}
