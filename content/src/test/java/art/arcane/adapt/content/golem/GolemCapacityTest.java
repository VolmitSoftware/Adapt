package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemCapacityTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  private final List<GolemCapacity.Source> registered = new ArrayList<>();
  private final FakeGolemPlatform platform = new FakeGolemPlatform();

  @AfterEach
  void tearDown() {
    for (GolemCapacity.Source source : registered) {
      GolemCapacity.unregister(source);
    }
  }

  @Test
  void carryIsTheHighestFloorRaisedByTheSummedBonuses() {
    assertThat(GolemCapacity.carry(16, List.of())).isEqualTo(16);
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.atLeast(32)))).isEqualTo(32);
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.atLeast(8)))).isEqualTo(16);
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.atLeast(48), GolemCapacity.Contribution.atLeast(128)))).isEqualTo(128);
    assertThat(GolemCapacity.carry(64, List.of(GolemCapacity.Contribution.bonus(0.5D)))).isEqualTo(96);
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.atLeast(64), GolemCapacity.Contribution.bonus(0.5D),
        GolemCapacity.Contribution.bonus(1D)))).isEqualTo(160);
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.NONE))).isEqualTo(16);
  }

  @Test
  void carryNeverGoesNegativeOrOverflows() {
    assertThat(GolemCapacity.carry(-5, List.of())).isZero();
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.bonus(-3D)))).isZero();
    assertThat(GolemCapacity.carry(16, List.of(GolemCapacity.Contribution.bonus(Double.NaN)))).isEqualTo(16);
    assertThat(GolemCapacity.carry(Integer.MAX_VALUE, List.of(GolemCapacity.Contribution.bonus(2D)))).isEqualTo(Integer.MAX_VALUE);
    assertThat(GolemCapacity.Contribution.atLeast(-8).minimum()).isZero();
  }

  @Test
  void aGolemCarriesItsBodyBaseUntilASourceRaisesIt() {
    register(source("copper_frame", handle -> handle.body() == GolemBody.COPPER
        ? GolemCapacity.Contribution.atLeast(48) : GolemCapacity.Contribution.NONE));

    assertThat(GolemCapacity.carry(handle(GolemBody.FROST))).isEqualTo(8);
    assertThat(GolemCapacity.carry(handle(GolemBody.COPPER))).isEqualTo(48);
    assertThat(GolemCapacity.carry(handle(GolemBody.IRON))).isEqualTo(64);
  }

  @Test
  void aFailingSourceIsSkippedAndTheRestStillCount() {
    register(source("broken", handle -> {
      throw new IllegalStateException("broken source");
    }));
    register(source("might", handle -> GolemCapacity.Contribution.bonus(1D)));

    assertThat(GolemCapacity.carry(handle(GolemBody.COPPER))).isEqualTo(32);
  }

  @Test
  void registeringReplacesASourceWithTheSameIdAndUnregisteringNeedsTheSameSource() {
    GolemCapacity.Source first = source("gatherer", handle -> GolemCapacity.Contribution.bonus(0.5D));
    GolemCapacity.Source second = source("gatherer", handle -> GolemCapacity.Contribution.bonus(1D));
    register(first);
    register(second);

    GolemCapacity.unregister(first);

    assertThat(GolemCapacity.carry(handle(GolemBody.FROST))).isEqualTo(16);
  }

  private void register(GolemCapacity.Source source) {
    registered.add(source);
    GolemCapacity.register(source);
  }

  private GolemHandle handle(GolemBody body) {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    MobRef golem = GolemTestEntities.golem(body);
    lenient().when(golem.location()).thenReturn(new Loc(world, 0, 64, 0));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    return new GolemHandle(golem, state, platform);
  }

  private static GolemCapacity.Source source(String id, Function<GolemHandle, GolemCapacity.Contribution> carry) {
    return new GolemCapacity.Source() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public GolemCapacity.Contribution carry(GolemHandle handle) {
        return carry.apply(handle);
      }
    };
  }
}
