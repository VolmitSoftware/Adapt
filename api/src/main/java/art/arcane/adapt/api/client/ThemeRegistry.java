package art.arcane.adapt.api.client;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/** Client-side drawing code per skill. Adapt's own 24 hover frames are registered here like anyone's. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ThemeRegistry {
  /**
   * Draws the hover frame of a skill's tiles in code. A registered renderer takes the place of the {@code frame}
   * section of the skill's theme kit.
   *
   * @param skill the skill whose tiles get the frame
   * @throws IllegalArgumentException when the skill already has a renderer
   */
  FrameRegistration frame(Id skill, FrameRenderer renderer);
}
