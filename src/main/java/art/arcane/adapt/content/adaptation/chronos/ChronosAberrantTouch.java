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
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosAberrantTouch extends SimpleAdaptation<ChronosAberrantTouch.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, StackState> targetStacks = new ConcurrentHashMap<>();

  public ChronosAberrantTouch() {
    super("chronos-aberrant-touch");
    registerConfiguration(Config.class);
    setIcon(Material.SPIDER_EYE);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_aberrant_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_aberrant_frozen")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_aberrant_500", "chronos.aberrant-touch.slowness-stacks-applied", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("chronos.aberrant_touch.lore1"));
    v.addLore(C.YELLOW + "+ " + getPvEDurationCapTicks(level) / 20D + "s " + Localizer.dLocalize("chronos.aberrant_touch.lore2"));
    v.addLore(C.RED + "* " + getConfig().playerAmplifierCap + " " + Localizer.dLocalize("chronos.aberrant_touch.lore3"));
    v.addLore(C.AQUA + "* " + getConfig().rootAtStacks + " stacks roots for " + (getConfig().rootDurationTicks / 20D) + "s");
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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player attacker)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (!cooldowns.isReady(attacker.getUniqueId(), getConfig().cooldownMillis)) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    attacker = combat.attacker();
    LivingEntity target = combat.target();
    if (!getPlayer(attacker).consumeFood(getConfig().hungerCost, getConfig().minimumFoodLevel)) {
      return;
    }

    int level = combat.level();
    int amplifierCap = target instanceof Player ? getConfig().playerAmplifierCap : getPvEAmplifierCap(level);
    int durationCap = target instanceof Player ? getConfig().playerDurationCapTicks : getPvEDurationCapTicks(level);

    PotionEffect existing = target.getPotionEffect(PotionEffectType.SLOWNESS);
    int currentAmplifier = existing == null ? -1 : existing.getAmplifier();
    int currentDuration = existing == null ? 0 : existing.getDuration();

    int newAmplifier = Math.min(amplifierCap, currentAmplifier + 1);
    int newDuration = Math.min(durationCap, currentDuration + getDurationAddedTicks(level));

    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.max(20, newDuration), Math.max(0, newAmplifier), true, true, true), true);
    addStat(attacker, "chronos.aberrant-touch.slowness-stacks-applied", 1);

    StackState state = targetStacks.getOrDefault(target.getUniqueId(), new StackState(0, 0L));
    int stacks = (now - state.lastHitMillis() > getConfig().stackResetMillis) ? 1 : state.stacks() + 1;
    boolean rooted = stacks >= getConfig().rootAtStacks;
    int chargeStacks = stacks;
    if (rooted) {
      target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().rootDurationTicks, getConfig().rootAmplifier, true, true, true), true);
      target.setVelocity(new Vector());
      stacks = 0;
      if (AdaptConfig.get().isAdvancements() && !getPlayer(attacker).getData().isGranted("challenge_chronos_aberrant_frozen")) {
        getPlayer(attacker).getAdvancementHandler().grant("challenge_chronos_aberrant_frozen");
      }
    }
    targetStacks.put(target.getUniqueId(), new StackState(stacks, now));

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playTouchProc(attacker, target.getLocation());
    }
    emitTouchFx(target, Math.max(0, newAmplifier), rooted, chargeStacks);
    xp(attacker, attacker.getLocation(), getConfig().xpPerProc + (getConfig().xpPerLevel * level));
    cooldowns.mark(attacker.getUniqueId());
  }

  private void emitTouchFx(LivingEntity target, int amplifier, boolean rooted, int stacks) {
    if (rooted) {
      Location center = target.getLocation();
      fx(center, FxPriority.COMBAT).particle(Particle.FLASH, 1, 0, 0.1D, 0, 0, 0);
      timeline(target)
          .duration(4)
          .priority(FxPriority.COMBAT)
          .cullRadius(40)
          .frame((f, tick, progress) -> {
            f.ring(Particles.END_ROD, 1.4D - (1.1D * progress), 12, 0.15D);
            if (tick == 0) {
              f.chord(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6F, 0.7F, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.4F);
            }
          })
          .start();
      return;
    }

    int motes = Math.min(6, 2 + amplifier);
    int rootAt = Math.max(1, getConfig().rootAtStacks);
    float ratio = Math.min(1F, (float) stacks / rootAt);
    Color charge = Color.fromRGB((int) (90 + (165 * ratio)), (int) (120 + (135 * ratio)), 255);
    Location body = target.getLocation().add(0, target.getHeight() * 0.55D, 0);
    fx(body, FxPriority.COMBAT)
        .particle(Particle.SCULK_SOUL, motes, 0, 0.15D, 0, 0.3D, 0.02D)
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
      baseCost = 7;
      costFactor = 0.38;
      initialCost = 6;
    }
  }

  private record StackState(int stacks, long lastHitMillis) {
  }
}
