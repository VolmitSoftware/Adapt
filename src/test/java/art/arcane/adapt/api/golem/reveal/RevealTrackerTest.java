package art.arcane.adapt.api.golem.reveal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RevealTrackerTest {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER_VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-0000000000b3");

  @Test
  void targetsEnteringTheDesiredSetAreShownOnce() {
    RevealTracker tracker = new RevealTracker();

    RevealTracker.Delta first = tracker.update(VIEWER, Set.of(FIRST, SECOND));
    RevealTracker.Delta again = tracker.update(VIEWER, Set.of(FIRST, SECOND));

    assertThat(first.entering()).containsExactlyInAnyOrder(FIRST, SECOND);
    assertThat(first.leaving()).isEmpty();
    assertThat(again.isEmpty()).isTrue();
    assertThat(tracker.isShown(VIEWER, FIRST)).isTrue();
    assertThat(tracker.isShown(VIEWER, SECOND)).isTrue();
  }

  @Test
  void targetsLeavingTheDesiredSetAreHiddenWhileStayersAreUntouched() {
    RevealTracker tracker = new RevealTracker();
    tracker.update(VIEWER, Set.of(FIRST, SECOND));

    RevealTracker.Delta delta = tracker.update(VIEWER, Set.of(SECOND, THIRD));

    assertThat(delta.entering()).containsExactly(THIRD);
    assertThat(delta.leaving()).containsExactly(FIRST);
    assertThat(tracker.isShown(VIEWER, FIRST)).isFalse();
    assertThat(tracker.isShown(VIEWER, SECOND)).isTrue();
    assertThat(tracker.isShown(VIEWER, THIRD)).isTrue();
  }

  @Test
  void swappingAwayFromARevealItemHidesEverythingAndSwappingBackShowsItAgain() {
    RevealTracker tracker = new RevealTracker();
    tracker.update(VIEWER, Set.of(FIRST, SECOND));

    RevealTracker.Delta swappedAway = tracker.update(VIEWER, Set.of());

    assertThat(swappedAway.entering()).isEmpty();
    assertThat(swappedAway.leaving()).containsExactlyInAnyOrder(FIRST, SECOND);
    assertThat(tracker.isShown(VIEWER, FIRST)).isFalse();

    RevealTracker.Delta swappedBack = tracker.update(VIEWER, Set.of(FIRST, SECOND));

    assertThat(swappedBack.entering()).containsExactlyInAnyOrder(FIRST, SECOND);
    assertThat(swappedBack.leaving()).isEmpty();
  }

  @Test
  void anEmptyUpdateForAnUnknownViewerChangesNothing() {
    RevealTracker tracker = new RevealTracker();

    assertThat(tracker.update(VIEWER, Set.of()).isEmpty()).isTrue();
    assertThat(tracker.reset(VIEWER)).isEmpty();
  }

  @Test
  void quitReturnsEverythingShownAndTheNextUpdateStartsFresh() {
    RevealTracker tracker = new RevealTracker();
    tracker.update(VIEWER, Set.of(FIRST, SECOND));
    tracker.update(OTHER_VIEWER, Set.of(THIRD));

    assertThat(tracker.reset(VIEWER)).containsExactlyInAnyOrder(FIRST, SECOND);
    assertThat(tracker.reset(VIEWER)).isEmpty();
    assertThat(tracker.isShown(VIEWER, FIRST)).isFalse();
    assertThat(tracker.isShown(OTHER_VIEWER, THIRD)).isTrue();
    assertThat(tracker.update(VIEWER, Set.of(FIRST)).entering()).containsExactly(FIRST);
  }

  @Test
  void droppingATargetForgetsItForEveryViewer() {
    RevealTracker tracker = new RevealTracker();
    tracker.update(VIEWER, Set.of(FIRST, SECOND));
    tracker.update(OTHER_VIEWER, Set.of(FIRST));

    assertThat(tracker.drop(FIRST)).containsExactlyInAnyOrder(VIEWER, OTHER_VIEWER);
    assertThat(tracker.isShown(VIEWER, FIRST)).isFalse();
    assertThat(tracker.isShown(OTHER_VIEWER, FIRST)).isFalse();
    assertThat(tracker.isShown(VIEWER, SECOND)).isTrue();
    assertThat(tracker.update(VIEWER, Set.of(FIRST, SECOND)).entering()).containsExactly(FIRST);
  }

  @Test
  void theDesiredSetIsCopiedRatherThanRetained() {
    RevealTracker tracker = new RevealTracker();
    HashSet<UUID> desired = new HashSet<>(Set.of(FIRST));
    tracker.update(VIEWER, desired);

    desired.add(SECOND);

    assertThat(tracker.isShown(VIEWER, SECOND)).isFalse();
    assertThat(tracker.update(VIEWER, desired).entering()).containsExactly(SECOND);
  }
}
