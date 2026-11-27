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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Set;

public class ChronosOvertime extends SimpleAdaptation<ChronosOvertime.Config> {
  public static final Id ID = Id.adapt("chronos/overtime");
  public static final Set<Id> BENEFICIAL = Set.of(
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

  public static final PlayerPreference<CommonPreferences.Toggle> HARMFUL = CommonPreferences.toggle("shorten-harmful", Text.key("adapt.chronos.preferences.chronosovertime.shorten-harmful"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPEED = CommonPreferences.toggle("speed", Text.key("adapt.chronos.preferences.chronosovertime.speed"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> JUMP_BOOST = CommonPreferences.toggle("jump-boost", Text.key("adapt.chronos.preferences.chronosovertime.jump-boost"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> REGENERATION = CommonPreferences.toggle("regeneration", Text.key("adapt.chronos.preferences.chronosovertime.regeneration"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RESISTANCE = CommonPreferences.toggle("resistance", Text.key("adapt.chronos.preferences.chronosovertime.resistance"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FIRE_RESISTANCE = CommonPreferences.toggle("fire-resistance", Text.key("adapt.chronos.preferences.chronosovertime.fire-resistance"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> WATER_BREATHING = CommonPreferences.toggle("water-breathing", Text.key("adapt.chronos.preferences.chronosovertime.water-breathing"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> INVISIBILITY = CommonPreferences.toggle("invisibility", Text.key("adapt.chronos.preferences.chronosovertime.invisibility"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> NIGHT_VISION = CommonPreferences.toggle("night-vision", Text.key("adapt.chronos.preferences.chronosovertime.night-vision"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HEALTH_BOOST = CommonPreferences.toggle("health-boost", Text.key("adapt.chronos.preferences.chronosovertime.health-boost"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ABSORPTION = CommonPreferences.toggle("absorption", Text.key("adapt.chronos.preferences.chronosovertime.absorption"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SATURATION = CommonPreferences.toggle("saturation", Text.key("adapt.chronos.preferences.chronosovertime.saturation"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LUCK = CommonPreferences.toggle("luck", Text.key("adapt.chronos.preferences.chronosovertime.luck"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SLOW_FALLING = CommonPreferences.toggle("slow-falling", Text.key("adapt.chronos.preferences.chronosovertime.slow-falling"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DOLPHINS_GRACE = CommonPreferences.toggle("dolphins-grace", Text.key("adapt.chronos.preferences.chronosovertime.dolphins-grace"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HERO_OF_THE_VILLAGE = CommonPreferences.toggle("hero-of-the-village", Text.key("adapt.chronos.preferences.chronosovertime.hero-of-the-village"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HASTE = CommonPreferences.toggle("haste", Text.key("adapt.chronos.preferences.chronosovertime.haste"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STRENGTH = CommonPreferences.toggle("strength", Text.key("adapt.chronos.preferences.chronosovertime.strength"), CommonPreferences.Toggle.ON);

  private final ChronosEffectStretch stretch = new ChronosEffectStretch(this, playerState(), (PlayerRef p, int bonus) -> {
    addStat(p, "chronos.overtime.seconds-extended", bonus / 20D);
    xpSilent(p, Math.min(getConfig().maxXpPerExtension, (bonus / 20D) * getConfig().xpPerBonusSecond), "chronos:overtime");
  });

  public ChronosOvertime() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("glistering_melon_slice"));
    setInterval(60000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glistering_melon_slice"))
        .key("challenge_chronos_overtime_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_overtime_1k", "chronos.overtime.seconds-extended", 1000, 750);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HARMFUL, SPEED, JUMP_BOOST, REGENERATION, RESISTANCE, FIRE_RESISTANCE, WATER_BREATHING, INVISIBILITY, NIGHT_VISION, HEALTH_BOOST, ABSORPTION, SATURATION, LUCK, SLOW_FALLING, DOLPHINS_GRACE, HERO_OF_THE_VILLAGE, HASTE, STRENGTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Math.round(getExtensionPercent(level) * 100D) + "%", 1);
    statLore(v, TextFormat.YELLOW, "+ ", (getConfig().maxBonusTicks / 20) + "s", 2);
    statLore(v, TextFormat.RED, "* ", getConfig().halveHarmfulEffectsAtMaxLevel && level >= getMaxLevel()
            ? Format.pc(getHarmfulDurationMultiplier(), 0)
            : "-", Text.key("adapt.chronos.overtime.max_level_lore"));
    v.addLore(TextFormat.GRAY + "* " + Text.key("adapt.chronos.overtime.lore3").resolve());
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
    stretch.forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGH)
  public void onHarmfulEffect(EntityPotionEffectEvent e) {
    EffectInstance incoming = e.newEffect();
    RegistryLookup registries = AdaptApi.platform().host().registries();
    if (incoming == null || registries.isInstantEffect(incoming.type())) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (stretch.isReapplying(p.uuid())) {
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
    EffectInstance halved = EffectInstance.of(incoming.type(), shortened, incoming.amplifier()).withAmbient(incoming.ambient()).withParticles(incoming.particles()).withIcon(incoming.icon());

    stretch.reapply(p, halved);

    addStat(p, "chronos.overtime.seconds-shortened", (duration - shortened) / 20D);
    fx(p.location(), FxPriority.TRANSITION)
        .particle(Particles.WAX_OFF, 4, 0, 0.9D, 0, 0.3D, 0.02D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 0.8F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent e) {
    stretch.stretch(e, (PlayerRef p, Id type) -> preferenceEnabled(p, switch (type.path()) {
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
    }), new ChronosEffectStretch.Stretch(this::getExtensionPercent,
        getConfig().maxBonusTicks, getConfig().minimumDurationTicks, getConfig().maximumBaseDurationTicks));
  }

  /** The shimmer of a stretched effect; {@code large} adds the ring for big extensions. */
  public static void stretchFx(Adaptation<?> owner, PlayerRef p, boolean large) {
    Loc body = p.location();
    FxEmitter shimmer = Fx.now(owner, body, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 5, 0, 0.6D, 0, 0.25D, 0.03D)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F, Sounds.BLOCK_BREWING_STAND_BREW, 0.3F, 1.3F);
    if (large) {
      shimmer.ring(Particles.ENCHANT, 0.9D, 8, 0.1D);
    }
  }

  @ConfigDescription("Beneficial potion effects applied to you last longer, scaled by adaptation level. At max level, harmful effects applied to you last half as long.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base duration extension as a fraction of the original duration.", impact = "Higher values extend beneficial effects more.")
    double baseExtensionPercent = 0.05;
    @ConfigDoc(value = "Extra extension fraction per adaptation level.", impact = "Higher values make leveling extend effects faster.")
    double extensionPercentPerLevel = 0.07;
    @ConfigDoc(value = "Hard cap on the duration extension fraction.", impact = "Higher values allow larger total extensions.")
    double maxExtensionPercent = 0.4;
    @ConfigDoc(value = "Maximum bonus ticks added to any single effect application.", impact = "Higher values let long potions gain bigger absolute extensions.")
    int maxBonusTicks = 2400;
    @ConfigDoc(value = "Minimum original duration in ticks for an effect to be extended.", impact = "Higher values ignore short pulsing effects entirely.")
    int minimumDurationTicks = 60;
    @ConfigDoc(value = "Maximum original duration in ticks eligible for extension.", impact = "Lower values stop near permanent effects from being extended.")
    int maximumBaseDurationTicks = 72000;
    @ConfigDoc(value = "XP granted per bonus second of effect duration.", impact = "Higher values grant more skill XP per extension.")
    double xpPerBonusSecond = 0.2;
    @ConfigDoc(value = "Maximum XP granted by a single extension.", impact = "Higher values allow bigger XP payouts per potion.")
    double maxXpPerExtension = 8;
    @ConfigDoc(value = "Shorten harmful potion effects applied to you once this adaptation is at max level.", impact = "True enables this behavior and false disables it.")
    boolean halveHarmfulEffectsAtMaxLevel = true;
    @ConfigDoc(value = "Fraction of the original duration kept by harmful effects at max level.", impact = "Lower values shorten harmful effects more; 1 leaves them untouched.")
    double maxLevelHarmfulDurationMultiplier = 0.5;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
