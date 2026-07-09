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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RangedHeartseeker extends SimpleAdaptation<RangedHeartseeker.Config> {
  public static final String SEEKING_ARROW_META = "adapt-heartseeker-arrow";
  private static final Color SEEKER_RED = Color.fromRGB(255, 30, 60);

  private final Map<UUID, Lock> locks = playerState();
  private final Map<UUID, UUID> viewerGlow = playerState();
  private final Map<UUID, HomingState> homing = new ConcurrentHashMap<>();

  public RangedHeartseeker() {
    super("ranged-heartseeker");
    registerConfiguration(Config.class);
    setIcon(Material.TARGET);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TARGET)
        .key("challenge_ranged_heartseeker_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_ranged_heartseeker_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_heartseeker_100", "ranged.heartseeker.hits", 100, 500);
    registerMilestone("challenge_ranged_heartseeker_1k", "ranged.heartseeker.hits", 1000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("ranged.heartseeker.lore1"));
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 2);
    v.addLore(C.GRAY + Localizer.dLocalize("ranged.heartseeker.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.getItem() == null || e.getItem().getType() != Material.BOW) {
      return;
    }

    Player p = e.getPlayer();
    if (getActiveLevel(p) <= 0 || p.hasCooldown(Material.BOW)) {
      return;
    }

    LivingEntity target = findLockTarget(p);
    if (target == null) {
      return;
    }

    UUID id = p.getUniqueId();
    Lock existing = locks.get(id);
    long now = System.currentTimeMillis();
    if (existing != null && existing.targetId().equals(target.getUniqueId())) {
      locks.put(id, new Lock(target.getUniqueId(), now));
      return;
    }

    locks.put(id, new Lock(target.getUniqueId(), now));
    applyGlow(p, target.getUniqueId());
    fx(target, FxPriority.TRANSITION)
        .dustRing(SEEKER_RED, 0.7D, 14, 1.0F)
        .chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.9F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 0.6F);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityShootBowEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getBow() == null || e.getBow().getType() != Material.BOW) {
      return;
    }

    if (!(e.getProjectile() instanceof AbstractArrow arrow) || arrow instanceof Trident) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    Lock lock = locks.remove(id);
    if (lock == null) {
      return;
    }

    if (System.currentTimeMillis() - lock.stamp() > getConfig().lockTimeoutMillis) {
      applyGlow(p, null);
      return;
    }

    Entity candidate = Bukkit.getEntity(lock.targetId());
    if (!(candidate instanceof LivingEntity target) || !target.isValid() || target.isDead()
        || target.getWorld() != p.getWorld() || !canDamageTarget(p, target)) {
      applyGlow(p, null);
      return;
    }

    p.setCooldown(Material.BOW, getCooldownTicks(level));
    HomingState state = new HomingState(id, target.getUniqueId());
    homing.put(arrow.getUniqueId(), state);
    arrow.setMetadata(SEEKING_ARROW_META, new FixedMetadataValue(Adapt.instance, true));
    fx(p.getLocation().add(0, 1.2, 0), FxPriority.GAMEPLAY)
        .dustRing(SEEKER_RED, 0.9D, 16, 1.1F)
        .chord(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.7F, 1.8F, Sound.ENTITY_ARROW_SHOOT, 0.6F, 0.7F);
    addStat(p, "ranged.heartseeker.seeks", 1);
    xp(p, getConfig().xpPerSeek);
    J.runEntity(arrow, () -> steer(arrow), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof AbstractArrow arrow)) {
      return;
    }

    HomingState state = homing.remove(arrow.getUniqueId());
    if (state == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(state.ownerId);
    if (!(e.getHitEntity() instanceof LivingEntity victim)) {
      if (owner != null) {
        applyGlow(owner, null);
      }
      if (arrow.isValid()) {
        arrow.setGravity(true);
      }
      fx(arrow.getLocation(), FxPriority.TRANSITION)
          .dustBurst(SEEKER_RED, 5, 0.25D, 1.0F)
          .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.8F);
      return;
    }

    state.hitIds.add(victim.getUniqueId());
    fx(victim.getLocation().add(0, victim.getHeight() * 0.6, 0), FxPriority.COMBAT)
        .dustBurst(SEEKER_RED, 14, 0.4D, 1.4F)
        .dustRing(SEEKER_RED, 1.0D, 16, 1.2F)
        .chord(Sound.ENTITY_ARROW_HIT_PLAYER, 0.8F, 1.2F, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 0.9F);
    if (owner != null) {
      addStat(owner, "ranged.heartseeker.hits", 1);
      xp(owner, getConfig().xpPerHit);
    }

    if (state.remainingPasses <= 0 || owner == null || !owner.isOnline()) {
      if (owner != null) {
        applyGlow(owner, null);
      }
      return;
    }

    LivingEntity next = findNextTarget(owner, victim.getLocation(), state.hitIds, victim, null);
    if (next == null) {
      applyGlow(owner, null);
      return;
    }

    double speed = Math.max(getConfig().minSpeed, state.speed);
    Vector back = arrow.getVelocity();
    Vector reversed = back.lengthSquared() <= 0.000001
        ? next.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize()
        : back.normalize().multiply(-1);
    AbstractArrow continuation = spawnContinuation(arrow, owner, reversed.multiply(speed));
    if (continuation == null) {
      applyGlow(owner, null);
      return;
    }

    HomingState nextState = new HomingState(state.ownerId, next.getUniqueId());
    nextState.remainingPasses = state.remainingPasses - 1;
    nextState.speed = speed;
    nextState.hitIds.addAll(state.hitIds);
    homing.put(continuation.getUniqueId(), nextState);
    applyGlow(owner, next.getUniqueId());
    fx(victim.getLocation().add(0, 1.0, 0), FxPriority.COMBAT)
        .dustRing(SEEKER_RED, 0.6D, 12, 1.0F)
        .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.6F, 2.0F);
    J.runEntity(continuation, () -> steer(continuation), 1);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    locks.remove(e.getPlayer().getUniqueId());
    viewerGlow.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (Map.Entry<UUID, Lock> entry : locks.entrySet()) {
      if (now - entry.getValue().stamp() <= getConfig().lockTimeoutMillis) {
        continue;
      }

      if (locks.remove(entry.getKey(), entry.getValue())) {
        Player viewer = Bukkit.getPlayer(entry.getKey());
        if (viewer != null && viewer.isOnline()) {
          applyGlow(viewer, null);
        }
      }
    }

    homing.keySet().removeIf(arrowId -> Bukkit.getEntity(arrowId) == null);
  }

  public LivingEntity getLockedTarget(Player p) {
    Lock lock = locks.get(p.getUniqueId());
    if (lock == null || System.currentTimeMillis() - lock.stamp() > getConfig().lockTimeoutMillis) {
      return null;
    }

    Entity candidate = Bukkit.getEntity(lock.targetId());
    if (!(candidate instanceof LivingEntity target) || !target.isValid() || target.isDead() || target.getWorld() != p.getWorld()) {
      return null;
    }

    return target;
  }

  private void steer(AbstractArrow arrow) {
    HomingState state = homing.get(arrow.getUniqueId());
    if (state == null) {
      return;
    }

    if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()) {
      endHoming(arrow, state);
      return;
    }

    state.ticks++;
    if (state.ticks > Math.max(40, getConfig().maxFlightTicksPerPass)) {
      endHoming(arrow, state);
      return;
    }

    Player owner = Bukkit.getPlayer(state.ownerId);
    Entity candidate = Bukkit.getEntity(state.targetId);
    LivingEntity target = candidate instanceof LivingEntity living && living.isValid() && !living.isDead()
        && living.getWorld() == arrow.getWorld() ? living : null;
    if (target == null) {
      target = owner == null ? null : findNextTarget(owner, arrow.getLocation(), state.hitIds, null, null);
      if (target == null) {
        endHoming(arrow, state);
        return;
      }

      state.targetId = target.getUniqueId();
      if (owner != null) {
        applyGlow(owner, target.getUniqueId());
      }
    }

    if (state.speed <= 0) {
      double factored = arrow.getVelocity().length() * Math.max(0.1, getConfig().seekSpeedFactor);
      double cap = Math.max(getConfig().minSpeed, getConfig().maxSeekSpeed);
      state.speed = Math.min(cap, Math.max(getConfig().minSpeed, factored));
      arrow.setGravity(false);
      arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
      if (state.remainingPasses < 0) {
        state.remainingPasses = arrow.getPierceLevel() + resolveRicochetLevel(owner);
        arrow.setPierceLevel(0);
      }
    }

    Location arrowLoc = arrow.getLocation();
    Vector toTarget = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(arrowLoc.toVector());
    double distance = toTarget.length();
    if (distance <= 0.000001) {
      J.runEntity(arrow, () -> steer(arrow), 1);
      return;
    }

    if (distance < state.bestDistance - 0.35) {
      state.bestDistance = distance;
      state.ticksSinceProgress = 0;
    } else {
      state.ticksSinceProgress++;
    }

    if (state.ticksSinceProgress > Math.max(10, getConfig().stuckTicks)) {
      LivingEntity swap = owner == null ? null : findNextTarget(owner, arrowLoc, state.hitIds, null, state.targetId);
      if (swap == null) {
        endHoming(arrow, state);
        return;
      }

      state.targetId = swap.getUniqueId();
      state.bestDistance = Double.MAX_VALUE;
      state.ticksSinceProgress = 0;
      if (owner != null) {
        applyGlow(owner, swap.getUniqueId());
      }
      target = swap;
      toTarget = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(arrowLoc.toVector());
      distance = Math.max(0.000001, toTarget.length());
    }

    Vector desired = toTarget.multiply(1D / distance);
    Vector dir;
    double effectiveSpeed;
    double lungeRadius = Math.max(1.0, getConfig().lungeRadius);
    if (distance <= lungeRadius) {
      dir = desired.clone();
      effectiveSpeed = state.speed;
    } else {
      Vector velocity = arrow.getVelocity();
      Vector current = velocity.lengthSquared() <= 0.000001 ? desired.clone() : velocity.normalize();
      double steerFactor = Math.min(1D, Math.max(0.05D, getConfig().steerFactor));
      dir = current.multiply(1D - steerFactor).add(desired.clone().multiply(steerFactor));
      if (dir.lengthSquared() <= 0.000001) {
        dir = desired.clone();
      }
      dir.normalize();

      double lookahead = Math.min(state.speed * 4D, distance - 0.5);
      if (lookahead > 1.5 && arrow.getWorld().rayTraceBlocks(arrowLoc, dir, lookahead, FluidCollisionMode.NEVER, true) != null) {
        dir = findClearDirection(arrow, arrowLoc, dir, lookahead);
      }

      effectiveSpeed = state.speed;
      double slowRadius = Math.max(lungeRadius, getConfig().approachSlowRadius);
      if (distance < slowRadius) {
        double factor = Math.max(Math.min(1D, getConfig().approachSpeedFloor), distance / slowRadius);
        effectiveSpeed = Math.max(0.9D, state.speed * factor);
      }
    }

    arrow.setVelocity(dir.multiply(effectiveSpeed));
    if (state.lastTrail != null && state.lastTrail.getWorld() == arrowLoc.getWorld()) {
      drawTrailSegment(state.lastTrail, arrowLoc);
    }
    state.lastTrail = arrowLoc.clone();
    if (state.ticks % 6 == 0) {
      float pitch = 1.4F + ((state.ticks % 24) / 24F * 0.6F);
      fx(arrowLoc, FxPriority.TRAIL).sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.35F, pitch);
    }

    J.runEntity(arrow, () -> steer(arrow), 1);
  }

  private void drawTrailSegment(Location from, Location to) {
    Vector delta = to.toVector().subtract(from.toVector());
    double length = delta.length();
    if (length <= 0.000001) {
      return;
    }

    double spacing = Math.max(0.15, getConfig().trailSpacing);
    Vector step = delta.multiply(1D / length);
    Particle.DustOptions core = new Particle.DustOptions(SEEKER_RED, (float) Math.max(0.4, getConfig().trailCoreSize));
    Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(255, 140, 40), (float) Math.max(0.3, getConfig().trailCoreSize * 0.55));
    int emberToggle = 0;
    for (double d = 0; d <= length; d += spacing) {
      Location point = from.clone().add(step.clone().multiply(d));
      point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, core);
      if (emberToggle++ % 2 == 0) {
        point.getWorld().spawnParticle(Particle.DUST, point, 1, 0.06, 0.06, 0.06, 0, ember);
      }
    }
  }

  private Vector findClearDirection(AbstractArrow arrow, Location arrowLoc, Vector blocked, double lookahead) {
    Vector up = new Vector(0, 1, 0);
    Vector side = blocked.clone().crossProduct(up);
    if (side.lengthSquared() <= 0.000001) {
      side = new Vector(1, 0, 0);
    }
    side.normalize();

    Vector[] candidates = {
        blocked.clone().add(up.clone().multiply(0.9)).normalize(),
        blocked.clone().add(up.clone().multiply(1.8)).normalize(),
        blocked.clone().add(side.clone().multiply(0.9)).normalize(),
        blocked.clone().add(side.clone().multiply(-0.9)).normalize(),
        blocked.clone().add(up.clone().multiply(-0.9)).normalize()
    };

    for (Vector option : candidates) {
      if (arrow.getWorld().rayTraceBlocks(arrowLoc, option, lookahead, FluidCollisionMode.NEVER, true) == null) {
        return option;
      }
    }

    return blocked;
  }

  private void endHoming(AbstractArrow arrow, HomingState state) {
    homing.remove(arrow.getUniqueId());
    if (arrow.isValid()) {
      arrow.setGravity(true);
      fx(arrow.getLocation(), FxPriority.TRANSITION)
          .dustBurst(SEEKER_RED, 5, 0.25D, 0.9F)
          .sound(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.4F, 0.7F);
    }

    Player owner = Bukkit.getPlayer(state.ownerId);
    if (owner != null && owner.isOnline()) {
      applyGlow(owner, null);
    }
  }

  private LivingEntity findLockTarget(Player p) {
    Location eye = p.getEyeLocation();
    double range = Math.max(4, getConfig().lockRange);
    RayTraceResult hit = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.6,
        en -> en instanceof LivingEntity le && le != p && !(le instanceof ArmorStand)
            && le.isValid() && !le.isDead() && !le.isInvisible()
            && (!(le instanceof Player other) || p.canSee(other)));
    if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
      return null;
    }

    return canDamageTarget(p, target) ? target : null;
  }

  private LivingEntity findNextTarget(Player owner, Location center, Set<UUID> hitIds, LivingEntity fallback, UUID excludeId) {
    double radius = Math.max(3, getConfig().reseekRadius);
    Location origin = center.clone().add(0, 0.75, 0);
    LivingEntity bestFresh = null;
    LivingEntity bestSeen = null;
    double bestFreshDistance = Double.MAX_VALUE;
    double bestSeenDistance = Double.MAX_VALUE;
    for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof LivingEntity living) || living == owner || living instanceof ArmorStand
          || !living.isValid() || living.isDead() || living.isInvisible()) {
        continue;
      }

      if (excludeId != null && excludeId.equals(living.getUniqueId())) {
        continue;
      }

      if (living instanceof Player other && !owner.canSee(other)) {
        continue;
      }

      if (!canDamageTarget(owner, living) || !hasClearPath(origin, living)) {
        continue;
      }

      double distance = living.getLocation().distanceSquared(center);
      if (hitIds.contains(living.getUniqueId())) {
        if (distance < bestSeenDistance) {
          bestSeenDistance = distance;
          bestSeen = living;
        }
        continue;
      }

      if (distance < bestFreshDistance) {
        bestFreshDistance = distance;
        bestFresh = living;
      }
    }

    if (bestFresh != null) {
      return bestFresh;
    }

    if (bestSeen != null) {
      return bestSeen;
    }

    return fallback != null && fallback.isValid() && !fallback.isDead() && canDamageTarget(owner, fallback) ? fallback : null;
  }

  private boolean hasClearPath(Location origin, LivingEntity target) {
    Vector to = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(origin.toVector());
    double distance = to.length();
    if (distance <= 0.5) {
      return true;
    }

    return origin.getWorld().rayTraceBlocks(origin, to.multiply(1D / distance), distance, FluidCollisionMode.NEVER, true) == null;
  }

  private AbstractArrow spawnContinuation(AbstractArrow source, Player owner, Vector velocity) {
    Location loc = source.getLocation().clone();
    AbstractArrow next;
    if (source instanceof SpectralArrow sourceSpectral) {
      SpectralArrow spectral = loc.getWorld().spawn(loc, SpectralArrow.class);
      spectral.setGlowingTicks(sourceSpectral.getGlowingTicks());
      next = spectral;
    } else if (source instanceof Arrow sourceArrow) {
      Arrow plain = loc.getWorld().spawn(loc, Arrow.class);
      plain.setBasePotionType(sourceArrow.getBasePotionType());
      sourceArrow.getCustomEffects().forEach(effect -> plain.addCustomEffect(effect, true));
      next = plain;
    } else {
      return null;
    }

    next.setShooter(owner);
    next.setVelocity(velocity);
    next.setDamage(source.getDamage());
    next.setCritical(source.isCritical());
    next.setPierceLevel(0);
    next.setGravity(false);
    next.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
    next.setFireTicks(source.getFireTicks());
    next.setMetadata(SEEKING_ARROW_META, new FixedMetadataValue(Adapt.instance, true));
    return next;
  }

  private int resolveRicochetLevel(Player owner) {
    if (owner == null) {
      return 0;
    }

    PlayerSkillLine line = getPlayer(owner).getData().getSkillLineNullable("ranged");
    PlayerAdaptation ricochet = line != null ? line.getAdaptation("ranged-ricochet-bolt") : null;
    return ricochet == null ? 0 : Math.max(0, ricochet.getLevel());
  }

  private void applyGlow(Player viewer, UUID targetId) {
    UUID viewerId = viewer.getUniqueId();
    UUID current = viewerGlow.get(viewerId);
    if (current != null && current.equals(targetId)) {
      return;
    }

    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
    if (glowingEntities == null) {
      return;
    }

    if (current != null) {
      Entity stale = Bukkit.getEntity(current);
      if (stale != null) {
        try {
          glowingEntities.unsetGlowing(stale, viewer);
        } catch (ReflectiveOperationException ignored) {
        }
      }
      viewerGlow.remove(viewerId);
    }

    if (targetId == null) {
      return;
    }

    Entity target = Bukkit.getEntity(targetId);
    if (target == null || !target.isValid()) {
      return;
    }

    try {
      glowingEntities.setGlowing(target, viewer, ChatColor.RED);
      viewerGlow.put(viewerId, targetId);
    } catch (ReflectiveOperationException ignored) {
    }
  }

  private int getCooldownTicks(int level) {
    int maxLevel = Math.max(1, getMaxLevel());
    if (maxLevel == 1) {
      return Math.max(1, getConfig().cooldownTicksEnd);
    }

    double t = Math.min(1D, Math.max(0D, (level - 1) / (double) (maxLevel - 1)));
    return Math.max(1, (int) Math.round(getConfig().cooldownTicksStart + ((getConfig().cooldownTicksEnd - getConfig().cooldownTicksStart) * t)));
  }

  private static final class HomingState {
    private final UUID ownerId;
    private final Set<UUID> hitIds = new HashSet<>();
    private UUID targetId;
    private int remainingPasses = -1;
    private double speed;
    private int ticks;
    private double bestDistance = Double.MAX_VALUE;
    private int ticksSinceProgress;
    private Location lastTrail;

    private HomingState(UUID ownerId, UUID targetId) {
      this.ownerId = ownerId;
      this.targetId = targetId;
    }
  }

  private record Lock(UUID targetId, long stamp) {
  }

  @ConfigDescription("Draw a bow while looking at a creature to lock on; the arrow whistles and curves to find its mark, chaining to fresh targets with Piercing and Ricochet Bolt.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance at which drawing a bow can lock onto a creature.", impact = "Higher values allow locking targets from further away.")
    double lockRange = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds a lock stays valid after acquiring it before the shot.", impact = "Higher values let players hold a draw longer without losing the lock.")
    long lockTimeoutMillis = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the arrow's heading corrected toward the target each tick.", impact = "Higher values curve harder and more dramatically toward the mark.")
    double steerFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum flight speed maintained while an arrow is seeking.", impact = "Higher values keep weakly drawn shots moving fast.")
    double minSpeed = 1.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the fired arrow's speed used while seeking.", impact = "Lower values make seeking arrows fly slower relative to the shot.")
    double seekSpeedFactor = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on seeking flight speed regardless of launch boosts.", impact = "Keeps Force Shot and other launch speed bonuses from accelerating seeking arrows.")
    double maxSeekSpeed = 2.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance from the target at which seeking arrows begin slowing for tighter turns.", impact = "Higher values start the approach slowdown earlier.")
    double approachSlowRadius = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest fraction of seeking speed kept during the close approach.", impact = "Lower values make near-target turns tighter and slower.")
    double approachSpeedFloor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance at which the arrow stops curving and lunges straight at the target at full seeking speed.", impact = "Higher values commit to the final strike from further out.")
    double lungeRadius = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum ticks a single seeking pass may fly before giving up.", impact = "Higher values let arrows chase evasive targets longer.")
    int maxFlightTicksPerPass = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched for the next target after each seeking hit.", impact = "Higher values let return passes chain to targets further away.")
    double reseekRadius = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks without closing distance before a seeking arrow retargets or gives up.", impact = "Lower values make unreachable targets release the arrow sooner.")
    int stuckTicks = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Distance in blocks between trail particles along the arrow's flight path.", impact = "Lower values draw a denser, more continuous trail.")
    double trailSpacing = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Dust size of the seeking arrow's core trail particles.", impact = "Higher values make the flight path more visible.")
    double trailCoreSize = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bow item cooldown in ticks applied after a seeking shot at level 1.", impact = "Higher values slow how often seeking shots can be fired at low levels.")
    int cooldownTicksStart = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bow item cooldown in ticks applied after a seeking shot at max level.", impact = "Higher values keep even max-level seeking shots on a longer cooldown.")
    int cooldownTicksEnd = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted when a seeking shot is fired.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSeek = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per seeking arrow connection.", impact = "Higher values reward landed seeking hits more.")
    double xpPerHit = 4;

    public Config() {
      baseCost = 6;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 8;
    }
  }
}
