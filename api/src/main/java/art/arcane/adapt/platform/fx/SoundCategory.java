package art.arcane.adapt.platform.fx;

import org.jetbrains.annotations.ApiStatus;

/**
 * Sound category, the volume slider of the client's sound settings a sound plays under.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum SoundCategory {
  MASTER,
  MUSIC,
  RECORDS,
  WEATHER,
  BLOCKS,
  HOSTILE,
  NEUTRAL,
  PLAYERS,
  AMBIENT,
  VOICE
}
