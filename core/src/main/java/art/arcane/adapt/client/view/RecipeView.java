package art.arcane.adapt.client.view;

import java.util.List;
import java.util.Objects;

/**
 * One recipe the adaptation {@code adaptationId} unlocks at {@code requiredLevel}, as its page draws it. {@code inputs}
 * depend on the kind: a shaped recipe's cells row by row, {@code width} to a row, with {@link Item#NONE} for an empty
 * cell; a shapeless recipe's ingredients; a cooking or stonecutting recipe's one input; a smithing recipe's template,
 * base and addition (the template may be {@link Item#NONE}); a brew's base bottle then its ingredient; a mix's bottles
 * then its ingredient. Recipes sharing {@code group} belong together on the page, such as a brew and its stronger brew.
 */
public record RecipeView(String adaptationId, Kind kind, int requiredLevel, String group, int width, List<Item> inputs,
                         Item result) {
  public RecipeView {
    Objects.requireNonNull(adaptationId, "adaptationId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(group, "group");
    Objects.requireNonNull(result, "result");
    inputs = List.copyOf(inputs);
  }

  public enum Kind {
    SHAPED,
    SHAPELESS,
    COOKING,
    STONECUTTING,
    SMITHING,
    BREWING,
    MIXING
  }

  /**
   * An item as a slot shows it: its icon, {@code count} of it, and its name: {@code name} when the server names it,
   * otherwise the client's own words for the language key {@code nameKey}, otherwise for the icon's item. A potion lists
   * its effects.
   */
  public record Item(IconRef icon, String name, String nameKey, int count, List<Effect> effects) {
    public static final Item NONE = new Item(IconRef.item("minecraft:air"), "", "", 0, List.of());

    public Item {
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(nameKey, "nameKey");
      effects = List.copyOf(effects);
    }

    public boolean empty() {
      return count <= 0;
    }
  }

  /** A potion effect: its id, amplifier (0 for level I), duration in ticks and whether it harms. */
  public record Effect(String effectId, int amplifier, int durationTicks, boolean harmful) {
    public Effect {
      Objects.requireNonNull(effectId, "effectId");
    }
  }
}
