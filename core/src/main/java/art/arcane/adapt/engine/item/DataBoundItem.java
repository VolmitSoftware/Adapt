package art.arcane.adapt.engine.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * An Adapt item whose state is JSON stored on the stack under {@code adapt:<dataKeyName()>}, STRING storage. The
 * name is persisted on every stack ever made, so it is a literal per item and never derived from a class name.
 */
public interface DataBoundItem<T> {
  Gson GSON = new Gson();

  Id getMaterial();

  Class<T> getType();

  /** The path of the persisted data key; changing it orphans every existing stack of the item. */
  String dataKeyName();

  void applyLore(T data, List<String> lore);

  void applyMeta(T data, ItemRef item);

  default DataKey<String> dataKey() {
    return DataKey.adapt(dataKeyName(), DataCodecs.STRING);
  }

  default ItemRef blank() {
    return AdaptCore.platform().host().items().create(getMaterial(), 1);
  }

  default boolean acceptsMaterial(Id material) {
    return getMaterial().equals(material);
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
  }
}
