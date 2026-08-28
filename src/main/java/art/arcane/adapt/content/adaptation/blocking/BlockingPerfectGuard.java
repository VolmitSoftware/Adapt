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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class BlockingPerfectGuard extends SimpleAdaptation<BlockingPerfectGuard.Config> {
  private final Map<UUID, Long> raiseAt = playerState();

  public BlockingPerfectGuard() {
    super("blocking-perfect-guard");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_perfect_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_blocking_perfect_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_perfect_100", "blocking.perfect-guard.hits-negated", 100, 600);
    registerMilestone("challenge_blocking_perfect_1k", "blocking.perfect-guard.hits-negated", 1000, 2500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "* ", Form.duration(getWindowMillis(level), 2), 1);
    statLore(v, Form.duration(getStaggerTicks(level) * 50D, 1), 2);
    statLore(v, Form.f(getStaggerAmplifier(level) + 1, 0), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (getLevel(p) <= 0 || !hasShield(p)) {
      return;
    }
    raiseAt.put(p.getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player defender)) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !hasShield(defender)) {
      return;
    }

    Long raised = raiseAt.get(defender.getUniqueId());
    long now = System.currentTimeMillis();
    if (raised == null || now - raised > getWindowMillis(level)) {
      return;
    }

    if (getStorageLong(defender, "perfectGuardNext", 0L) > now) {
      return;
    }

    Entity damager = e.getDamager();
    LivingEntity attacker = null;
    Location sourceLocation;
    if (damager instanceof Projectile projectile) {
      sourceLocation = projectile.getLocation();
      if (projectile.getShooter() instanceof LivingEntity shooter) {
        attacker = shooter;
      }
    } else if (damager instanceof LivingEntity living) {
      attacker = living;
      sourceLocation = living.getLocation();
    } else {
      sourceLocation = damager.getLocation();
    }

    Location eye = defender.getEyeLocation();
    Vector toSource = sourceLocation.toVector().subtract(eye.toVector());
    if (toSource.lengthSquared() > 1.0E-6) {
      toSource.normalize();
      if (eye.getDirection().dot(toSource) < getConfig().minFacingAlignment) {
        return;
      }
    }

    e.setCancelled(true);
    raiseAt.remove(defender.getUniqueId());
    setStorage(defender, "perfectGuardNext", now + getConfig().cooldownMillis);
    xp(defender, getConfig().xpOnNegate);
    addStat(defender, "blocking.perfect-guard.hits-negated", 1);
    parryFlash(defender);

    if (attacker == null || !canDamageTarget(defender, attacker)) {
      return;
    }

    LivingEntity staggered = attacker;
    Location origin = defender.getLocation().clone();
    int staggerTicks = getStaggerTicks(level);
    int staggerAmplifier = getStaggerAmplifier(level);
    double knockback = getConfig().staggerKnockback;
    J.runEntity(staggered, () -> applyStagger(staggered, origin, staggerTicks, staggerAmplifier, knockback));
  }

  private void applyStagger(LivingEntity attacker, Location origin, int staggerTicks, int staggerAmplifier, double knockback) {
    if (!attacker.isValid()) {
      return;
    }

    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, staggerTicks, staggerAmplifier, false, false, true), true);
    attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, staggerTicks, 0, false, false, true), true);

    Vector push = attacker.getLocation().toVector().subtract(origin.toVector()).setY(0);
    if (push.lengthSquared() < 1.0E-6) {
      push = origin.getDirection().clone().setY(0);
    }
    if (push.lengthSquared() < 1.0E-6) {
      push = new Vector(1, 0, 0);
    }
    push.normalize();
    attacker.setVelocity(attacker.getVelocity().multiply(0.3D).add(push.multiply(knockback).setY(0.2D)));

    fx(attacker.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 6, 0.3D)
        .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.7F, Sound.BLOCK_ANVIL_LAND, 0.35F, 1.5F);
  }

  private void parryFlash(Player defender) {
    Vector look = defender.getEyeLocation().getDirection();
    Location shield = defender.getEyeLocation().add(look.getX() * 0.6D, look.getY() * 0.6D, look.getZ() * 0.6D);
    fx(shield, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 8, 0.25D)
        .ring(Particles.CRIT_MAGIC, 0.6D, 8, 0)
        .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.6F, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 1.4F, Sound.ITEM_TRIDENT_RETURN, 0.4F, 1.8F);
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private long getWindowMillis(int level) {
    return Math.round(getConfig().windowMillisBase + (getLevelPercent(level) * getConfig().windowMillisFactor));
  }

  private int getStaggerTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().staggerTicksBase + (getLevelPercent(level) * getConfig().staggerTicksFactor)));
  }

  private int getStaggerAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().staggerAmplifierBase + (getLevelPercent(level) * getConfig().staggerAmplifierFactor)));
  }


  @ConfigDescription("Raise your shield in the instant before a hit lands to negate it and stagger the attacker.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Base for the Blocking Perfect Guard adaptation.", impact = "Higher values widen the perfect-guard timing window at every level.")
    double windowMillisBase = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values widen the perfect-guard timing window as you level up.")
    double windowMillisFactor = 240;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stagger Ticks Base for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerTicksBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stagger Ticks Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerTicksFactor = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stagger Amplifier Base for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerAmplifierBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stagger Amplifier Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stagger Knockback for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerKnockback = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Facing Alignment for the Blocking Perfect Guard adaptation.", impact = "Higher values require the attacker to be more directly in front to be parried.")
    double minFacingAlignment = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis for the Blocking Perfect Guard adaptation.", impact = "Higher values space out how often a perfect guard can fire and bound abuse.")
    long cooldownMillis = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Negate for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnNegate = 14;

    public Config() {
      costFactor = 0.78;
      initialCost = 5;
    }
  }
}
