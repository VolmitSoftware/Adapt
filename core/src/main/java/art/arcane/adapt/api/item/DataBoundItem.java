package art.arcane.adapt.api.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * An Adapt item whose state is JSON stored on the stack under {@code adapt:<hash of the data class name>},
 * the same key and STRING storage the Bukkit DataItem writes, so existing stacks stay readable.
 */
public interface DataBoundItem<T> {
  Gson GSON = new Gson();

  Id getMaterial();

  Class<T> getType();

  void applyLore(T data, List<String> lore);

  void applyMeta(T data, ItemRef item);

  /**
   * Cooldown group this item belongs to, or null when the item has no use cooldown.
   */
  default Id getCooldownGroup() {
    return null;
  }

  default DataKey<String> dataKey() {
    return dataKey(getType());
  }

  static DataKey<String> dataKey(Class<?> dataType) {
    return DataKey.adapt(String.valueOf(dataType.getCanonicalName().hashCode()), DataCodecs.STRING);
  }

  default ItemRef blank() {
    return AdaptCore.platform().host().items().create(getMaterial(), 1);
  }

  default boolean acceptsMaterial(Id material) {
    return getMaterial().equals(material);
  }

  /**
   * Stamps the declared cooldown group onto a stack that predates the group.
   *
   * @return true when the stack was upgraded
   */
  default boolean ensureCooldownGroup(ItemRef stack) {
    Id group = getCooldownGroup();
    if (group == null || !hasData(stack)) {
      return false;
    }

    return ItemCooldowns.stampGroup(stack, group);
  }

  default T getData(ItemRef stack) {
    if (stack != null && !stack.isEmpty() && acceptsMaterial(stack.type())) {
      String raw = stack.data().get(dataKey());
      if (raw != null) {
        return GSON.fromJson(raw, getType());
      }
    }

    return null;
  }

  default boolean hasData(ItemRef stack) {
    return stack != null && !stack.isEmpty() && acceptsMaterial(stack.type()) && stack.data().has(dataKey());
  }

  /**
   * True when the stored JSON holds a non-null {@code field}, even when its value cannot be resolved now (a bound
   * position in a world that is not loaded reads back as null but stays stored).
   */
  default boolean storesField(ItemRef stack, String field) {
    if (!hasData(stack)) {
      return false;
    }

    JsonElement stored = JsonParser.parseString(stack.data().get(dataKey()));
    if (!stored.isJsonObject()) {
      return false;
    }
    JsonElement value = stored.getAsJsonObject().get(field);
    return value != null && !value.isJsonNull();
  }

  /**
   * Replaces everything on the stack with a fresh item for {@code t} (data, name, lore and meta; anything else on the
   * stack is dropped, as the Bukkit DataItem did) on the same stack instance; write it back to its slot afterwards.
   */
  default void setData(ItemRef item, T t) {
    item.clearComponents();
    write(item, t);
  }

  default ItemRef withData(T t) {
    ItemRef item = blank();
    write(item, t);
    return item;
  }

  private void write(ItemRef item, T t) {
    applyMeta(t, item);
    List<String> lore = new ArrayList<>();
    applyLore(t, lore);
    item.setLore(lore);
    item.data().set(dataKey(), GSON.toJson(t));
    ItemCooldowns.stampGroup(item, getCooldownGroup());
  }
}
