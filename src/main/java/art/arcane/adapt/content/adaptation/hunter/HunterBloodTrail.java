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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HunterBloodTrail extends SimpleAdaptation<HunterBloodTrail.Config> {
  private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(150, 10, 10), 1.1F);
  private final Map<UUID, Wound> wounds = new ConcurrentHashMap<>();

  public HunterBloodTrail() {
    super("hunter-blood-trail");
    registerConfiguration(Config.class);
    setIcon(Material.REDSTONE);
    setInterval(250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_hunter_blood_trail_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.REDSTONE_BLOCK)
            .key("challenge_hunter_blood_trail_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_hunter_blood_trail_250", "hunter.blood-trail.trails-followed", 250, 400);
    registerMilestone("challenge_hunter_blood_trail_2500", "hunter.blood-trail.trails-followed", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getTrailDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, C.YELLOW, "+ ", Form.f(getTrackingRange(level), 1), 2);
  }

  static boolean isWounded(double healthAfter, double maxHealth, double fraction) {
    return healthAfter > 0 && maxHealth > 0 && healthAfter <= maxHealth * fraction;
  }

  static double trackingRange(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }

  static long trailDurationTicks(double levelPercent, long base, double factor) {
    return base + Math.round(levelPercent * factor);
  }

  long getTrailDurationTicks(int level) {
    return trailDurationTicks(getLevelPercent(level), getConfig().trailDurationTicksBase, getConfig().trailDurationTicksFactor);
  }

  double getTrackingRange(int level) {
    return trackingRange(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null || attack.target() instanceof Player || !(attack.target() instanceof LivingEntity victim)) {
      return;
    }

    double maxHealth = victim.getMaxHealth();
    double healthAfter = victim.getHealth() - e.getFinalDamage();
    if (!isWounded(healthAfter, maxHealth, getConfig().woundHealthFraction)) {
      return;
    }

    Player p = attack.attacker();
    int level = attack.level();
    long expiresAt = M.ms() + (getTrailDurationTicks(level) * 50L);
    double range = getTrackingRange(level);

    Wound previous = wounds.put(victim.getUniqueId(),
        new Wound(p.getUniqueId(), expiresAt, range, victim.getWorld().getUID()));
    if (wounds.size() > Math.max(1, getConfig().maxTrackedWounds)) {
      pruneOldest();
    }
    if (previous == null || !previous.hunterId.equals(p.getUniqueId())) {
      xpSilent(p, getConfig().xpPerWound);
    }
  }

  private void pruneOldest() {
    UUID oldestKey = null;
    long oldestExpiry = Long.MAX_VALUE;
    for (Map.Entry<UUID, Wound> entry : wounds.entrySet()) {
      if (entry.getValue().expiresAt < oldestExpiry) {
        oldestExpiry = entry.getValue().expiresAt;
        oldestKey = entry.getKey();
      }
    }
    if (oldestKey != null) {
      wounds.remove(oldestKey);
    }
  }

  @Override
  public void onTick() {
    long now = M.ms();
    int processed = 0;
    int limit = Math.max(1, getConfig().maxTrackedWounds);
    Iterator<Map.Entry<UUID, Wound>> iterator = wounds.entrySet().iterator();
    while (iterator.hasNext() && processed < limit) {
      Map.Entry<UUID, Wound> entry = iterator.next();
      Wound wound = entry.getValue();
      if (wound.expiresAt <= now) {
        iterator.remove();
        continue;
      }
      processed++;
      renderWound(entry.getKey(), wound);
    }
  }

  private void renderWound(UUID entityId, Wound wound) {
    Entity mob = Bukkit.getEntity(entityId);
    Player hunter = Bukkit.getPlayer(wound.hunterId);
    if (mob == null || hunter == null) {
      wounds.remove(entityId);
      return;
    }

    J.runEntity(mob, () -> {
      if (!mob.isValid()) {
        wounds.remove(entityId);
        return;
      }
      Location current = mob.getLocation().add(0, mob.getHeight() * 0.35D, 0);
      J.runEntity(hunter, () -> emitTrail(hunter, wound, current));
    });
  }

  private void emitTrail(Player hunter, Wound wound, Location current) {
    if (!hunter.isOnline()) {
      return;
    }

    World hunterWorld = hunter.getWorld();
    if (!hunterWorld.getUID().equals(wound.worldId) || current.getWorld() == null || !hunterWorld.equals(current.getWorld())) {
      wound.lastRender = null;
      return;
    }

    Location hunterLocation = hunter.getLocation();
    if (hunterLocation.distanceSquared(current) > wound.range * wound.range) {
      wound.lastRender = null;
      return;
    }

    if (!wound.followed) {
      wound.followed = true;
      addStat(hunter, "hunter.blood-trail.trails-followed", 1);
    }

    Location previous = wound.lastRender;
    int points = Math.max(1, getConfig().trailPointsPerRender);
    if (previous != null && previous.getWorld() != null && previous.getWorld().equals(current.getWorld())) {
      for (int i = 1; i <= points; i++) {
        double t = i / (double) points;
        double x = previous.getX() + ((current.getX() - previous.getX()) * t);
        double y = previous.getY() + ((current.getY() - previous.getY()) * t);
        double z = previous.getZ() + ((current.getZ() - previous.getZ()) * t);
        hunter.spawnParticle(Particle.DUST, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, BLOOD_DUST);
      }
    } else {
      hunter.spawnParticle(Particle.DUST, current, 1, 0.05D, 0.05D, 0.05D, 0.0D, BLOOD_DUST);
    }
    wound.lastRender = current.clone();
  }

  @ConfigDescription("Mobs you wound below half health leave a blood scent trail only you can see.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base blood-trail duration in ticks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long trailDurationTicksBase = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blood-trail duration in ticks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trailDurationTicksFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base tracking range in blocks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional tracking range in blocks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of max health a target must fall to or below to bleed for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double woundHealthFraction = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum simultaneously tracked blood trails for the Hunter Blood Trail adaptation.", impact = "Lower values reduce scheduling and particle work.")
    int maxTrackedWounds = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Interpolated blood-scent points drawn per render for the Hunter Blood Trail adaptation.", impact = "Lower values reduce particle dispatch.")
    int trailPointsPerRender = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent xp granted when a fresh target starts bleeding for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerWound = 3;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      initialCost = 5;
    }
  }

  private static final class Wound {
    private final UUID hunterId;
    private final long expiresAt;
    private final double range;
    private final UUID worldId;
    private volatile Location lastRender;
    private volatile boolean followed;

    private Wound(UUID hunterId, long expiresAt, double range, UUID worldId) {
      this.hunterId = hunterId;
      this.expiresAt = expiresAt;
      this.range = range;
      this.worldId = worldId;
    }
  }
}
