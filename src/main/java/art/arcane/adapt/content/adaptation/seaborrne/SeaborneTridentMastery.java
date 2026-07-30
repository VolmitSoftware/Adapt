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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SeaborneTridentMastery extends SimpleAdaptation<SeaborneTridentMastery.Config> {
  private static final int MAX_RECALL_TICKS = 120;
  private static final double PICKUP_DISTANCE = 1.6;

  private final NamespacedKey masteryLevelKey;

  public SeaborneTridentMastery() {
    super("seaborne-trident-mastery");
    masteryLevelKey = new NamespacedKey(Adapt.instance, "seaborne_trident_mastery_level");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.trident_mastery");
    setIcon(Material.TRIDENT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TRIDENT)
        .key("challenge_seaborne_trident_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.HEART_OF_THE_SEA)
            .key("challenge_seaborne_trident_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_trident_250", "seaborne.trident-mastery.trident-hits", 250, 400);
    registerMilestone("challenge_seaborne_trident_2500", "seaborne.trident-mastery.trident-hits", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDamageBonus(level), 0), 1);
    statLore(v, Form.f(getRecallSpeed(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    DamageContext context = resolveDamageContext(e);
    if (context == null) {
      return;
    }

    e.setDamage(e.getDamage() * (1D + getDamageBonus(context.level())));
    fx(e.getEntity().getLocation().clone().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .particle(Particle.CRIT, 8, 0D, 0D, 0D, 0.35D, 0.1D)
        .particle(Particle.BUBBLE, 6, 0D, 0D, 0D, 0.3D, 0.05D)
        .sound(Sound.ITEM_TRIDENT_HIT, 0.6F, 1.1F);
    J.runEntity(context.attacker(), () -> rewardHitOwned(context.attacker()));
  }

  private DamageContext resolveDamageContext(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Trident trident && trident.getShooter() instanceof Player shooter) {
      Integer level = trident.getPersistentDataContainer().get(masteryLevelKey, PersistentDataType.INTEGER);
      return level == null || level <= 0 ? null : new DamageContext(shooter, level);
    }

    if (e.getDamager() instanceof Player melee && melee.getInventory().getItemInMainHand().getType() == Material.TRIDENT) {
      int level = getActiveLevel(melee);
      return level <= 0 ? null : new DamageContext(melee, level);
    }

    return null;
  }

  private void rewardHitOwned(Player attacker) {
    if (!attacker.isOnline() || getActiveLevel(attacker) <= 0) {
      return;
    }
    addStat(attacker, "seaborne.trident-mastery.trident-hits", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    trident.getPersistentDataContainer().set(masteryLevelKey, PersistentDataType.INTEGER, level);

    if (!getConfig().enableRecall) {
      return;
    }

    int delay = Math.max(2, (int) Math.round(getConfig().recallDelayTicks));
    J.runEntity(trident, () -> recallTick(trident, p, level, 0), delay);
  }

  private void recallTick(Trident trident, Player p, int level, int ticksLived) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }
    UUID tridentId = trident.getUniqueId();
    if (!J.runEntity(p, () -> captureRecallTargetOwned(trident, tridentId, p, level, ticksLived))) {
      Adapt.warn("Trident Mastery could not schedule recall owner snapshot for " + tridentId + ".");
    }
  }

  private void captureRecallTargetOwned(Trident trident, UUID tridentId, Player p, int level, int ticksLived) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }
    Location playerLocation = p.getLocation().clone().add(0D, 1.0D, 0D);
    if (!J.runEntity(trident,
        () -> applyRecallTargetOwned(trident, tridentId, p, level, ticksLived, playerLocation))) {
      Adapt.warn("Trident Mastery could not schedule recall continuation for " + tridentId + ".");
    }
  }

  private void applyRecallTargetOwned(Trident trident, UUID tridentId, Player p, int level, int ticksLived,
                                      Location playerLocation) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }

    Location tridentLocation = trident.getLocation();
    if (tridentLocation.getWorld() != playerLocation.getWorld()) {
      return;
    }

    boolean stuck = trident.isInBlock() || trident.isOnGround();
    if (!stuck && ticksLived < getFlightGraceTicks(level)) {
      scheduleNextRecall(trident, p, level, ticksLived);
      return;
    }

    Vector toPlayer = playerLocation.toVector().subtract(tridentLocation.toVector());
    double distance = toPlayer.length();
    if (distance <= PICKUP_DISTANCE) {
      return;
    }

    if (stuck) {
      Location freed = tridentLocation.clone()
          .add(toPlayer.clone().normalize().multiply(0.4D))
          .add(0D, 0.2D, 0D);
      beginReleaseTeleport(trident, tridentId, p, level, ticksLived, freed);
      return;
    }

    continueRecallOwned(trident, p, level, ticksLived, tridentLocation, toPlayer);
  }

  private void beginReleaseTeleport(Trident trident, UUID tridentId, Player p, int level, int ticksLived,
                                    Location freed) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = trident.teleportAsync(freed);
    } catch (RuntimeException error) {
      Adapt.error("Trident Mastery could not start release teleport for " + tridentId + ".");
      error.printStackTrace();
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeReleaseTeleport(trident, tridentId, p, level, ticksLived, success, failure));
  }

  private void completeReleaseTeleport(Trident trident, UUID tridentId, Player p, int level, int ticksLived,
                                       Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error("Trident Mastery release teleport failed for " + tridentId + ".");
      failure.printStackTrace();
    }
    if (!successfulReleaseTeleport(success, failure)) {
      return;
    }
    if (!J.runEntity(trident, () -> recallTick(trident, p, level, ticksLived))) {
      Adapt.warn("Trident Mastery completed release teleport but could not schedule completion for "
          + tridentId + ".");
    }
  }

  private void continueRecallOwned(Trident trident, Player p, int level, int ticksLived,
                                   Location tridentLocation, Vector toPlayer) {
    trident.setVelocity(toPlayer.normalize().multiply(getRecallSpeed(level)));
    if ((ticksLived & 3) == 0) {
      fx(tridentLocation, FxPriority.TRAIL)
          .particle(Particle.BUBBLE, 2, 0D, 0D, 0D, 0.1D, 0.02D)
          .sound(Sound.ITEM_TRIDENT_RETURN, 0.25F, 1.4F);
    }

    scheduleNextRecall(trident, p, level, ticksLived);
  }

  private void scheduleNextRecall(Trident trident, Player p, int level, int ticksLived) {
    J.runEntity(trident, () -> recallTick(trident, p, level, ticksLived + 1), 1);
  }

  static boolean successfulReleaseTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private int getFlightGraceTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().flightGraceTicksBase - (getLevelPercent(level) * getConfig().flightGraceTicksReduction)));
  }

  private double getDamageBonus(int level) {
    return damageBonus(getConfig().damageBonusBase, getConfig().damageBonusFactor, getLevelPercent(level));
  }

  private double getRecallSpeed(int level) {
    return recallSpeed(getConfig().recallSpeedBase, getConfig().recallSpeedFactor, getLevelPercent(level));
  }

  static double damageBonus(double base, double factor, double levelPercent) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  static double recallSpeed(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  private record DamageContext(Player attacker, int level) {
  }

  @ConfigDescription("Tridents deal bonus damage and home back to you faster after a throw.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus trident damage as a fraction.", impact = "Higher values increase trident damage at low levels.")
    double damageBonusBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus trident damage fraction gained across levels.", impact = "Higher values greatly increase trident damage at higher levels.")
    double damageBonusFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base velocity applied when a thrown trident homes back to you.", impact = "Higher values pull thrown tridents back faster at low levels.")
    double recallSpeedBase = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional recall velocity gained across levels.", impact = "Higher values recover tridents much faster at higher levels.")
    double recallSpeedFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a thrown trident is allowed to fly before recall forces its return.", impact = "Higher values let tridents travel farther before homing back at low levels.")
    double flightGraceTicksBase = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flight grace ticks removed at max level.", impact = "Higher values make higher levels begin the return sooner.")
    double flightGraceTicksReduction = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Delay in ticks before recall first evaluates a thrown trident.", impact = "Higher values wait longer before the trident may begin returning.")
    double recallDelayTicks = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables the trident recall/homing return behavior.", impact = "Set to false to keep only the bonus damage without homing return.")
    boolean enableRecall = true;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
