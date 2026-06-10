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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class UnarmedShockwaveClap extends SimpleAdaptation<UnarmedShockwaveClap.Config> {
  private final Map<UUID, Long> nextClapAt = new java.util.concurrent.ConcurrentHashMap<>();

  public UnarmedShockwaveClap() {
    super("unarmed-shockwave-clap");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.shockwave_clap.description"));
    setDisplayName(Localizer.dLocalize("unarmed.shockwave_clap.name"));
    setIcon(Material.NOTE_BLOCK);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(5230);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_clap_250")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_clap_250.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_clap_250.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_clap_2500")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_clap_2500.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_clap_2500.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_clap_250", "unarmed.shockwave-clap.mobs-clapped", 250, 400);
    registerMilestone("challenge_unarmed_clap_2500", "unarmed.shockwave-clap.mobs-clapped", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRange(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.shockwave_clap.lore1"));
    v.addLore(C.GREEN + "+ " + Form.f(getForce(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.shockwave_clap.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.shockwave_clap.lore3"));
    v.addLore(C.RED + "- " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("unarmed.shockwave_clap.lore4"));
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

    long now = System.currentTimeMillis();
    Long next = nextClapAt.get(p.getUniqueId());
    if (next != null && now < next) {
      return;
    }

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    nextClapAt.put(p.getUniqueId(), now + getCooldownMillis(level));
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
      affected++;
    }

    SoundPlayer sp = SoundPlayer.of(p.getWorld());
    sp.play(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
    sp.play(origin, Sound.BLOCK_BELL_RESONATE, 0.7f, 1.4f);
    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.EXPLOSION, origin.clone().add(look.clone().multiply(1.2)).add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0.02);
      p.getWorld().spawnParticle(Particle.CLOUD, origin.clone().add(look.clone().multiply(1.5)).add(0, 0.8, 0), 20, 0.6, 0.4, 0.6, 0.08);
    }

    getPlayer(p).getData().addStat("unarmed.shockwave-clap.claps", 1);
    if (affected > 0) {
      xp(p, getConfig().xpPerTargetHit * affected);
      getPlayer(p).getData().addStat("unarmed.shockwave-clap.mobs-clapped", affected);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    nextClapAt.remove(e.getPlayer().getUniqueId());
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak and punch the air to clap a shockwave that knocks back enemies in a cone.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.7;
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
  }
}
