package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class KineticsWindburst extends SimpleAdaptation<KineticsWindburst.Config> {
  private static final String SLOT_BRACE = "brace";
  private static final int HARD_MAX_CANDIDATES = 32;
  private static final int HARD_MAX_AFFECTED = 16;
  private static final int HARD_MAX_TARGET_FX = 12;
  private static final int BRACE_TICKS = 20;

  private final Cooldowns cooldowns = cooldowns();

  public KineticsWindburst() {
    super("kinetics-windburst");
    registerConfiguration(Config.class);
    setIcon(Material.WIND_CHARGE);
    setInterval(9999);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.f(getForce(level), 2), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!smashLands(e.getOriginalResult(), e.getResult())) {
      return;
    }

    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!burstReady(p.getFallDistance(), getMinFallDistance(level))) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, getConfig().cooldownMs)) {
      return;
    }

    cooldowns.mark(id);
    double radius = getRadius(level);
    double force = getForce(level);
    Location origin = p.getLocation();
    List<LivingEntity> candidates = collectCandidates(p, origin, radius);
    playBurst(origin, radius);

    WindburstBatch batch = new WindburstBatch(p, origin.clone(), radius, force);
    for (LivingEntity target : candidates) {
      if (!batch.hasAffectedCapacity()) {
        break;
      }
      J.runEntity(target, () -> inspectCandidateOwned(batch, target));
    }

    if (Attributes.EXPLOSION_KNOCKBACK_RESISTANCE != null) {
      AdaptAttributeService.get().applyTimed(p, getName(), SLOT_BRACE, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D, AttributeModifier.Operation.ADD_NUMBER, BRACE_TICKS);
    }

    xp(p, origin, getConfig().xpPerBurst, "kinetics:windburst");
    addStat(p, "kinetics.windburst.bursts", 1);
  }

  static boolean burstReady(double fallDistance, double minFallDistance) {
    return fallDistance >= minFallDistance;
  }

  static boolean smashLands(boolean originalResult, Event.Result result) {
    return result == Event.Result.ALLOW || (result == Event.Result.DEFAULT && originalResult);
  }

  static double minFallDistance(double base, double factor, double levelPercent) {
    double distance = base + (levelPercent * factor);
    if (Double.isNaN(distance)) {
      return 0D;
    }
    return Math.max(0D, distance);
  }

  private List<LivingEntity> collectCandidates(Player player, Location origin, double radius) {
    List<LivingEntity> candidates = new ArrayList<>(HARD_MAX_CANDIDATES);
    for (LivingEntity candidate : origin.getWorld().getNearbyLivingEntities(origin, radius, radius, radius)) {
      if (candidate == player) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= HARD_MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(WindburstBatch batch, LivingEntity target) {
    if (!isRuntimeRegistered() || !batch.hasAffectedCapacity()) {
      return;
    }

    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      return;
    }

    boolean playerTarget = target instanceof Player;
    J.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget));
  }

  private void authorizeCandidate(WindburstBatch batch, LivingEntity target, Location targetLocation, boolean playerTarget) {
    if (!isRuntimeRegistered() || !batch.player.isOnline()) {
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed) {
      return;
    }

    J.runEntity(target, () -> applyImpactOwned(batch, target));
  }

  private void applyImpactOwned(WindburstBatch batch, LivingEntity target) {
    if (!isRuntimeRegistered()) {
      return;
    }
    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null || !batch.claimAffected()) {
      return;
    }

    Vector away = targetLocation.toVector().subtract(batch.origin.toVector()).normalize();
    target.setVelocity(target.getVelocity().multiply(0.2D).add(away.multiply(batch.force)));
    if (batch.claimTargetFx()) {
      fx(targetLocation.clone().add(0, 1, 0), FxPriority.COMBAT)
          .particle(Particle.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
    }
  }

  private Location validTargetLocation(WindburstBatch batch, LivingEntity target) {
    if (!target.isValid() || target.isDead() || isProtectedFriendly(null, target)) {
      return null;
    }
    if (target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer owner = tameable.getOwner();
      if (owner != null && batch.playerId.equals(owner.getUniqueId())) {
        return null;
      }
    }

    Location location = target.getLocation();
    if (location.getWorld() != batch.origin.getWorld()) {
      return null;
    }

    double lengthSquared = location.toVector().subtract(batch.origin.toVector()).lengthSquared();
    if (lengthSquared <= 0.0001D || lengthSquared > batch.radiusSquared) {
      return null;
    }
    return location;
  }

  private void playBurst(Location origin, double radius) {
    fx(origin, FxPriority.COMBAT)
        .particle(Particle.GUST, 1, 0, 0, 0, 0.1D, 0.02D)
        .chord(Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.9F, 0.8F, Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 0.6F);
    int ringPoints = Math.min(24, 10 + (int) (radius * 2));
    timeline(origin.clone().add(0, 0.4D, 0))
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (radius * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.ring(Particle.CLOUD, 0.4D + (radius * progress), ringPoints, 0.05D))
        .start();
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getMinFallDistance(int level) {
    return minFallDistance(getConfig().minFallDistanceBase, getConfig().minFallDistanceFactor, getLevelPercent(level));
  }

  @ConfigDescription("Heavy smashes erupt in a radial shockwave that hurls nearby enemies away.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base shockwave radius in blocks at level 1.", impact = "Higher values let the burst reach more distant enemies.")
    double radiusBase = 2.5;
    @ConfigDoc(value = "Additional shockwave radius granted at max level.", impact = "Higher values extend the burst reach as levels increase.")
    double radiusFactor = 2.5;
    @ConfigDoc(value = "Base knockback force at level 1.", impact = "Higher values hurl enemies further from the smash point.")
    double forceBase = 0.6;
    @ConfigDoc(value = "Additional knockback force granted at max level.", impact = "Higher values hurl enemies further as levels increase.")
    double forceFactor = 0.8;
    @ConfigDoc(value = "Base fall distance in blocks required to trigger a burst at level 1.", impact = "Higher values demand higher drops before smashes burst.")
    double minFallDistanceBase = 3;
    @ConfigDoc(value = "Fall distance requirement change applied at max level.", impact = "Negative values make bursts easier to trigger as levels increase.")
    double minFallDistanceFactor = -1;
    @ConfigDoc(value = "Cooldown in milliseconds between bursts.", impact = "Higher values make bursts available less often.")
    long cooldownMs = 4000;
    @ConfigDoc(value = "XP granted per triggered burst.", impact = "Higher values speed up kinetics skill progression from bursts.")
    double xpPerBurst = 8;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  private static final class WindburstBatch {
    private final Player player;
    private final UUID playerId;
    private final Location origin;
    private final double radiusSquared;
    private final double force;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();

    private WindburstBatch(Player player, Location origin, double radius, double force) {
      this.player = player;
      playerId = player.getUniqueId();
      this.origin = origin;
      radiusSquared = radius * radius;
      this.force = force;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < HARD_MAX_AFFECTED;
    }

    private boolean claimAffected() {
      return claim(affected, HARD_MAX_AFFECTED);
    }

    private boolean claimTargetFx() {
      return claim(targetFx, HARD_MAX_TARGET_FX);
    }

    private static boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }
  }
}
