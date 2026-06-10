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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;


public class CraftingStations extends SimpleAdaptation<CraftingStations.Config> {
  public CraftingStations() {
    super("crafting-stations");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("crafting.stations.description"));
    setDisplayName(Localizer.dLocalize("crafting.stations.name"));
    setIcon(Material.CRAFTING_TABLE);
    setBaseCost(getConfig().baseCost);
    setCostFactor(getConfig().costFactor);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setInterval(9248);
    AdvancementSpec stations5k = AdvancementSpec.challenge(
        "challenge_crafting_stations_5k",
        Material.SMITHING_TABLE,
        Localizer.dLocalize("advancement.challenge_crafting_stations_5k.title"),
        Localizer.dLocalize("advancement.challenge_crafting_stations_5k.description")
    );
    AdvancementSpec stations200 = AdvancementSpec.challenge(
        "challenge_crafting_stations_200",
        Material.CRAFTING_TABLE,
        Localizer.dLocalize("advancement.challenge_crafting_stations_200.title"),
        Localizer.dLocalize("advancement.challenge_crafting_stations_200.description")
    ).withChild(stations5k);
    registerAdvancementSpec(stations200);
    registerStatTracker(stations200.statTracker("crafting.stations.portable-opens", 200, 300));
    registerStatTracker(stations5k.statTracker("crafting.stations.portable-opens", 5000, 1000));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.RED + Localizer.dLocalize("crafting.stations.lore2"));
    v.addLore(C.GRAY + Localizer.dLocalize("crafting.stations.lore3"));
    if (getConfig().hungerCost > 0) {
      v.addLore(C.YELLOW + "* " + getConfig().hungerCost + C.GRAY + " " + Localizer.dLocalize("crafting.stations.lore4"));
    }
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();

    if (p.hasCooldown(hand.getType())) {
      e.setCancelled(true);
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    InventoryType station = switch (hand.getType()) {
      case CRAFTING_TABLE -> InventoryType.WORKBENCH;
      case GRINDSTONE -> InventoryType.GRINDSTONE;
      case ANVIL -> InventoryType.ANVIL;
      case STONECUTTER -> InventoryType.STONECUTTER;
      case CARTOGRAPHY_TABLE -> InventoryType.CARTOGRAPHY;
      case LOOM -> InventoryType.LOOM;
      default -> null;
    };

    if (station == null) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (hungerCost > 0 && p.getFoodLevel() < hungerCost) {
      return;
    }

    if (hungerCost > 0) {
      p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));
    }

    p.setCooldown(hand.getType(), 1000);
    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.10f);
    sp.play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.10f);
    if (station == InventoryType.WORKBENCH) {
      p.openWorkbench(null, true);
    } else {
      Inventory inv = Bukkit.createInventory(p, station);
      p.openInventory(inv);
    }
    getPlayer(p).getData().addStat("crafting.stations.portable-opens", 1);
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Use crafting tables, anvils, and other stations in the palm of your hand.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Crafting Stations adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed each time a portable station is opened.", impact = "Higher values make portable station access cost more food; 0 disables the cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 1;
  }
}
