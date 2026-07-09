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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

public class RangedRicochetBolt extends SimpleAdaptation<RangedRicochetBolt.Config> {
  private static final String RICOCHET_COUNT_META = "adapt-ricochet-count";
  private static final String RICOCHET_MAX_META = "adapt-ricochet-max";
  private static final String BONUS_DAMAGE_META = "adapt-ricochet-bonus-damage";

  public RangedRicochetBolt() {
    super("ranged-ricochet-bolt");
    registerConfiguration(Config.class);
    setIcon(Material.SPECTRAL_ARROW);
    setInterval(1400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPECTRAL_ARROW)
        .key("challenge_ranged_ricochet_kills_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_ranged_ricochet_kills_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_ranged_ricochet_kills_50", "ranged.ricochet-bolt.ricochet-kills", 50, 500);
    registerMilestone("challenge_ranged_ricochet_kills_500", "ranged.ricochet-bolt.ricochet-kills", 500, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxRicochets(level), 1);
    statLore(v, Form.pc(getSpeedBonusPerRicochet(level), 0), 2);
    statLore(v, Form.f(getDamageBonusPerRicochet(level), 2), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player p)) {
      return;
    }

    if (projectile.hasMetadata(RangedHeartseeker.SEEKING_ARROW_META)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (e.getHitBlock() == null || !supportsRicochet(projectile)) {
      return;
    }

    int ricochetCount = Math.max(0, getMetadataInt(projectile, RICOCHET_COUNT_META, 0));
    int maxRicochets = Math.max(1, getMetadataInt(projectile, RICOCHET_MAX_META, getMaxRicochets(level)));
    if (ricochetCount >= maxRicochets) {
      fx(e.getHitBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.2D)
          .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.2F);
      return;
    }

    Vector incoming = resolveIncomingVector(projectile);
    if (incoming.lengthSquared() < getConfig().minRicochetVelocitySquared) {
      return;
    }

    BlockFace hitFace = resolveHitFace(e, incoming);
    if (hitFace == null) {
      return;
    }

    Vector reflectedDir = reflect(incoming.clone().normalize(), hitFace);
    if (reflectedDir.lengthSquared() <= 0.0000001) {
      return;
    }

    reflectedDir.normalize();
    double nextSpeed = Math.max(getConfig().minimumPostBounceSpeed, incoming.length()) * (1D + getSpeedBonusPerRicochet(level));
    if (nextSpeed <= 0) {
      return;
    }

    Vector ricochetVelocity = reflectedDir.clone().multiply(nextSpeed);
    int nextRicochetCount = ricochetCount + 1;
    double bonusDamage = getMetadataDouble(projectile, BONUS_DAMAGE_META, 0D) + getDamageBonusPerRicochet(level);

    Location bounceLocation = projectile.getLocation().clone()
        .add(hitFace.getDirection().normalize().multiply(getConfig().spawnOffsetFromSurface))
        .add(reflectedDir.clone().multiply(getConfig().spawnOffsetAlongDirection));
    Projectile ricochet = spawnRicochetProjectile(projectile, bounceLocation, ricochetVelocity, p);
    if (ricochet == null) {
      return;
    }

    ricochet.setMetadata(RICOCHET_COUNT_META, new FixedMetadataValue(Adapt.instance, nextRicochetCount));
    ricochet.setMetadata(RICOCHET_MAX_META, new FixedMetadataValue(Adapt.instance, maxRicochets));
    ricochet.setMetadata(BONUS_DAMAGE_META, new FixedMetadataValue(Adapt.instance, bonusDamage));

