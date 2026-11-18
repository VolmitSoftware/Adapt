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
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@AllArgsConstructor
@Data
public class ChronoTimeBottle implements DataBoundItem<ChronoTimeBottle.Data> {
  public static ChronoTimeBottle io = new ChronoTimeBottle();
  private static final Id POTION = Id.minecraft("potion");

  public static boolean isBindableItem(ItemRef stack) {
    return stack != null && !stack.isEmpty() && stack.type().equals(POTION) && io.hasData(stack);
  }

  public static double getStoredSeconds(ItemRef stack) {
    Data data = io.getData(stack);
    return data == null ? 0 : Math.max(0, data.getStoredSeconds());
  }

  public static void setStoredSeconds(ItemRef stack, double seconds) {
    io.setData(stack, new Data(Math.max(0, seconds)));
  }

  public static ItemRef withStoredSeconds(double seconds) {
    return io.withData(new Data(Math.max(0, seconds)));
  }

  @Override
  public Id getMaterial() {
    return POTION;
  }

  @Override
  public Class<Data> getType() {
    return Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    lore.add(C.WHITE + AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOTTLE_NAME));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOTTLE_USAGE1));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOTTLE_USAGE2));
    lore.add(C.AQUA + AdaptLanguage.text(
        ItemsMessages.CHRONO_TIME_BOTTLE_STORED_VALUE,
        trusted("duration", C.WHITE + Form.duration((long) (Math.max(0, data.getStoredSeconds()) * 1000D), 1))
    ));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setPotionContents(new PotionContents(Id.minecraft("water"), new Rgb(235, 245, 255), List.of(), null));
    item.setEnchantment(Id.minecraft("binding_curse"), 1);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.POTION_EFFECTS);
    item.setDisplayName(AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOTTLE_NAME));
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    private double storedSeconds;
  }
}
