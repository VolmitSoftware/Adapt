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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class SeaborneOxygen extends SimpleAdaptation<SeaborneOxygen.Config> {
  private final Map<UUID, Boolean> submerged = playerState();

  public SeaborneOxygen() {
    super("seaborne-oxygen");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.oxygen");
    setIcon(Material.GLASS_PANE);
    setInterval(3750);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TURTLE_HELMET)
        .key("challenge_seaborne_oxygen_12k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_seaborne_oxygen_12k", "seaborne.oxygen.bonus-air-ticks", 12000, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getAirBoost(level), 0), 1);
  }

  public double getAirBoost(int level) {
    return getLevelPercent(level);
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline()) {
        continue;
      }

      withPlayerThread(player, () -> {
        if (!player.isOnline() || player.getWorld() == null) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        UUID id = player.getUniqueId();
        boolean inWater = player.isInWater() || player.isSwimming();
        boolean was = submerged.getOrDefault(id, false);
        if (!inWater) {
          if (was) {
            submerged.put(id, false);
            fx(player.getEyeLocation(), FxPriority.AMBIENT)
                .particle(Particle.SPLASH, 6, 0D, 0D, 0D, 0.2D, 0.05D)
                .sound(Sound.BLOCK_CONDUIT_DEACTIVATE, 0.3F, 1.0F);
          }
          return;
        }

        int airTicks = level * getConfig().airPerLevelTics;
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, airTicks, level));
        addStat(player, "seaborne.oxygen.bonus-air-ticks", airTicks);

        if (!was) {
          submerged.put(id, true);
          fx(player.getEyeLocation(), FxPriority.TRANSITION)
              .ring(Particle.BUBBLE, 0.45D, 10, 0.0D)
              .dustBurst(4, 0.3D, 0.9F)
              .chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.35F, 1.15F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.4F);
          return;
        }

        fx(player.getEyeLocation(), FxPriority.AMBIENT)
            .column(Particle.BUBBLE, 2, 0.6D);
      });
    }
  }

  @ConfigDescription("Hold more oxygen underwater.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Air Per Level Tics for the Seaborne Oxygen adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int airPerLevelTics = 15;

    public Config() {
      baseCost = 3;
      costFactor = 0.525;
      initialCost = 5;
    }
  }
}
