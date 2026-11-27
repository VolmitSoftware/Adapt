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

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChronoTimeBombItem {
  private static final Id LINGERING_POTION = Id.minecraft("lingering_potion");
  private static final Id CLOCK = Id.minecraft("clock");
  public static final CustomItem.Definition<Data> ITEM = CustomItem.builder(Id.adapt("chrono_time_bomb"),
          LINGERING_POTION, StoredJson.codec(ChronoTimeBombItem::encode, ChronoTimeBombItem::decode), new Data(0L))
      .storedUnder(Id.adapt("703518614"))
      .recognisedBy(ChronoTimeBombItem::isBindableItem)
      .allow(VanillaUse.USE)
      .ownCooldownGroup()
      .build();
  private static final StoredItem<Data> STORED = new StoredItem<>(ITEM, ChronoTimeBombItem::applyMeta,
      ChronoTimeBombItem::applyLore);
  private static final DataKey<String> RAW = DataKey.of(ITEM.dataKey(), DataCodecs.STRING);

  private ChronoTimeBombItem() {
  }

  public static boolean isBindableItem(ItemRef stack) {
    if (stack == null || stack.isEmpty()) {
      return false;
    }

    if (stack.type().equals(LINGERING_POTION)) {
      return STORED.has(stack);
    }

    if (stack.type().equals(CLOCK)) {
      return stack.data().has(RAW);
    }

    return false;
  }

  public static ItemRef withData() {
    return STORED.create(new Data(System.currentTimeMillis()));
  }

  /**
   * Legacy time bombs are clocks, which the base item check rejects, so the group upgrade keys off the dual
   * identification predicate instead.
   */
  public static boolean ensureCooldownGroup(ItemRef stack) {
    if (!isBindableItem(stack)) {
      return false;
    }

    return STORED.stampCooldownGroup(stack);
  }

  private static void applyLore(Data data, List<String> lore) {
    lore.add(TextFormat.WHITE + Text.key("adapt.items.chrono_time_bomb.name").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.chrono_time_bomb.usage1").resolve());
  }

  private static void applyMeta(Data data, ItemRef item) {
    item.setPotionContents(PotionContents.of(Id.minecraft("weakness")));

    item.setEnchantment(Id.minecraft("binding_curse"), 1);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.POTION_EFFECTS);
    item.setDisplayName(Text.key("adapt.items.chrono_time_bomb.name").resolve());
  }

  private static Map<String, Object> encode(Data data) {
    Map<String, Object> object = StoredJson.object();
    object.put("created", data.getCreated());
    return object;
  }

  private static Data decode(Map<String, Object> object) {
    return new Data((long) StoredJson.number(object, "created"));
  }

  public static class Data {
    private long created;

    public Data(long created) {
      this.created = created;
    }

    public long getCreated() {
      return created;
    }

    public void setCreated(long created) {
      this.created = created;
    }

    @Override
    public boolean equals(Object o) {
      return o == this || o instanceof Data other && created == other.created;
    }

    @Override
    public int hashCode() {
      return Objects.hash(created);
    }

    @Override
    public String toString() {
      return "ChronoTimeBombItem.Data(created=" + created + ")";
    }
  }
}
