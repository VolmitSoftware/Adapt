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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class ChronosAberrantTouch extends SimpleAdaptation<ChronosAberrantTouch.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", ChronosMessages.PREFERENCE_CHRONOSABERRANTTOUCH_CLOCK_SOUNDS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("passive-targets", ChronosMessages.PREFERENCE_CHRONOSABERRANTTOUCH_PASSIVE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("player-targets", ChronosMessages.PREFERENCE_CHRONOSABERRANTTOUCH_PLAYERS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", ChronosMessages.PREFERENCE_CHRONOSABERRANTTOUCH_FOOD_RESERVE, CommonPreferences.Toggle.OFF);

  private static final Id SLOWNESS = Id.minecraft("slowness");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, StackState> targetStacks = new ConcurrentHashMap<>();

  public ChronosAberrantTouch() {
    super("chronos-aberrant-touch");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spider_eye"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_aberrant_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_aberrant_frozen")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_aberrant_500", "chronos.aberrant-touch.slowness-stacks-applied", 500, 400);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_SOUNDS, PASSIVE, PLAYERS, FOOD_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(ChronosMessages.ABERRANT_TOUCH_LORE1));
    statLore(v, C.YELLOW, "+ ", getPvEDurationCapTicks(level) / 20D + "s", 2);
    statLore(v, C.RED, "* ", getConfig().playerAmplifierCap, 3);
    v.addLore(C.AQUA + "* " + AdaptLanguage.text(
        ChronosMessages.ABERRANT_TOUCH_ROOT_LORE,
        trusted("stacks", getConfig().rootAtStacks),
        trusted("seconds", getConfig().rootDurationTicks / 20D)
    ));
  }

  private int getPvEAmplifierCap(int level) {
    return Math.max(0, Math.min(getConfig().entityAmplifierCap, level));
  }

  private int getPvEDurationCapTicks(int level) {
    return getConfig().entityDurationCapTicks + (level * getConfig().entityDurationCapPerLevelTicks);
  }

  private int getDurationAddedTicks(int level) {
    return getConfig().durationAddTicks + (level * getConfig().durationPerLevelTicks);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef attacker)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (!cooldowns.isReady(attacker.uuid(), getConfig().cooldownMillis)) {
      return;
    }

    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    attacker = combat.attacker();
    LivingRef target = combat.target();
    if (target instanceof PlayerRef && !preferenceEnabled(attacker, PLAYERS)
        || !(target instanceof PlayerRef) && !target.has(EntityTrait.ENEMY) && !preferenceEnabled(attacker, PASSIVE)
        || preferenceEnabled(attacker, FOOD_RESERVE) && attacker.foodLevel() - getConfig().hungerCost < 8) {
      return;
    }
    PlayerRef payer = attacker;
    if (!payHungerCost(payer, "hunger", (int) Math.ceil(getConfig().hungerCost),
        () -> getPlayer(payer).consumeFood(getConfig().hungerCost, getConfig().minimumFoodLevel))) {
      return;
    }

    int level = combat.level();
    int amplifierCap = target instanceof PlayerRef ? getConfig().playerAmplifierCap : getPvEAmplifierCap(level);
    int durationCap = target instanceof PlayerRef ? getConfig().playerDurationCapTicks : getPvEDurationCapTicks(level);

    EffectInstance existing = target.effect(SLOWNESS);
    int currentAmplifier = existing == null ? -1 : existing.amplifier();
    int currentDuration = existing == null ? 0 : existing.durationTicks();

    int newAmplifier = Math.min(amplifierCap, currentAmplifier + 1);
    int newDuration = Math.min(durationCap, currentDuration + getDurationAddedTicks(level));

    target.addEffect(new EffectInstance(
        SLOWNESS,
        Math.max(20, newDuration),
        Math.max(0, newAmplifier),
        true,
        true,
        true
    ));
    addStat(attacker, "chronos.aberrant-touch.slowness-stacks-applied", 1);

    StackState state = targetStacks.getOrDefault(target.uuid(), new StackState(0, 0L));
    int stacks = (now - state.lastHitMillis() > getConfig().stackResetMillis) ? 1 : state.stacks() + 1;
    boolean rooted = stacks >= getConfig().rootAtStacks;
    int chargeStacks = stacks;
    if (rooted) {
      ChronosRoot.root(target, getConfig().rootDurationTicks, getConfig().rootAmplifier);
      stacks = 0;
      if (AdaptConfig.get().isAdvancements() && !getPlayer(attacker).getData().isGranted("challenge_chronos_aberrant_frozen")) {
        getPlayer(attacker).getAdvancementHandler().grant("challenge_chronos_aberrant_frozen");
      }
    }
    targetStacks.put(target.uuid(), new StackState(stacks, now));

    if (getConfig().playClockSounds && preferenceEnabled(attacker, CLOCK_SOUNDS)) {
      ChronosSoundFX.playTouchProc(attacker, target.location());
    }
    emitTouchFx(target, Math.max(0, newAmplifier), rooted, chargeStacks);
    xp(attacker, attacker.location(), getConfig().xpPerProc + (getConfig().xpPerLevel * level));
    cooldowns.mark(attacker.uuid());
  }

  /** The flash and collapsing ring of a root. */
  public static void rootFx(Adaptation<?> owner, LivingRef target) {
    Fx.now(owner, target.location(), FxPriority.COMBAT).particle(Particles.FLASH, 1, 0, 0.1D, 0, 0, 0);
    FxTimeline.follow(owner, target)
        .duration(4)
        .priority(FxPriority.COMBAT)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.ring(Particles.END_ROD, 1.4D - (1.1D * progress), 12, 0.15D);
          if (tick == 0) {
            f.chord(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6F, 0.7F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.4F);
          }
        })
        .start();
  }

  private void emitTouchFx(LivingRef target, int amplifier, boolean rooted, int stacks) {
    if (rooted) {
      rootFx(this, target);
      return;
    }

    int motes = Math.min(6, 2 + amplifier);
    int rootAt = Math.max(1, getConfig().rootAtStacks);
    float ratio = Math.min(1F, (float) stacks / rootAt);
    Rgb charge = new Rgb((int) (90 + (165 * ratio)), (int) (120 + (135 * ratio)), 255);
    Loc body = target.location().add(0, target.height() * 0.55D, 0);
    fx(body, FxPriority.COMBAT)
        .particle(Particles.SCULK_SOUL, motes, 0, 0.15D, 0, 0.3D, 0.02D)
        .dustRing(charge, 0.5D, 6, 0.8F);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    targetStacks.entrySet().removeIf(entry -> now - entry.getValue().lastHitMillis() > getConfig().stackResetMillis);
  }

  @ConfigDescription("Melee attacks apply stacking slowness at the cost of hunger, with PvP caps.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Aberrant Touch adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Add Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durationAddTicks = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Per Level Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durationPerLevelTicks = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Duration Cap Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int playerDurationCapTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Amplifier Cap for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int playerAmplifierCap = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entity Duration Cap Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int entityDurationCapTicks = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entity Duration Cap Per Level Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int entityDurationCapPerLevelTicks = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entity Amplifier Cap for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int entityAmplifierCap = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCost = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Food Level for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minimumFoodLevel = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Root At Stacks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootAtStacks = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Root Duration Ticks for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootDurationTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Root Amplifier for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootAmplifier = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Reset Millis for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long stackResetMillis = 2500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownMillis = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Proc for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerProc = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level for the Chronos Aberrant Touch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLevel = 1.25;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private record StackState(int stacks, long lastHitMillis) {
  }
}
