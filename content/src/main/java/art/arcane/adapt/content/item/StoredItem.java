package art.arcane.adapt.content.item;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.ItemCooldowns;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A built-in item whose stacks carry one {@link StoredJson} object under a pinned data key, with a name and lore drawn
 * from the data. Only stacks of the definition's base item count, as in the Bukkit plugin.
 */
final class StoredItem<D> {
  private final CustomItem.Definition<D> definition;
  private final DataKey<D> key;
  private final DataKey<String> raw;
  private final BiConsumer<D, ItemRef> meta;
  private final BiConsumer<D, List<String>> lore;

  StoredItem(CustomItem.Definition<D> definition, BiConsumer<D, ItemRef> meta, BiConsumer<D, List<String>> lore) {
    this.definition = definition;
    this.key = DataKey.of(definition.dataKey(), definition.codec());
    this.raw = DataKey.of(definition.dataKey(), DataCodecs.STRING);
    this.meta = meta;
    this.lore = lore;
  }

  CustomItem.Definition<D> definition() {
    return definition;
  }

  Id baseItem() {
    return definition.baseItem();
  }

  boolean has(@Nullable ItemRef stack) {
    return stack != null && !stack.isEmpty() && definition.baseItem().equals(stack.type()) && stack.data().has(raw);
  }

  @Nullable D read(@Nullable ItemRef stack) {
    return has(stack) ? stack.data().get(key) : null;
  }

  /** A new stack carrying {@code data}. */
  ItemRef create(D data) {
    ItemRef item = definition.create(data);
    present(data, item);
    return item;
  }

  /**
   * Replaces everything on the stack with a fresh item for {@code data} (anything else on it is dropped, as the Bukkit
   * item data did) on the same stack instance; write it back to its slot afterwards.
   */
  void replace(ItemRef item, D data) {
    definition.replace(item, data);
    present(data, item);
  }

  /** Rewrites only the stored data, leaving name and lore as they are. */
  void store(ItemRef item, D data) {
    item.data().set(key, data);
  }

  /**
   * True when the stored object holds a non-null {@code field}, even when its value cannot be resolved now (a bound
   * position in a world that is not loaded reads back as null but stays stored).
   */
  boolean storesField(@Nullable ItemRef stack, String field) {
    if (!has(stack)) {
      return false;
    }
    return Json.parse(stack.data().get(raw)) instanceof Map<?, ?> stored && stored.get(field) != null;
  }

  /** Stamps the declared cooldown group onto a stack of this item that predates the group; true when it changed. */
  boolean ensureCooldownGroup(@Nullable ItemRef stack) {
    return has(stack) && stampCooldownGroup(stack);
  }

  /** Stamps the declared cooldown group onto the stack; true when it changed. */
  boolean stampCooldownGroup(ItemRef stack) {
    return definition.cooldownGroup().map((Id group) -> ItemCooldowns.stampGroup(stack, group)).orElse(false);
  }

  private void present(D data, ItemRef item) {
    meta.accept(data, item);
    List<String> lines = new ArrayList<>();
    lore.accept(data, lines);
    item.setLore(lines);
  }
}
