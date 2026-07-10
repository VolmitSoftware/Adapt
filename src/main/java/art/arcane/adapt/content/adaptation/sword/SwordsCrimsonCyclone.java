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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.sword.effects.DamagingBleedEffect;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import de.slikey.effectlib.effect.BleedEffect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SwordsCrimsonCyclone extends SimpleAdaptation<SwordsCrimsonCyclone.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final Color CRIMSON = Color.fromRGB(0x9E1414);

  public SwordsCrimsonCyclone() {
    super("sword-crimson-cyclone");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_SWORD);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_cyclone_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_cyclone_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_cyclone_500", "swords.crimson-cyclone.mobs-hit", 500, 400);
    registerMilestone("challenge_swords_cyclone_5k", "swords.crimson-cyclone.mobs-hit", 5000, 1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_SWORD)
        .key("challenge_swords_cyclone_6")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, Form.f(getBaseDamage(level), 2), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity primaryTarget = combat.target();
    ItemStack hand = combat.mainHand();
    if (!isCritTrigger(p)) {
      return;
    }

    int level = combat.level();
    int hungerCost = getHungerCost(level);
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    if (!applyDurabilityCost(hand, getDurabilityCost(level))) {
      return;
    }

    double radius = getRadius(level);
    double damage = getBaseDamage(level);
    Location center = primaryTarget.getLocation().clone();
    int targetFxLimit = getTargetFxLimit();
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    p.setCooldown(hand.getType(), getCooldownTicks(level));

    e.setDamage(e.getDamage() + damage);
    applyBleed(primaryTarget, level, targetFxLimit > 0);
    if (targetFxLimit > 0) {
      crimsonSpark(primaryTarget);
    }

    List<LivingEntity> candidates = collectCandidates(p, primaryTarget, center, radius);
    if (candidates.isEmpty() || getAffectedLimit() <= 1) {
      finishCyclone(p, center, radius, level, 1, getConfig().xpPerTargetHit);
      return;
    }

    CycloneBatch batch = new CycloneBatch(p, center, radius, level, damage, getAffectedLimit(),
        targetFxLimit, getConfig().xpPerTargetHit, targetFxLimit > 0, candidates.size());
    for (LivingEntity target : candidates) {
      if (!J.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    J.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<LivingEntity> collectCandidates(Player player, LivingEntity primaryTarget, Location center, double radius) {
    int limit = getCandidateLimit();
    List<LivingEntity> candidates = new ArrayList<>(limit);
    for (LivingEntity candidate : center.getWorld().getNearbyLivingEntities(center, radius, radius, radius)) {
      if (candidate == player || candidate == primaryTarget) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(CycloneBatch batch, LivingEntity target) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof Player;
    if (!J.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(CycloneBatch batch, LivingEntity target, Location targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed || !J.runEntity(target, () -> applyCycloneOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyCycloneOwned(CycloneBatch batch, LivingEntity target) {
    if (batch.isFinalized()) {
      return;
    }
    if (validTargetLocation(batch, target) == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    target.damage(batch.damage, batch.player);
    boolean visual = batch.claimTargetFx();
    applyBleed(target, batch.level, visual);
    if (visual) {
      crimsonSpark(target);
    }
    batch.complete();
  }

  private Location validTargetLocation(CycloneBatch batch, LivingEntity target) {
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
    if (location.getWorld() != batch.center.getWorld()
        || Math.abs(location.getX() - batch.center.getX()) > batch.radius
        || Math.abs(location.getY() - batch.center.getY()) > batch.radius
        || Math.abs(location.getZ() - batch.center.getZ()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void finishCyclone(Player player, Location center, double radius, int level, int hits, double xpPerTargetHit) {
    double maxRadius = radius;
    int ringPoints = Math.min(24, 10 + (level * 2));
    timeline(center)
        .duration(8)
        .priority(FxPriority.COMBAT)
        .cullRadius(Math.min(48D, maxRadius + 16D))
        .frame((fx, tick, progress) -> {
          double growth = tick <= 3 ? (tick + 1) / 4.0D : (8 - tick) / 4.0D;
          fx.ring(Particle.CRIMSON_SPORE, maxRadius * growth, ringPoints, 1.0D);
          if (tick == 0) {
            fx.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1F, 0.6F);
          } else if (tick == 2) {
            fx.sound(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.2F);
          } else if (tick == 4) {
            fx.sound(Sound.ENTITY_WITHER_HURT, 0.65F, 1.45F);
          }
        })
        .start();
    xp(player, hits * xpPerTargetHit);
    addStat(player, "swords.crimson-cyclone.mobs-hit", hits);

    if (hits >= 6 && grantOnce(player, "challenge_swords_cyclone_6")) {
      fx(player.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.TOTEM, 12, 0.4D)
          .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1F);
    }
  }

  private void crimsonSpark(LivingEntity target) {
    fx(target.getLocation().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particle.CRIMSON_SPORE, 3, 0, 0, 0, 0.3D, 0.01D)
        .dustBurst(CRIMSON, 1, 0.2D, 1.0F);
  }

  private boolean isCritTrigger(Player p) {
    return p.getFallDistance() >= getConfig().minFallDistanceForCrit
        && !p.isOnGround()
        && !p.isInWater()
        && !p.isInsideVehicle()
        && !p.isClimbing();
  }

  private boolean applyDurabilityCost(ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable) || hand.getType().getMaxDurability() <= 0) {
      return true;
    }

    int max = hand.getType().getMaxDurability();
    int next = damageable.getDamage() + durabilityCost;
    if (next >= max) {
      return false;
    }

    damageable.setDamage(next);
    hand.setItemMeta(damageable);
    return true;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private double getBaseDamage(int level) {
    return getConfig().baseDamage + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private void applyBleed(LivingEntity target, int level, boolean visual) {
    BleedEffect bleed = new DamagingBleedEffect(Adapt.instance.adaptEffectManager, getBleedDamagePerProc(level), target);
    bleed.setEntity(target);
    bleed.material = visual && getConfig().showBleedParticles ? Material.CRIMSON_ROOTS : Material.VOID_AIR;
    bleed.height = -1;
    bleed.period = 5;
    bleed.hurt = false;
    bleed.iterations = Math.max(4, (int) Math.ceil(getBleedTicks(level) / 5D));
    bleed.start();
  }

  private int getBleedTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().bleedTicksBase + (getLevelPercent(level) * getConfig().bleedTicksFactor)));
  }

  private double getBleedDamagePerProc(int level) {
    return Math.max(0.01, getConfig().bleedDamagePerProcBase + (getLevelPercent(level) * getConfig().bleedDamagePerProcFactor));
  }

  private int getHungerCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
  }

  private int getDurabilityCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().durabilityCostBase - (getLevelPercent(level) * getConfig().durabilityCostFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit() + 1), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Land a sword crit while falling to unleash a bleeding crimson cyclone around your target.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Bleed Particles for the Swords Crimson Cyclone adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showBleedParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 2.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bleed Damage Per Proc Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bleedDamagePerProcFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durabilityCostFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 160;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Swords Crimson Cyclone adaptation.", impact = "Minimum fall distance required to trigger the cyclone on hit.")
    float minFallDistanceForCrit = 0.08f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Swords Crimson Cyclone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum secondary living targets inspected by one crimson cyclone.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total targets hit by one crimson cyclone, including the primary target.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hit targets that receive individual crimson spark effects.", impact = "Lower values reduce particle dispatch while preserving the cyclone ring; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 9;

    public Config() {
      baseCost = 5;
      costFactor = 0.76;
      initialCost = 5;
    }
  }

  private final class CycloneBatch {
    private final Player player;
    private final UUID playerId;
    private final Location center;
    private final double radius;
    private final int level;
    private final double damage;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerTargetHit;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger(1);
    private final AtomicInteger targetFx;
    private final AtomicBoolean finalized = new AtomicBoolean();

    private CycloneBatch(Player player, Location center, double radius, int level, double damage,
                         int affectedLimit, int targetFxLimit, double xpPerTargetHit, boolean primaryFx,
                         int candidates) {
      this.player = player;
      playerId = player.getUniqueId();
      this.center = center;
      this.radius = radius;
      this.level = level;
      this.damage = damage;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerTargetHit = xpPerTargetHit;
      remaining = new AtomicInteger(candidates);
      targetFx = new AtomicInteger(primaryFx ? 1 : 0);
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
      J.runEntity(player, () -> finishCyclone(player, center, radius, level, affected.get(), xpPerTargetHit));
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finishCyclone(player, center, radius, level, affected.get(), xpPerTargetHit);
      }
    }
  }
}
