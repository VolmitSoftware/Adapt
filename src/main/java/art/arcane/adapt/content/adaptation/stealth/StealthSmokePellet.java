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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class StealthSmokePellet extends SimpleAdaptation<StealthSmokePellet.Config> {
  private static final int PULSE_INTERVAL_TICKS = 10;
  private static final int BLIND_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int INVISIBILITY_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int MAX_TARGET_HANDOFFS_PER_TICK = 24;
  private static final double AGGRO_CLEAR_RADIUS = 64D;

  private final Cooldowns cooldowns = cooldowns();
  private final ConcurrentHashMap<UUID, Long> concealmentLeases = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong concealmentSequence = new AtomicLong();

  public StealthSmokePellet() {
    super("stealth-smoke-pellet");
    registerConfiguration(Config.class);
    setIcon(Material.GUNPOWDER);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GUNPOWDER)
        .key("challenge_stealth_smoke_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TNT)
            .key("challenge_stealth_smoke_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_smoke_100", "stealth.smoke-pellet.thrown", 100, 400);
    registerMilestone("challenge_stealth_smoke_1k", "stealth.smoke-pellet.thrown", 1000, 1500);
  }

  static double computeRadius(double base, double factor, double maxRadius, double percent) {
    return Math.min(maxRadius, base + (percent * factor));
  }

  static int computePulses(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  static double computeRaycastRange(double configured) {
    return Double.isFinite(configured) ? Math.max(2D, Math.min(64D, configured)) : 24D;
  }

  static EquipmentSlot gunpowderHand(boolean enteringSneak, Material mainHand, Material offHand) {
    if (!enteringSneak) {
      return null;
    }
    if (mainHand == Material.GUNPOWDER) {
      return EquipmentSlot.HAND;
    }
    return offHand == Material.GUNPOWDER ? EquipmentSlot.OFF_HAND : null;
  }

  static boolean currentConcealmentLease(Long currentLease, long expectedLease) {
    return currentLease != null && currentLease == expectedLease;
  }

  static boolean isBlindable(Entity entity) {
    return entity instanceof LivingEntity;
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.duration(getPulses(level) * PULSE_INTERVAL_TICKS * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    PlayerInventory inventory = p.getInventory();
    EquipmentSlot hand = gunpowderHand(
        e.isSneaking(),
        inventory.getItemInMainHand().getType(),
        inventory.getItemInOffHand().getType()
    );
    if (hand == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownMillis)) {
      return;
    }

    if (!consumeGunpowder(p, inventory, hand)) {
      return;
    }
    cooldowns.mark(p.getUniqueId());
    throwRay(p, level);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player player) || !isConcealed(player)) {
      return;
    }
    e.setCancelled(true);
    if (e.getEntity() instanceof Mob mob) {
      clearAggroOwned(mob, player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    concealmentLeases.remove(e.getPlayer().getUniqueId());
  }

  boolean isConcealed(Player player) {
    return concealmentLeases.containsKey(player.getUniqueId());
  }

  private boolean consumeGunpowder(Player p, PlayerInventory inventory, EquipmentSlot hand) {
    ItemStack held = hand == EquipmentSlot.OFF_HAND
        ? inventory.getItemInOffHand()
        : inventory.getItemInMainHand();
    return payItemCost(p, "pellet", new ItemStack(Material.GUNPOWDER), 1, () -> {
      held.setAmount(held.getAmount() - 1);
      return true;
    });
  }

  private void throwRay(Player p, int level) {
    Location eye = p.getEyeLocation();
    Vector direction = eye.getDirection().normalize();
    double raycastRange = computeRaycastRange(getConfig().raycastRange);
    RayTraceResult hit = p.getWorld().rayTrace(
        eye,
        direction,
        raycastRange,
        FluidCollisionMode.NEVER,
        true,
        0.35D,
        entity -> entity instanceof LivingEntity && entity != p
    );
    Vector hitPosition = hit == null
        ? eye.toVector().add(direction.clone().multiply(raycastRange))
        : hit.getHitPosition();
    if (hit != null && hit.getHitBlock() != null) {
      hitPosition.subtract(direction.clone().multiply(0.2D));
    }
    Location impact = hitPosition.toLocation(p.getWorld());
    double radius = getRadius(level);
    int pulses = getPulses(level);
    int trailPoints = Math.max(4, Math.min(32, (int) Math.ceil(eye.distance(impact) * 2D)));

    addStat(p, "stealth.smoke-pellet.thrown", 1);
    xp(p, getConfig().xpOnThrow);
    fx(eye, FxPriority.TRANSITION)
        .line(Particles.SMOKE, impact.getX(), impact.getY(), impact.getZ(), trailPoints)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sound.ENTITY_WITCH_THROW, 0.4F, 1.4F);
    CloudState cloud = new CloudState(impact, radius, pulses);
    J.runAt(impact, () -> cloudPulse(cloud));
  }

  private void cloudPulse(CloudState cloud) {
    Location center = cloud.center;
    if (closed.get() || center.getWorld() == null || cloud.pulsesLeft <= 0) {
      return;
    }

    fx(center, FxPriority.GAMEPLAY)
        .particle(Particles.SMOKE, (int) Math.max(8, cloud.radius * 6), 0, 0, 0, cloud.radius * 0.55D, 0.01D)
        .particle(Particle.CAMPFIRE_COSY_SMOKE, (int) Math.max(2, cloud.radius), 0, 0, 0, cloud.radius * 0.5D, 0.005D);
    if (cloud.pulsesLeft % 2 == 0) {
      fx(center, FxPriority.AMBIENT).sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 0.7F);
    }

    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, cloud.radius, cloud.radius, cloud.radius)) {
      if (target instanceof Player player) {
        renewConcealment(player);
      }
      enqueueTarget(cloud, target, true);
    }
    double aggroRadius = Math.max(cloud.radius, AGGRO_CLEAR_RADIUS);
    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, aggroRadius, aggroRadius, aggroRadius)) {
      if (target instanceof Mob) {
        enqueueTarget(cloud, target, false);
      }
    }
    startTargetDispatch(cloud);

    cloud.pulsesLeft--;
    if (cloud.pulsesLeft > 0) {
      J.runAt(center, () -> cloudPulse(cloud), PULSE_INTERVAL_TICKS);
    }
  }

  private void enqueueTarget(CloudState cloud, LivingEntity target, boolean cloudOccupant) {
    UUID targetId = target.getUniqueId();
    if (!cloud.pendingIds.add(targetId)) {
      return;
    }
    cloud.pending.add(new TargetWork(target, cloudOccupant));
  }

  private void startTargetDispatch(CloudState cloud) {
    if (cloud.dispatching.compareAndSet(false, true)) {
      dispatchTargets(cloud);
    }
  }

  private void dispatchTargets(CloudState cloud) {
    if (closed.get()) {
      cloud.pending.clear();
      cloud.pendingIds.clear();
      cloud.dispatching.set(false);
      return;
    }

    for (int index = 0; index < MAX_TARGET_HANDOFFS_PER_TICK; index++) {
      TargetWork work = cloud.pending.poll();
      if (work == null) {
        finishTargetDispatch(cloud);
        return;
      }
      LivingEntity target = work.target();
      cloud.pendingIds.remove(target.getUniqueId());
      J.runEntity(target, () -> processTargetOwned(cloud, work));
    }
    if (!cloud.pending.isEmpty()) {
      J.runAt(cloud.center, () -> dispatchTargets(cloud), 1);
      return;
    }
    finishTargetDispatch(cloud);
  }

  private void finishTargetDispatch(CloudState cloud) {
    cloud.dispatching.set(false);
    if (!cloud.pending.isEmpty() && cloud.dispatching.compareAndSet(false, true)) {
      J.runAt(cloud.center, () -> dispatchTargets(cloud), 1);
    }
  }

  private void processTargetOwned(CloudState cloud, TargetWork work) {
    LivingEntity target = work.target();
    if (closed.get() || !target.isValid() || target.isDead() || target.getWorld() != cloud.center.getWorld()) {
      return;
    }
    if (work.cloudOccupant()) {
      Location targetLocation = target.getLocation();
      if (targetLocation.distanceSquared(cloud.center) > cloud.radius * cloud.radius) {
        return;
      }
      target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_TICKS, 0, false, false, false), true);
      if (target instanceof Player player) {
        renewConcealment(player);
        applyInvisibilityOwned(player);
      }
    }
    if (target instanceof Mob mob) {
      LivingEntity currentTarget = mob.getTarget();
      if (work.cloudOccupant()) {
        clearAggroOwned(mob, currentTarget);
      } else if (currentTarget instanceof Player player && isConcealed(player)) {
        clearAggroOwned(mob, player);
      } else if (mob instanceof Warden warden
          && warden.getEntityAngryAt() instanceof Player player
          && isConcealed(player)) {
        clearAggroOwned(mob, player);
      }
    }
  }

  private void renewConcealment(Player player) {
    UUID playerId = player.getUniqueId();
    long lease = concealmentSequence.incrementAndGet();
    concealmentLeases.put(playerId, lease);
    boolean scheduled = J.runEntity(player, () -> {
      Long currentLease = concealmentLeases.get(playerId);
      if (currentConcealmentLease(currentLease, lease)) {
        concealmentLeases.remove(playerId, currentLease);
      }
    }, INVISIBILITY_TICKS);
    if (!scheduled) {
      concealmentLeases.remove(playerId, lease);
    }
  }

  private void applyInvisibilityOwned(Player player) {
    PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getDuration() > INVISIBILITY_TICKS + 5) {
      return;
    }
    player.addPotionEffect(new PotionEffect(
        PotionEffectType.INVISIBILITY,
        INVISIBILITY_TICKS,
        0,
        false,
        false,
        false
    ), true);
  }

  private void clearAggroOwned(Mob mob, LivingEntity currentTarget) {
    mob.setTarget(null);
    mob.setAggressive(false);
    if (mob instanceof Warden warden && currentTarget != null) {
      warden.clearAnger(currentTarget);
    }
  }

  @Override
  public void unregister() {
    closed.set(true);
    concealmentLeases.clear();
    super.unregister();
  }

  private double getRadius(int level) {
    return computeRadius(getConfig().radiusBase, getConfig().radiusFactor, getConfig().radiusMax, getLevelPercent(level));
  }

  private int getPulses(int level) {
    return computePulses(getConfig().pulsesBase, getConfig().pulsesFactor, getLevelPercent(level));
  }

  private static final class CloudState {
    private final Location center;
    private final double radius;
    private final ConcurrentLinkedQueue<TargetWork> pending = new ConcurrentLinkedQueue<>();
    private final Set<UUID> pendingIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private int pulsesLeft;

    private CloudState(Location center, double radius, int pulsesLeft) {
      this.center = center.clone();
      this.radius = radius;
      this.pulsesLeft = pulsesLeft;
    }
  }

  private record TargetWork(LivingEntity target, boolean cloudOccupant) {
  }

  @ConfigDescription("Sneak while holding gunpowder in either hand to cast a smoke cloud that conceals players, blinds living entities, and clears mob aggression.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cloud radius of the smoke pellet.", impact = "Higher values blind living entities across a wider area.")
    double radiusBase = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cloud radius gained across levels.", impact = "Higher values expand the cloud more per level.")
    double radiusFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum cloud radius after scaling.", impact = "Caps how large the smoke cloud can get.")
    double radiusMax = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of blinding pulses in the cloud.", impact = "Higher values make the cloud last longer.")
    double pulsesBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pulses gained across levels.", impact = "Higher values extend cloud duration more per level.")
    double pulsesFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum smoke ray distance in blocks.", impact = "Higher values place the smoke cloud farther along the player's aim.")
    double raycastRange = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between pellet throws, in milliseconds.", impact = "Higher values mean longer waits between throws.")
    long cooldownMillis = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per pellet thrown.", impact = "Higher values level the adaptation faster.")
    double xpOnThrow = 10;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
