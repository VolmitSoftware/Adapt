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
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class SeaborneFishersFantasy extends SimpleAdaptation<SeaborneFishersFantasy.Config> {

  public SeaborneFishersFantasy() {
    super("seaborne-fishers-fantasy");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.fishers_fantasy");
    setIcon(Material.FISHING_ROD);
    setInterval(8080);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FISHING_ROD)
        .key("challenge_seaborne_fish_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TROPICAL_FISH)
            .key("challenge_seaborne_fish_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_fish_500", "seaborne.fishers-fantasy.fish-caught", 500, 300);
    registerMilestone("challenge_seaborne_fish_5k", "seaborne.fishers-fantasy.fish-caught", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("seaborn.fishers_fantasy.lore1"));
  }

  @EventHandler
  public void on(PlayerFishEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
        addStat(p, "seaborne.fishers-fantasy.fish-caught", 1);
        int level = getActiveLevel(p);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int successes = 0;
        for (int i = 0; i < level; i++) {
          if (!random.nextBoolean()) {
            continue;
          }

          ItemStack item = new ItemStack(ItemListings.getFishingDrops().getRandom(), 1);
          p.getWorld().dropItemNaturally(p.getLocation(), item);
          p.getWorld().spawn(p.getLocation(), ExperienceOrb.class);
          xp(p, 15 * level);

          float pitch = (float) Math.min(2.0D, 1.2D + (0.1D * successes));
          float chimePitch = (float) Math.min(2.0D, 1.5D + (0.08D * successes));
          fx(p.getLocation().add(0D, 0.5D, 0D), FxPriority.TRANSITION)
              .particle(Particle.SPLASH, 6, 0D, 0.3D, 0D, 0.25D, 0.05D)
              .particle(Particle.GLOW, 4, 0D, 0.4D, 0D, 0.3D, 0D)
              .dustBurst(3, 0.3D, 0.9F)
              .chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.4F, pitch, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, chimePitch);
          successes++;
        }
      }
    });
  }


  @ConfigDescription("Earn more XP from fishing and catch more fish.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      maxLevel = 7;
    }
  }
}
