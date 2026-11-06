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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.nms.NMS;
import art.arcane.adapt.util.common.io.BukkitGson;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface MultiItem {
  boolean supportsItem(ItemStack itemStack);

  String getKey();

  default WindowResolution getWindowType() {
    return WindowResolution.W5_H1;
  }

  default ItemStack build(ItemStack... stacks) {
    ItemStack s = stacks[0];

    for (int i = 1; i < stacks.length; i++) {
      add(s, stacks[i]);
    }

    return s;
  }

  default boolean remove(ItemStack multi, ItemStack toRemove) {
    int ind = getItems(multi).indexOf(toRemove);
    if (ind == -1) {
      return false;
    }

    remove(multi, ind);
    return true;
  }

  default void remove(ItemStack multi, int index) {
    List<ItemStack> it = getItems(multi);
    it.remove(index);
    setItems(multi, it);
  }

  default void add(ItemStack multi, ItemStack item) {
    if (isMultiItem(item)) {
      explode(item).forEach(i -> add(multi, i));
    } else {
      setItems(multi, getItems(multi).qadd(item));
    }
  }

  default ItemStack nextMatching(ItemStack item, Predicate<ItemStack> predicate) {
    KList<ItemStack> items = getItems(item);
    for (int i = 0; i < items.size(); i++) {
      if (predicate.test(items.get(i))) {
        return switchTo(item, i);
      }
    }

    return item;
  }

  default ItemStack nextTool(ItemStack multi) {
    return switchTo(multi, 0);
  }

  default ItemStack switchTo(ItemStack multi, int index) {
    List<ItemStack> items = getItems(multi);
    ItemStack next = items.remove(index);
    items.add(getRealItem(multi));
    setItems(next, items);
    return next;
  }

  default void setItems(ItemStack multi, List<ItemStack> itemStacks) {
    List<ItemStack> stored = itemStacks.stream().filter(this::supportsItem).collect(Collectors.toList());
    setMultiItemData(multi, MultiItemData.builder()
        .rawItems(stored.stream().map(NMS::serializeStack).collect(Collectors.toList()))
        .build(), stored);
  }

  default KList<ItemStack> getItems(ItemStack multi) {
    MultiItemData d = getMultiItemData(multi);

    if (d == null) {
      return new KList<>();
    }

    return d.getItems();
  }

  default KList<ItemStack> explode(ItemStack multi) {
    KList<ItemStack> it = new KList<>();
    it.add(getRealItem(multi));
    it.add(getItems(multi));
    return it;
  }

  List<String> tagLore(ItemStack item, List<ItemStack> otherItems);

  default boolean isMultiItem(ItemStack item) {
    return item != null
        && item.hasItemMeta()
        && supportsItem(item)
        && item.getItemMeta().getPersistentDataContainer().has(dataKey(), PersistentDataType.STRING);
  }

  default ItemStack getRealItem(ItemStack multi) {
    ItemStack c = multi.clone();
    if (c.hasItemMeta()) {
      ItemMeta meta = c.getItemMeta();
      PersistentDataContainer data = meta.getPersistentDataContainer();
      if (data.has(dataKey(), PersistentDataType.STRING)) {
        meta.lore(ownLore(data));
        data.remove(dataKey());
        data.remove(loreKey());
      }
      c.setItemMeta(meta);
    }

    return c;
  }

  default MultiItemData getMultiItemData(ItemStack multi) {
    try {
      ItemMeta meta = multi.getItemMeta();
      String st = meta.getPersistentDataContainer().get(dataKey(), PersistentDataType.STRING);
      return BukkitGson.gson.fromJson(st, MultiItemData.class);
    } catch (Throwable e) {
      return null;
    }
  }

  default void setMultiItemData(ItemStack multi, MultiItemData data, List<ItemStack> storedItems) {
    ItemMeta meta = multi.getItemMeta();
    PersistentDataContainer container = meta.getPersistentDataContainer();
    if (!container.has(dataKey(), PersistentDataType.STRING)) {
      saveOwnLore(container, meta.lore());
    }
    container.set(dataKey(), PersistentDataType.STRING, BukkitGson.gson.toJson(data));

    List<Component> lore = new ArrayList<>();
    List<Component> own = ownLore(container);
    if (own != null) {
      lore.addAll(own);
    }
    for (String line : tagLore(multi, storedItems)) {
      lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
    }
    meta.lore(lore);
    multi.setItemMeta(meta);
  }

  private NamespacedKey dataKey() {
    return new NamespacedKey(Adapt.instance, getKey());
  }

  private NamespacedKey loreKey() {
    return new NamespacedKey(Adapt.instance, getKey() + "-lore");
  }

  private void saveOwnLore(PersistentDataContainer container, List<Component> lore) {
    if (lore == null || lore.isEmpty()) {
      container.remove(loreKey());
      return;
    }

    List<String> serialized = new ArrayList<>(lore.size());
    for (Component line : lore) {
      serialized.add(GsonComponentSerializer.gson().serialize(line));
    }
    container.set(loreKey(), PersistentDataType.LIST.strings(), serialized);
  }

  private List<Component> ownLore(PersistentDataContainer container) {
    List<String> serialized = container.get(loreKey(), PersistentDataType.LIST.strings());
    if (serialized == null || serialized.isEmpty()) {
      return null;
    }

    List<Component> lore = new ArrayList<>(serialized.size());
    for (String line : serialized) {
      lore.add(GsonComponentSerializer.gson().deserialize(line));
    }
    return lore;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  class MultiItemData {
    @Singular
    List<String> rawItems;

    KList<ItemStack> getItems() {
      return rawItems.stream().map(NMS::deserializeStack).collect(KList.collector());
    }

    void setItems(List<ItemStack> is) {
      rawItems = is.stream().map(NMS::serializeStack).collect(KList.collector());
    }
  }
}
