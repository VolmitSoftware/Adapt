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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class TragoulLastRites extends SimpleAdaptation<TragoulLastRites.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public TragoulLastRites() {
    super("tragoul-last-rites");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_tragoul_last_rites_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_TORCH)
            .key("challenge_tragoul_last_rites_50")
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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (e.getFinalDamage() < p.getHealth()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || p.isDead()) {
        return;
      }

      UUID id = p.getUniqueId();
      if (!cooldowns.isReady(id, getCooldownMillis(level))) {
        return;
      }

      cooldowns.mark(id);
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

      playSpiritAscension(p);
      fx(p.getLocation().add(0, 0.2, 0), FxPriority.TRANSITION)
          .dustRing(radius, 24, 1.0F)
          .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.25F, 1.8F);
      J.runEntity(p, () -> fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
          .particle(Particle.SOUL, 6, 0, 0.5, 0, 0.2, 0.03)
          .particle(Particle.CRIT, 3, 0, 0.8, 0, 0.3, 0.05)
          .sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.4F, 1.2F), spiritTicks);
      addStat(p, "tragoul.last-rites.deaths-defied", 1);
      xp(p, getConfig().xpPerSave);
    });
  }

  private void playSpiritAscension(Player p) {
    timeline(p)
        .duration(30)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(40)
        .frame((fx, tick, progress) -> {
          if (tick < 6) {
            double spread = 0.3 + (tick * 0.12);
            fx.particle(Particle.SCULK_SOUL, 5, 0, 1.0, 0, spread, 0.05);
            fx.particle(Particle.SOUL, 3, 0, 0.6, 0, spread * 0.8, 0.03);
          } else if (tick < 20) {
            fx.helix(Particle.SOUL_FIRE_FLAME, 0.8, 2.4, 4, progress * Math.PI * 4.0);
          } else {
            fx.particle(Particle.SCULK_SOUL, 3, 0, 1.2, 0, 0.3, 0.02);
          }
          if (tick == 0) {
            fx.chord(Sound.ITEM_TOTEM_USE, 0.8F, 1.5F, Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 0.6F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.5F);
          } else if (tick == 20) {
            fx.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
          }
        })
        .start();
  }

  private long getCooldownMillis(int level) {
    return Math.max(30000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("A killing blow leaves you at 1 HP as a fleeting spirit instead of dying.")
  protected static class Config extends AdaptationConfig {
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

    public Config() {
      baseCost = 6;
      costFactor = 0.85;
      initialCost = 6;
    }
  }
}
