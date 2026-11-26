package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.platform.value.Id;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The quiet tick of a running effect. {@link #stop()} silences it and plays the short tail once. */
public final class LingeringSound {
  private final Id tick;
  private final SoundStage stage;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicInteger variant = new AtomicInteger();

  LingeringSound(Id tick, SoundStage stage) {
    this.tick = tick;
    this.stage = stage;
  }

  public boolean isRunning() {
    return running.get();
  }

  public void stop() {
    if (running.compareAndSet(true, false)) {
      stage.sound(tick, MutationSoundPlayer.TAIL_VOLUME, MutationSoundPlayer.TAIL_PITCH);
    }
  }

  void schedule() {
    stage.later(MutationSoundPlayer.TICK_INTERVAL_MILLIS, this::tick);
  }

  private void tick() {
    if (!running.get()) {
      return;
    }
    int next = variant.getAndUpdate((int current) -> (current + 1) % MutationSoundPlayer.PITCHES.length);
    stage.sound(tick, MutationSoundPlayer.TICK_VOLUME, MutationSoundPlayer.PITCHES[next]);
    schedule();
  }
}
