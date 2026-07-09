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

package art.arcane.adapt.content.adaptation.axe;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;

import java.util.List;

public class AxeDropToInventory extends SimpleAdaptation<AxeDropToInventory.Config> {
  public AxeDropToInventory() {
    super("axe-drop-to-inventory");
    registerConfiguration(AxeDropToInventory.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.drop_to_inventory.description"));
    setDisplayName(Localizer.dLocalize("axe.drop_to_inventory.name"));
    setIcon(Material.BARREL);
    setInterval(8800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_axe_dti_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_dti_5k", "axe.drop-to-inv.items-caught", 5000, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("pickaxe.drop_to_inventory.lore1"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {

    Player p = e.getPlayer();
    if (resolveBlockBreakContext(p, e.getBlock().getLocation(), null, true) == null) {
      return;
    }

    if (!ItemListings.toolAxes.contains(p.getInventory().getItemInMainHand().getType())) {
      return;
    }

    List<Item> items = new KList<>(e.getItems());
    e.getItems().clear();
    int caught = 0;
    boolean overflow = false;
    for (Item i : items) {
      if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
        p.getWorld().dropItem(p.getLocation(), i.getItemStack());
        overflow = true;
      }
      caught++;
    }
    if (caught <= 0) {
      return;
    }

    addStat(p, "axe.drop-to-inv.items-caught", caught);
    int caughtCount = caught;
    Location from = e.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
    Location to = p.getLocation().add(0.0D, 1.0D, 0.0D);
    double dx = to.getX() - from.getX();
    double dy = to.getY() - from.getY();
    double dz = to.getZ() - from.getZ();
    timeline(from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.ENCHANTMENT_TABLE, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0.0D);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_ITEM_PICKUP, 0.5F, (float) Math.min(2.0D, 1.2D + (caughtCount * 0.03D)), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.3F, 1.8F);
          }
        })
        .start();
    if (overflow) {
      fx(to, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2D)
          .sound(Sound.BLOCK_DISPENSER_FAIL, 0.4F, 0.8F);
    }
  }


  @Override
  public void onTick() {
  }

  @ConfigDescription("Chopped wood drops directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
