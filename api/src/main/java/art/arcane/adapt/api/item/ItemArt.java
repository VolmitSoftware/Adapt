package art.arcane.adapt.api.item;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** How plain vanilla stacks that stand for Adapt items are drawn: server model overrides and the client mod's looks. */
@ApiStatus.Experimental
public final class ItemArt {
  private ItemArt() {
  }

  /**
   * A new stack drawn the way the server's models file sets for {@code path} (for example
   * {@code "architect", "elevator", "item"}): its material and custom model, or plain {@code fallback} when the file
   * sets nothing there. Needs a running core.
   */
  public static ItemRef modelled(Id fallback, String... path) {
    return Engine.bridge().items().modelled(fallback, List.of(path));
  }

  /**
   * A number that changes whenever the server reloads its models file, for caching what {@link #modelled} returns.
   * Needs a running core.
   */
  public static long revision() {
    return Engine.bridge().items().modelsRevision();
  }

  /**
   * Marks {@code item} with the client mod's look {@code key}, which a client with the mod draws as Adapt's art and a
   * vanilla client ignores. A key without a look leaves the item alone.
   */
  public static void applyLook(ItemRef item, String key) {
    Engine.bridge().items().applyLook(item, key);
  }
}
