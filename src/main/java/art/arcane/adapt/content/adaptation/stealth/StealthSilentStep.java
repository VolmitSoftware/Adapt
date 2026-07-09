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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StealthSilentStep extends SimpleAdaptation<StealthSilentStep.Config> {
  private final Map<UUID, Boolean> dimmed = new ConcurrentHashMap<>();
  private final Map<UUID, List<Long>> recentBackstabs = playerState();
  private final Map<UUID, Map<UUID, ThreatLevel>> threatGlows = playerState();
  private final Set<UUID> activeSneakers = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final Cooldowns moveScanCooldown = cooldowns();
  private final Cooldowns redThreatCooldown = cooldowns();
  private volatile EnumSet<EntityType> blacklistCache;
  private volatile List<String> blacklistSource;

  public StealthSilentStep() {
    super("stealth-silent-step");
    registerConfiguration(Config.class);
    setIcon(Material.WHITE_WOOL);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_stealth_silent_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_stealth_silent_5in10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_silent_200", "stealth.silent-step.backstabs", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getStealthRadius(level)), 1);
    statLore(v, Form.pc(getMobBackstabMultiplier(level) - 1D, 0), 2);
    statLore(v, Form.pc(getPlayerBackstabMultiplier(level) - 1D, 0), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    clearDimming(player);
    clearThreatGlows(player);
    recentBackstabs.remove(id);
    activeSneakers.remove(id);
    moveScanCooldown.clear(id);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (e.isSneaking()) {
      activeSneakers.add(id);
      return;
    }

    activeSneakers.remove(id);
    moveScanCooldown.clear(id);
    clearDimming(p);
    clearThreatGlows(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player p)) {
      return;
    }

    if (getActiveLevel(p, Player::isSneaking) <= 0) {
      return;
    }

    if (isTargetBlacklistType(e.getEntity().getType())) {
      return;
    }

    e.setCancelled(true);
    if (e.getEntity() instanceof Mob mob && mob.getTarget() == p) {
      mob.setTarget(null);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    if (e.getFrom().getWorld() == e.getTo().getWorld()
        && e.getFrom().distanceSquared(e.getTo()) < Math.max(0D, getConfig().minimumMoveSquared)) {
      return;
    }
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      activeSneakers.remove(id);
      moveScanCooldown.clear(id);
      return;
    }

    activeSneakers.add(id);
    if (!moveScanCooldown.isReady(id, Math.max(20L, getConfig().targetDropScanIntervalMillis))) {
      return;
    }
    moveScanCooldown.mark(id);

    double radius = getStealthRadius(level);
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
      if (!(entity instanceof Mob mob)) {
        continue;
      }

      if (isTargetBlacklistType(mob.getType())) {
        continue;
      }

      if (mob.getTarget() == p) {
        mob.setTarget(null);
        xp(p, getConfig().xpPerTargetDrop);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player attacker = combat.attacker();
    LivingEntity target = combat.target();
    boolean unseen = attacker.hasPotionEffect(PotionEffectType.INVISIBILITY) || !isLookingAt(target, attacker);
    if (target == attacker || !unseen) {
      return;
    }

    int level = combat.level();
    double multiplier = (target instanceof Player) ? getPlayerBackstabMultiplier(level) : getMobBackstabMultiplier(level);
    e.setDamage(e.getDamage() * multiplier);
    xp(attacker, e.getDamage() * getConfig().xpPerBonusDamage);
    addStat(attacker, "stealth.silent-step.backstabs", 1);

    Location hit = target.getLocation().add(0, 1.0D, 0);
    int count = (int) Math.min(18, Math.round(6 * multiplier));
    FxEmitter impact = fx(hit, FxPriority.COMBAT)
        .burst(Particle.CRIT, count, 0.3D)
        .particle(Particle.DAMAGE_INDICATOR, Math.max(2, count / 3), 0, 0, 0, 0.25D, 0.05D)
        .burst(Particles.SMOKE, 3, 0.2D);
    if (multiplier >= 1.8D) {
      impact.ring(Particle.SWEEP_ATTACK, 0.8D, 6, 1.0D);
    }
    impact.chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.9F, Sound.ITEM_TRIDENT_HIT, 0.4F, 1.4F, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.2F, 1.8F);

    long now = System.currentTimeMillis();
    UUID uid = attacker.getUniqueId();
    recentBackstabs.computeIfAbsent(uid, k -> new ArrayList<>()).add(now);
    recentBackstabs.get(uid).removeIf(t -> now - t > 10000);
    if (recentBackstabs.get(uid).size() >= 5
        && AdaptConfig.get().isAdvancements()
        && !getPlayer(attacker).getData().isGranted("challenge_stealth_silent_5in10")) {
      getPlayer(attacker).getAdvancementHandler().grant("challenge_stealth_silent_5in10");
      timeline(attacker).duration(10).priority(FxPriority.TRANSITION).cullRadius(32)
          .frame((fx, tick, progress) -> {
            fx.ring(Particle.SOUL, 1.2D - progress, 12, 1.0D);
            fx.dustRing(1.2D - progress, 12, 1.0F);
            if (tick == 0) {
              fx.chord(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5F, 1.2F, Sound.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.5F);
            }
            if (progress >= 0.95D) {
              fx.particle(Particle.FLASH, 1, 0, 1.0D, 0, 0, 0);
            }
          }).start();
    }
  }

  @Override
  public void onTick() {
    Set<UUID> tracked = new HashSet<>(activeSneakers);
    for (UUID id : tracked) {
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline()) {
        activeSneakers.remove(id);
        moveScanCooldown.clear(id);
        continue;
      }

      int level = getActiveLevel(p, Player::isSneaking);
      if (level <= 0) {
        clearDimming(p);
        clearThreatGlows(p);
        activeSneakers.remove(id);
        moveScanCooldown.clear(id);
        continue;
      }

      p.setFallDistance(Math.min(p.getFallDistance(), getConfig().maxSilentFallDistance));
      ThreatSnapshot threatSnapshot = collectThreatSnapshot(p, level);
      if (threatSnapshot.canDetect.isEmpty()) {
        applyDimming(p, level);
      } else {
        clearDimming(p);
      }
      updateThreatGlows(p, threatSnapshot);
    }
  }

  private ThreatSnapshot collectThreatSnapshot(Player p, int level) {
    ThreatSnapshot snapshot = new ThreatSnapshot();
    double detectionLookDotThreshold = getDetectionLookDotThreshold();
    double mobRadius = getStealthRadius(level);
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), mobRadius, mobRadius, mobRadius)) {
      if (!(entity instanceof Mob mob)) {
        continue;
      }

      if (!getConfig().allMobsAffectStealthVisibility && !isTargetBlacklistType(mob.getType())) {
        continue;
      }

      snapshot.add(mob, getThreatLevel(mob, p, detectionLookDotThreshold));
    }

    double playerRadius = getPlayerDetectionRadius(level);
    for (Entity nearby : p.getWorld().getNearbyEntities(p.getLocation(), playerRadius, playerRadius, playerRadius)) {
      if (!(nearby instanceof Player other)) {
        continue;
      }
      if (other == p || other.isDead()) {
        continue;
      }

      snapshot.add(other, getThreatLevel(other, p, detectionLookDotThreshold));
    }

    return snapshot;
  }

  private void applyDimming(Player p, int level) {
    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getDimDurationTicks(level), getConfig().dimAmplifier, false, false, false), true);
    boolean wasDimmed = Boolean.TRUE.equals(dimmed.put(p.getUniqueId(), true));
    if (!wasDimmed) {
      fx(p.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 5, 0.25D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.25F, 0.6F);
    }
  }

  private void clearDimming(Player p) {
    if (dimmed.remove(p.getUniqueId()) != null) {
      p.removePotionEffect(PotionEffectType.DARKNESS);
    }
  }

  private void updateThreatGlows(Player p, ThreatSnapshot snapshot) {
    if (!getConfig().showThreatGlows) {
      clearThreatGlows(p);
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      clearThreatGlows(p);
      return;
    }

    UUID viewerId = p.getUniqueId();
    Map<UUID, ThreatLevel> active = threatGlows.computeIfAbsent(viewerId, k -> new java.util.concurrent.ConcurrentHashMap<>());

    List<UUID> stale = new ArrayList<>();
    for (UUID entityId : active.keySet()) {
      if (!snapshot.threats.containsKey(entityId)) {
        stale.add(entityId);
      }
    }

    for (UUID entityId : stale) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity != null) {
        try {
          glowingEntities.unsetGlowing(entity, p);
        } catch (ReflectiveOperationException ignored) {
          // Ignore reflective failures and continue clearing other entities.
        }
      }
      active.remove(entityId);
    }

    for (Map.Entry<UUID, ThreatLevel> entry : snapshot.threats.entrySet()) {
      UUID entityId = entry.getKey();
      ThreatLevel desired = entry.getValue();
      ThreatLevel current = active.get(entityId);
      if (desired == current) {
        continue;
      }

      if (desired == ThreatLevel.CAN_DETECT && current != ThreatLevel.CAN_DETECT
          && redThreatCooldown.isReady(p.getUniqueId(), 1000L)) {
        redThreatCooldown.mark(p.getUniqueId());
        fx(p.getLocation(), FxPriority.COMBAT).sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.3F, 0.5F);
      }

      Entity entity = snapshot.entities.get(entityId);
      if (entity == null) {
        entity = Bukkit.getEntity(entityId);
      }
      if (entity == null || !entity.isValid()) {
        continue;
      }

      try {
        glowingEntities.setGlowing(entity, p, getThreatColor(desired));
        active.put(entityId, desired);
      } catch (ReflectiveOperationException ignored) {
        // Ignore reflective failures and keep runtime behavior intact.
      }
    }

    if (active.isEmpty()) {
      threatGlows.remove(viewerId);
    }
  }

  private void clearThreatGlows(Player p) {
    Map<UUID, ThreatLevel> active = threatGlows.remove(p.getUniqueId());
    if (active == null || active.isEmpty()) {
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      return;
    }

    for (UUID entityId : active.keySet()) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity == null) {
        continue;
      }

      try {
        glowingEntities.unsetGlowing(entity, p);
      } catch (ReflectiveOperationException ignored) {
        // Ignore reflective failures and continue clearing other entities.
      }
    }
  }

  private ThreatLevel getThreatLevel(LivingEntity observer, LivingEntity target, double detectThreshold) {
    double lookDot = getLookDot(observer, target);
    double almostThreshold = Math.max(-1, detectThreshold - Math.max(0, getConfig().almostLookDotMargin));
    if (lookDot < almostThreshold) {
      return ThreatLevel.NONE;
    }

    if (!observer.hasLineOfSight(target)) {
      return ThreatLevel.NONE;
    }

    return lookDot >= detectThreshold ? ThreatLevel.CAN_DETECT : ThreatLevel.ALMOST_DETECT;
  }

  private double getDetectionLookDotThreshold() {
    return Math.max(-1, Math.min(1, getConfig().detectionLookDotThreshold));
  }

  private ChatColor getThreatColor(ThreatLevel level) {
    return switch (level) {
      case CAN_DETECT -> ChatColor.RED;
      case ALMOST_DETECT -> ChatColor.GRAY;
      default -> ChatColor.WHITE;
    };
  }

  private boolean isLookingAt(LivingEntity observer, LivingEntity target) {
    return getLookDot(observer, target) >= getConfig().lookDotThreshold;
  }

  private double getLookDot(LivingEntity observer, LivingEntity target) {
    Vector look = observer.getEyeLocation().getDirection().normalize();
    Vector toTarget = target.getEyeLocation().toVector().subtract(observer.getEyeLocation().toVector());
    if (toTarget.lengthSquared() <= 0.0001) {
      return 1;
    }

    toTarget.normalize();
    return look.dot(toTarget);
  }

  private boolean isTargetBlacklistType(EntityType type) {
    return type != null && resolveBlacklist().contains(type);
  }

  private EnumSet<EntityType> resolveBlacklist() {
    List<String> source = getConfig().targetingBlacklistTypes;
    EnumSet<EntityType> cache = blacklistCache;
    if (cache != null && source == blacklistSource) {
      return cache;
    }

    EnumSet<EntityType> built = EnumSet.noneOf(EntityType.class);
    for (String raw : source) {
      if (raw == null || raw.isBlank()) {
        continue;
      }

      try {
        built.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
      }
    }

    blacklistSource = source;
    blacklistCache = built;
    return built;
  }

  private double getStealthRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getPlayerDetectionRadius(int level) {
    return getConfig().playerDetectionRadiusBase + (getLevelPercent(level) * getConfig().playerDetectionRadiusFactor);
  }

  private int getDimDurationTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().dimDurationTicksBase + (getLevelPercent(level) * getConfig().dimDurationTicksFactor)));
  }

  private double getMobBackstabMultiplier(int level) {
    return getConfig().mobBackstabBase + (getLevelPercent(level) * getConfig().mobBackstabFactor);
  }

  private double getPlayerBackstabMultiplier(int level) {
    return getConfig().playerBackstabBase + (getLevelPercent(level) * getConfig().playerBackstabFactor);
  }

  private enum ThreatLevel {
    NONE,
    ALMOST_DETECT,
    CAN_DETECT
  }

  @ConfigDescription("Sneaking prevents hostile mob detection, and unseen hits deal backstab damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerDetectionRadiusFactor = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dimDurationTicksFactor = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Amplifier for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int dimAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mobBackstabFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabBase = 1.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double playerBackstabFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Look Dot Threshold for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lookDotThreshold = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Drop for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetDrop = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDamage = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Silent Fall Distance for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    float maxSilentFallDistance = 1.6f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shows nearby threats with per-player glowing while sneaking (red = can detect, gray = almost).", impact = "Enable to get visual awareness of entities that can or almost can spot you.")
    boolean showThreatGlows = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot margin below the full detection threshold used for gray 'almost detect' glow.", impact = "Higher values make gray warnings appear earlier; lower values make warnings stricter.")
    double almostLookDotMargin = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot threshold for stealth visibility checks while sneaking.", impact = "Lower values make crossing an entity's view count as seen more easily; higher values require a more direct look.")
    double detectionLookDotThreshold = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, all nearby mobs (including passive) can break hidden state when they have line-of-sight.", impact = "Enable to prevent stealth from feeling hidden in front of passive mobs like pigs.")
    boolean allMobsAffectStealthVisibility = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Entity types that are NOT ignored by stealth targeting suppression.", impact = "Mobs listed here can still detect/target sneaking players with Silent Step.")
    List<String> targetingBlacklistTypes = new ArrayList<>(List.of("WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"));
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum squared movement distance required before running target-drop scans.", impact = "Higher values skip tiny movement jitter and reduce move-event scan pressure.")
    double minimumMoveSquared = 0.0025;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between mob target-drop scans while sneaking.", impact = "Lower values react faster but increase nearby-entity scan frequency.")
    long targetDropScanIntervalMillis = 120;

    public Config() {
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 2;
      initialCost = 3;
    }
  }

  private static class ThreatSnapshot {
    private final Map<UUID, ThreatLevel> threats = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Entity> entities = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ThreatLevel> canDetect = new java.util.concurrent.ConcurrentHashMap<>();

    private void add(Entity entity, ThreatLevel level) {
      if (entity == null || level == ThreatLevel.NONE) {
        return;
      }

      UUID id = entity.getUniqueId();
      entities.put(id, entity);
      ThreatLevel existing = threats.get(id);
      if (existing == null || level.ordinal() > existing.ordinal()) {
        threats.put(id, level);
      }

      if (level == ThreatLevel.CAN_DETECT) {
        canDetect.put(id, level);
      }
    }
  }
}
