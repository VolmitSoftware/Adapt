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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class UnarmedShockwaveClap extends SimpleAdaptation<UnarmedShockwaveClap.Config> {
  private static final Color WAVE_COLOR = Color.WHITE;
  private final Map<UUID, Long> nextClapAt = playerState();
  private final Cooldowns failCue = cooldowns();

  public UnarmedShockwaveClap() {
    super("unarmed-shockwave-clap");
    registerConfiguration(Config.class);
    setIcon(Material.NOTE_BLOCK);
    setInterval(5230);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_clap_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_clap_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_clap_250", "unarmed.shockwave-clap.mobs-clapped", 250, 400);
    registerMilestone("challenge_unarmed_clap_2500", "unarmed.shockwave-clap.mobs-clapped", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level)), 1);
    statLore(v, Form.f(getForce(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 3);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    Long next = nextClapAt.get(id);
    if (next != null && now < next) {
      playBlocked(p);
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      playBlocked(p);
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    nextClapAt.put(id, now + getCooldownMillis(level));
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));

    double range = getRange(level);
    double force = getForce(level);
    Location origin = p.getLocation();
    Vector look = origin.getDirection().normalize();
    int affected = 0;
    for (Entity nearby : p.getWorld().getNearbyEntities(origin, range, range, range)) {
      if (!(nearby instanceof LivingEntity hit) || hit == p) {
        continue;
      }

      Vector to = hit.getLocation().toVector().subtract(origin.toVector());
      double lengthSquared = to.lengthSquared();
      if (lengthSquared <= 0.0001 || lengthSquared > range * range) {
        continue;
      }

      to.multiply(1 / Math.sqrt(lengthSquared));
      if (look.dot(to) < getConfig().coneDotThreshold) {
        continue;
      }

      if (!canDamageTarget(p, hit)) {
        continue;
      }

      hit.setVelocity(hit.getVelocity().multiply(0.2).add(to.multiply(force).setY(getUpwardForce(level))));
      if (affected < 8) {
        fx(hit.getLocation().add(0, 1, 0), FxPriority.COMBAT)
            .particle(Particle.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
      }
      affected++;
    }

    playShockwave(origin, look, range);
    addStat(p, "unarmed.shockwave-clap.claps", 1);
    if (affected > 0) {
      xp(p, getConfig().xpPerTargetHit * affected);
      addStat(p, "unarmed.shockwave-clap.mobs-clapped", affected);
    }
  }

  private void playShockwave(Location origin, Vector look, double range) {
    fx(origin, FxPriority.COMBAT)
        .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F, Sound.BLOCK_BELL_RESONATE, 0.7F, 1.4F, Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 0.7F);
    Location center = origin.clone().add(look.clone().multiply(0.6)).add(0, 1.0D, 0);
    fx(center, FxPriority.COMBAT)
        .particle(Particle.EXPLOSION, 1, 0, 0, 0, 0.1D, 0.02D)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0);
    int ringPoints = Math.min(28, 12 + (int) (range * 2));
    timeline(center)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (range * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.dustRing(WAVE_COLOR, 0.4D + (range * progress), ringPoints, 1.0F))
        .start();
  }

  private void playBlocked(Player p) {
    if (!failCue.isReady(p.getUniqueId(), 700L)) {
      return;
    }

    failCue.mark(p.getUniqueId());
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.25F, 0.5F);
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getUpwardForce(int level) {
    return getConfig().upwardForceBase + (getLevelPercent(level) * getConfig().upwardForceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Sneak and punch the air to clap a shockwave that knocks back enemies in a cone.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base shockwave range in blocks at level 1.", impact = "Higher values let the clap reach more distant enemies.")
    double rangeBase = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional shockwave range granted at max level.", impact = "Higher values extend the clap reach as levels increase.")
    double rangeFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knockback force at level 1.", impact = "Higher values launch enemies further.")
    double forceBase = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional knockback force granted at max level.", impact = "Higher values launch enemies further as levels increase.")
    double forceFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base upward knockback component at level 1.", impact = "Higher values pop enemies into the air more.")
    double upwardForceBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional upward knockback granted at max level.", impact = "Higher values pop enemies higher as levels increase.")
    double upwardForceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-direction dot threshold that defines the cone width.", impact = "Lower values widen the cone; higher values narrow it.")
    double coneDotThreshold = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base clap cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per shockwave clap.", impact = "Higher values drain more food per clap; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per enemy knocked back by a clap.", impact = "Higher values speed up unarmed skill progression from claps.")
    double xpPerTargetHit = 14;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      initialCost = 6;
    }
  }
}
