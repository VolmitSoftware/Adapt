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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class BlockingBulwarkBash extends SimpleAdaptation<BlockingBulwarkBash.Config> {
  private final Cooldowns sprintTimestamps = cooldowns();

  public BlockingBulwarkBash() {
    super("blocking-bulwark-bash");
    registerConfiguration(Config.class);
    setIcon(Material.BELL);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bulwark_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_bulwark_500", "blocking.bulwark-bash.mobs-bashed", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bulwark_4")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level)), 1);
    statLore(v, Form.pc(getDamageBonus(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    if (e.isSprinting()) {
      sprintTimestamps.mark(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();
    if (p.getInventory().getItemInOffHand().getType() != Material.SHIELD || p.hasCooldown(Material.SHIELD)) {
      return;
    }

    if (!isJumpCrit(p) || !wasRecentlySprinting(p)) {
      return;
    }

    int level = combat.level();
    int affected = 0;
    double radius = getRange(level);
    for (org.bukkit.entity.Entity nearby : target.getWorld().getNearbyEntities(target.getLocation(), radius, radius, radius)) {
      if (!(nearby instanceof LivingEntity hit) || hit == p) {
        continue;
      }

      if (!canDamageTarget(p, hit)) {
        continue;
      }

      applyImpact(p, hit, level, affected < 6);
      affected++;
    }

    if (affected <= 0) {
      return;
    }

    e.setDamage(e.getDamage() + getBaseDamage(level));
    p.setCooldown(Material.SHIELD, getCooldownTicks(level));
    boolean hype = affected >= 4;
    bashShockwave(p, radius, hype);
    xp(p, getConfig().xpPerTargetHit * affected);
    addStat(p, "blocking.bulwark-bash.mobs-bashed", affected);

    if (hype) {
      grantOnce(p, "challenge_blocking_bulwark_4");
    }
  }

  private void bashShockwave(Player p, double radius, boolean hype) {
    FxEmitter burst = fx(p, FxPriority.GAMEPLAY)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 1.0D, 0, 0, 0)
        .particle(Particle.CLOUD, 18, 0, 0.3D, 0, 0.35D, 0.06D);
    if (hype) {
      burst.burst(Particle.FLASH, 1, 0)
          .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.85F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.7F, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F);
    } else {
      burst.chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.85F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.7F, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 1.4F);
    }

    double maxRadius = Math.max(0.5D, radius);
    timeline(p.getLocation())
        .duration(5)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(Math.max(16.0D, maxRadius * 3.0D))
        .frame((fxE, tick, progress) -> {
          fxE.ring(Particle.CLOUD, 0.4D + (maxRadius * progress), 8, 0.2D);
          if (tick == 0) {
            fxE.sound(Sound.BLOCK_ANVIL_PLACE, 0.4F, 0.8F);
          }
        })
        .start();
  }

  private void applyImpact(Player p, LivingEntity target, int level, boolean visual) {
    Vector kb = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
    if (kb.lengthSquared() <= 0.0001) {
      kb = p.getLocation().getDirection().setY(0);
    }
    if (kb.lengthSquared() <= 0.0001) {
      return;
    }

    kb.normalize();
    target.setVelocity(target.getVelocity().multiply(0.25).add(kb.multiply(getKnockback(level)).setY(getUpwardKnockback(level))));
    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getStunTicks(level), getStunAmplifier(level), false, false, true), true);

    if (visual) {
      fx(target.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, 3, 0.2D);
    }
  }

  private boolean wasRecentlySprinting(Player p) {
    return p.isSprinting() || !sprintTimestamps.isReady(p.getUniqueId(), getConfig().recentSprintWindowMillis);
  }

  private boolean isJumpCrit(Player p) {
    return p.getFallDistance() > getConfig().minFallDistanceForCrit
        && !p.isOnGround()
        && !p.isInWater()
        && !p.isInsideVehicle()
        && !p.isClimbing();
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBonusBase + (getLevelPercent(level) * getConfig().damageBonusFactor);
  }

  private double getBaseDamage(int level) {
    return getConfig().baseDamage + getDamageBonus(level);
  }

  private double getKnockback(int level) {
    return getConfig().knockbackBase + (getLevelPercent(level) * getConfig().knockbackFactor);
  }

  private double getUpwardKnockback(int level) {
    return getConfig().upwardKnockbackBase + (getLevelPercent(level) * getConfig().upwardKnockbackFactor);
  }

  private int getStunTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().stunTicksBase + (getLevelPercent(level) * getConfig().stunTicksFactor)));
  }

  private int getStunAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().stunAmplifierBase + (getLevelPercent(level) * getConfig().stunAmplifierFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  @Override
  public void onTick() {
  }

  @ConfigDescription("Sprint, jump, and land a shielded crit to unleash a bash shockwave.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusFactor = 2.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 1.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double upwardKnockbackBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double upwardKnockbackFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunTicksBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunTicksFactor = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunAmplifierBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 220;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    float minFallDistanceForCrit = 0.08f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recent Sprint Window Millis for the Blocking Bulwark Bash adaptation.", impact = "Allows crit bash to trigger shortly after sprint momentum, even if sprint toggles off mid-air.")
    long recentSprintWindowMillis = 900L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 8;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
