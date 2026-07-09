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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class SeaborneTurtlesVision extends SimpleAdaptation<SeaborneTurtlesVision.Config> {
  private final Map<UUID, Boolean> submerged = playerState();

  public SeaborneTurtlesVision() {
    super("seaborne-turtles-vision");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.night_vision");
    setIcon(Material.DIAMOND_HORSE_ARMOR);
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TURTLE_HELMET)
        .key("challenge_seaborne_vision_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_seaborne_vision_72k", "seaborne.turtles-vision.time-underwater", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("seaborn.night_vision.lore1"));
  }


  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        UUID id = player.getUniqueId();
        boolean was = submerged.getOrDefault(id, false);
        if (!player.isInWater()) {
          if (was) {
            submerged.put(id, false);
            fx(player.getEyeLocation(), FxPriority.AMBIENT)
                .particle(Particle.GLOW, 3, 0D, 0D, 0D, 0.2D, 0.02D)
                .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.25F, 1.0F);
          }
          return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 62, 0, false, false));
        addStat(player, "seaborne.turtles-vision.time-underwater", 1);

        if (!was) {
          submerged.put(id, true);
          timeline(player)
              .duration(8)
              .priority(FxPriority.TRANSITION)
              .cullRadius(16)
              .frame((f, tick, progress) -> {
                f.dome(Particle.GLOW, 0.3D + (0.7D * progress), 10);
                if (tick == 0) {
                  f.chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.35F, 1.2F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.5F);
                } else if ((tick & 1) == 0) {
                  f.particle(Particle.END_ROD, 2, 0D, 0.6D, 0D, 0.15D, 0.01D);
                }
              })
              .start();
        }
      });
    }
  }

  @ConfigDescription("Gain night vision while underwater.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
