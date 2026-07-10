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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class HerbalismCraftableCobweb extends SimpleAdaptation<HerbalismCraftableCobweb.Config> {

  public HerbalismCraftableCobweb() {
    super("herbalism-cobweb");
    registerConfiguration(Config.class);
    setIcon(Material.STRING);
    setInterval(17771);
    registerRecipe(AdaptRecipe.shaped()
        .key("herbalism-cobwebBlock")
        .ingredient(new MaterialChar('I', Material.STRING))
        .shapes(List.of(
            "III",
            "III",
            "III"))
        .result(new ItemStack(Material.COBWEB, 1))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COBWEB)
        .key("challenge_herbalism_cobweb_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_cobweb_100", "herbalism.cobweb.cobwebs-crafted", 100, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("herbalism.cobweb.lore1"));
  }


  @EventHandler(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !hasActiveAdaptation(p)) {
      return;
    }
    if (e.getRecipe() instanceof org.bukkit.inventory.ShapedRecipe recipe && recipe.getKey().getNamespace().equals("adapt") && recipe.getKey().getKey().equals("herbalism-cobwebBlock")) {
      addStat(p, "herbalism.cobweb.cobwebs-crafted", 1);
      if (getPlayer(p).getData().getStat("herbalism.cobweb.cobwebs-crafted") == 1) {
        fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.6D, 0, 0.4D, 0.05D)
            .chord(Sound.BLOCK_WOOL_BREAK, 0.6F, 1.2F, Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Cobwebs from String in a Crafting Table.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
