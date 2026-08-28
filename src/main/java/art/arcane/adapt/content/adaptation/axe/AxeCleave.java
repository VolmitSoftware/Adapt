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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.Adaptation;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeCleave extends SimpleAdaptation<AxeCleave.Config> {
  private static final long CLEAVE_MARK_MS = 250L;
  private final Map<UUID, Long> cleaving = new ConcurrentHashMap<>();

  public AxeCleave() {
    super("axe-cleave");
    registerConfiguration(Config.class);
    setIcon(Material.DIAMOND_AXE);
    setInterval(9973);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_AXE)
        .key("challenge_axe_cleave_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_AXE)
            .key("challenge_axe_cleave_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_cleave_1k", "axe.cleave.targets-hit", 1000, 500);
    registerMilestone("challenge_axe_cleave_10k", "axe.cleave.targets-hit", 10000, 1800);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.RED, "+ ", (int) Math.round(getHalfArcDegrees(level) * 2D) + "°", 1);
    statLore(v, C.RED, "x ", getTargetCap(level), 2);
    statLore(v, C.RED, "+ ", Form.pc(getDamageShare(level), 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    long now = System.currentTimeMillis();
    Long marked = cleaving.get(e.getEntity().getUniqueId());
    if (marked != null && marked > now) {
      return;
    }

    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    double baseDamage = e.getDamage();
    if (baseDamage <= 0D) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity primary = combat.target();
    int level = combat.level();
    double secondary = baseDamage * getDamageShare(level);
    if (secondary <= 0D) {
      return;
    }

    double radius = getRadius(level);
    double cosHalf = Math.cos(Math.toRadians(getHalfArcDegrees(level)));
    int cap = getTargetCap(level);
    Location origin = p.getEyeLocation();
    Vector look = origin.getDirection().normalize();

    int hits = 0;
    for (Entity nearby : primary.getWorld().getNearbyEntities(primary.getLocation(), radius, radius, radius)) {
      if (hits >= cap) {
        break;
      }
      if (!(nearby instanceof LivingEntity target) || target == p || target == primary || target instanceof ArmorStand) {
        continue;
      }
      if (!canDamageTarget(p, target)) {
        continue;
      }

      Vector to = target.getLocation().add(0D, target.getHeight() * 0.5D, 0D).toVector().subtract(origin.toVector());
      double distance = to.length();
      if (distance < 1.0E-4D) {
        continue;
      }
      if (look.dot(to.multiply(1D / distance)) < cosHalf) {
        continue;
      }

      hits++;
      markAndDamage(target, p, secondary, now);
    }

    if (hits > 0) {
      int total = hits;
      J.runEntity(p, () -> {
        damageHand(p, getConfig().durabilityCost);
        addStat(p, "axe.cleave.targets-hit", total);
        xp(p, total * getConfig().xpPerTarget);
      });
      Location tip = origin.clone().add(look.clone().multiply(1.6D));
      fx(tip, FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, Math.min(10, total * 3 + 3), 0D, 0D, 0D, 0.35D, 0.08D)
          .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.8F, Sound.ITEM_AXE_STRIP, 0.35F, 1.2F);
    }
  }

  private void markAndDamage(LivingEntity target, Player attacker, double damage, long now) {
    UUID id = target.getUniqueId();
    cleaving.put(id, now + CLEAVE_MARK_MS);
    boolean scheduled = J.runEntity(target, () -> {
      try {
        if (target.isValid() && !target.isDead()) {
          target.damage(damage, attacker);
          fx(target.getLocation().add(0D, target.getHeight() * 0.6D, 0D), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, 5, 0.25D);
        }
      } finally {
        cleaving.remove(id);
      }
    });
    if (!scheduled) {
      cleaving.remove(id);
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    cleaving.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  private double getHalfArcDegrees(int level) {
    return getConfig().halfArcDegreesBase + (getLevelPercent(level) * getConfig().halfArcDegreesFactor);
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getTargetCap(int level) {
    return getConfig().targetCapBase + (int) Math.floor(getLevelPercent(level) * getConfig().targetCapMaxBonus);
  }

  private double getDamageShare(int level) {
    return getConfig().damageShareBase + (getLevelPercent(level) * getConfig().damageShareFactor);
  }

  @Override
  public void unregister() {
    cleaving.clear();
    super.unregister();
  }

  @ConfigDescription("Melee axe swings cleave extra enemies in a short frontal arc.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Half the cleave arc width in degrees before level scaling.", impact = "Higher values widen the frontal cone that cleave can hit.")
    double halfArcDegreesBase = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra half-arc degrees granted by leveling.", impact = "Higher values widen the cleave cone faster with level.")
    double halfArcDegreesFactor = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cleave reach in blocks before level scaling.", impact = "Higher values let cleave strike enemies farther out.")
    double radiusBase = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cleave reach in blocks granted by leveling.", impact = "Higher values extend cleave range with level.")
    double radiusFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of secondary targets cleave can strike.", impact = "Higher values cleave more enemies at low levels.")
    int targetCapBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional secondary targets unlocked at maximum level.", impact = "Higher values raise the cleave target cap toward its ceiling.")
    int targetCapMaxBonus = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the primary hit's damage dealt to each cleaved target.", impact = "Higher values make cleaved targets take more damage.")
    double damageShareBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra damage-share fraction granted by leveling.", impact = "Higher values reward leveling with stronger cleave damage.")
    double damageShareFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability spent from the axe when a cleave connects.", impact = "Higher values wear the axe out faster when cleaving.")
    int durabilityCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per cleaved target.", impact = "Higher values accelerate skill progression from cleaving.")
    double xpPerTarget = 4;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 6;
    }
  }
}
