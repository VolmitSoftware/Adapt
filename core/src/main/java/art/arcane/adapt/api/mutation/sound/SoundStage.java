package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

/** Where a mutation's sounds play and how later voices are timed. */
public interface SoundStage {
  long MILLIS_PER_TICK = 50L;

  void sound(Id sound, float volume, float pitch);

  void later(long delayMillis, Runnable task);

  /** Plays through the skill's {@link Fx} emitter at the entity, like {@link #following(Adaptation, EntityRef)}. */
  static SoundStage following(Skill<?> skill, EntityRef entity) {
    return new SoundStage() {
      @Override
      public void sound(Id sound, float volume, float pitch) {
        Fx.now(skill, entity, FxPriority.GAMEPLAY).sound(sound, volume, pitch);
      }

      @Override
      public void later(long delayMillis, Runnable task) {
        int ticks = (int) Math.max(1L, Math.round(delayMillis / (double) MILLIS_PER_TICK));
        Tasks.runEntity(entity, task, ticks);
      }
    };
  }

  /**
   * Plays through the adaptation's {@link Fx} emitter at the entity's current position, so the adaptation's sound
   * toggle and the global effects switches apply. Later voices run on the entity's owner, rounded to whole ticks.
   */
  static SoundStage following(Adaptation<?> adaptation, EntityRef entity) {
    return new SoundStage() {
      @Override
      public void sound(Id sound, float volume, float pitch) {
        Fx.now(adaptation, entity, FxPriority.GAMEPLAY).sound(sound, volume, pitch);
      }

      @Override
      public void later(long delayMillis, Runnable task) {
        int ticks = (int) Math.max(1L, Math.round(delayMillis / (double) MILLIS_PER_TICK));
        Tasks.runEntity(entity, task, ticks);
      }
    };
  }
}
