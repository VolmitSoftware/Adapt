package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client mod's own look for Adapt items that are plain vanilla materials. Each look marks its stack with a custom
 * model data number; the client mod's built-in resources turn that number on that material into Adapt's art, and a
 * vanilla client, which has no such model, keeps drawing the material. Numbers are {@link #FIRST} plus the look's place
 * in {@link #LOOKS}, so new looks are only ever appended.
 */
public final class ItemLooks {
  public static final int FIRST = 4_902_900;

  /** One look: its key, which names its art {@code adapt:item/<key>}, and the material it is drawn over. */
  public record Look(String key, Id material) {
  }

  public static final List<Look> LOOKS = List.of(
      look("tool-agility-grappling-line", "fishing_rod"),
      look("tool-architect-masons-template", "paper"),
      look("tool-brewing-bandolier", "leather"),
      look("tool-crafting-artisans-ledger", "written_book"),
      look("tool-crafting-prefab-crate", "chest"),
      look("tool-discovery-explorers-atlas", "book"),
      look("tool-excavation-grading-stakes", "stick"),
      look("tool-herbalism-scarecrow", "hay_block"),
      look("tool-hunter-trackers-compass", "compass"),
      look("tool-pickaxe-prospectors-charge", "firework_star"),
      look("tool-ranged-quiver", "rabbit_hide"),
      look("tool-ranged-ricochet-arrow", "arrow"),
      look("tool-ranged-snare-arrow", "arrow"),
      look("tool-rift-anchor", "lodestone"),
      look("tool-rift-eye", "ender_eye"),
      look("tool-seaborne-tide-shrine", "conduit"),
      look("tool-signature-crafting-enchanting-runed-book", "enchanted_book"),
      look("tool-signature-enchanting-crafting-runed-book", "enchanted_book"),
      look("tool-stealth-smoke-pellet", "firework_star"),
      look("tool-swords-whetstone", "flint"),
      look("tool-taming-whistle", "goat_horn"),
      look("chalk-straightedge", "stick"),
      look("chalk-polyline", "stick"),
      look("chalk-compass", "stick"),
      look("chalk-arc-bow", "stick"),
      look("knowledge-orb", "snowball"),
      look("experience-orb", "snowball"));

  private static final Map<String, Integer> NUMBERS = numbers();

  private ItemLooks() {
  }

  /** The custom model data number of {@code key}, or 0 when it has no look. */
  public static int number(String key) {
    return NUMBERS.getOrDefault(key, 0);
  }

  /** Marks {@code item} with the look {@code key}; an item without that look is left alone. */
  public static void apply(ItemRef item, String key) {
    int number = number(key);
    if (number != 0) {
      AdaptPorts.get(CustomModelPort.class, CustomModelPort.NONE).setCustomModelData(item, number);
    }
  }

  /** Like {@link #apply} unless the server's models file already gives {@code model} its own art. */
  public static void applyUnlessCustom(ItemRef item, String key, CustomModel model) {
    boolean custom = model.model() != 0 || model.modelKey() != null && !CustomModel.EMPTY_KEY.equals(model.modelKey());
    if (!custom) {
      apply(item, key);
    }
  }

  private static Look look(String key, String material) {
    return new Look(key, Id.minecraft(material));
  }

  private static Map<String, Integer> numbers() {
    Map<String, Integer> numbers = new HashMap<>();
    for (int index = 0; index < LOOKS.size(); index++) {
      numbers.put(LOOKS.get(index).key(), FIRST + index);
    }
    return Map.copyOf(numbers);
  }
}
