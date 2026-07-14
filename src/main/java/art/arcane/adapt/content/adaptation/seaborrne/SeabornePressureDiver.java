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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class SeabornePressureDiver extends SimpleAdaptation<SeabornePressureDiver.Config> {
  private static final long ENTRY_CHECK_INTERVAL_MS = 250L;
  private static final int REFRESH_BATCH_SIZE = 128;
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns absorbFx = cooldowns();
  private final Map<UUID, Boolean> deep = playerState();
  private final Map<UUID, Boolean> deepTier = playerState();
  private final Map<UUID, Long> nextRefreshAt = playerState();
  private final Map<UUID, Long> lastDeepUpdateAt = playerState();
  private final Queue<UUID> activeQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();

  public SeabornePressureDiver() {
    super("seaborne-pressure-diver");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.pressure_diver");
    setIcon(Material.NAUTILUS_SHELL);
    setInterval(20);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_seaborne_pressure_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_seaborne_pressure_1k", "seaborne.pressure-diver.deep-blocks-mined", 1000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDepthThreshold(level), 1), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
    statLore(v, Form.pc(getFatigueTrimChance(level), 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !p.isInWater() || !isDeepEnough(p, level)) {
        return;
      }

      e.setDamage(e.getDamage() * (1D - getDamageReduction(level)));
      if (!absorbFx.isReady(p.getUniqueId(), 400L)) {
        return;
      }

      absorbFx.mark(p.getUniqueId());
      fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
          .ring(Particle.BUBBLE, 0.7D, 10, 0.0D)
          .dustRing(0.7D, 8, 1.0F)
          .chord(Sound.ITEM_SHIELD_BLOCK, 0.4F, 0.8F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 0.9F);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!p.isInWater()) {
      if (deep.containsKey(id)) {
        clearDepthState(id, true);
      }
      return;
    }
    long now = System.currentTimeMillis();
    Long due = nextRefreshAt.get(id);
    if (due != null && due > now) {
      return;
    }
    long interval = deep.getOrDefault(id, false)
        ? refreshIntervalMillis(getConfig().effectTicks)
        : ENTRY_CHECK_INTERVAL_MS;
    nextRefreshAt.put(id, now + interval);
    updateDepthPlayer(p, now);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearDepthState(e.getPlayer().getUniqueId(), true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (!deep.getOrDefault(p.getUniqueId(), false) || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "seaborne.pressure-diver.deep-blocks-mined", 1);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    int attempts = Math.min(REFRESH_BATCH_SIZE, queuedPlayers.size());
    for (int i = 0; i < attempts; i++) {
      UUID id = activeQueue.poll();
      if (id == null) {
        break;
      }
      queuedPlayers.remove(id);
      Long due = nextRefreshAt.get(id);
      if (due != null && due > now) {
        queuePlayer(id);
        continue;
      }
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline()) {
        clearDepthState(id, true);
        continue;
      }
      nextRefreshAt.put(id, now + refreshIntervalMillis(getConfig().effectTicks));
      withPlayerThread(p, () -> updateDepthPlayer(p, System.currentTimeMillis()));
      queuePlayer(id);
    }
  }

  private void updateDepthPlayer(Player p, long now) {
    UUID id = p.getUniqueId();
    int level = getActiveLevel(p);
    if (level <= 0 || !p.isInWater()) {
      clearDepthState(id, false);
      nextRefreshAt.put(id, now + ENTRY_CHECK_INTERVAL_MS);
      return;
    }

    double depth = p.getWorld().getSeaLevel() - p.getEyeLocation().getY();
    if (depth < getDepthThreshold(level)) {
      clearDepthState(id, false);
      nextRefreshAt.put(id, now + ENTRY_CHECK_INTERVAL_MS);
      return;
    }

    Long previous = lastDeepUpdateAt.put(id, now);
    double elapsedTicks = elapsedActiveTicks(previous, now);
    boolean inDeepTier = depth >= getDeepThreshold(level);
    applyDepthBuffs(p, level, inDeepTier ? 1 : 0, elapsedTicks);
    awardDepthXp(p);

    if (!deep.getOrDefault(id, false)) {
      deep.put(id, true);
      emitPressureSeal(p);
    }

    boolean wasTier = deepTier.getOrDefault(id, false);
    if (inDeepTier && !wasTier) {
      deepTier.put(id, true);
      emitDeepTier(p);
    } else if (!inDeepTier && wasTier) {
      deepTier.put(id, false);
    }
    queuePlayer(id);
  }

  private void clearDepthState(UUID id, boolean removeSchedule) {
    deep.remove(id);
    deepTier.remove(id);
    lastDeepUpdateAt.remove(id);
    if (queuedPlayers.remove(id)) {
      activeQueue.remove(id);
    }
    if (removeSchedule) {
      nextRefreshAt.remove(id);
    }
  }

  private void queuePlayer(UUID id) {
    if (deep.getOrDefault(id, false) && queuedPlayers.add(id)) {
      activeQueue.add(id);
    }
  }

  private void applyDepthBuffs(Player p, int level, int resistanceAmp, double elapsedTicks) {
    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, getConfig().effectTicks, resistanceAmp, false, false, true), true);
    p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, getConfig().effectTicks, 0, false, false, true), true);

    PotionEffect fatigue = p.getPotionEffect(PotionEffectType.MINING_FATIGUE);
    if (fatigue == null) {
      return;
    }

    int trimCount = sampleProcCount(getFatigueTrimChance(level), elapsedTicks, ThreadLocalRandom.current());
    if (trimCount <= 0) {
      return;
    }

    int reducedAmp = Math.max(0, fatigue.getAmplifier() - (getFatigueTrimAmount(level) * trimCount));
    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
        Math.max(20, Math.min(fatigue.getDuration(), getConfig().fatigueReplaceTicks)),
        reducedAmp,
        false,
        true,
        true), true);
    fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
        .particle(Particle.CRIT, 4, 0D, 0.3D, 0D, 0.3D, 0.05D)
        .dustBurst(3, 0.25D, 0.8F)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F);
  }

  private void awardDepthXp(Player p) {
    UUID id = p.getUniqueId();
    if (!xpCooldowns.isReady(id, getConfig().xpPulseCooldownMillis)) {
      return;
    }

    xpCooldowns.mark(id);
    xp(p, getConfig().xpPerDepthPulse);
    fx(p.getLocation(), FxPriority.AMBIENT).sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2F, 1.4F);
  }

  private void emitPressureSeal(Player p) {
    timeline(p)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((f, tick, progress) -> {
          double radius = 1.2D - (1.0D * progress);
          f.ring(Particle.BUBBLE, radius, 14, 0.4D);
          f.dustRing(radius, 8, 1.0F);
          if (tick == 0) {
            f.chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.5F, 1.4F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.3F, 0.8F);
          } else if (tick == 6) {
            f.sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.35F, 0.7F);
          }
        })
        .start();
  }

  private void emitDeepTier(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .dome(Particle.SOUL, 1.0D, 6)
        .particle(Particle.GLOW, 4, 0D, 0.5D, 0D, 0.4D, 0.01D)
        .sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.4F, 0.9F);
  }

  private boolean isDeepEnough(Player p, int level) {
    double seaLevel = p.getWorld().getSeaLevel();
    double depth = seaLevel - p.getEyeLocation().getY();
    return depth >= getDepthThreshold(level);
  }

  private double getDepthThreshold(int level) {
    return Math.max(2, getConfig().depthThresholdBase - (getLevelPercent(level) * getConfig().depthThresholdFactor));
  }

  private double getDeepThreshold(int level) {
    return Math.max(4, getConfig().deepThresholdBase - (getLevelPercent(level) * getConfig().deepThresholdFactor));
  }

  private double getDamageReduction(int level) {
    return Math.min(getConfig().maxDamageReduction, getConfig().damageReductionBase + (getLevelPercent(level) * getConfig().damageReductionFactor));
  }

  private double getFatigueTrimChance(int level) {
    return Math.min(1.0, getConfig().fatigueTrimChanceBase + (getLevelPercent(level) * getConfig().fatigueTrimChanceFactor));
  }

  private int getFatigueTrimAmount(int level) {
    return Math.max(1, (int) Math.round(getConfig().fatigueTrimAmountBase + (getLevelPercent(level) * getConfig().fatigueTrimAmountFactor)));
  }

  static long refreshIntervalMillis(int effectTicks) {
    return Math.max(250L, Math.min(750L, Math.max(1, effectTicks) * 25L));
  }

  static double elapsedActiveTicks(Long previousUpdateAt, long now) {
    if (previousUpdateAt == null) {
      return 1D;
    }
    if (now <= previousUpdateAt) {
      return 0D;
    }
    return Math.min(40D, (now - previousUpdateAt) / 50D);
  }

  static double accumulatedChance(double perTickChance, double elapsedTicks) {
    double chance = Math.max(0D, Math.min(1D, perTickChance));
    if (chance <= 0D || elapsedTicks <= 0D) {
      return 0D;
    }
    return 1D - Math.pow(1D - chance, elapsedTicks);
  }

  private static int sampleProcCount(double perTickChance, double elapsedTicks, ThreadLocalRandom random) {
    double chance = Math.max(0D, Math.min(1D, perTickChance));
    double ticks = Math.max(0D, Math.min(40D, elapsedTicks));
    int wholeTicks = (int) Math.floor(ticks);
    int procs = 0;
    for (int i = 0; i < wholeTicks; i++) {
      if (random.nextDouble() < chance) {
        procs++;
      }
    }

    double partialTick = ticks - wholeTicks;
    if (partialTick > 0D && random.nextDouble() < accumulatedChance(chance, partialTick)) {
      procs++;
    }
    return procs;
  }

  @ConfigDescription("Gain depth scaling protection underwater and partially counter mining fatigue in deep ocean play.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthThresholdBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthThresholdFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepThresholdBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Deep Threshold Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepThresholdFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionFactor = 0.26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Damage Reduction for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageReduction = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimChanceBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Chance Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Base for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimAmountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Trim Amount Factor for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fatigueTrimAmountFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int effectTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fatigue Replace Ticks for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int fatigueReplaceTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Depth Pulse for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDepthPulse = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Pulse Cooldown Millis for the Seaborne Pressure Diver adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long xpPulseCooldownMillis = 3000;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
