package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * One skill's sounds in a mutation: three one-shot cues plus the quiet tick played while a lingering effect runs.
 *
 * @param fight the sound id of the {@link MutationCue#FIGHT} cue
 * @param work  the sound id of the {@link MutationCue#WORK} cue
 * @param boon  the sound id of the {@link MutationCue#BOON} cue
 * @param tick  the sound id repeated while a lasting effect runs
 */
@ApiStatus.Experimental
public record SoundPalette(Id fight, Id work, Id boon, Id tick) {
  public SoundPalette {
    Objects.requireNonNull(fight, "fight");
    Objects.requireNonNull(work, "work");
    Objects.requireNonNull(boon, "boon");
    Objects.requireNonNull(tick, "tick");
  }

  /** Same as the constructor. */
  public static SoundPalette of(Id fight, Id work, Id boon, Id tick) {
    return new SoundPalette(fight, work, boon, tick);
  }

  /** The one-shot sound of {@code cue}. */
  public Id cue(MutationCue cue) {
    return switch (cue) {
      case FIGHT -> fight;
      case WORK -> work;
      case BOON -> boon;
    };
  }

  /** All four sound ids: fight, work, boon, tick. */
  public List<Id> sounds() {
    return List.of(fight, work, boon, tick);
  }
}
