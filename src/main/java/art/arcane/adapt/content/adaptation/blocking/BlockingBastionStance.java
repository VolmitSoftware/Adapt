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
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class BlockingBastionStance extends SimpleAdaptation<BlockingBastionStance.Config> {
  public BlockingBastionStance() {
    super("blocking-bastion-stance");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bastion_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_bastion_500", "blocking.bastion-stance.projectiles-softened", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bastion_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getKnockbackReduction(level), 0), 1);
    statLore(v, Form.pc(getProjectileReduction(level), 0), 2);
    statLore(v, Form.pc(getProjectileNegateChance(level), 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player defender) || !isBastionStance(defender)) {
      return;
    }

    if (!(e.getDamager() instanceof Projectile)) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0) {
      return;
    }

    int sessionCount = getStorageInt(defender, "bastionSessionCount", 0) + 1;
    setStorage(defender, "bastionSessionCount", sessionCount);
    if (sessionCount >= 10) {
      grantOnce(defender, "challenge_blocking_bastion_10");
    }

    if (ThreadLocalRandom.current().nextDouble() <= getProjectileNegateChance(level)) {
      e.setCancelled(true);
      hardParry(defender);
      xp(defender, getConfig().xpOnNegate);
      addStat(defender, "blocking.bastion-stance.projectiles-softened", 1);
      return;
    }

    e.setDamage(Math.max(0, e.getDamage() * (1D - getProjectileReduction(level))));
    softenPuff(defender);
    xp(defender, e.getDamage() * getConfig().xpPerMitigatedDamage);
    addStat(defender, "blocking.bastion-stance.projectiles-softened", 1);
  }

  private void hardParry(Player defender) {
    Location chest = defender.getLocation().add(0, 1.0D, 0);
    fx(chest, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 2, 0.05D)
        .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.9F, Sound.BLOCK_ANVIL_LAND, 0.3F, 1.6F);
    timeline(chest)
        .duration(3)
        .priority(FxPriority.COMBAT)
        .cullRadius(24.0D)
        .frame((fxE, tick, progress) -> fxE.ring(Particles.CRIT_MAGIC, 1.0D - (0.7D * progress), 4, 0))
        .start();
  }

  private void softenPuff(Player defender) {
    Vector look = defender.getEyeLocation().getDirection();
    Location shield = defender.getEyeLocation().add(look.getX() * 0.5D, look.getY() * 0.5D, look.getZ() * 0.5D);
    fx(shield, FxPriority.COMBAT)
        .particle(Particle.CLOUD, 5, 0, 0, 0, 0.1D, 0.02D)
        .sound(Sound.ITEM_SHIELD_BLOCK, 0.75F, 0.75F);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerVelocityEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (!isBastionStance(p, level)) {
      return;
    }

    double reduction = getKnockbackReduction(level);
    Vector v = e.getVelocity();
    double horizontal = Math.sqrt((v.getX() * v.getX()) + (v.getZ() * v.getZ()));
    e.setVelocity(new Vector(v.getX() * (1D - reduction), v.getY(), v.getZ() * (1D - reduction)));

    if (reduction > 0 && horizontal > 0.4D) {
      fx(p.getLocation(), FxPriority.COMBAT)
          .ring(Particles.BLOCK_CRACK, 0.6D, 4, 0.1D, Material.IRON_BLOCK.createBlockData())
          .sound(Sound.ENTITY_IRON_GOLEM_STEP, 0.4F, 0.9F);
    }
  }

  private boolean isBastionStance(Player p) {
    return isBastionStance(p, getActiveLevel(p));
  }

  private boolean isBastionStance(Player p, int level) {
    return level > 0 && p.isBlocking() && p.isSneaking() && hasShield(p);
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
  }

  private double getKnockbackReduction(int level) {
    return Math.min(getConfig().maxKnockbackReduction, getConfig().knockbackReductionBase + (getLevelPercent(level) * getConfig().knockbackReductionFactor));
  }

  private double getProjectileReduction(int level) {
    return Math.min(getConfig().maxProjectileReduction, getConfig().projectileReductionBase + (getLevelPercent(level) * getConfig().projectileReductionFactor));
  }

  private double getProjectileNegateChance(int level) {
    return Math.min(getConfig().maxProjectileNegateChance, getConfig().projectileNegateChanceBase + (getLevelPercent(level) * getConfig().projectileNegateChanceFactor));
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      int level = getActiveLevel(p);
      if (level > 0 && !isBastionStance(p, level)) {
        setStorage(p, "bastionSessionCount", 0);
      }
    }
  }

  @ConfigDescription("Sneak-block with a shield to brace against knockback and soften projectiles.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackReductionBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackReductionFactor = 0.52;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Knockback Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxKnockbackReduction = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileReductionBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileReductionFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProjectileReduction = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileNegateChanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileNegateChanceFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Negate Chance for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProjectileNegateChance = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mitigated Damage for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMitigatedDamage = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Negate for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnNegate = 8.0;

    public Config() {
      costFactor = 0.68;
      initialCost = 4;
    }
  }
}
