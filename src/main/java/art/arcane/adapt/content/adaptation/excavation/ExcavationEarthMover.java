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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ExcavationEarthMover extends SimpleAdaptation<ExcavationEarthMover.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final PotionEffectType SLOWNESS = RegistryUtil.find(PotionEffectType.class, "slowness", "slow");
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationEarthMover() {
    super("excavation-earth-mover");
    registerConfiguration(Config.class);
    setIcon(Material.DIRT);
    setInterval(3730);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIRT)
        .key("challenge_excavation_earthmover_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_earthmover_250", "excavation.earth-mover.waves-unleashed", 250, 450);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.pc(getForce(level), 0), 2);
    statLore(v, Form.duration(getSlowTicks(level) * 50D, 1), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 4);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 5);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));

    double radius = getRadius(level);
    double force = getForce(level);
    int slowTicks = getSlowTicks(level);
    int slowAmplifier = getSlowAmplifier(level);
    Location origin = p.getLocation().clone();
    BlockData dirtData = Material.DIRT.createBlockData();
    List<Monster> candidates = collectCandidates(origin, radius);
    renderWave(origin, radius);
    addStat(p, "excavation.earth-mover.waves-unleashed", 1);

    if (candidates.isEmpty()) {
      fx(origin, FxPriority.COMBAT).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
      return;
    }

    EarthMoverBatch batch = new EarthMoverBatch(p, origin, radius, getConfig().verticalRange, force,
        getConfig().liftVelocity, slowTicks, slowAmplifier, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerMobHit, dirtData, candidates.size());
    for (Monster monster : candidates) {
      if (!J.runEntity(monster, () -> inspectCandidateOwned(batch, monster))) {
        batch.complete();
      }
    }
    J.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<Monster> collectCandidates(Location origin, double radius) {
    int limit = getCandidateLimit();
    List<Monster> candidates = new ArrayList<>(limit);
    for (Monster monster : origin.getWorld().getNearbyEntitiesByType(
        Monster.class, origin, radius, getConfig().verticalRange, radius)) {
      candidates.add(monster);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(EarthMoverBatch batch, Monster monster) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Location targetLocation = validTargetLocation(batch, monster);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    if (!J.runEntity(batch.player, () -> authorizeCandidate(batch, monster, targetLocation))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(EarthMoverBatch batch, Monster monster, Location targetLocation) {
    if (batch.isFinalized() || !batch.player.isOnline() || !canPVE(batch.player, targetLocation)) {
      batch.complete();
      return;
    }

    if (!J.runEntity(monster, () -> applyImpactOwned(batch, monster))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(EarthMoverBatch batch, Monster monster) {
    if (batch.isFinalized()) {
      return;
    }
    Location targetLocation = validTargetLocation(batch, monster);
    if (targetLocation == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    Vector direction = targetLocation.toVector().subtract(batch.origin.toVector()).setY(0);
    if (direction.lengthSquared() < 0.01D) {
      direction = new Vector(1, 0, 0);
    }

    direction.normalize().multiply(batch.force).setY(batch.liftVelocity);
    monster.setVelocity(direction);
    monster.addPotionEffect(new PotionEffect(SLOWNESS, batch.slowTicks, batch.slowAmplifier, false, true, true));
    if (batch.claimTargetFx()) {
      fx(monster, FxPriority.COMBAT)
          .particle(Particles.BLOCK_CRACK, 4, 0, 0.1D, 0, 0.2D, 0.05D, batch.dirtData);
    }
    batch.complete();
  }

  private Location validTargetLocation(EarthMoverBatch batch, Monster monster) {
    if (!monster.isValid() || monster.isDead() || isProtectedFriendly(null, monster)) {
      return null;
    }

    Location location = monster.getLocation();
    if (location.getWorld() != batch.origin.getWorld()
        || Math.abs(location.getX() - batch.origin.getX()) > batch.radius
        || Math.abs(location.getY() - batch.origin.getY()) > batch.verticalRange
        || Math.abs(location.getZ() - batch.origin.getZ()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void renderWave(Location center, double radius) {
    ItemStack dirt = new ItemStack(Material.DIRT);
    timeline(center)
        .duration(8)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          int points = 10 + (int) (progress * 14);
          fx.ring(Particles.ITEM_CRACK, r, points, 0.2D, dirt);
          fx.ring(Particle.CLOUD, r, Math.max(8, points / 2), 0.15D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 1.0f, 0.6f, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 0.5f, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 0.6f);
          }
          if (tick == 7 && radius > 5.0D) {
            fx.particle(Particle.EXPLOSION, 1, 0, 0.3D, 0, 0, 0);
          }
        })
        .start();
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private int getSlowTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().slowTicksBase + (getLevelPercent(level) * getConfig().slowTicksFactor)));
  }

  private int getSlowAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * getConfig().slowAmplifierMax));
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit()), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Sneak-right-click the air with a shovel to fling a wave of earth that knocks back and slows hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Vertical Range for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double verticalRange = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lift Velocity for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double liftVelocity = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Ticks Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Slow Amplifier Max for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double slowAmplifierMax = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 16000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per earth wave.", impact = "Higher values drain more food per wave; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mob Hit for the Excavation Earth Mover adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMobHit = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs inspected by one earth wave.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs launched by one earth wave.", impact = "Lower values cap combat work and rewards in dense farms; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum launched mobs that receive individual block-particle effects.", impact = "Lower values reduce particle dispatch while preserving the main wave effect; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 8;

    public Config() {
      baseCost = 6;
      costFactor = 0.8;
      initialCost = 7;
    }
  }

  private final class EarthMoverBatch {
    private final Player player;
    private final Location origin;
    private final double radius;
    private final double verticalRange;
    private final double force;
    private final double liftVelocity;
    private final int slowTicks;
    private final int slowAmplifier;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerMobHit;
    private final BlockData dirtData;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private EarthMoverBatch(Player player, Location origin, double radius, double verticalRange, double force,
                            double liftVelocity, int slowTicks, int slowAmplifier, int affectedLimit,
                            int targetFxLimit, double xpPerMobHit, BlockData dirtData, int candidates) {
      this.player = player;
      this.origin = origin;
      this.radius = radius;
      this.verticalRange = verticalRange;
      this.force = force;
      this.liftVelocity = liftVelocity;
      this.slowTicks = slowTicks;
      this.slowAmplifier = slowAmplifier;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerMobHit = xpPerMobHit;
      this.dirtData = dirtData;
      remaining = new AtomicInteger(candidates);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= affectedLimit) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < affectedLimit;
    }

    private boolean claimTargetFx() {
      return claim(targetFx, targetFxLimit);
    }

    private boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }

    private void complete() {
      if (finalized.get() || remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      J.runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      if (!player.isOnline()) {
        return;
      }

      int hit = affected.get();
      if (hit <= 0) {
        fx(origin, FxPriority.COMBAT).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f);
        return;
      }

      fx(origin, FxPriority.COMBAT).sound(Sound.ENTITY_HOSTILE_BIG_FALL, 0.4f, 0.8f);
      addStat(player, "excavation.earth-mover.mobs-launched", hit);
      xp(player, hit * xpPerMobHit);
    }
  }
}
