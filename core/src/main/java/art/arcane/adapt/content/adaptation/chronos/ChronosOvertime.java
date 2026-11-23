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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChronosOvertime extends SimpleAdaptation<ChronosOvertime.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> HARMFUL = CommonPreferences.toggle("shorten-harmful", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_HARMFUL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_SPEED, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> JUMP_BOOST = CommonPreferences.toggle("jump-boost", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_JUMP_BOOST, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> REGENERATION = CommonPreferences.toggle("regeneration", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_REGENERATION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RESISTANCE = CommonPreferences.toggle("resistance", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_RESISTANCE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FIRE_RESISTANCE = CommonPreferences.toggle("fire-resistance", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_FIRE_RESISTANCE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> WATER_BREATHING = CommonPreferences.toggle("water-breathing", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_WATER_BREATHING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> INVISIBILITY = CommonPreferences.toggle("invisibility", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_INVISIBILITY, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> NIGHT_VISION = CommonPreferences.toggle("night-vision", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_NIGHT_VISION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HEALTH_BOOST = CommonPreferences.toggle("health-boost", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_HEALTH_BOOST, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ABSORPTION = CommonPreferences.toggle("absorption", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_ABSORPTION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SATURATION = CommonPreferences.toggle("saturation", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_SATURATION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LUCK = CommonPreferences.toggle("luck", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_LUCK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SLOW_FALLING = CommonPreferences.toggle("slow-falling", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_SLOW_FALLING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DOLPHINS_GRACE = CommonPreferences.toggle("dolphins-grace", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_DOLPHINS_GRACE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HERO_OF_THE_VILLAGE = CommonPreferences.toggle("hero-of-the-village", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_HERO_OF_THE_VILLAGE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HASTE = CommonPreferences.toggle("haste", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_HASTE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STRENGTH = CommonPreferences.toggle("strength", ChronosMessages.PREFERENCE_CHRONOSOVERTIME_STRENGTH, CommonPreferences.Toggle.ON);

  private final Map<UUID, Boolean> reapplying = playerState();

  public ChronosOvertime() {
    super("chronos-overtime");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
    setInterval(60000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glistering_melon_slice"))
        .key("challenge_chronos_overtime_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_overtime_1k", "chronos.overtime.seconds-extended", 1000, 750);
  }

  private static final Set<Id> BENEFICIAL = Set.of(
      Id.minecraft("speed"),
      Id.minecraft("jump_boost"),
      Id.minecraft("regeneration"),
      Id.minecraft("resistance"),
      Id.minecraft("fire_resistance"),
      Id.minecraft("water_breathing"),
      Id.minecraft("invisibility"),
      Id.minecraft("night_vision"),
      Id.minecraft("health_boost"),
      Id.minecraft("absorption"),
      Id.minecraft("saturation"),
      Id.minecraft("luck"),
      Id.minecraft("slow_falling"),
      Id.minecraft("dolphins_grace"),
      Id.minecraft("hero_of_the_village"),
      Id.minecraft("haste"),
      Id.minecraft("strength"));


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HARMFUL, SPEED, JUMP_BOOST, REGENERATION, RESISTANCE, FIRE_RESISTANCE, WATER_BREATHING, INVISIBILITY, NIGHT_VISION, HEALTH_BOOST, ABSORPTION, SATURATION, LUCK, SLOW_FALLING, DOLPHINS_GRACE, HERO_OF_THE_VILLAGE, HASTE, STRENGTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
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

  static boolean shortensHarmfulEffect(boolean enabled, boolean atMaxLevel, EffectCategory category,
                                       EntityPotionEffectEvent.Action action) {
    return enabled
        && atMaxLevel
        && category == EffectCategory.HARMFUL
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

  @Handle
  public void on(PlayerQuitEvent e) {
    reapplying.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGH)
  public void onHarmfulEffect(EntityPotionEffectEvent e) {
    EffectInstance incoming = e.newEffect();
    RegistryLookup registries = AdaptCore.platform().host().registries();
    if (incoming == null || registries.isInstantEffect(incoming.type())) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    UUID id = p.uuid();
    if (reapplying.containsKey(id)) {
      return;
    }

    int level = getActiveLevel(p);
    if (!shortensHarmfulEffect(getConfig().halveHarmfulEffectsAtMaxLevel && preferenceEnabled(p, HARMFUL), level > 0 && level >= getMaxLevel(),
        registries.effectCategory(incoming.type()), e.action())) {
      return;
    }

    int duration = incoming.durationTicks();
    int shortened = shortenedHarmfulDurationTicks(duration, getHarmfulDurationMultiplier());
    if (shortened >= duration) {
      return;
    }

    // Cancel the vanilla application and re-apply the shortened copy under a guard so the
    // nested EntityPotionEffectEvent from our own addPotionEffect is not shortened again.
    e.setCancelled(true);
    EffectInstance halved = new EffectInstance(incoming.type(), shortened, incoming.amplifier(),
        incoming.ambient(), incoming.particles(), incoming.icon());

    reapplying.put(id, true);
    try {
      p.addEffect(halved);
    } finally {
      reapplying.remove(id);
    }

    addStat(p, "chronos.overtime.seconds-shortened", (duration - shortened) / 20D);
    fx(p.location(), FxPriority.TRANSITION)
        .particle(Particles.WAX_OFF, 4, 0, 0.9D, 0, 0.3D, 0.02D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 0.8F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent e) {
    EntityPotionEffectEvent.Action action = e.action();
    if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    UUID id = p.uuid();
    if (reapplying.containsKey(id)) {
      return;
    }

    EffectInstance newEffect = e.newEffect();
    if (newEffect == null) {
      return;
    }

    int duration = newEffect.durationTicks();
    if (duration < getConfig().minimumDurationTicks || duration > getConfig().maximumBaseDurationTicks) {
      return;
    }

    Id type = newEffect.type();
    if (!BENEFICIAL.contains(type) || !preferenceEnabled(p, switch (type.path()) {
      case "speed" -> SPEED;
      case "jump_boost" -> JUMP_BOOST;
      case "regeneration" -> REGENERATION;
      case "resistance" -> RESISTANCE;
      case "fire_resistance" -> FIRE_RESISTANCE;
      case "water_breathing" -> WATER_BREATHING;
      case "invisibility" -> INVISIBILITY;
      case "night_vision" -> NIGHT_VISION;
      case "health_boost" -> HEALTH_BOOST;
      case "absorption" -> ABSORPTION;
      case "saturation" -> SATURATION;
      case "luck" -> LUCK;
      case "slow_falling" -> SLOW_FALLING;
      case "dolphins_grace" -> DOLPHINS_GRACE;
      case "hero_of_the_village" -> HERO_OF_THE_VILLAGE;
      case "haste" -> HASTE;
      case "strength" -> STRENGTH;
      default -> CommonPreferences.ENABLED;
    })) {
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

    int amplifier = newEffect.amplifier();
    int targetDuration = duration + bonus;

    Runnable extend = () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }

      EffectInstance current = p.effect(type);
      if (current == null
          || current.amplifier() != amplifier
          || current.durationTicks() <= 0
          || current.durationTicks() >= targetDuration) {
        return;
      }

      reapplying.put(id, true);
      try {
        p.addEffect(new EffectInstance(type, targetDuration, amplifier,
            current.ambient(), current.particles(), current.icon()));
      } finally {
        reapplying.remove(id);
      }

      addStat(p, "chronos.overtime.seconds-extended", bonus / 20D);
      xpSilent(p, Math.min(getConfig().maxXpPerExtension, (bonus / 20D) * getConfig().xpPerBonusSecond), "chronos:overtime");

      Loc body = p.location();
      FxEmitter shimmer = fx(body, FxPriority.TRANSITION)
          .particle(Particles.WAX_ON, 5, 0, 0.6D, 0, 0.25D, 0.03D)
          .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F, Sounds.BLOCK_BREWING_STAND_BREW, 0.3F, 1.3F);
      if (bonus >= getConfig().maxBonusTicks * 0.6D) {
        shimmer.ring(Particles.ENCHANTMENT_TABLE, 0.9D, 8, 0.1D);
      }
    };

    if (Tasks.isFoliaThreading()) {
      Tasks.runEntity(p, extend, 1);
    } else {
      Tasks.s(extend, 1);
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
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