    Location hitCenter = e.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
    fx(hitCenter, FxPriority.COMBAT)
        .particle(Particle.ELECTRIC_SPARK, Math.max(1, getConfig().sparkParticleCount), 0, 0, 0, getConfig().sparkSpread, 0.02D)
        .particle(Particles.CRIT_MAGIC, Math.max(1, getConfig().critParticleCount), 0, 0, 0, getConfig().critSpread, 0.08D)
        .dustRing(Color.fromRGB(180, 210, 255), 0.4D + (nextRicochetCount * 0.15D), 12, 1.0F)
        .line(Particle.ELECTRIC_SPARK, hitCenter.getX() + (reflectedDir.getX() * 1.2D), hitCenter.getY() + (reflectedDir.getY() * 1.2D), hitCenter.getZ() + (reflectedDir.getZ() * 1.2D), 5)
        .chord(Sound.BLOCK_ANVIL_HIT, 0.85F, (float) Math.max(0.4, getConfig().bouncePitchBase - (nextRicochetCount * getConfig().bouncePitchDropPerRicochet)),
            Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9F, (float) Math.min(2.0, getConfig().sparkPitchBase + (nextRicochetCount * getConfig().sparkPitchRaisePerRicochet)));
    xp(p, getConfig().xpPerRicochet + (nextRicochetCount * getConfig().xpPerRicochetStep));
    addStat(p, "ranged.ricochet-bolt.total-ricochets", 1);
    projectile.remove();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player p) || !projectile.hasMetadata(BONUS_DAMAGE_META)) {
      return;
    }

    if (!canDamageTarget(p, e.getEntity())) {
      return;
    }

    double bonusDamage = getMetadataDouble(projectile, BONUS_DAMAGE_META, 0D);
    if (bonusDamage > 0 && e.getDamage() > 0) {
      e.setDamage(e.getDamage() + bonusDamage);
      int count = Math.min(12, 4 + (int) Math.round(bonusDamage));
      int ricochets = Math.max(1, getMetadataInt(projectile, RICOCHET_COUNT_META, 1));
      fx(e.getEntity(), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, count, 0.3D)
          .sound(Sound.ENTITY_ARROW_HIT, 0.7F, (float) Math.min(2.0, 1.2 + (ricochets * 0.12)));
    }
  }

  @EventHandler
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() instanceof Player p && hasActiveAdaptation(p)) {
      if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
          && dmg.getDamager() instanceof Projectile projectile
          && projectile.hasMetadata(RICOCHET_COUNT_META)
          && projectile.getShooter() instanceof Player) {
        addStat(p, "ranged.ricochet-bolt.ricochet-kills", 1);
        fx(e.getEntity().getLocation(), FxPriority.COMBAT)
            .dustRing(Color.fromRGB(90, 220, 230), 1.0D, 16, 1.1F)
            .burst(Particles.FIREWORK, 8, 0.3D)
            .chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.6F, 1.6F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 2.0F);
      }
    }
  }

  private Projectile spawnRicochetProjectile(Projectile original, Location spawnLocation, Vector velocity, Player shooter) {
    Vector dir = velocity.clone().normalize();
    float speed = (float) Math.max(0.2, velocity.length());
    if (original instanceof SpectralArrow sourceSpectral && sourceSpectral instanceof AbstractArrow sourceAbstract) {
      SpectralArrow spectral = original.getWorld().spawn(spawnLocation, SpectralArrow.class);
      copyArrowState(sourceAbstract, spectral, shooter, velocity);
      spectral.setGlowingTicks(sourceSpectral.getGlowingTicks());
      return spectral;
    }

    if (original instanceof Trident sourceTrident) {
      Trident trident = original.getWorld().spawn(spawnLocation, Trident.class);
      copyArrowState(sourceTrident, trident, shooter, velocity);
      trident.setItem(sourceTrident.getItem());
      return trident;
    }

    if (original instanceof Arrow sourceArrow) {
      Arrow arrow = original.getWorld().spawnArrow(spawnLocation, dir, speed, 0f);
      copyArrowState(sourceArrow, arrow, shooter, velocity);
      arrow.setBasePotionType(sourceArrow.getBasePotionType());
      sourceArrow.getCustomEffects().forEach(effect -> arrow.addCustomEffect(effect, true));
      return arrow;
    }

    if (original instanceof Snowball) {
      Snowball snowball = original.getWorld().spawn(spawnLocation, Snowball.class);
      copyProjectileState(original, snowball, shooter, velocity);
      return snowball;
    }

    if (original instanceof Egg) {
      Egg egg = original.getWorld().spawn(spawnLocation, Egg.class);
      copyProjectileState(original, egg, shooter, velocity);
      return egg;
    }

    if (original instanceof EnderPearl) {
      EnderPearl pearl = original.getWorld().spawn(spawnLocation, EnderPearl.class);
      copyProjectileState(original, pearl, shooter, velocity);
      return pearl;
    }

    if (original instanceof ThrownPotion sourcePotion) {
      ThrownPotion potion = original.getWorld().spawn(spawnLocation, ThrownPotion.class);
      copyProjectileState(sourcePotion, potion, shooter, velocity);
      potion.setItem(sourcePotion.getItem().clone());
      return potion;
    }

    if (original instanceof ThrownExpBottle) {
      ThrownExpBottle bottle = original.getWorld().spawn(spawnLocation, ThrownExpBottle.class);
      copyProjectileState(original, bottle, shooter, velocity);
      return bottle;
    }

    return null;
  }

  private void copyArrowState(AbstractArrow source, AbstractArrow target, Player shooter, Vector velocity) {
    copyProjectileState(source, target, shooter, velocity);
    target.setDamage(source.getDamage());
    target.setCritical(source.isCritical());
    target.setKnockbackStrength(source.getKnockbackStrength());
    target.setPierceLevel(source.getPierceLevel());
    target.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
  }

  private void copyProjectileState(Projectile source, Projectile target, Player shooter, Vector velocity) {
    target.setShooter(shooter);
    target.setVelocity(velocity);
    target.setBounce(source.doesBounce());
    target.setGravity(source.hasGravity());
    target.setFireTicks(source.getFireTicks());
    source.getPersistentDataContainer().copyTo(target.getPersistentDataContainer(), true);
  }

  private Vector reflect(Vector incoming, BlockFace face) {
    Vector normal = face.getDirection().normalize();
    double dot = incoming.dot(normal);
    return incoming.clone().subtract(normal.multiply(2D * dot));
  }

  private Vector resolveIncomingVector(Projectile projectile) {
    Vector liveVelocity = projectile.getVelocity().clone();
    if (liveVelocity.lengthSquared() >= getConfig().minimumLiveVelocitySquared) {
      return liveVelocity;
    }

    Vector facing = projectile.getLocation().getDirection().clone();
    if (facing.lengthSquared() > 0.0000001) {
      return facing.normalize().multiply(Math.max(getConfig().minimumPostBounceSpeed, liveVelocity.length()));
    }

    return liveVelocity;
  }

  private boolean supportsRicochet(Projectile projectile) {
    if (projectile instanceof AbstractArrow) {
      return true;
    }

    return getConfig().applyToAllProjectiles
        && (projectile instanceof Snowball
        || projectile instanceof Egg
        || projectile instanceof EnderPearl
        || projectile instanceof ThrownPotion
        || projectile instanceof ThrownExpBottle);
  }

  private BlockFace resolveHitFace(ProjectileHitEvent e, Vector incoming) {
    if (e.getHitBlockFace() != null) {
      return e.getHitBlockFace();
    }

    double ax = Math.abs(incoming.getX());
    double ay = Math.abs(incoming.getY());
    double az = Math.abs(incoming.getZ());

    if (ay >= ax && ay >= az) {
      return incoming.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
    }

    if (ax >= az) {
      return incoming.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
    }

    return incoming.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
  }

  private int getMetadataInt(Projectile projectile, String key, int fallback) {
    for (MetadataValue value : projectile.getMetadata(key)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asInt();
      }
    }

    return fallback;
  }

  private double getMetadataDouble(Projectile projectile, String key, double fallback) {
    for (MetadataValue value : projectile.getMetadata(key)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asDouble();
      }
    }

    return fallback;
  }

  private int getMaxRicochets(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxRicochetsBase + (getLevelPercent(level) * getConfig().maxRicochetsFactor)));
  }

  private double getSpeedBonusPerRicochet(int level) {
    return Math.min(getConfig().maxSpeedBonusPerRicochet,
        getConfig().speedBonusPerRicochetBase + (getLevelPercent(level) * getConfig().speedBonusPerRicochetFactor));
  }

  private double getDamageBonusPerRicochet(int level) {
    return Math.min(getConfig().maxDamageBonusPerRicochet,
        getConfig().damageBonusPerRicochetBase + (getLevelPercent(level) * getConfig().damageBonusPerRicochetFactor));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Projectiles ricochet from block impacts with chained bounces, scaling speed, and bonus damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRicochetsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRicochetsFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedBonusPerRicochetBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double speedBonusPerRicochetFactor = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Speed Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxSpeedBonusPerRicochet = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusPerRicochetBase = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusPerRicochetFactor = 2.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Damage Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageBonusPerRicochet = 3.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Ricochet Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minRicochetVelocitySquared = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Live Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumLiveVelocitySquared = 0.0004;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Post Bounce Speed for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumPostBounceSpeed = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset From Surface for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spawnOffsetFromSurface = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset Along Direction for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spawnOffsetAlongDirection = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int sparkParticleCount = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkSpread = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crit Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int critParticleCount = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crit Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double critSpread = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bouncePitchBase = 1.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Drop Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bouncePitchDropPerRicochet = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkPitchBase = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Raise Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sparkPitchRaisePerRicochet = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRicochet = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet Step for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRicochetStep = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allow ricochet behavior to apply to throwables (snowballs, eggs, pearls, potions, exp bottles) so all supported player projectiles can bounce.", impact = "True enables universal ricochet across most player-thrown projectiles.")
    boolean applyToAllProjectiles = true;

    public Config() {
      costFactor = 0.74;
      initialCost = 4;
    }
  }
}
