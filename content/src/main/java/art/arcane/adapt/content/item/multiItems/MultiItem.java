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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public interface MultiItem {
  boolean supportsItem(ItemRef item);

  String getKey();

  List<String> tagLore(ItemRef item, List<ItemRef> otherItems);

  default ItemRef build(ItemRef... stacks) {
    ItemRef s = stacks[0];

    for (int i = 1; i < stacks.length; i++) {
      add(s, stacks[i]);
    }

    return s;
  }

  default void add(ItemRef multi, ItemRef item) {
    if (isMultiItem(item)) {
      for (ItemRef component : explode(item)) {
        add(multi, component);
      }
    } else {
      List<ItemRef> items = getItems(multi);
      items.add(item);
      setItems(multi, items);
    }
  }

  default ItemRef nextMatching(ItemRef item, Predicate<ItemRef> predicate) {
    List<ItemRef> items = getItems(item);
    for (int i = 0; i < items.size(); i++) {
      if (predicate.test(items.get(i))) {
        return switchTo(item, i);
      }
    }

    return item;
  }

  default ItemRef switchTo(ItemRef multi, int index) {
    List<ItemRef> items = getItems(multi);
    ItemRef next = items.remove(index);
    items.add(getRealItem(multi));
    setItems(next, items);
    return next;
  }

  default void setItems(ItemRef multi, List<ItemRef> itemStacks) {
    ItemOps ops = items();
    List<ItemRef> stored = new ArrayList<>(itemStacks.size());
    List<String> rawItems = new ArrayList<>(itemStacks.size());
    for (ItemRef item : itemStacks) {
      if (supportsItem(item)) {
        stored.add(item);
        rawItems.add(ops.encode(item));
      }
    }
    setMultiItemData(multi, new MultiItemData(rawItems), stored);
  }

  default List<ItemRef> getItems(ItemRef multi) {
    MultiItemData data = getMultiItemData(multi);
    if (data == null || data.rawItems == null) {
      return new ArrayList<>();
    }

    ItemOps ops = items();
    List<ItemRef> decoded = new ArrayList<>(data.rawItems.size());
    for (String raw : data.rawItems) {
      ItemRef item = ops.decode(raw);
      if (item == null || item.isEmpty()) {
        throw new IllegalStateException("Unreadable item stored in " + dataKey());
      }
      decoded.add(item);
    }
    return decoded;
  }

  default List<ItemRef> explode(ItemRef multi) {
    List<ItemRef> it = new ArrayList<>();
    it.add(getRealItem(multi));
    it.addAll(getItems(multi));
    return it;
  }

  default boolean isMultiItem(ItemRef item) {
    return item != null
        && !item.isEmpty()
        && supportsItem(item)
        && item.data().has(dataKey());
  }

  default ItemRef getRealItem(ItemRef multi) {
    ItemRef c = multi.copy();
    DataHolder data = c.data();
    if (data.has(dataKey())) {
      List<String> ownLore = data.get(loreKey());
      c.setLoreJson(ownLore);
      data.remove(dataKey());
      data.remove(loreKey());
    }

    return c;
  }

  default MultiItemData getMultiItemData(ItemRef multi) {
    String stored = multi.data().get(dataKey());
    if (stored == null) {
      return null;
    }
    try {
      return Json.read(stored, MultiItemData.class);
    } catch (IllegalArgumentException unreadable) {
      return null;
    }
  }

  default void setMultiItemData(ItemRef multi, MultiItemData data, List<ItemRef> storedItems) {
    DataHolder container = multi.data();
    if (!container.has(dataKey())) {
      List<String> lore = multi.loreJson();
      if (lore.isEmpty()) {
        container.remove(loreKey());
      } else {
        container.set(loreKey(), lore);
      }
    }
    container.set(dataKey(), Json.write(data));

    List<String> lore = new ArrayList<>();
    List<String> own = container.get(loreKey());
    if (own != null) {
      lore.addAll(own);
    }
    for (String line : tagLore(multi, storedItems)) {
      lore.add(LegacyTextJson.toJson(line));
    }
    multi.setLoreJson(lore);
  }

  private DataKey<String> dataKey() {
    return DataKey.adapt(getKey(), DataCodecs.STRING);
  }

  private DataKey<List<String>> loreKey() {
    return DataKey.adapt(getKey() + "-lore", DataCodecs.STRING_LIST);
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }

  final class MultiItemData {
    List<String> rawItems;

    MultiItemData() {
    }

    MultiItemData(List<String> rawItems) {
      this.rawItems = rawItems;
    }
  }
}
