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

import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Data
public class BoundSnowBall implements DataBoundItem<BoundSnowBall.Data> {
  public static BoundSnowBall io = new BoundSnowBall();
  private static final Id SNOWBALL = Id.minecraft("snowball");

  public static boolean isBindableItem(ItemRef item) {
    return io.hasData(item);
  }

  @Override
  public Id getMaterial() {
    return SNOWBALL;
  }

  @Override
  public Class<Data> getType() {
    return BoundSnowBall.Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    lore.add(C.WHITE + AdaptLanguage.text(ItemsMessages.BOUND_SNOWBALL_NAME));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.BOUND_SNOWBALL_USAGE1));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.DYE);
    item.setDisplayName(AdaptLanguage.text(ItemsMessages.BOUND_SNOWBALL_NAME));
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    private UUID player;
  }
}
