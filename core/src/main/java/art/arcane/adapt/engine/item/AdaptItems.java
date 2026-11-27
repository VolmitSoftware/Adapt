package art.arcane.adapt.engine.item;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The custom items the item guard knows: the engine's own orbs, the mutation tools of every upgrade and built pair
 * signature, then every item definition in the content catalog. A stack is the first definition that recognises it.
 */
public final class AdaptItems {
  private static final List<CustomItem.Definition<?>> ENGINE_ITEMS = List.of(ExperienceOrb.ITEM, KnowledgeOrb.ITEM);
  private static volatile Snapshot snapshot = new Snapshot(Long.MIN_VALUE, List.of(), List.of());

  private AdaptItems() {
  }

  /** The definitions the guard checks, the orbs and mutation tools first; rebuilt when the catalog or the tools change. */
  public static List<CustomItem.Definition<?>> items() {
    List<CustomItem.Definition<?>> tools = MutationTools.items();
    if (!AdaptApi.isRunning()) {
      List<CustomItem.Definition<?>> engine = new ArrayList<>(ENGINE_ITEMS);
      engine.addAll(tools);
      return engine;
    }
    ContentCatalog catalog = AdaptApi.content();
    long revision = catalog.revision();
    Snapshot current = snapshot;
    if (current.revision() != revision || current.tools() != tools) {
      Map<Id, CustomItem.Definition<?>> items = new LinkedHashMap<>();
      for (CustomItem.Definition<?> item : ENGINE_ITEMS) {
        items.put(item.id(), item);
      }
      for (CustomItem.Definition<?> tool : tools) {
        items.putIfAbsent(tool.id(), tool);
      }
      for (CustomItem.Definition<?> item : catalog.items()) {
        items.putIfAbsent(item.id(), item);
      }
      current = new Snapshot(revision, tools, List.copyOf(items.values()));
      snapshot = current;
    }
    return current.items();
  }

  public static @Nullable CustomItem.Definition<?> of(@Nullable ItemRef item) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    for (CustomItem.Definition<?> custom : items()) {
      if (custom.is(item)) {
        return custom;
      }
    }
    return null;
  }

  /** Whether {@code item} is an Adapt item that the vanilla interaction {@code use} would spend or misuse. */
  public static boolean refuses(@Nullable ItemRef item, VanillaUse use) {
    CustomItem.Definition<?> custom = of(item);
    return custom != null && !custom.allows(item, use);
  }

  /** Whether any of {@code items} is an Adapt item that {@code recipe} may not take. */
  public static boolean refusedBy(@Nullable Id recipe, @Nullable List<ItemRef> items) {
    if (items == null) {
      return false;
    }
    for (ItemRef item : items) {
      CustomItem.Definition<?> custom = of(item);
      if (custom != null && !custom.consumableBy(recipe)) {
        return true;
      }
    }
    return false;
  }

  /** Whether any of {@code items} is an Adapt item that the vanilla interaction {@code use} would spend. */
  public static boolean anyRefuses(@Nullable List<ItemRef> items, VanillaUse use) {
    if (items == null) {
      return false;
    }
    for (ItemRef item : items) {
      if (refuses(item, use)) {
        return true;
      }
    }
    return false;
  }

  private record Snapshot(long revision, List<CustomItem.Definition<?>> tools, List<CustomItem.Definition<?>> items) {
  }
}
