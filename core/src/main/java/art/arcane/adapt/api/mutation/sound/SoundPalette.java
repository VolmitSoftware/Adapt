package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

/** One skill's vanilla sounds: three one-shot cues plus the quiet tick played while a lingering effect runs. */
public record SoundPalette(String skill, Id fight, Id work, Id boon, Id tick) {
  public SoundPalette {
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(fight, "fight");
    Objects.requireNonNull(work, "work");
    Objects.requireNonNull(boon, "boon");
    Objects.requireNonNull(tick, "tick");
  }

  public Id cue(MutationCue cue) {
    return switch (cue) {
      case FIGHT -> fight;
      case WORK -> work;
      case BOON -> boon;
    };
  }

  public List<Id> sounds() {
    return List.of(fight, work, boon, tick);
  }
}
