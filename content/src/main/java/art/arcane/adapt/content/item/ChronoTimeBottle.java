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
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChronoTimeBottle {
  private static final Id POTION = Id.minecraft("potion");
  public static final CustomItem.Definition<Data> ITEM = CustomItem.builder(Id.adapt("chrono_time_bottle"), POTION,
          StoredJson.codec(ChronoTimeBottle::encode, ChronoTimeBottle::decode), new Data(0D))
      .storedUnder(Id.adapt("1823321605"))
      .recognisedBy(ChronoTimeBottle::isBindableItem)
      .allow(VanillaUse.USE)
      .build();
  private static final StoredItem<Data> STORED = new StoredItem<>(ITEM, ChronoTimeBottle::applyMeta,
      ChronoTimeBottle::applyLore);

  private ChronoTimeBottle() {
  }

  public static boolean isBindableItem(ItemRef stack) {
    return STORED.has(stack);
  }

  public static double getStoredSeconds(ItemRef stack) {
    Data data = STORED.read(stack);
    return data == null ? 0 : Math.max(0, data.getStoredSeconds());
  }

  public static void setStoredSeconds(ItemRef stack, double seconds) {
    STORED.replace(stack, new Data(Math.max(0, seconds)));
  }

  public static ItemRef withStoredSeconds(double seconds) {
    return STORED.create(new Data(Math.max(0, seconds)));
  }

  private static void applyLore(Data data, List<String> lore) {
    lore.add(TextFormat.WHITE + Text.key("adapt.items.chrono_time_bottle.name").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.chrono_time_bottle.usage1").resolve());
    lore.add(TextFormat.GRAY + Text.key("adapt.items.chrono_time_bottle.usage2").resolve());
    lore.add(TextFormat.AQUA + Text.key("adapt.items.chrono_time_bottle.stored_value").withMarkup("duration", TextFormat.WHITE + Format.duration((long) (Math.max(0, data.getStoredSeconds()) * 1000D), 1)).resolve());
  }

  private static void applyMeta(Data data, ItemRef item) {
    item.setPotionContents(PotionContents.of(Id.minecraft("water")).withColor(new Rgb(235, 245, 255)));
    item.setEnchantment(Id.minecraft("binding_curse"), 1);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.hideTooltip(TooltipPart.POTION_EFFECTS);
    item.setDisplayName(Text.key("adapt.items.chrono_time_bottle.name").resolve());
  }

  private static Map<String, Object> encode(Data data) {
    Map<String, Object> object = StoredJson.object();
    object.put("storedSeconds", data.getStoredSeconds());
    return object;
  }

  private static Data decode(Map<String, Object> object) {
    return new Data(StoredJson.number(object, "storedSeconds"));
  }

  public static class Data {
    private double storedSeconds;

    public Data(double storedSeconds) {
      this.storedSeconds = storedSeconds;
    }

    public double getStoredSeconds() {
      return storedSeconds;
    }

    public void setStoredSeconds(double storedSeconds) {
      this.storedSeconds = storedSeconds;
    }

    @Override
    public boolean equals(Object o) {
      return o == this || o instanceof Data other && Double.compare(storedSeconds, other.storedSeconds) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(storedSeconds);
    }

    @Override
    public String toString() {
      return "ChronoTimeBottle.Data(storedSeconds=" + storedSeconds + ")";
    }
  }
}
