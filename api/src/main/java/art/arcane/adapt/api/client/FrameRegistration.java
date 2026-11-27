package art.arcane.adapt.api.client;

import org.jetbrains.annotations.ApiStatus;

/** A renderer registered through {@link ThemeRegistry#frame}; keep it to remove the renderer again. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface FrameRegistration {
  /** Removes the renderer; the skill falls back to its kit's frame data. Safe to call twice. */
  void remove();
}
