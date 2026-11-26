package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class MutationSoundPlayerTest {
  private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final String ADAPTATION = "verdant-blade-graft";
  private static final SoundPalette LEAD = SoundPalettes.of("herbalism");
  private static final SoundPalette PARTNER = SoundPalettes.of("chronos");

  private final FakeStage stage = new FakeStage();
  private final MutationSoundPlayer player = new MutationSoundPlayer(stage);

  @Test
  void aSecondCueInside150MillisIsDropped() {
    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage)).isTrue();
    stage.advance(149L);
    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage)).isFalse();
    stage.advance(1L);
    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage)).isTrue();

    assertThat(stage.played).extracting(Played::atMillis).containsExactly(0L, 150L);
  }

  @Test
  void theCooldownIsPerPlayerAndPerAdaptation() {
    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.WORK, stage)).isTrue();
    assertThat(player.cue(OTHER_PLAYER, ADAPTATION, LEAD, MutationCue.WORK, stage)).isTrue();
    assertThat(player.cue(PLAYER, "verdant-blade-echo", LEAD, MutationCue.WORK, stage)).isTrue();
    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.WORK, stage)).isFalse();
  }

  @Test
  void forgettingAPlayerClearsTheirCooldown() {
    player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.BOON, stage);
    player.forget(PLAYER);

    assertThat(player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.BOON, stage)).isTrue();
  }

  @Test
  void pitchRotatesThroughThreeVariants() {
    for (int trigger = 0; trigger < 4; trigger++) {
      player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage);
      stage.advance(MutationSoundPlayer.COOLDOWN_MILLIS);
    }

    assertThat(stage.played).extracting(Played::pitch).containsExactly(
        MutationSoundPlayer.PITCHES[0], MutationSoundPlayer.PITCHES[1], MutationSoundPlayer.PITCHES[2], MutationSoundPlayer.PITCHES[0]);
    assertThat(MutationSoundPlayer.PITCHES).doesNotHaveDuplicates();
  }

  @Test
  void aDroppedCueDoesNotAdvanceTheRotation() {
    player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage);
    player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage);
    stage.advance(MutationSoundPlayer.COOLDOWN_MILLIS);
    player.cue(PLAYER, ADAPTATION, LEAD, MutationCue.FIGHT, stage);

    assertThat(stage.played).extracting(Played::pitch)
        .containsExactly(MutationSoundPlayer.PITCHES[0], MutationSoundPlayer.PITCHES[1]);
  }

  @Test
  void aMutatedCuePlaysThePartnerVoice80MillisAfterTheLeadAtAnotherPitch() {
    player.cue(PLAYER, ADAPTATION, LEAD, PARTNER, MutationCue.WORK, stage);
    stage.advance(79L);
    assertThat(stage.played).extracting(Played::sound).containsExactly(LEAD.work());

    stage.advance(1L);

    assertThat(stage.played).extracting(Played::sound).containsExactly(LEAD.work(), PARTNER.work());
    assertThat(stage.played).extracting(Played::atMillis).containsExactly(0L, MutationSoundPlayer.VOICE_GAP_MILLIS);
    assertThat(stage.played.get(1).pitch()).isNotEqualTo(stage.played.get(0).pitch());
  }

  @Test
  void aConvergenceStrikesBothFightCuesOverTheSharedHit() {
    player.convergence(PLAYER, ADAPTATION, LEAD, PARTNER, stage);

    assertThat(stage.played).extracting(Played::sound)
        .containsExactly(LEAD.fight(), PARTNER.fight(), SoundPalettes.CONVERGENCE_HIT);
    assertThat(stage.played).extracting(Played::atMillis).containsOnly(0L);
  }

  @Test
  void aLingeringTickRunsUntilStoppedThenPlaysOneTail() {
    LingeringSound lingering = player.linger(PARTNER, stage);
    stage.advance(3 * MutationSoundPlayer.TICK_INTERVAL_MILLIS);

    assertThat(lingering.isRunning()).isTrue();
    assertThat(stage.played).extracting(Played::atMillis).containsExactly(1_000L, 2_000L, 3_000L);
    assertThat(stage.played).extracting(Played::volume).containsOnly(MutationSoundPlayer.TICK_VOLUME);

    lingering.stop();
    lingering.stop();
    stage.advance(5 * MutationSoundPlayer.TICK_INTERVAL_MILLIS);

    assertThat(lingering.isRunning()).isFalse();
    assertThat(stage.played).hasSize(4).extracting(Played::sound).containsOnly(PARTNER.tick());
    assertThat(stage.played.get(3)).isEqualTo(new Played(3_000L, PARTNER.tick(), MutationSoundPlayer.TAIL_VOLUME, MutationSoundPlayer.TAIL_PITCH));
    assertThat(stage.pending).isEmpty();
  }

  @Test
  void theMutationSoundRisesThroughBothParentsIntoAChordAboutTwoSecondsLong() {
    player.mutation(LEAD, PARTNER, stage);
    stage.advance(2_000L);

    assertThat(stage.played).extracting(Played::sound)
        .containsExactly(LEAD.boon(), PARTNER.boon(), LEAD.boon(), PARTNER.boon(), LEAD.boon(), PARTNER.boon());
    assertThat(stage.played).extracting(Played::atMillis).containsExactly(0L, 400L, 800L, 1_200L, 1_600L, 1_600L);
    assertThat(stage.played.subList(0, 5)).extracting(Played::pitch).isSorted();
  }

  private record Played(long atMillis, Id sound, float volume, float pitch) {
  }

  private record Pending(long dueMillis, long order, Runnable task) {
  }

  private static final class FakeStage implements SoundStage, LongSupplier {
    private final List<Played> played = new ArrayList<>();
    private final PriorityQueue<Pending> pending = new PriorityQueue<>(
        Comparator.comparingLong(Pending::dueMillis).thenComparingLong(Pending::order));
    private long now;
    private long scheduled;

    @Override
    public long getAsLong() {
      return now;
    }

    @Override
    public void sound(Id sound, float volume, float pitch) {
      played.add(new Played(now, sound, volume, pitch));
    }

    @Override
    public void later(long delayMillis, Runnable task) {
      pending.add(new Pending(now + delayMillis, scheduled++, task));
    }

    void advance(long millis) {
      long until = now + millis;
      while (!pending.isEmpty() && pending.peek().dueMillis() <= until) {
        Pending next = pending.poll();
        now = next.dueMillis();
        next.task().run();
      }
      now = until;
    }
  }
}
