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

package art.arcane.adapt.content.adaptation.excavation;

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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.List;

public class ExcavationDropToInventory extends SimpleAdaptation<ExcavationDropToInventory.Config> {
  public ExcavationDropToInventory() {
    super("excavation-drop-to-inventory");
    registerConfiguration(ExcavationDropToInventory.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.drop_to_inventory.description"));
    setDisplayName(Localizer.dLocalize("excavation.drop_to_inventory.name"));
    setIcon(Material.CHEST);
    setInterval(11777);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_excavation_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_dti_10k", "excavation.drop-to-inv.items-caught", 10000, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("pickaxe.drop_to_inventory.lore1"));
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    Player p = e.getPlayer();
    if (resolveInteractBreakContext(p, e.getBlock().getLocation(), null, true) == null) {
      return;
    }
    if (!ItemListings.toolShovels.contains(p.getInventory().getItemInMainHand().getType())) {
      return;
    }

    List<Item> items = new KList<>(e.getItems());
    e.getItems().clear();
    int caught = 0;
    boolean overflow = false;
    for (Item i : items) {
      xp(p, 2);
      addStat(p, "excavation.drop-to-inv.items-caught", 1);
      if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
        p.getWorld().dropItem(p.getLocation(), i.getItemStack());
        overflow = true;
      } else {
        caught++;
      }
    }

    if (caught > 0) {
      fx(p.getLocation(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 4, 0, 1.0D, 0, 0.3D, 0.05D)
          .chord(Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.4f, Sound.BLOCK_CALCITE_HIT, 0.3f, 1.6f);
    }

    if (overflow) {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 1.0D, 0, 0.1D, 0.01D)
          .sound(Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.0f);
    }
  }



  @ConfigDescription("Excavated blocks drop directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
