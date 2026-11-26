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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.event.entity.EntityMountEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
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
  private static final Id STRIDER = Id.minecraft("strider");
  private static final Id PIG = Id.minecraft("pig");
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private final Map<UUID, Loc> lastMountedLocation = playerState();
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
    setIcon(Id.minecraft("saddle"));
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("saddle"))
        .key("challenge_taming_mounted_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_horse_armor"))
        .key("challenge_taming_mounted_50k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_taming_mounted_200", "taming.mounted-tactics.mounted-kills", 200, 400);
    registerMilestone("challenge_taming_mounted_50k", "taming.mounted-tactics.distance", 50000, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    removeMountBuffs(player.getPlayer().vehicle());
    clearPlayerState(player.getPlayer().uuid());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.HANDLING, TamingPreferences.COMBAT);
  }

  @Override
  public void addStats(int level, StatLines v) {
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
      PlayerRef p = AdaptCore.platform().host().player(id);
      if (p == null || !p.isOnline()) {
        clearPlayerState(id);
        continue;
      }
      nextRefreshAt.put(id, now + REFRESH_INTERVAL_MS);
      withPlayerThread(p, () -> {
        EntityRef vehicle = p.vehicle();
        if (updateMountedPlayer(p, vehicle, null)) {
          queuePlayer(id);
        } else {
          clearPlayerState(id);
        }
      });
      queuePlayer(id);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    refreshFromEvent(e.player(), null, null);
  }

  @Handle
  public void on(PlayerToggleSprintEvent e) {
    refreshFromEvent(e.player(), null, e.isSprinting());
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    removeMountBuffs(e.player().vehicle());
    clearPlayerState(e.player().uuid());
  }

  @Handle
  public void on(PlayerDeathEvent e) {
    removeMountBuffs(e.player().vehicle());
    clearPlayerState(e.player().uuid());
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      removeMountBuffs(e.player().vehicle());
    }
  }

  @ReceiveCancelledEvents
  @Handle
  public void on(EntityMountEvent e) {
    if (e.entity() instanceof PlayerRef p) {
      refreshFromEvent(p, e.mount(), null);
    }
  }

  @ReceiveCancelledEvents
  @Handle
  public void on(EntityDismountEvent e) {
    if (e.entity() instanceof PlayerRef p) {
      removeMountBuffs(e.dismounted());
      clearPlayerState(p.uuid());
    }
  }

  private void refreshFromEvent(PlayerRef p, EntityRef vehicleOverride, Boolean sprintingOverride) {
    EntityRef vehicle = vehicleOverride == null ? p.vehicle() : vehicleOverride;
    if (vehicle == null && nextRefreshAt.isEmpty()) {
      return;
    }

    UUID id = p.uuid();
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

  private boolean updateMountedPlayer(PlayerRef p, EntityRef vehicle, Boolean sprintingOverride) {
    UUID id = p.uuid();
    int level = preferenceEnabled(p, TamingPreferences.HANDLING) ? mountedLevel(p, vehicle) : 0;
    if (level <= 0) {
      removeMountBuffs(vehicle);
      return false;
    }

    if (vehicle == null) {
      return false;
    }

    Loc current = p.location();
    Loc last = lastMountedLocation.put(id, current);
    if (last != null && last.sameWorld(current)) {
      double distance = last.distance(current);
      if (distance > 0.1 && distance < 100) {
        addStat(p, "taming.mounted-tactics.distance", distance);
      }
    }

    boolean sprinting = sprintingOverride == null ? p.isSprinting() : sprintingOverride;
    Boolean sprintingBefore = wasSprinting.put(id, sprinting);
    if (sprinting && (sprintingBefore == null || !sprintingBefore)) {
      fx(vehicle.location(), FxPriority.TRAIL)
          .particle(Particles.POOF, 3, 0, 0.1D, 0, 0.15D, 0.02D)
          .dustBurst(Rgb.of(0x9A7B4F), 4, 0.25D, 1.0F)
          .sound(Sounds.ENTITY_HORSE_GALLOP, 0.4F, 1.1F);
    }

    if (vehicle instanceof LivingRef horse && vehicle.has(EntityTrait.HORSE)) {
      if (shouldAssertBuffs(id)) {
        applyMountBuff(horse, SLOT_SPEED, Attributes.MOVEMENT_SPEED, getHorseSpeedBonus(level), AttributeOperation.ADD_MULTIPLIED_TOTAL);
        applyMountBuff(horse, SLOT_JUMP, Attributes.JUMP_STRENGTH, getHorseJumpBonus(level), AttributeOperation.ADD_MULTIPLIED_BASE);
      }
      if (sprinting) {
        applyHorizontalPush(horse, current.direction(), 0.8D, getHorsePush(level));
      }
      return true;
    }

    if (vehicle instanceof LivingRef strider && STRIDER.equals(vehicle.type())) {
      p.addEffect(new EffectInstance(FIRE_RESISTANCE, 40, 0, false, false, true));
      if (shouldAssertBuffs(id)) {
        applyMountBuff(strider, SLOT_SPEED, Attributes.MOVEMENT_SPEED, getStriderSpeedBonus(level), AttributeOperation.ADD_MULTIPLIED_TOTAL);
      }
      BlockRef striderBlock = strider.world().blockAt(strider.location());
      if (LAVA.equals(striderBlock.type())
          || LAVA.equals(striderBlock.relative(BlockFace.DOWN).type())) {
        AdaptCore.platform().host().animals().setShivering(strider, false);
        if (emberCd.isReady(id, 1000)) {
          emberCd.mark(id);
          fx(strider, FxPriority.AMBIENT)
              .particle(Particles.FLAME, 2, 0, 0.3D, 0, 0.1D, 0.01D)
              .particle(Particles.SCRAPE, 1, 0, 0.4D, 0, 0.1D, 0);
        }
      }
      return true;
    }

    if (PIG.equals(vehicle.type())) {
      p.addEffect(new EffectInstance(RESISTANCE, 25, getPigResistanceAmplifier(level), false, false, true));
      if (sprinting) {
        applyHorizontalPush(vehicle, current.direction(), 0.7D, getPigPush(level));
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

  private void applyMountBuff(LivingRef mount, String slot, Id attribute, double amount, AttributeOperation operation) {
    if (amount <= 0) {
      AdaptAttributeService.get().remove(mount, getName(), slot, attribute);
      return;
    }

    AdaptAttributeService.get().applyTimed(mount, getName(), slot, attribute, amount, operation, MOUNT_BUFF_DURATION_TICKS);
  }

  private void removeMountBuffs(EntityRef mount) {
    if (mount instanceof LivingRef living) {
      AdaptAttributeService.get().removeAll(living, getName());
    }
  }

  private void applyHorizontalPush(EntityRef mount, Vec3 direction, double damping, double amount) {
    Vec3 flat = direction.withY(0);
    if (flat.lengthSquared() <= VelocitySpeed.EPSILON) {
      return;
    }
    Vec3 push = flat.normalize().multiply(Math.max(0D, amount));
    mount.setVelocity(mount.velocity().multiply(damping).add(push));
  }

  private boolean isTacticalMount(EntityRef vehicle) {
    return vehicle != null && (vehicle.has(EntityTrait.HORSE) || STRIDER.equals(vehicle.type()) || PIG.equals(vehicle.type()));
  }

  private boolean requiresStationaryRefresh(EntityRef vehicle) {
    return isTacticalMount(vehicle);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    if (e.killer() instanceof PlayerRef p
        && p.vehicle() != null
        && !GolemKillCredit.isGolemKill(e.entity())
        && hasActiveAdaptation(p)) {
      addStat(p, "taming.mounted-tactics.mounted-kills", 1);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef attacker && isTacticalMount(attacker.vehicle())) {
      int level = preferenceEnabled(attacker, TamingPreferences.COMBAT) ? mountedLevel(attacker, attacker.vehicle()) : 0;
      if (level > 0) {
        if (!canDamageTarget(attacker, e.entity())) {
          return;
        }

        e.setDamage(e.damage() * (1D + getMountedDamageBonus(level)));
        xp(attacker, e.damage() * getConfig().xpPerMountedDamage);
        if (hitCd.isReady(attacker.uuid(), 200)) {
          hitCd.mark(attacker.uuid());
          int count = Math.min(9, 4 + level);
          fx(e.entity().location().add(0, 0.8D, 0), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, count, 0.35D)
              .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
              .chord(Sounds.ENTITY_HORSE_BREATHE, 0.4F, 0.9F, Sounds.ITEM_TRIDENT_RETURN, 0.35F, 1.3F);
        }
      }
    }

    if (e.entity() instanceof PlayerRef defender && isTacticalMount(defender.vehicle())) {
      int level = preferenceEnabled(defender, TamingPreferences.COMBAT) ? mountedLevel(defender, defender.vehicle()) : 0;
      if (level > 0) {
        e.setDamage(e.damage() * (1D - getMountedDamageReduction(level)));
        Vec3 face = defender.location().direction().withY(0);
        Loc front = defender.location().add(0, 1, 0);
        if (face.lengthSquared() > 1.0E-6D) {
          front = front.add(face.normalize().multiply(0.6D));
        }
        fx(front, FxPriority.COMBAT)
            .dustBurst(Rgb.of(0x8FA3B0), 4, 0.35D, 1.0F)
            .sound(Sounds.ITEM_SHIELD_BLOCK, 0.3F, 1.4F);
      }
    }
  }

  /** The player's own level, or the top level a bonding upgrade lends them on their bonded mount. */
  private int mountedLevel(PlayerRef p, EntityRef vehicle) {
    return Math.max(getActiveLevel(p), TameBonds.tacticsLevel(this, p, vehicle));
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
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
