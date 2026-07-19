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
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthSmokePellet extends SimpleAdaptation<StealthSmokePellet.Config> {
  private static final int PULSE_INTERVAL_TICKS = 10;
  private static final int BLIND_TICKS = PULSE_INTERVAL_TICKS + 30;
  private static final int MAX_TARGET_HANDOFFS_PER_TICK = 24;

  private final Cooldowns cooldowns = cooldowns();
  private final AtomicBoolean closed = new AtomicBoolean();

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

  static boolean isActivation(Action action, EquipmentSlot hand, boolean sneaking, Material held) {
    if (hand != EquipmentSlot.HAND || !sneaking || held == null) {
      return false;
    }
    if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
        && (isAir(held) || held == Material.GUNPOWDER)) {
      return true;
    }
    return action == Action.LEFT_CLICK_AIR && isAir(held);
  }

  private static boolean isAir(Material material) {
    return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
  }

  static boolean isBlindable(Entity entity) {
    return entity instanceof LivingEntity;
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.duration(getPulses(level) * PULSE_INTERVAL_TICKS * 50D, 1), 2);
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    Material held = p.getInventory().getItemInMainHand().getType();
    if (!isActivation(e.getAction(), e.getHand(), p.isSneaking(), held)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownMillis)) {
      return;
    }

    if (!p.getInventory().containsAtLeast(new ItemStack(Material.GUNPOWDER), 1)) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.12D)
          .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, 0.6F);
      return;
    }

    consumeGunpowder(p);
    cooldowns.mark(p.getUniqueId());
    e.setCancelled(true);
    e.setUseInteractedBlock(Event.Result.DENY);
    e.setUseItemInHand(Event.Result.DENY);
    throwRay(p, level);
  }

  private void consumeGunpowder(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.getType() == Material.GUNPOWDER) {
      held.setAmount(held.getAmount() - 1);
      return;
    }
    player.getInventory().removeItem(new ItemStack(Material.GUNPOWDER, 1));
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
      UUID targetId = target.getUniqueId();
      if (!cloud.pendingIds.add(targetId)) {
        continue;
      }
      cloud.pending.add(target);
    }
    startTargetDispatch(cloud);

    cloud.pulsesLeft--;
    if (cloud.pulsesLeft > 0) {
      J.runAt(center, () -> cloudPulse(cloud), PULSE_INTERVAL_TICKS);
    }
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
      LivingEntity target = cloud.pending.poll();
      if (target == null) {
        finishTargetDispatch(cloud);
        return;
      }
      cloud.pendingIds.remove(target.getUniqueId());
      J.runEntity(target, () -> blindTargetOwned(cloud, target));
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

  private void blindTargetOwned(CloudState cloud, LivingEntity target) {
    if (closed.get() || !target.isValid() || target.isDead() || target.getWorld() != cloud.center.getWorld()) {
      return;
    }
    Location targetLocation = target.getLocation();
    if (targetLocation.distanceSquared(cloud.center) > cloud.radius * cloud.radius) {
      return;
    }
    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, BLIND_TICKS, 0, false, false, false), true);
    if (target instanceof Mob mob) {
      mob.setTarget(null);
    }
  }

  @Override
  public void unregister() {
    closed.set(true);
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
    private final ConcurrentLinkedQueue<LivingEntity> pending = new ConcurrentLinkedQueue<>();
    private final Set<UUID> pendingIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dispatching = new AtomicBoolean();
    private int pulsesLeft;

    private CloudState(Location center, double radius, int pulsesLeft) {
      this.center = center.clone();
      this.radius = radius;
      this.pulsesLeft = pulsesLeft;
    }
  }

  @ConfigDescription("Sneak and cast a smoke ray with an empty hand or gunpowder to blind every living entity in its cloud; costs one gunpowder.")
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
