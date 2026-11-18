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

package art.arcane.adapt.content.item.multiItems;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class MultiArmor implements MultiItem {
  private static final Id ELYTRA = Id.minecraft("elytra");

  @Override
  public boolean supportsItem(ItemRef item) {
    return true;
  }

  @Override
  public String getKey() {
    return "multiarmor";
  }

  @Override
  public List<String> tagLore(ItemRef item, List<ItemRef> otherItems) {
    List<String> lore = new ArrayList<>();
    lore.add(AdaptLanguage.text(
        ItemsMessages.MULTI_ARMOR_COUNT,
        trusted("count", otherItems.size() + 1)
    ));
    lore.add("-> " + OmniTool.displayName(item));

    for (ItemRef i : otherItems) {
      lore.add("-  " + OmniTool.displayName(i));
    }

    return lore;
  }

  public ItemRef nextElytra(ItemRef item) {
    return nextMatching(item, (ItemRef i) -> i.type().equals(ELYTRA));
  }

  public ItemRef nextChestplate(ItemRef item) {
    return nextMatching(item, (ItemRef i) -> i.type().legacyName().endsWith("_CHESTPLATE"));
  }
}
