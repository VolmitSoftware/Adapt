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

package art.arcane.adapt.content.adaptation.rift;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class RiftPearlRebound extends SimpleAdaptation<RiftPearlRebound.Config> {
  private static final double HARD_MAX_DAMAGE_REDUCTION = 0.9D;
  private static final double HARD_MAX_AIM_BIAS = 0.9D;

  private final NamespacedKey reboundedKey;
  private final Map<UUID, Long> pearlLandedAt = playerState();

  public RiftPearlRebound() {
    super("rift-pearl-rebound");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BALL);
    setInterval(4000);
    reboundedKey = new NamespacedKey(Adapt.instance, "rift_pearl_rebounded");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_rebound_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SLIME_BALL)
            .key("challenge_rift_rebound_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_rebound_100", "rift.pearl-rebound.rebounds", 100, 400);
    registerMilestone("challenge_rift_rebound_1k", "rift.pearl-rebound.rebounds", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.GREEN, "+ ", Form.pc(getDamageReduction(level), 0), 1);
    statLore(v, C.GREEN, "+ ", Form.pc(getAimBias(level), 0), 2);
  }

  @Override
  public void onTick() {
    long cutoff = System.currentTimeMillis() - getWindowMillis();
    pearlLandedAt.entrySet().removeIf(entry -> entry.getValue() <= cutoff);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p)) {
      return;
    }

    if (e.getHitBlock() == null || e.getHitBlockFace() == null) {
      return;
    }

    if (!pearl.getPersistentDataContainer().getKeys().isEmpty()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Vector normal = e.getHitBlockFace().getDirection().clone().normalize();
    Vector incoming = pearl.getVelocity().clone();
    if (incoming.lengthSquared() <= 1.0E-6D) {
      incoming = p.getEyeLocation().getDirection().clone();
    }

    Vector reflected = reflect(incoming, normal);
    if (reflected.lengthSquared() <= 1.0E-6D) {
      reflected = normal.clone();
    }
    reflected.normalize();

    double bias = getAimBias(level);
    Vector aim = p.getEyeLocation().getDirection().clone();
    if (aim.lengthSquared() <= 1.0E-6D) {
      aim = reflected.clone();
    }
    aim.normalize();

    Vector direction = reflected.multiply(1D - bias).add(aim.multiply(bias));
    if (direction.lengthSquared() <= 1.0E-6D) {
      direction = normal.clone();
    }
    Vector velocity = direction.normalize().multiply(getReboundSpeed());

    Location spawnAt = pearl.getLocation().clone().add(normal.clone().multiply(0.35));
    e.setCancelled(true);
    pearl.remove();

    J.runAt(spawnAt, () -> launchReboundPearl(p, spawnAt, velocity));

    fx(spawnAt, FxPriority.TRAIL)
        .burst(Particles.SMOKE, 4, 0.15)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 0.2, 0, 0.25, 0.03)
        .chord(Sound.BLOCK_SLIME_BLOCK_HIT, 0.7f, 1.4f, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.4f, 1.6f);
    addStat(p, "rift.pearl-rebound.rebounds", 1);
    xp(p, getConfig().xpOnRebound, "rift:pearl-rebound:rebound");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
      return;
    }

    Player p = e.getPlayer();
    if (getActiveLevel(p) <= 0) {
      return;
    }
    pearlLandedAt.put(p.getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    Long landed = pearlLandedAt.remove(id);
    if (landed == null || System.currentTimeMillis() - landed > getWindowMillis()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double reduction = getDamageReduction(level);
    e.setDamage(Math.max(0D, e.getDamage() * (1D - reduction)));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    pearlLandedAt.remove(e.getPlayer().getUniqueId());
  }

  private void launchReboundPearl(Player p, Location spawnAt, Vector velocity) {
    World world = spawnAt.getWorld();
    if (world == null) {
      return;
    }
    EnderPearl pearl = world.spawn(spawnAt, EnderPearl.class);
    pearl.setShooter(p);
    pearl.getPersistentDataContainer().set(reboundedKey, PersistentDataType.BYTE, (byte) 1);
    pearl.setVelocity(velocity);
  }

  private Vector reflect(Vector velocity, Vector normal) {
    double dot = velocity.dot(normal);
    return velocity.clone().subtract(normal.clone().multiply(2D * dot));
  }

  private double getDamageReduction(int level) {
    double value = getConfig().damageReductionBase + (Math.max(0, level - 1) * getConfig().damageReductionPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_DAMAGE_REDUCTION, value));
  }

  private double getAimBias(int level) {
    double value = getConfig().aimBiasBase + (Math.max(0, level - 1) * getConfig().aimBiasPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_AIM_BIAS, value));
  }

  private double getReboundSpeed() {
    return Math.max(0.4D, getConfig().reboundSpeed);
  }

  private long getWindowMillis() {
    return Math.max(50L, getConfig().damageWindowMillis);
  }

  @ConfigDescription("Thrown pearls bounce once off surfaces toward where you aim, and pearl teleport damage is reduced.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of ender pearl teleport damage removed at level 1.", impact = "Higher values soften pearl landings more; capped at 90 percent.")
    double damageReductionBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pearl damage reduction fraction per adaptation level beyond the first.", impact = "Higher values make leveling reduce pearl damage faster.")
    double damageReductionPerLevel = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction the rebounded pearl steers toward your look direction at level 1.", impact = "Higher values aim the bounce more at your crosshair and less like a pure reflection; capped at 90 percent.")
    double aimBiasBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra aim steering fraction per adaptation level beyond the first.", impact = "Higher values make leveling aim the bounce at you more precisely.")
    double aimBiasPerLevel = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Launch speed of the rebounded pearl.", impact = "Higher values throw the bounced pearl further and faster.")
    double reboundSpeed = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds after a pearl teleport during which fall damage is treated as pearl damage.", impact = "Higher values widen the window that reduces pearl landing damage.")
    long damageWindowMillis = 400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted each time a pearl rebounds.", impact = "Higher values grant more skill XP per rebound.")
    double xpOnRebound = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.35;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
