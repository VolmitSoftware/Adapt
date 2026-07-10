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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

public class RangedHeavyDraw extends SimpleAdaptation<RangedHeavyDraw.Config> {
  static final String HEAVY_LEVEL_META = "adapt-heavy-draw-level";

  public RangedHeavyDraw() {
    super("ranged-heavy-draw");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    setInterval(3277);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_ranged_heavy_hits_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_BLOCK)
            .key("challenge_ranged_heavy_hits_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_heavy_hits_250", "ranged.heavy-draw.heavy-hits", 250, 500);
    registerMilestone("challenge_ranged_heavy_hits_2500", "ranged.heavy-draw.heavy-hits", 2500, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getDamageBonus(level), 0), 1);
    statLore(v, C.RED, "- ", Form.pc(getVelocityPenalty(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (readHeavyLevel(e.getEntity()) > 0
        || !(e.getEntity().getShooter() instanceof Player p)
        || !isHeavyCapable(e.getEntity())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Vector velocity = e.getEntity().getVelocity().clone().multiply(getVelocityFactor(level));
    e.getEntity().setVelocity(velocity);
    e.getEntity().setMetadata(HEAVY_LEVEL_META, new FixedMetadataValue(Adapt.instance, level));
    fx(e.getEntity().getLocation(), FxPriority.TRAIL)
        .dustBurst(Color.fromRGB(120, 90, 60), 4, 0.15D, 1.1F)
        .chord(Sound.ITEM_CROSSBOW_SHOOT, 0.4F, 0.6F, Sound.BLOCK_ANVIL_STEP, 0.25F, 0.7F);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Projectile projectile)
        || !(projectile.getShooter() instanceof Player p)) {
      return;
    }

    int level = readHeavyLevel(projectile);
    if (level <= 0 || isProtectedFriendly(p, e.getEntity()) || e.getDamage() <= 0D) {
      return;
    }

    double multiplier = 1D + getDamageBonus(level);
    if (isVelocityScaledDamage(projectile)) {
      multiplier /= Math.max(0.05D, getVelocityFactor(level));
    }

    e.setDamage(e.getDamage() * multiplier);
    J.runEntity(p, () -> rewardHeavyHitOwned(p));
    fx(e.getEntity(), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 8, 0.3D)
        .chord(Sound.BLOCK_ANVIL_LAND, 0.3F, 1.6F, Sound.ENTITY_ARROW_HIT, 0.6F, 0.7F);
  }

  static int readHeavyLevel(Projectile projectile) {
    for (MetadataValue value : projectile.getMetadata(HEAVY_LEVEL_META)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return Math.max(0, value.asInt());
      }
    }
    return 0;
  }

  static void copyHeavyState(Projectile source, Projectile target) {
    applyHeavyState(target, readHeavyLevel(source));
  }

  static void applyHeavyState(Projectile target, int level) {
    if (level > 0) {
      target.setMetadata(HEAVY_LEVEL_META, new FixedMetadataValue(Adapt.instance, level));
    }
  }

  private void rewardHeavyHitOwned(Player player) {
    if (!player.isOnline()) {
      return;
    }
    xp(player, getConfig().xpPerHeavyHit);
    addStat(player, "ranged.heavy-draw.heavy-hits", 1);
  }

  private boolean isHeavyCapable(Projectile projectile) {
    return projectile instanceof AbstractArrow || projectile instanceof Snowball || projectile instanceof Egg;
  }

  private boolean isVelocityScaledDamage(Projectile projectile) {
    return projectile instanceof AbstractArrow && !(projectile instanceof Trident);
  }

  private double getVelocityFactor(int level) {
    return Math.min(1D, Math.max(0.05D, 1D - getVelocityPenalty(level)));
  }

  private double getVelocityPenalty(int level) {
    return lerpByLevel(getConfig().velocityPenaltyStart, getConfig().velocityPenaltyEnd, level);
  }

  private double getDamageBonus(int level) {
    return lerpByLevel(getConfig().damageBonusStart, getConfig().damageBonusEnd, level);
  }

  private double lerpByLevel(double start, double end, int level) {
    int maxLevel = Math.max(1, getMaxLevel());
    if (maxLevel == 1) {
      return end;
    }

    double t = Math.min(1D, Math.max(0D, (level - 1) / (double) (maxLevel - 1)));
    return start + ((end - start) * t);
  }

  @ConfigDescription("Heavier projectiles fly slower but hit far harder.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of projectile speed removed at level 1 for the Ranged Heavy Draw adaptation.", impact = "Higher values slow low-level projectiles more.")
    double velocityPenaltyStart = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of projectile speed removed at max level for the Ranged Heavy Draw adaptation.", impact = "Higher values slow max-level projectiles more.")
    double velocityPenaltyEnd = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus damage fraction at level 1 for the Ranged Heavy Draw adaptation.", impact = "Higher values increase low-level projectile damage.")
    double damageBonusStart = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus damage fraction at max level for the Ranged Heavy Draw adaptation.", impact = "Higher values increase max-level projectile damage.")
    double damageBonusEnd = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Heavy Hit for the Ranged Heavy Draw adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerHeavyHit = 4;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
