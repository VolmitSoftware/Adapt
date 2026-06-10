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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulLastRites extends SimpleAdaptation<TragoulLastRites.Config> {
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public TragoulLastRites() {
    super("tragoul-last-rites");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("tragoul.last_rites.description"));
    setDisplayName(Localizer.dLocalize("tragoul.last_rites.name"));
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(25000);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_tragoul_last_rites_5")
        .title(Localizer.dLocalize("advancement.challenge_tragoul_last_rites_5.title"))
        .description(Localizer.dLocalize("advancement.challenge_tragoul_last_rites_5.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_TORCH)
            .key("challenge_tragoul_last_rites_50")
            .title(Localizer.dLocalize("advancement.challenge_tragoul_last_rites_50.title"))
            .description(Localizer.dLocalize("advancement.challenge_tragoul_last_rites_50.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_last_rites_5", "tragoul.last-rites.deaths-defied", 5, 500);
    registerMilestone("challenge_tragoul_last_rites_50", "tragoul.last-rites.deaths-defied", 50, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.last_rites.lore1"));
    v.addLore(C.GREEN + "+ " + Form.duration(getConfig().spiritDurationTicks * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.last_rites.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.last_rites.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (e.getFinalDamage() < p.getHealth()) {
      return;
    }

    long now = System.currentTimeMillis();
    Long until = cooldowns.get(p.getUniqueId());
    if (until != null && until > now) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || p.isDead()) {
        return;
      }

      cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
      e.setCancelled(true);
      p.setHealth(1.0);

      int spiritTicks = getConfig().spiritDurationTicks;
      p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, spiritTicks, 0, true, false, true), true);
      p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, spiritTicks, getConfig().resistanceAmplifier, true, false, true), true);

      double radius = getConfig().targetClearRadius;
      for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
        if (!(entity instanceof Mob mob) || mob.getTarget() != p) {
          continue;
        }

        J.runEntity(mob, () -> {
          if (mob.isValid() && mob.getTarget() == p) {
            mob.setTarget(null);
          }
        });
      }

      if (areParticlesEnabled()) {
        p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1.0, 0), 30, 0.4, 0.7, 0.4, 0.03);
        p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 0.6, 0), 18, 0.35, 0.5, 0.35, 0.02);
      }
      SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
      getPlayer(p).getData().addStat("tragoul.last-rites.deaths-defied", 1);
      xp(p, getConfig().xpPerSave);
    });
  }

  private long getCooldownMillis(int level) {
    return Math.max(30000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("A killing blow leaves you at 1 HP as a fleeting spirit instead of dying.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.85;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the spirit state after defying death.", impact = "Higher values keep the protection window open longer.")
    int spiritDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the resistance effect during the spirit state.", impact = "Higher values reduce more damage while in spirit form.")
    int resistanceAmplifier = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in which hostile mob targets locked onto you are cleared.", impact = "Higher values shake off pursuers from further away.")
    double targetClearRadius = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds before level scaling.", impact = "Higher values make death defiance rarer.")
    double cooldownMillisBase = 600000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels defy death more often.")
    double cooldownMillisFactor = 300000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per death defied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSave = 120;
  }
}
