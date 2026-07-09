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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.UUID;

public class TragoulGlobe extends SimpleAdaptation<TragoulGlobe.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public TragoulGlobe() {
    super("tragoul-globe");
    registerConfiguration(TragoulGlobe.Config.class);
    setIcon(Material.CRYING_OBSIDIAN);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_tragoul_globe_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_globe_1k", "tragoul.globe.mobs-shared-with", 1000, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_tragoul_globe_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.globe.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore2") + Form.f((getConfig().rangePerLevel * level) + getConfig().initalRange, 1));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore3") + Form.f(getConfig().bonusDamagePerLevel * level, 1));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !canDamageTarget(p, e.getEntity())) {
        return;
      }

      UUID id = p.getUniqueId();
      if (!cooldowns.isReady(id, (long) (1000 * getConfig().cooldown))) {
        return;
      }

      cooldowns.mark(id);
      double range = (getConfig().rangePerLevel * level) + getConfig().initalRange;

      List<Entity> nearby = p.getNearbyEntities(range, range, range);
      int entitiesCount = 0;
      for (Entity entity : nearby) {
        if (entity instanceof LivingEntity && !entity.equals(p)) {
          entitiesCount++;
        }
      }

      if (entitiesCount <= 1) {
        return;
      }

      double damagePerEntity = e.getDamage() / entitiesCount + (getConfig().bonusDamagePerLevel * level);
      e.setDamage(damagePerEntity);

      Location chest = p.getLocation().add(0, 1.0, 0);
      FxEmitter tether = fx(chest, FxPriority.COMBAT);
      int mobsSharedWith = 0;
      for (Entity entity : nearby) {
        if (entity instanceof LivingEntity le && !entity.equals(p) && canDamageTarget(p, entity)) {
          Location tloc = le.getLocation();
          tether.line(Particle.SOUL, tloc.getX(), tloc.getY() + 1.0, tloc.getZ(), 3);
          le.damage(damagePerEntity, p);
          mobsSharedWith++;
        }
      }

      addStat(p, "tragoul.globe.mobs-shared-with", mobsSharedWith);
      playShareShockwave(p.getLocation(), range, mobsSharedWith);
      if (mobsSharedWith >= 5) {
        fx(chest, FxPriority.TRANSITION)
            .burst(Particle.SCULK_SOUL, 12, 0.5D)
            .sound(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.6F, 1.2F);
        grantOnce(p, "challenge_tragoul_globe_5");
      }
    });
  }

  private void playShareShockwave(Location center, double range, int mobsSharedWith) {
    if (mobsSharedWith <= 0) {
      return;
    }

    double inner = range * 0.3D;
    int ringPoints = Math.max(8, Math.min(24, mobsSharedWith * 4));
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(range + 8)
        .frame((fx, tick, progress) -> {
          double radius = inner + ((range - inner) * progress);
          Color ringColor = Color.fromRGB((int) (150 - (120 * progress)), (int) (12 - (7 * progress)), (int) (12 - (7 * progress)));
          fx.dustRing(ringColor, radius, ringPoints, 1.2F);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_WITHER_SHOOT, 0.4F, 1.6F, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 0.9F);
          }
        })
        .start();
  }


  @Override
  public void onTick() {
  }


  @ConfigDescription("Spread your damage among all nearby enemies.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangePerLevel = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Inital Range for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double initalRange = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamagePerLevel = 1;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
