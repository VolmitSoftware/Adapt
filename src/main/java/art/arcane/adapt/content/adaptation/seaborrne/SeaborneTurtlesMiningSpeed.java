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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;

public class SeaborneTurtlesMiningSpeed extends SimpleAdaptation<SeaborneTurtlesMiningSpeed.Config> {
  private final Map<UUID, Boolean> submerged = playerState();
  private final Cooldowns breakPulse = cooldowns();

  public SeaborneTurtlesMiningSpeed() {
    super("seaborne-turtles-mining-speed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.haste");
    setIcon(Material.PRISMARINE_SHARD);
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_seaborne_mining_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_seaborne_mining_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_mining_2500", "seaborne.turtles-mining.blocks-underwater", 2500, 300);
    registerMilestone("challenge_seaborne_mining_25k", "seaborne.turtles-mining.blocks-underwater", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("seaborn.haste.lore1"));
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    Location at = e.getBlock().getLocation();
    withAdaptedPlayer(p, e, () -> {
      if (!p.isInWater() || !breakPulse.isReady(p.getUniqueId(), 150L)) {
        return;
      }

      breakPulse.mark(p.getUniqueId());
      fx(at.add(0.5D, 0.5D, 0.5D), FxPriority.AMBIENT)
          .particle(Particle.BUBBLE, 4, 0, 0, 0, 0.05D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.2F, 1.7F);
    });
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
          }
          return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, 62, 1, false, false));
        addStat(player, "seaborne.turtles-mining.blocks-underwater", 1);

        if (!was) {
          submerged.put(id, true);
          fx(player.getLocation().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
              .ring(Particle.CRIT, 0.5D, 6, 0.0D)
              .particle(Particle.BUBBLE, 6, 0D, 0.2D, 0D, 0.35D, 0.02D)
              .dustBurst(3, 0.3D, 0.9F)
              .chord(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.4F, 1.5F, Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 0.25F, 1.0F);
        }
      });
    }
  }

  @ConfigDescription("Gain haste while mining underwater.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 15;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
