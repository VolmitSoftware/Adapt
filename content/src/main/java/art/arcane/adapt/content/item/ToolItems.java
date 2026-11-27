package art.arcane.adapt.content.item;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodec;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.value.Id;

/**
 * Item definitions of the built-in mutation tools: no vanilla use of the base item, and only the tools' own recipes
 * (ids below {@code <skill>/tool/}) may take one as an ingredient.
 */
public final class ToolItems {
  private ToolItems() {
  }

  /** A tool whose stacks carry {@code type}, stored as JSON; {@code defaults} is what a plain stack holds. */
  public static <D> CustomItem.Builder<D> builder(Id id, Id baseItem, Class<D> type, D defaults) {
    return CustomItem.builder(id, baseItem, json(type), defaults).consumableBy(ToolItems::toolRecipe);
  }

  /** A tool whose stacks carry no data. */
  public static CustomItem.Builder<CustomItem.Plain> plain(Id id, Id baseItem) {
    return CustomItem.builder(id, baseItem).consumableBy(ToolItems::toolRecipe);
  }

  private static boolean toolRecipe(Id recipe) {
    return recipe.path().contains("/tool/");
  }

  private static <D> DataCodec<D> json(Class<D> type) {
    return DataCodecs.map(DataCodecs.STRING, Json::write, (String raw) -> {
      try {
        return Json.read(raw, type);
      } catch (IllegalArgumentException unreadable) {
        return null;
      }
    });
  }
}
