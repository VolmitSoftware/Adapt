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

import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.item.BoundPositionJson;
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class BoundRedstoneTorch implements DataBoundItem<BoundRedstoneTorch.Data> {
  public static final BoundRedstoneTorch io = new BoundRedstoneTorch();
  private static final Id REDSTONE_TORCH = Id.minecraft("redstone_torch");

  public static Data getBinding(ItemRef stack) {
    return io.getData(stack);
  }

  /*
  renamed from hasData as the types are the same (ItemRef -> boolean), but this is static
   */
  public static boolean hasItemData(ItemRef stack) {
    return io.hasData(stack);
  }

  public static void setData(ItemRef item, Loc target, BlockFace face) {
    io.setData(item, new Data(target, face));
  }

  public static ItemRef withData(Loc target, BlockFace face) {
    return io.withData(new Data(target, face));
  }

  public static boolean isBindableItem(ItemRef item) {
    return io.hasData(item);
  }

  public static final Id COOLDOWN_GROUP = ItemCooldowns.groupKeyFor(BoundRedstoneTorch.class);

  @Override
  public Id getMaterial() {
    return REDSTONE_TORCH;
  }

  @Override
  public Id getCooldownGroup() {
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
  public void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.DYE);
    item.setDisplayName(AdaptLanguage.text(ItemsMessages.BOUND_REDSTONE_TORCH_NAME));
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    @JsonAdapter(BoundPositionJson.Location.class)
    private Loc location;
    private BlockFace face;
  }
}
