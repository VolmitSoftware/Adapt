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

package art.arcane.adapt.content.adaptation.blocking;

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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class BlockingMirrorBlock extends SimpleAdaptation<BlockingMirrorBlock.Config> {
  private static final String REFLECTED_META = "adapt-mirror-reflected";
  private static final String DAMAGE_FACTOR_META = "adapt-mirror-damage-factor";

  public BlockingMirrorBlock() {
    super("blocking-mirror-block");
    registerConfiguration(Config.class);
    setIcon(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_mirror_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_mirror_100", "blocking.mirror-block.projectiles-reflected", 100, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_mirror_3in5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getReflectChance(level), 0), 1);
    statLore(v, Form.pc(getReflectedDamageFactor(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getReflectCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Projectile projectile)) {
      return;
    }

    applyReflectedDamageModifier(e, projectile);

    if (!(e.getEntity() instanceof Player defender) || !isMirrorReady(defender) || projectile.hasMetadata(REFLECTED_META)) {
      return;
    }

    int level = getActiveLevel(defender);
    long now = System.currentTimeMillis();
    long next = getStorageLong(defender, "mirrorBlockNext", 0L);
    if (next > now) {
      onCooldownTell(defender);
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getReflectChance(level)) {
      return;
    }

    e.setCancelled(true);
    reflectProjectile(defender, projectile, level);
    setStorage(defender, "mirrorBlockNext", now + getReflectCooldownMillis(level));

    mirrorFlash(defender);
    reflectTrail(projectile);
    xp(defender, getConfig().xpOnReflect);
    addStat(defender, "blocking.mirror-block.projectiles-reflected", 1);

    long windowStart = getStorageLong(defender, "mirrorWindowStart", 0L);
    int windowCount = getStorageInt(defender, "mirrorWindowCount", 0);
    if (now - windowStart > 5000L) {
      windowStart = now;
      windowCount = 1;
    } else {
      windowCount++;
    }
    setStorage(defender, "mirrorWindowStart", windowStart);
    setStorage(defender, "mirrorWindowCount", windowCount);
    if (windowCount >= 3 && grantOnce(defender, "challenge_blocking_mirror_3in5")) {
      fx(defender.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .particle(Particle.REVERSE_PORTAL, 12, 0, 0.3D, 0, 0.4D, 0.02D)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
    }
  }

  private void mirrorFlash(Player defender) {
    Location chest = defender.getLocation().add(0, 1.0D, 0);
    fx(chest, FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.5D, 8, 0)
        .burst(Particles.CRIT_MAGIC, 10, 0.35D)
        .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.35F, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8F, 0.8F, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.5F);
  }

  private void reflectTrail(Projectile projectile) {
    timeline(projectile)
        .duration(40)
        .priority(FxPriority.TRAIL)
        .cullRadius(24.0D)
        .frame((fxE, tick, progress) -> fxE.particle(Particle.WAX_ON, 1, 0, 0, 0, 0, 0))
        .start();
  }

  private void onCooldownTell(Player defender) {
    Vector look = defender.getEyeLocation().getDirection();
    Location front = defender.getEyeLocation().add(look.getX() * 0.4D, look.getY() * 0.4D, look.getZ() * 0.4D);
    fx(front, FxPriority.AMBIENT)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D);
  }

  private void applyReflectedDamageModifier(EntityDamageByEntityEvent e, Projectile projectile) {
    if (!(projectile.getShooter() instanceof Player shooter) || !projectile.hasMetadata(DAMAGE_FACTOR_META)) {
      return;
    }

    if (!canDamageTarget(shooter, e.getEntity())) {
      return;
    }

    double factor = getMetadataDouble(projectile, DAMAGE_FACTOR_META, 1D);
    e.setDamage(e.getDamage() * factor);
  }

  private void reflectProjectile(Player defender, Projectile projectile, int level) {
    Vector incoming = projectile.getVelocity().clone();
    Vector reflected = incoming.multiply(-Math.max(0.01, getReflectVelocityFactor(level)));
    if (reflected.lengthSquared() < getConfig().minReflectedVelocitySquared) {
      reflected = defender.getEyeLocation().getDirection().normalize().multiply(getConfig().fallbackReflectedSpeed);
    }

    J.teleport(projectile, defender.getEyeLocation().add(defender.getEyeLocation().getDirection().multiply(0.55)));
    projectile.setShooter(defender);
    projectile.setVelocity(reflected);
    projectile.setMetadata(REFLECTED_META, new FixedMetadataValue(Adapt.instance, true));
    projectile.setMetadata(DAMAGE_FACTOR_META, new FixedMetadataValue(Adapt.instance, getReflectedDamageFactor(level)));
  }

  private boolean isMirrorReady(Player p) {
    return hasActiveAdaptation(p) && p.isBlocking() && hasShield(p);
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private double getMetadataDouble(Projectile projectile, String key, double fallback) {
    for (MetadataValue value : projectile.getMetadata(key)) {
      if (value.getOwningPlugin() == Adapt.instance) {
        return value.asDouble();
      }
    }

    return fallback;
  }

  private double getReflectChance(int level) {
    return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
  }

  private double getReflectedDamageFactor(int level) {
    return Math.min(getConfig().maxReflectedDamageFactor,
        getConfig().reflectedDamageFactorBase + (getLevelPercent(level) * getConfig().reflectedDamageFactorIncrease));
  }

  private double getReflectVelocityFactor(int level) {
    return Math.min(getConfig().maxReflectVelocityFactor, getConfig().reflectVelocityFactorBase + (getLevelPercent(level) * getConfig().reflectVelocityFactor));
  }

  private long getReflectCooldownMillis(int level) {
    return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Blocking with a shield can reflect incoming projectiles at reduced force and damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorBase = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Increase for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorIncrease = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflected Damage Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectedDamageFactor = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactorBase = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectVelocityFactor = 1.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Reflected Velocity Squared for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minReflectedVelocitySquared = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fallback Reflected Speed for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallbackReflectedSpeed = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Reflect for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnReflect = 8;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
