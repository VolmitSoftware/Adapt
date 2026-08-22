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
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SeabornePressureDiver extends SimpleAdaptation<SeabornePressureDiver.Config> {
  private static final long ENTRY_CHECK_INTERVAL_MS = 250L;
  private static final int REFRESH_BATCH_SIZE = 128;
  private static final String FATIGUE_SLOT = "fatigue";
  private static final String ABSORPTION_SLOT = "pressure-absorption";
  private static final double SUBMERGED_MINING_BASE = 0.2D;
  private static final double[] FATIGUE_LEVEL_FACTORS = {0.3D, 0.09D, 0.0027D, 8.1E-4D};
  private static final double FATIGUE_TRIM_HORIZON_TICKS = 20D;
  private static final int MAX_EFFECT_DURATION_TICKS = 20 * 60;
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns absorbFx = cooldowns();
  private final Map<UUID, Boolean> deep = playerState();
  private final Map<UUID, Boolean> deepTier = playerState();
  private final Map<UUID, Boolean> fatigueCountered = playerState();
  private final Map<UUID, Double> absorptionCapacity = playerState();
  private final Map<UUID, Long> nextRefreshAt = playerState();
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
  public void unregister() {
    List<UUID> absorptionPlayers = new ArrayList<>(absorptionCapacity.keySet());
    super.unregister();
    deep.clear();
    deepTier.clear();
    fatigueCountered.clear();
    absorptionCapacity.clear();
    nextRefreshAt.clear();
    activeQueue.clear();
    queuedPlayers.clear();
    for (UUID playerId : absorptionPlayers) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null && !J.runEntity(player, () -> removeAbsorptionCapacity(player))) {
        AdaptAttributeService.get().remove(player, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION);
      }
    }
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDepthThreshold(level), 1), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
    statLore(v, Form.pc(getFatigueTrimChance(level), 0), 3);
    statLore(v, Form.f(getAbsorptionHealth(level) / 2D, 1) + " ♥", 4);
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
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!p.isInWater()) {
      if (deep.isEmpty()) {
        return;
      }
      UUID surfacedId = p.getUniqueId();
      if (deep.containsKey(surfacedId)) {
        clearDepthState(surfacedId, true);
      }
      return;
    }

    UUID id = p.getUniqueId();
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
  public boolean hasTickDemand() {
    return !queuedPlayers.isEmpty() || !activeQueue.isEmpty();
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

    boolean inDeepTier = depth >= getDeepThreshold(level);
    applyDepthBuffs(p, level, inDeepTier ? 1 : 0);
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
    if (absorptionCapacity.remove(id) != null) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        removeAbsorptionCapacity(p);
      }
    }
    if (fatigueCountered.remove(id) != null) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        AdaptAttributeService.get().remove(p, getName(), FATIGUE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
      }
    }
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

  private void applyDepthBuffs(Player p, int level, int resistanceAmp) {
    int effectTicks = effectDurationTicks(getConfig().effectTicks);
    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, effectTicks, resistanceAmp, false, false, true), true);
    updateAbsorption(p, level);
    updateFatigueCounter(p, level);
  }

  private void updateAbsorption(Player p, int level) {
    if (Attributes.MAX_ABSORPTION == null) {
      return;
    }

    UUID id = p.getUniqueId();
    double capacity = getAbsorptionHealth(level);
    if (capacity <= 0D) {
      if (absorptionCapacity.remove(id) != null) {
        removeAbsorptionCapacity(p);
      }
      return;
    }

    Double previous = absorptionCapacity.put(id, capacity);
    AdaptAttributeService.get().apply(p, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION,
        capacity, AttributeModifier.Operation.ADD_NUMBER);
    AttributeInstance attribute = p.getAttribute(Attributes.MAX_ABSORPTION);
    if (attribute == null) {
      absorptionCapacity.remove(id);
      return;
    }

    double previousCapacity = previous == null ? 0D : previous;
    double current = p.getAbsorptionAmount();
    double target = absorptionFillTarget(current, previousCapacity, capacity, attribute.getValue());
    if (Double.compare(target, current) != 0) {
      p.setAbsorptionAmount(target);
    }
  }

  private void removeAbsorptionCapacity(Player p) {
    AdaptAttributeService.get().remove(p, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION);
    AttributeInstance attribute = Attributes.MAX_ABSORPTION == null ? null : p.getAttribute(Attributes.MAX_ABSORPTION);
    if (attribute != null && p.getAbsorptionAmount() > attribute.getValue()) {
      p.setAbsorptionAmount(Math.max(0D, attribute.getValue()));
    }
  }

  private void updateFatigueCounter(Player p, int level) {
    UUID id = p.getUniqueId();
    PotionEffect fatigue = p.getPotionEffect(PotionEffectType.MINING_FATIGUE);
    if (fatigue == null) {
      clearFatigueCounter(p, id);
      return;
    }

    long durationTicks = counterDurationTicks(fatigue.getDuration(), getConfig().fatigueCounterDurationTicks);
    if (durationTicks <= 0) {
      clearFatigueCounter(p, id);
      return;
    }

    double bonus = fatigueCounterBonus(getFatigueTrimChance(level), getFatigueTrimAmount(level), fatigue.getAmplifier());
    if (bonus <= 0D) {
      clearFatigueCounter(p, id);
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getName(), FATIGUE_SLOT, Attributes.SUBMERGED_MINING_SPEED,
        bonus, AttributeModifier.Operation.ADD_NUMBER, durationTicks);
    if (fatigueCountered.put(id, true) == null) {
      fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
          .particle(Particle.CRIT, 4, 0D, 0.3D, 0D, 0.3D, 0.05D)
          .dustBurst(3, 0.25D, 0.8F)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.6F);
    }
  }

  private void clearFatigueCounter(Player p, UUID id) {
    if (fatigueCountered.remove(id) != null) {
      AdaptAttributeService.get().remove(p, getName(), FATIGUE_SLOT, Attributes.SUBMERGED_MINING_SPEED);
    }
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

  private double getAbsorptionHealth(int level) {
    return absorptionHealth(getConfig().absorptionHealthBase, getConfig().absorptionHealthFactor, level, getMaxLevel());
  }

  static double absorptionHealth(double base, double factor, int level, int maxLevel) {
    if (!Double.isFinite(base) || !Double.isFinite(factor) || level <= 0 || maxLevel <= 0) {
      return 0D;
    }
    double progress = maxLevel == 1 ? 1D : Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return Math.max(0D, base + (factor * progress));
  }

  static double absorptionFillTarget(double current, double previousCapacity, double capacity, double maximum) {
    if (!Double.isFinite(current) || !Double.isFinite(previousCapacity) || !Double.isFinite(capacity)
        || !Double.isFinite(maximum) || maximum <= 0D) {
      return Math.max(0D, Math.min(Double.isFinite(current) ? current : 0D, Double.isFinite(maximum) ? maximum : 0D));
    }
    double gainedCapacity = Math.max(0D, capacity - Math.max(0D, previousCapacity));
    return Math.max(0D, Math.min(maximum, Math.max(0D, current) + gainedCapacity));
  }

  static long refreshIntervalMillis(int effectTicks) {
    return Math.max(250L, Math.min(750L, Math.max(1, effectTicks) * 25L));
  }

  static long counterDurationTicks(int fatigueDurationTicks, int configuredCounterTicks) {
    if (configuredCounterTicks <= 0) {
      return 0L;
    }
    long boundedCounterTicks = Math.min(MAX_EFFECT_DURATION_TICKS, configuredCounterTicks);
    if (fatigueDurationTicks < 0) {
      return Math.max(20L, boundedCounterTicks);
    }
    return Math.max(20L, Math.min(fatigueDurationTicks, boundedCounterTicks));
  }

  static int effectDurationTicks(int configuredTicks) {
    return Math.max(20, Math.min(MAX_EFFECT_DURATION_TICKS, configuredTicks));
  }

  static double fatigueCounterBonus(double trimChance, int trimAmount, int fatigueAmplifier) {
    double chance = Math.max(0D, Math.min(1D, trimChance));
    double trimPerSecond = chance * FATIGUE_TRIM_HORIZON_TICKS * Math.max(0, trimAmount);
    int amplifier = Math.max(0, fatigueAmplifier);
    double reducedAmplifier = Math.max(0D, amplifier - trimPerSecond);
    double current = fatigueFactor(amplifier);
    double target = fatigueFactor(reducedAmplifier);
    if (target <= current) {
      return 0D;
    }
    return SUBMERGED_MINING_BASE * (target / current - 1D);
  }

  static double fatigueFactor(double amplifier) {
    double clamped = Math.max(0D, Math.min(3D, amplifier));
    int lower = (int) Math.floor(clamped);
    int upper = (int) Math.ceil(clamped);
    double lowerLog = Math.log(FATIGUE_LEVEL_FACTORS[lower]);
    double upperLog = Math.log(FATIGUE_LEVEL_FACTORS[upper]);
    return Math.exp(lowerLog + (upperLog - lowerLog) * (clamped - lower));
  }

  @ConfigDescription("Gain absorption hearts and depth-scaling protection underwater while partially countering mining fatigue.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Required eye depth below sea level in blocks at the lowest adaptation level.", impact = "Higher values require a deeper dive before any Pressure Diver buff activates.")
    double depthThresholdBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks removed from the activation depth requirement across adaptation levels.", impact = "Higher values let experienced divers activate the buff closer to the surface; the threshold floors at 2 blocks.")
    double depthThresholdFactor = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Required eye depth below sea level for the stronger Resistance tier at the lowest level.", impact = "Higher values reserve Resistance II for deeper water.")
    double deepThresholdBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks removed from the stronger Resistance tier requirement across levels.", impact = "Higher values make the deep tier activate closer to the surface; the threshold floors at 4 blocks.")
    double deepThresholdFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Incoming damage fraction removed while Pressure Diver is active at the lowest level.", impact = "0.12 removes 12% of incoming damage before the rolling Resistance effect is considered.")
    double damageReductionBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional incoming damage reduction gained across adaptation levels.", impact = "Higher values scale the depth protection more strongly toward max level.")
    double damageReductionFactor = 0.26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum incoming damage reduction fraction from Pressure Diver itself.", impact = "Caps the direct reduction independently of the Resistance effect.")
    double maxDamageReduction = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Absorption health points granted when Pressure Diver activates at level 1; two health points display as one heart.", impact = "Higher values give low-level divers more temporary bonus hearts without refilling consumed absorption during refreshes.")
    double absorptionHealthBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorption health points granted by maximum adaptation level.", impact = "Higher values increase Pressure Diver's visible bonus-heart capacity as it levels.")
    double absorptionHealthFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest-level per-tick Mining Fatigue trim chance represented by the deterministic speed counter.", impact = "Higher values offset more of Mining Fatigue II and above; Mining Fatigue I remains the floor.")
    double fatigueTrimChanceBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional Mining Fatigue trim chance gained across levels.", impact = "Higher values make the submerged mining-speed counter stronger at higher levels.")
    double fatigueTrimChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest-level Mining Fatigue amplifier steps trimmed by each conceptual proc.", impact = "Higher values counter stronger Mining Fatigue more aggressively.")
    double fatigueTrimAmountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional Mining Fatigue amplifier trim gained across levels.", impact = "Higher values increase high-level fatigue suppression.")
    double fatigueTrimAmountFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed Resistance effect.", impact = "Higher values give more refresh tolerance; the runtime checks at least every 15 ticks while active.")
    int effectTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed submerged mining-speed counter while Mining Fatigue is present.", impact = "Higher values tolerate refresh delays; zero disables fatigue countering.")
    int fatigueCounterDurationTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Seaborne XP granted on each active depth pulse.", impact = "Higher values level Seaborne faster while Pressure Diver stays active.")
    double xpPerDepthPulse = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between active-depth XP pulses.", impact = "Higher values reduce XP frequency without changing the defensive buffs.")
    long xpPulseCooldownMillis = 3000;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
