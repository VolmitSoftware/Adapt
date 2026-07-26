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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import art.arcane.adapt.util.reflect.events.api.entity.EntityMountEvent;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TamingMountedTactics extends SimpleAdaptation<TamingMountedTactics.Config> {
  private static final int REFRESH_BATCH_SIZE = 64;
  private static final long REFRESH_INTERVAL_MS = 500L;
  private static final long MOUNT_BUFF_DURATION_TICKS = 60L;
  private static final String SLOT_SPEED = "speed";
  private static final String SLOT_JUMP = "jump";
  private final Map<UUID, Location> lastMountedLocation = playerState();
  private final Map<UUID, Boolean> wasSprinting = playerState();
  private final Map<UUID, Long> nextRefreshAt = playerState();
  private final Map<UUID, Long> nextBuffAssertAt = playerState();
  private final Cooldowns emberCd = cooldowns();
  private final Cooldowns hitCd = cooldowns();
  private final Queue<UUID> activeQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();

  public TamingMountedTactics() {
    super("tame-mounted-tactics");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.mounted_tactics");
    setIcon(Material.SADDLE);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SADDLE)
        .key("challenge_taming_mounted_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_HORSE_ARMOR)
        .key("challenge_taming_mounted_50k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_mounted_200", "taming.mounted-tactics.mounted-kills", 200, 400);
    registerMilestone("challenge_taming_mounted_50k", "taming.mounted-tactics.distance", 50000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getMountedDamageBonus(level), 0), 1);
    statLore(v, Form.pc(getMountedDamageReduction(level), 0), 2);
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
        clearPlayerState(id);
        continue;
      }
      nextRefreshAt.put(id, now + REFRESH_INTERVAL_MS);
      withPlayerThread(p, () -> {
        Entity vehicle = p.getVehicle();
        if (updateMountedPlayer(p, vehicle, null)) {
          queuePlayer(id);
        } else {
          clearPlayerState(id);
        }
      });
      queuePlayer(id);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    refreshFromEvent(e.getPlayer(), null, null);
  }

  @EventHandler
  public void on(PlayerToggleSprintEvent e) {
    refreshFromEvent(e.getPlayer(), null, e.isSprinting());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    removeMountBuffs(e.getPlayer().getVehicle());
    clearPlayerState(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    removeMountBuffs(e.getEntity().getVehicle());
    clearPlayerState(e.getEntity().getUniqueId());
  }

  @EventHandler
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.getNewGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      removeMountBuffs(e.getPlayer().getVehicle());
    }
  }

  @ReflectiveHandler
  public void on(EntityMountEvent e) {
    if (e.getEntity() instanceof Player p) {
      refreshFromEvent(p, e.getMount(), null);
    }
  }

  @ReflectiveHandler
  public void on(EntityDismountEvent e) {
    if (e.getEntity() instanceof Player p) {
      removeMountBuffs(e.getDismounted());
      clearPlayerState(p.getUniqueId());
    }
  }

  private void refreshFromEvent(Player p, Entity vehicleOverride, Boolean sprintingOverride) {
    Entity vehicle = vehicleOverride == null ? p.getVehicle() : vehicleOverride;
    if (vehicle == null && nextRefreshAt.isEmpty()) {
      return;
    }

    UUID id = p.getUniqueId();
    if (vehicle == null && !nextRefreshAt.containsKey(id)) {
      return;
    }
    if (updateMountedPlayer(p, vehicle, sprintingOverride)) {
      if (!requiresStationaryRefresh(vehicle)) {
        clearRefreshSchedule(id);
        return;
      }
      nextRefreshAt.put(id, System.currentTimeMillis() + REFRESH_INTERVAL_MS);
      queuePlayer(id);
      return;
    }
    clearPlayerState(id);
  }

  private boolean updateMountedPlayer(Player p, Entity vehicle, Boolean sprintingOverride) {
    UUID id = p.getUniqueId();
    int level = getActiveLevel(p);
    if (level <= 0) {
      removeMountBuffs(vehicle);
      return false;
    }

    if (vehicle == null) {
      return false;
    }

    Location current = p.getLocation();
    Location last = lastMountedLocation.put(id, current);
    if (last != null && last.getWorld() == current.getWorld()) {
      double distance = last.distance(current);
      if (distance > 0.1 && distance < 100) {
        addStat(p, "taming.mounted-tactics.distance", distance);
      }
    }

    boolean sprinting = sprintingOverride == null ? p.isSprinting() : sprintingOverride;
    Boolean sprintingBefore = wasSprinting.put(id, sprinting);
    if (sprinting && (sprintingBefore == null || !sprintingBefore)) {
      fx(vehicle.getLocation(), FxPriority.TRAIL)
          .particle(Particle.POOF, 3, 0, 0.1D, 0, 0.15D, 0.02D)
          .dustBurst(Color.fromRGB(0x9A7B4F), 4, 0.25D, 1.0F)
          .sound(Sound.ENTITY_HORSE_GALLOP, 0.4F, 1.1F);
    }

    if (vehicle instanceof AbstractHorse horse) {
      if (shouldAssertBuffs(id)) {
        applyMountBuff(horse, SLOT_SPEED, Attributes.MOVEMENT_SPEED, getHorseSpeedBonus(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyMountBuff(horse, SLOT_JUMP, Attributes.JUMP_STRENGTH, getHorseJumpBonus(level), AttributeModifier.Operation.ADD_SCALAR);
      }
      if (sprinting) {
        applyHorizontalPush(horse, current.getDirection(), 0.8D, getHorsePush(level));
      }
      return true;
    }

    if (vehicle instanceof Strider strider) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false, true), true);
      if (shouldAssertBuffs(id)) {
        applyMountBuff(strider, SLOT_SPEED, Attributes.MOVEMENT_SPEED, getStriderSpeedBonus(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
      }
      Location striderLocation = strider.getLocation();
      if (striderLocation.getBlock().getType() == Material.LAVA
          || striderLocation.getBlock().getRelative(BlockFace.DOWN).getType() == Material.LAVA) {
        strider.setShivering(false);
        if (emberCd.isReady(id, 1000)) {
          emberCd.mark(id);
          fx(strider, FxPriority.AMBIENT)
              .particle(Particle.FLAME, 2, 0, 0.3D, 0, 0.1D, 0.01D)
              .particle(Particle.SCRAPE, 1, 0, 0.4D, 0, 0.1D, 0);
        }
      }
      return true;
    }

    if (vehicle instanceof Pig pig) {
      p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 25, getPigResistanceAmplifier(level), false, false, true), true);
      if (sprinting) {
        applyHorizontalPush(pig, current.getDirection(), 0.7D, getPigPush(level));
      }
    }
    return true;
  }

  private void queuePlayer(UUID id) {
    if (nextRefreshAt.containsKey(id) && queuedPlayers.add(id)) {
      activeQueue.add(id);
    }
  }

  private void clearRefreshSchedule(UUID id) {
    nextRefreshAt.remove(id);
    if (queuedPlayers.remove(id)) {
      activeQueue.remove(id);
    }
  }

  private void clearPlayerState(UUID id) {
    lastMountedLocation.remove(id);
    wasSprinting.remove(id);
    nextBuffAssertAt.remove(id);
    clearRefreshSchedule(id);
    emberCd.clear(id);
    hitCd.clear(id);
  }

  private boolean shouldAssertBuffs(UUID id) {
    long now = System.currentTimeMillis();
    Long due = nextBuffAssertAt.get(id);
    if (due != null && now < due) {
      return false;
    }

    nextBuffAssertAt.put(id, now + REFRESH_INTERVAL_MS);
    return true;
  }

  private void applyMountBuff(LivingEntity mount, String slot, Attribute attribute, double amount, AttributeModifier.Operation operation) {
    if (amount <= 0) {
      AdaptAttributeService.get().remove(mount, getName(), slot, attribute);
      return;
    }

    AdaptAttributeService.get().applyTimed(mount, getName(), slot, attribute, amount, operation, MOUNT_BUFF_DURATION_TICKS);
  }

  private void removeMountBuffs(Entity mount) {
    if (mount instanceof LivingEntity living) {
      AdaptAttributeService.get().removeAll(living, getName());
    }
  }

  private void applyHorizontalPush(Entity mount, Vector direction, double damping, double amount) {
    direction.setY(0);
    if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
      return;
    }
    Vector push = direction.normalize().multiply(Math.max(0D, amount));
    mount.setVelocity(mount.getVelocity().multiply(damping).add(push));
  }

  private boolean isTacticalMount(Entity vehicle) {
    return vehicle instanceof AbstractHorse || vehicle instanceof Strider || vehicle instanceof Pig;
  }

  private boolean requiresStationaryRefresh(Entity vehicle) {
    return isTacticalMount(vehicle);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() instanceof Player p
        && p.getVehicle() != null
        && hasActiveAdaptation(p)) {
      addStat(p, "taming.mounted-tactics.mounted-kills", 1);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player attacker && isTacticalMount(attacker.getVehicle())) {
      int level = getActiveLevel(attacker);
      if (level > 0) {
        if (!canDamageTarget(attacker, e.getEntity())) {
          return;
        }

        e.setDamage(e.getDamage() * (1D + getMountedDamageBonus(level)));
        xp(attacker, e.getDamage() * getConfig().xpPerMountedDamage);
        if (hitCd.isReady(attacker.getUniqueId(), 200)) {
          hitCd.mark(attacker.getUniqueId());
          int count = Math.min(9, 4 + level);
          fx(e.getEntity().getLocation().add(0, 0.8D, 0), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, count, 0.35D)
              .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
              .chord(Sound.ENTITY_HORSE_BREATHE, 0.4F, 0.9F, Sound.ITEM_TRIDENT_RETURN, 0.35F, 1.3F);
        }
      }
    }

    if (e.getEntity() instanceof Player defender && isTacticalMount(defender.getVehicle())) {
      int level = getActiveLevel(defender);
      if (level > 0) {
        e.setDamage(e.getDamage() * (1D - getMountedDamageReduction(level)));
        Vector face = defender.getLocation().getDirection().setY(0);
        Location front = defender.getLocation().add(0, 1, 0);
        if (face.lengthSquared() > 1.0E-6D) {
          front.add(face.normalize().multiply(0.6D));
        }
        fx(front, FxPriority.COMBAT)
            .dustBurst(Color.fromRGB(0x8FA3B0), 4, 0.35D, 1.0F)
            .sound(Sound.ITEM_SHIELD_BLOCK, 0.3F, 1.4F);
      }
    }
  }

  private double getMountedDamageBonus(int level) {
    return Math.min(getConfig().maxMountedDamageBonus, getConfig().mountedDamageBonusBase + (getLevelPercent(level) * getConfig().mountedDamageBonusFactor));
  }

  private double getMountedDamageReduction(int level) {
    return Math.min(getConfig().maxMountedDamageReduction, getConfig().mountedDamageReductionBase + (getLevelPercent(level) * getConfig().mountedDamageReductionFactor));
  }

  private int getHorseSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().horseSpeedAmplifierBase + (getLevelPercent(level) * getConfig().horseSpeedAmplifierFactor)));
  }

  private int getStriderSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().striderSpeedAmplifierBase + (getLevelPercent(level) * getConfig().striderSpeedAmplifierFactor)));
  }

  private int getPigResistanceAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().pigResistanceAmplifierBase + (getLevelPercent(level) * getConfig().pigResistanceAmplifierFactor)));
  }

  private double getHorseSpeedBonus(int level) {
    return mountSpeedBonus(getHorseSpeedAmplifier(level), getConfig().horseBaseHorizontalSpeed, getConfig().mountMaxHorizontalSpeed);
  }

  private double getStriderSpeedBonus(int level) {
    return mountSpeedBonus(getStriderSpeedAmplifier(level), getConfig().striderBaseHorizontalSpeed, getConfig().mountMaxHorizontalSpeed);
  }

  private double getHorseJumpBonus(int level) {
    return Math.max(0D, getConfig().horseJumpStrengthBonusBase + (getLevelPercent(level) * getConfig().horseJumpStrengthBonusFactor));
  }

  static double mountSpeedBonus(int amplifier, double baseSpeed, double maxSpeed) {
    double scalar = VelocitySpeed.speedAmplifierScalar(amplifier);
    if (baseSpeed > 0 && maxSpeed > 0) {
      scalar = Math.min(scalar, maxSpeed / baseSpeed);
    }
    return Math.max(0D, scalar - 1.0D);
  }

  private double getHorsePush(int level) {
    return getConfig().horsePushBase + (getLevelPercent(level) * getConfig().horsePushFactor);
  }

  private double getPigPush(int level) {
    return getConfig().pigPushBase + (getLevelPercent(level) * getConfig().pigPushFactor);
  }

  @ConfigDescription("Gain mount-specific combat and control bonuses while riding.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageBonusBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageBonusFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Bonus for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxMountedDamageBonus = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageReductionBase = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageReductionFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Reduction for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxMountedDamageReduction = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horseSpeedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horseSpeedAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horse jump strength bonus as a percent of the mount's own jump strength.", impact = "Higher values increase mounted horse jump height at minimum level.")
    double horseJumpStrengthBonusBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional horse jump strength bonus scaling with level percent.", impact = "Higher values increase mounted horse jump height at higher levels.")
    double horseJumpStrengthBonusFactor = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigResistanceAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigResistanceAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed target used for horse mounted speed scaling.", impact = "Higher values increase steady mounted horse acceleration when moving forward.")
    double horseBaseHorizontalSpeed = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed target used for strider mounted speed scaling.", impact = "Higher values increase steady mounted strider acceleration when moving forward.")
    double striderBaseHorizontalSpeed = 0.24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force on mounts.", impact = "Acts as a hard cap to prevent runaway mounted momentum.")
    double mountMaxHorizontalSpeed = 0.78;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horsePushBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horsePushFactor = 0.16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigPushBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigPushFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mounted Damage for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMountedDamage = 1.5;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
