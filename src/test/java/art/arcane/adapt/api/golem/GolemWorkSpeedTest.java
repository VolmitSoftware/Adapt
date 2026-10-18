package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemWorkSpeedTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private final List<GolemWorkSpeed.Source> registered = new ArrayList<>();
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.COPPER, GolemCoreType.BLANK,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    handle = new GolemHandle(golem, state, new FakeGolemPlatform());
  }

  @AfterEach
  void tearDown() {
    for (GolemWorkSpeed.Source source : registered) {
      GolemWorkSpeed.unregister(source);
    }
  }

  @Test
  void multipliersCombineByProductAndIgnoreInvalidValues() {
    assertThat(GolemWorkSpeed.combine(List.of())).isEqualTo(1D);
    assertThat(GolemWorkSpeed.combine(List.of(0.5D))).isEqualTo(0.5D);
    assertThat(GolemWorkSpeed.combine(List.of(0.5D, 0.7D))).isCloseTo(0.35D, offset(1.0E-9));
    assertThat(GolemWorkSpeed.combine(List.of(0.5D, 0D, -1D, Double.NaN, Double.POSITIVE_INFINITY))).isEqualTo(0.5D);
  }

  @Test
  void aGolemWithNoSourcesWorksAtTheNormalInterval() {
    assertThat(GolemWorkSpeed.intervalMultiplier(handle)).isEqualTo(1D);
  }

  @Test
  void registeredSourcesScaleTheIntervalAndAFailingSourceIsSkipped() {
    register(source("copper_frame", golem -> 0.5D));
    register(source("swiftness", golem -> 0.8D));
    register(source("broken", golem -> {
      throw new IllegalStateException("broken source");
    }));

    assertThat(GolemWorkSpeed.intervalMultiplier(handle)).isCloseTo(0.4D, offset(1.0E-9));
  }

  @Test
  void registeringReplacesASourceWithTheSameIdAndUnregisteringNeedsTheSameSource() {
    GolemWorkSpeed.Source first = source("copper_frame", golem -> 0.5D);
    GolemWorkSpeed.Source second = source("copper_frame", golem -> 0.25D);
    register(first);
    register(second);

    GolemWorkSpeed.unregister(first);

    assertThat(GolemWorkSpeed.sources()).contains(second).doesNotContain(first);
    assertThat(GolemWorkSpeed.intervalMultiplier(handle)).isEqualTo(0.25D);
  }

  private void register(GolemWorkSpeed.Source source) {
    registered.add(source);
    GolemWorkSpeed.register(source);
  }

  private static GolemWorkSpeed.Source source(String id, ToDoubleFunction<GolemHandle> multiplier) {
    return new GolemWorkSpeed.Source() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public double intervalMultiplier(GolemHandle handle) {
        return multiplier.applyAsDouble(handle);
      }
    };
  }
}
