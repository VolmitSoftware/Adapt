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
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.List;

public class HerbalismDropToInventory extends SimpleAdaptation<HerbalismDropToInventory.Config> {
  public HerbalismDropToInventory() {
    super("herbalism-drop-to-inventory");
    registerConfiguration(HerbalismDropToInventory.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.drop_to_inventory.description"));
    setDisplayName(Localizer.dLocalize("herbalism.drop_to_inventory.name"));
    setIcon(Material.HOPPER);
    setInterval(7999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_herbalism_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_dti_10k", "herbalism.drop-to-inv.items-caught", 10000, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("pickaxe.drop_to_inventory.lore1"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p) || p.getGameMode() != GameMode.SURVIVAL) {
      return;
    }

    if (!ItemListings.toolHoes.contains(p.getInventory().getItemInMainHand().getType())) {
      return;
    }

    List<Item> items = new KList<>(e.getItems());
    if (items.isEmpty()) {
      return;
    }

    e.getItems().clear();
    int stored = 0;
    boolean overflow = false;
    for (Item i : items) {
      xp(p, 2);
      addStat(p, "herbalism.drop-to-inv.items-caught", 1);
      if (p.getInventory().addItem(i.getItemStack()).isEmpty()) {
        stored++;
      } else {
        overflow = true;
        p.getWorld().dropItem(p.getLocation(), i.getItemStack());
      }
    }

    if (stored > 0) {
      Location source = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
      Location chest = p.getEyeLocation().subtract(0, 0.4, 0);
      fx(source, FxPriority.TRANSITION)
          .trail(Particles.CRIT_MAGIC, chest.getX() - source.getX(), chest.getY() - source.getY(), chest.getZ() - source.getZ(), Math.min(6.0D, source.distance(chest)), 4)
          .particle(Particles.VILLAGER_HAPPY, 2, 0, 0.1D, 0, 0.15D, 0.02D)
          .chord(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sound.BLOCK_CALCITE_HIT, 0.3F, 1.2F);
    }

    if (overflow) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.25D)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.8F);
    }
  }


  @Override
  public void onTick() {
  }

  @ConfigDescription("Harvested crops drop directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
