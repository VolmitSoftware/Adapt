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
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class ChronoTimeBombItem implements DataBoundItem<ChronoTimeBombItem.Data> {
  public static ChronoTimeBombItem io = new ChronoTimeBombItem();
  private static final Id LINGERING_POTION = Id.minecraft("lingering_potion");
  private static final Id CLOCK = Id.minecraft("clock");

  public static boolean isBindableItem(ItemRef stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }

    if (stack.type().equals(LINGERING_POTION)) {
      return io.hasData(stack);
    }

    if (stack.type().equals(CLOCK)) {
      return stack.data().has(DataBoundItem.dataKey(Data.class));
    }

    return false;
  }

  public static ItemRef withData() {
    return io.withData(new Data(System.currentTimeMillis()));
  }

  public static final Id COOLDOWN_GROUP = ItemCooldowns.groupKeyFor(ChronoTimeBombItem.class);

  @Override
  public Id getMaterial() {
    return LINGERING_POTION;
  }

  @Override
  public Id getCooldownGroup() {
    return COOLDOWN_GROUP;
  }

  /**
   * Legacy time bombs are clocks, which {@link #hasData(ItemRef)} rejects on
   * the material check, so the group upgrade keys off the dual identification
   * predicate instead.
   */
  @Override
  public boolean ensureCooldownGroup(ItemRef stack) {
    if (!isBindableItem(stack)) {
      return false;
    }

    return ItemCooldowns.stampGroup(stack, COOLDOWN_GROUP);
  }

  @Override
  public Class<Data> getType() {
    return Data.class;
  }

  @Override
  public void applyLore(Data data, List<String> lore) {
    lore.add(C.WHITE + AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOMB_NAME));
    lore.add(C.GRAY + AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOMB_USAGE1));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setPotionContents(new PotionContents(Id.minecraft("weakness"), null, List.of(), null));

    item.setEnchantment(Id.minecraft("binding_curse"), 1);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.POTION_EFFECTS);
    item.setDisplayName(AdaptLanguage.text(ItemsMessages.CHRONO_TIME_BOMB_NAME));
  }

  @AllArgsConstructor
  @lombok.Data
  public static class Data {
    private long created;
  }
}
