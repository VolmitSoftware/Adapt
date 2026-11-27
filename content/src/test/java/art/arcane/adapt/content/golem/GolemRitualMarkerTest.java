package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRitualMarkerTest {
  private static final AttributeMod BOUND = new AttributeMod(GolemScale.MODIFIER_KEY, -0.5D, AttributeOperation.ADD_VALUE);
  private static final AttributeMod STEP = new AttributeMod(GolemScale.RITUAL_KEY, -0.3D, AttributeOperation.ADD_VALUE);

  private final List<AttributeMod> modifiers = new ArrayList<>(List.of(BOUND, STEP));
  private final GolemAttribute scale = new FakeAttribute(1D, modifiers);

  @Test
  void theMarkerStoresTheGolemsAwarenessUnderItsOwnKey() {
    MobRef golem = valid(GolemBody.FROST);

    GolemRitualMarker.start(golem, false);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.KEY).isEqualTo(DataKey.of(Id.of("adapt", "golem_ritual"), DataCodecs.BYTE));
    assertThat(GolemRitualMarker.has(golem)).isTrue();
    assertThat(GolemRitualMarker.wasAware(golem)).contains(false);
    GolemRitualMarker.finish(golem, scale);
  }

  @Test
  void recoveringALoadedGolemRestoresAwarenessClearsTheRitualScaleAndRemovesTheMarker() {
    MobRef golem = valid(GolemBody.FROST);
    golem.data().set(GolemRitualMarker.KEY, (byte) 1);

    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();

    verify(golem).setAware(true);
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void recoveryLeavesALiveRitualAloneUntilItFinishes() {
    MobRef golem = valid(GolemBody.FROST);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.recover(golem, scale)).isFalse();
    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).contains(STEP);

    assertThat(GolemRitualMarker.finish(golem, scale)).isTrue();
    verify(golem).setAware(true);
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void aBoundGolemKeepsItsAwarenessButLosesTheMarkerAndRitualScale() {
    MobRef golem = valid(GolemBody.IRON);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    golem.data().set(GolemRitualMarker.KEY, (byte) 0);

    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).containsExactly(BOUND);
    assertThat(GolemRitualMarker.has(golem)).isFalse();
  }

  @Test
  void aGolemWithoutAMarkerIsUntouched() {
    MobRef golem = valid(GolemBody.FROST);

    assertThat(GolemRitualMarker.recover(golem, scale)).isFalse();
    assertThat(GolemRitualMarker.finish(golem, scale)).isFalse();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(modifiers).containsExactly(BOUND, STEP);
  }

  @Test
  void anUnloadedGolemIsOnlyForgottenSoItsMarkerSurvivesForTheNextLoad() {
    MobRef golem = GolemTestEntities.golem(GolemBody.FROST);
    GolemRitualMarker.start(golem, true);

    assertThat(GolemRitualMarker.finish(golem, scale)).isFalse();

    verify(golem, never()).setAware(anyBoolean());
    assertThat(GolemRitualMarker.has(golem)).isTrue();
    assertThat(GolemRitualMarker.recover(golem, scale)).isTrue();
  }

  private static MobRef valid(GolemBody body) {
    MobRef golem = GolemTestEntities.golem(body);
    when(golem.isValid()).thenReturn(true);
    return golem;
  }
}
