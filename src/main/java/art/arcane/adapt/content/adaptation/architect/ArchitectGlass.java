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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class ArchitectGlass extends SimpleAdaptation<ArchitectGlass.Config> {
  public ArchitectGlass() {
    super("architect-glass");
    registerConfiguration(ArchitectGlass.Config.class);
    setIcon(Material.GLASS);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_architect_glass_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GLASS)
            .key("challenge_architect_glass_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_glass_200", "architect.glass.blocks-recovered", 200, 300);
    registerMilestone("challenge_architect_glass_5k", "architect.glass.blocks-recovered", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.GLASS_LORE1));
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (p.getInventory().getItemInMainHand().getType() == Material.AIR || !isTool(p.getInventory().getItemInMainHand())) {
        if (!canBlockBreak(p, e.getBlock().getLocation())) {
          return;
        }
        if (e.getBlock().getType().toString().contains("GLASS") && !e.getBlock().getType().toString().contains("TINTED_GLASS")) {
          e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(e.getBlock().getType(), 1));
          fx(e.getBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
              .dustRing(Color.fromRGB(180, 230, 255), 0.4D, 12, 0.8F)
              .burst(Particle.REVERSE_PORTAL, 6, 0.2D)
              .chord(Sound.BLOCK_LARGE_AMETHYST_BUD_BREAK, 0.7f, 1.0f, Sound.BLOCK_GLASS_BREAK, 0.4f, 1.8f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 2.0f);
          e.getBlock().breakNaturally();
          addStat(p, "architect.glass.blocks-recovered", 1);
        }
      }
    });
  }



  @ConfigDescription("Silk-touch glass blocks when breaking them with an empty hand.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 5;
      maxLevel = 1;
      initialCost = 0;
    }
  }
}
