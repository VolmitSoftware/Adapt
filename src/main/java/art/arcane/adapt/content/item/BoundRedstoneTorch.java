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

package art.arcane.adapt.content.item;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;

import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.item.DataItem;
import art.arcane.adapt.util.common.format.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundRedstoneTorch implements DataItem<BoundRedstoneTorch.Data> {
  public static final BoundRedstoneTorch io = new BoundRedstoneTorch();

  public static Data getBinding(ItemStack stack) {
    return io.getData(stack);
  }

  /*
  renamed from hasData as the types are the same (ItemStack -> boolean), but this is static
   */
  public static boolean hasItemData(ItemStack stack) {
    return io.hasData(stack);
  }

  public static void setData(ItemStack item, Location target, BlockFace face) {
    io.setData(item, new Data(target, face));
  }

  public static ItemStack withData(Location target, BlockFace face) {
    return io.withData(new Data(target, face));
  }

  public static boolean isBindableItem(ItemStack item) {
    return io.hasData(item);
  }

  public static final NamespacedKey COOLDOWN_GROUP = ItemCooldowns.groupKeyFor(BoundRedstoneTorch.class);

  @Override
  public Material getMaterial() {
    return Material.REDSTONE_TORCH;
  }

  @Override
  public NamespacedKey getCooldownGroup() {
    return COOLDOWN_GROUP;
  }

  @Override
  public Class<Data> getType() {
    return BoundRedstoneTorch.Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    lore.add(C.WHITE + AdaptLanguage.text(ItemsMessages.BOUND_REDSTONE_TORCH_NAME));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.BOUND_REDSTONE_TORCH_USAGE1));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.BOUND_REDSTONE_TORCH_USAGE2));
  }

  @Override
  public void applyMeta(Data data, ItemMeta meta) {
    meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);
    meta.setDisplayName(AdaptLanguage.text(ItemsMessages.BOUND_REDSTONE_TORCH_NAME));
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    private Location location;
    private BlockFace face;
  }
}
