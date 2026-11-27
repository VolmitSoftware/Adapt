package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A craftable item an upgrade or signature hands the player. The owner registers it with {@code registerTool};
 * its recipes then need the owner at {@link #requiredLevel()}, and a click with it reaches {@link #use} only for
 * a player who holds the owner at that level.
 *
 * @param <D> the data each tool stack carries
 */
@ApiStatus.Experimental
public abstract class MutationTool<D> {
  private final CustomItem.Definition<D> item;
  private final Adaptation<?> owner;
  private final int requiredLevel;

  /**
   * @param item          the tool's item definition
   * @param owner         the upgrade or signature that hands it out
   * @param requiredLevel the owner level that unlocks crafting and using it, at least 1
   */
  protected MutationTool(CustomItem.Definition<D> item, Adaptation<?> owner, int requiredLevel) {
    this.item = Objects.requireNonNull(item, "item");
    this.owner = Objects.requireNonNull(owner, "owner");
    if (requiredLevel < 1) {
      throw new IllegalArgumentException("A tool's required level is at least 1");
    }
    this.requiredLevel = requiredLevel;
  }

  /** The recipes that craft the tool. */
  public abstract List<RecipeSpec> recipes();

  /** The player clicked with the tool and holds its owner at the required level. */
  public abstract void use(ToolUse use);

  /** Adds lines below the tool's conventional lore, in the viewer's language, for the data a stack carries. */
  public void applyLore(D data, List<String> lines) {
  }

  /** The tool's item definition. */
  public final CustomItem.Definition<D> item() {
    return item;
  }

  /** The upgrade or signature that hands the tool out. */
  public final Adaptation<?> owner() {
    return owner;
  }

  /** The owner level that unlocks crafting and using the tool, at least 1. */
  public final int requiredLevel() {
    return requiredLevel;
  }

  /** The player's active level in the owner when it reaches {@link #requiredLevel()}, else 0. */
  public final int level(PlayerRef player) {
    int level = owner.getActiveLevel(player);
    return level >= requiredLevel ? level : 0;
  }

  /** One tool stack carrying {@code data}, named by the item's messages, with the lore {@link #applyLore} adds. */
  public final ItemRef create(D data) {
    ItemRef stack = item.create(data);
    decorate(stack, data);
    return stack;
  }

  /** Replaces the data of a tool stack in place and refreshes its lore; false when the stack is not this tool. */
  public final boolean write(ItemRef stack, D data) {
    if (!item.write(stack, data)) {
      return false;
    }
    decorate(stack, data);
    return true;
  }

  /** Whether {@code stack} is one of this tool's stacks; false for null. */
  public final boolean is(@Nullable ItemRef stack) {
    return item.is(stack);
  }

  /** The data a stack of this tool carries; empty for null or any other stack. */
  public final Optional<D> read(@Nullable ItemRef stack) {
    return item.read(stack);
  }

  /** A map for per-player state. Adapt removes players who leave; call from a field initialiser. */
  protected final <V> Map<UUID, V> playerState() {
    return Engine.bridge().progress().playerState();
  }

  private void decorate(ItemRef stack, D data) {
    stack.setGlintOverride(true);
    List<String> extra = new ArrayList<>();
    applyLore(data, extra);
    if (!extra.isEmpty()) {
      List<String> lines = new ArrayList<>(stack.lore());
      lines.addAll(extra);
      stack.setLore(lines);
    }
  }
}
