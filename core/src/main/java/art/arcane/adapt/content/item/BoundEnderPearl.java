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

import art.arcane.adapt.api.item.BoundPositionJson;
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundEnderPearl implements DataBoundItem<BoundEnderPearl.Data> {
  public static BoundEnderPearl io = new BoundEnderPearl();
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  public static BlockRef getBlock(ItemRef stack) {
    if (io.getData(stack) != null) {
      return io.getData(stack).getBlock();
    }

    return null;
  }

  public static void setData(ItemRef item, BlockRef t) {
    io.setData(item, new Data(t));
  }

  public static boolean clearBinding(ItemRef item) {
    Data data = io.getData(item);
    if (data == null || data.getBlock() == null) {
      return false;
    }
    data.setBlock(null);
    item.data().set(io.dataKey(), GSON.toJson(data));
    return true;
  }

  public static ItemRef withData(BlockRef t) {
    return io.withData(new Data(t));
  }

  public static boolean isBindableItem(ItemRef item) {
    return io.hasData(item);
  }

  @Override
  public Id getMaterial() {
    return ENDER_PEARL;
  }

  @Override
  public Class<Data> getType() {
    return BoundEnderPearl.Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    lore.add(C.WHITE + AdaptLanguage.text(ItemsMessages.BOUND_ENDER_PERAL_NAME));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.BOUND_ENDER_PERAL_USAGE1));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.BOUND_ENDER_PERAL_USAGE2));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.setDisplayName(AdaptLanguage.text(ItemsMessages.BOUND_ENDER_PERAL_NAME));

  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    @JsonAdapter(BoundPositionJson.Block.class)
    private BlockRef block;

    public static BoundEnderPearl.Data at(BlockRef l) {
      return new BoundEnderPearl.Data(l);
    }
  }
}
