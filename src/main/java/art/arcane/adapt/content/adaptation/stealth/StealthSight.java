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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StealthSight extends SimpleAdaptation<StealthSight.Config> {
  private final Set<UUID> sneaking;
  private final Set<UUID> appliedNightVision;


  public StealthSight() {
    super("stealth-vision");
    registerConfiguration(Config.class);
    setLocalizationKey("stealth.night_vision");
    setIcon(Material.POTION);
    setInterval(1500);
    sneaking = ConcurrentHashMap.newKeySet();
    appliedNightVision = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_stealth_sight_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_sight_72k", "stealth.sight.time-in-darkness", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("stealth.night_vision.lore1") + C.GREEN + Localizer.dLocalize("stealth.night_vision.lore2") + C.GRAY + Localizer.dLocalize("stealth.night_vision.lore3"));
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      if (!hasActiveAdaptation(p)) {
        sneaking.remove(id);
        if (clearNightVisionIfApplied(p, id)) {
          closeSightFx(p);
        }
        return;
      }

      if (e.isSneaking()) {
        sneaking.add(id);
        applyNightVisionIfNeeded(p, id);
        addStat(p, "stealth.sight.time-in-darkness", 1);
        openSightFx(p);
        return;
      }

      sneaking.remove(id);
      if (clearNightVisionIfApplied(p, id)) {
        closeSightFx(p);
      }
    });
  }

  private void openSightFx(Player p) {
    timeline(p).duration(5).priority(FxPriority.TRANSITION).cullRadius(12)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.5D - (0.35D * progress), 6, 1.5D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.3F, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.25F, 1.6F);
          }
        }).start();
  }

  private void closeSightFx(Player p) {
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3F, 0.7F);
  }


  @Override
  public void onTick() {
    Set<UUID> snapshot = new HashSet<>(sneaking);
    for (UUID id : snapshot) {
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline()) {
        sneaking.remove(id);
        appliedNightVision.remove(id);
        continue;
      }

      Runnable check = () -> {
        if (getActiveLevel(p, Player::isSneaking) <= 0) {
          sneaking.remove(id);
          J.runEntity(p, () -> clearNightVisionIfApplied(p, id));
        }
      };

      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) {
        J.runEntity(p, check);
      } else {
        check.run();
      }
    }
  }


  private void applyNightVisionIfNeeded(Player player, UUID id) {
    if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
      appliedNightVision.remove(id);
      return;
    }

    PotionEffect effect = new PotionEffect(PotionEffectType.NIGHT_VISION, 1000, 0, false, false);
    boolean applied = player.addPotionEffect(effect);
    if (applied) {
      appliedNightVision.add(id);
    } else {
      appliedNightVision.remove(id);
    }
  }

  private boolean clearNightVisionIfApplied(Player player, UUID id) {
    if (!appliedNightVision.remove(id)) {
      return false;
    }

    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    return true;
  }

  @ConfigDescription("Gain night vision while sneaking.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.6;
      maxLevel = 1;
      initialCost = 5;
    }
  }
}
