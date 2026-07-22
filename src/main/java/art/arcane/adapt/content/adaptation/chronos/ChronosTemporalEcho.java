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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class ChronosTemporalEcho extends SimpleAdaptation<ChronosTemporalEcho.Config> {
  private static final String ECHO_META = "adapt-chronos-temporal-echo";
  private final Cooldowns cooldowns = cooldowns();

  public ChronosTemporalEcho() {
    super("chronos-temporal-echo");
    registerConfiguration(Config.class);
    setIcon(Material.AMETHYST_CLUSTER);
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_chronos_echo_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_echo_200", "chronos.temporal-echo.echo-hits", 200, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getEchoDelayTicks(level) * 50D, 1), 1);
    statLore(v, Form.pc(getEchoVelocityFactor(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getEntity())
        || !(e.getEntity().getShooter() instanceof Player p)
        || !hasActiveAdaptation(p)
        || e.getEntity().hasMetadata(ECHO_META)) {
      return;
    }

    EchoType echoType = getEchoType(e.getEntity());
    if (echoType == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    Projectile original = e.getEntity();
    Vector originalVelocity = original.getVelocity().clone();
    int delay = getEchoDelayTicks(level);
    J.runEntity(p, () -> spawnEcho(p, echoType, originalVelocity, level), delay);
    fx(p.getEyeLocation(), FxPriority.TRAIL).particle(Particles.ENCHANTMENT_TABLE, 2, 0, 0, 0, 0.2D, 0.02D);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (e.getHitEntity() == null || !e.getEntity().hasMetadata(ECHO_META)) {
      return;
    }

    if (!(e.getHitEntity() instanceof LivingEntity target)) {
      return;
    }

    Player shooter = e.getEntity().getShooter() instanceof Player p ? p : null;
    if (isProtectedFriendly(shooter, target)) {
      return;
    }

    if (shooter != null && shooter.isOnline()) {
      addStat(shooter, "chronos.temporal-echo.echo-hits", 1);
    }

    target.setNoDamageTicks(0);
    target.setLastDamage(0.0D);
  }

  private void spawnEcho(Player p, EchoType type, Vector velocity, int level) {
    if (!p.isOnline() || p.isDead()) {
      return;
    }

    Projectile echo = switch (type) {
      case ARROW ->
          p.getWorld().spawnArrow(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)),
              p.getLocation().getDirection(), 0.6f, 0f);
      case SNOWBALL ->
          p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), Snowball.class);
      case EGG ->
          p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), Egg.class);
      case ENDER_PEARL ->
          p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), EnderPearl.class);
    };
    echo.setShooter(p);
    echo.setVelocity(velocity.multiply(getEchoVelocityFactor(level)));
    echo.setMetadata(ECHO_META, new FixedMetadataValue(Adapt.instance, true));

    Location spawnAt = echo.getLocation();
    fx(spawnAt, FxPriority.COMBAT)
        .ring(Particle.REVERSE_PORTAL, 0.6D, 6, 0.0D)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75F, 1.35F, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.1F);
    timeline(echo)
        .duration(4)
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((f, tick, progress) -> f.particle(Particle.REVERSE_PORTAL, 2, 0, 0, 0, 0.05D, 0.01D))
        .start();

    xp(p, getConfig().xpPerEcho);
  }

  private EchoType getEchoType(Projectile projectile) {
    if (projectile instanceof Arrow) {
      return EchoType.ARROW;
    }

    if (projectile instanceof Snowball) {
      return EchoType.SNOWBALL;
    }

    if (projectile instanceof Egg) {
      return EchoType.EGG;
    }

    if (projectile instanceof EnderPearl) {
      return EchoType.ENDER_PEARL;
    }

    return null;
  }

  private int getEchoDelayTicks(int level) {
    return Math.max(1, (int) Math.round(getConfig().echoDelayTicksBase - (getLevelPercent(level) * getConfig().echoDelayTicksFactor)));
  }

  private double getEchoVelocityFactor(int level) {
    return Math.min(getConfig().maxEchoVelocityFactor, getConfig().echoVelocityFactorBase + (getLevelPercent(level) * getConfig().echoVelocityFactorFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  private enum EchoType {
    ARROW,
    SNOWBALL,
    EGG,
    ENDER_PEARL
  }

  @ConfigDescription("Replays your last projectile action shortly after firing at reduced power.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoDelayTicksBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoDelayTicksFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoVelocityFactorBase = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double echoVelocityFactorFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Echo Velocity Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEchoVelocityFactor = 0.92;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 2600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Echo for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerEcho = 12;

    public Config() {
      baseCost = 5;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
