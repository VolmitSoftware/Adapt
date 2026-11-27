package art.arcane.adapt.engine.mutation.sound;

import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Plays mutation sounds by the section 7 rules: three pitch variants in rotation, the partner skill's voice 80 ms after
 * the lead's, at most one cue per adaptation per player every 150 ms, a quiet tick for lingering effects and the
 * two-second mutation chord.
 */
public final class MutationSoundPlayer {
  public static final long VOICE_GAP_MILLIS = 80L;
  public static final long COOLDOWN_MILLIS = 150L;
  public static final long TICK_INTERVAL_MILLIS = 1_000L;
  public static final long MUTATION_STEP_MILLIS = 400L;
  /** The low hit every Convergence adds under its two strike cues. */
  public static final Id CONVERGENCE_HIT = Id.minecraft("entity.player.attack.knockback");

  static final float[] PITCHES = {1.0F, 1.12F, 0.89F};
  static final float LEAD_VOLUME = 0.8F;
  static final float PARTNER_VOLUME = 0.7F;
  static final float TICK_VOLUME = 0.2F;
  static final float TAIL_VOLUME = 0.4F;
  static final float TAIL_PITCH = 0.75F;
  static final float CONVERGENCE_HIT_PITCH = 0.5F;
  static final float[] MUTATION_RISE = {0.6F, 0.75F, 0.9F, 1.05F};
  static final float MUTATION_CHORD_LEAD = 1.2F;
  static final float MUTATION_CHORD_PARTNER = 1.5F;

  private final LongSupplier clockMillis;
  private final Map<Key, Rotation> rotations = new ConcurrentHashMap<>();

  public MutationSoundPlayer(LongSupplier clockMillis) {
    this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
  }

  /** One parent's cue, for an upgraded parent adaptation. Returns false when the cooldown swallowed it. */
  public boolean cue(UUID player, String adaptation, SoundPalette palette, MutationCue cue, SoundStage stage) {
    int variant = claim(player, adaptation);
    if (variant < 0) {
      return false;
    }
    stage.sound(palette.cue(cue), LEAD_VOLUME, PITCHES[variant]);
    return true;
  }

  /** A mutated adaptation: the lead skill's cue now, the partner's 80 ms later at the next pitch variant. */
  public boolean cue(UUID player, String adaptation, SoundPalette lead, SoundPalette partner, MutationCue cue, SoundStage stage) {
    int variant = claim(player, adaptation);
    if (variant < 0) {
      return false;
    }
    stage.sound(lead.cue(cue), LEAD_VOLUME, PITCHES[variant]);
    Id partnerSound = partner.cue(cue);
    float partnerPitch = PITCHES[(variant + 1) % PITCHES.length];
    stage.later(VOICE_GAP_MILLIS, () -> stage.sound(partnerSound, PARTNER_VOLUME, partnerPitch));
    return true;
  }

  /** Both fight cues at once over the low hit shared by every Convergence. */
  public boolean convergence(UUID player, String adaptation, SoundPalette lead, SoundPalette partner, SoundStage stage) {
    int variant = claim(player, adaptation);
    if (variant < 0) {
      return false;
    }
    stage.sound(lead.fight(), LEAD_VOLUME, PITCHES[variant]);
    stage.sound(partner.fight(), PARTNER_VOLUME, PITCHES[(variant + 1) % PITCHES.length]);
    stage.sound(CONVERGENCE_HIT, LEAD_VOLUME, CONVERGENCE_HIT_PITCH);
    return true;
  }

  /** Starts the quiet tick of a lingering effect; the first tick sounds one interval from now. */
  public LingeringSound linger(SoundPalette palette, SoundStage stage) {
    TickingSound lingering = new TickingSound(palette.tick(), stage);
    lingering.schedule();
    return lingering;
  }

  /** The confirm sound: the parents' boon cues alternate upward, then meet in one chord about two seconds in. */
  public void mutation(SoundPalette lead, SoundPalette partner, SoundStage stage) {
    for (int step = 0; step < MUTATION_RISE.length; step++) {
      Id sound = step % 2 == 0 ? lead.boon() : partner.boon();
      float pitch = MUTATION_RISE[step];
      if (step == 0) {
        stage.sound(sound, LEAD_VOLUME, pitch);
      } else {
        stage.later(step * MUTATION_STEP_MILLIS, () -> stage.sound(sound, LEAD_VOLUME, pitch));
      }
    }
    stage.later(MUTATION_RISE.length * MUTATION_STEP_MILLIS, () -> {
      stage.sound(lead.boon(), LEAD_VOLUME, MUTATION_CHORD_LEAD);
      stage.sound(partner.boon(), PARTNER_VOLUME, MUTATION_CHORD_PARTNER);
    });
  }

  /** Drops a player's cooldown and rotation state, for when they leave. */
  public void forget(UUID player) {
    rotations.keySet().removeIf((Key key) -> key.player().equals(player));
  }

  private int claim(UUID player, String adaptation) {
    long now = clockMillis.getAsLong();
    Rotation rotation = rotations.computeIfAbsent(new Key(player, adaptation), (Key missing) -> new Rotation());
    synchronized (rotation) {
      if (rotation.played && now - rotation.lastPlayedMillis < COOLDOWN_MILLIS) {
        return -1;
      }
      rotation.played = true;
      rotation.lastPlayedMillis = now;
      int variant = rotation.next;
      rotation.next = (variant + 1) % PITCHES.length;
      return variant;
    }
  }

  private record Key(UUID player, String adaptation) {
  }

  private static final class Rotation {
    private boolean played;
    private long lastPlayedMillis;
    private int next;
  }
}
