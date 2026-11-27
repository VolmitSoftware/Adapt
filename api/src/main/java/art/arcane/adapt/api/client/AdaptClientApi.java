package art.arcane.adapt.api.client;

import art.arcane.adapt.api.internal.FrameRegistry;
import org.jetbrains.annotations.ApiStatus;

/**
 * The client side of the API, for mods that draw their own skill art in code. Optional: an addon without client
 * code gets a themed page, hover frame and toast from the theme kit JSON in its asset bundle, or a generated theme
 * when it ships none.
 *
 * <p>Call it from client initialisation only. The classes here load on a dedicated server, where they do nothing.
 */
@ApiStatus.Experimental
public final class AdaptClientApi {
  private AdaptClientApi() {
  }

  /** Where skills register hover frames drawn in code. */
  public static ThemeRegistry themes() {
    return FrameRegistry.INSTANCE;
  }
}
