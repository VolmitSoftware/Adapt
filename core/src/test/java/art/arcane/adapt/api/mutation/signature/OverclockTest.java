package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Chronos with Golemancy: the clock gesture runs nearby golems at triple speed for stored bottle time. */
class OverclockTest {
  private static final Id CLOCK = Id.minecraft("clock");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private final List<GolemHandle> golems = new ArrayList<>();
  private MockedStatic<GolemancyRuntime> runtime;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Clockmaker", 0.5D, 61D, 0.5D);
    player.holding(new FakeItem(CLOCK, 1));
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillGolemancy.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(player.uuid())).thenAnswer(invocation -> List.copyOf(golems));
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
  }

  @AfterEach
  void stop() {
    runtime.close();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void nearbyGolemsRunAtTripleSpeedAndTheBottlePays() {
    kit.mutate("chronos", "golemancy");
    kit.learn("chronos", "golemancy", Recipe.CONVERGENCE, 3);
    GolemHandle near = golem(6.5D);
    GolemHandle far = golem(40.5D);
    bottle(100D);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(GolemWorkSpeed.intervalMultiplier(near)).isCloseTo(1D / 3D, within(0.001D));
    assertThat(GolemWorkSpeed.intervalMultiplier(far)).isEqualTo(1D);
    assertThat(stored()).isCloseTo(40D, within(0.001D));
  }

  @Test
  void golemancyLeadingWorksTheSameWay() {
    kit.mutate("golemancy", "chronos");
    kit.learn("golemancy", "chronos", Recipe.CONVERGENCE, 1);
    GolemHandle near = golem(3.5D);
    bottle(60D);

    swap();

    assertThat(GolemWorkSpeed.intervalMultiplier(near)).isCloseTo(1D / 3D, within(0.001D));
  }

  @Test
  void withoutBottleTimeOrGolemsNothingIsSpentOrSped() {
    kit.mutate("chronos", "golemancy");
    kit.learn("chronos", "golemancy", Recipe.CONVERGENCE, 3);
    bottle(100D);
    assertThat(swap().isCancelled()).isTrue();
    assertThat(stored()).isCloseTo(100D, within(0.001D));

    GolemHandle near = golem(4.5D);
    bottle(59D);
    assertThat(swap().isCancelled()).isTrue();

    assertThat(GolemWorkSpeed.intervalMultiplier(near)).isEqualTo(1D);
    assertThat(stored()).isCloseTo(59D, within(0.001D));
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    GolemHandle near = golem(4.5D);
    bottle(100D);

    assertThat(swap().isCancelled()).isFalse();

    assertThat(GolemWorkSpeed.intervalMultiplier(near)).isEqualTo(1D);
    assertThat(stored()).isCloseTo(100D, within(0.001D));
  }

  @Test
  void theCooldownStopsASecondOverclockAndItsBottleCharge() {
    kit.mutate("chronos", "golemancy");
    kit.learn("chronos", "golemancy", Recipe.CONVERGENCE, 3);
    golem(4.5D);
    bottle(200D);

    swap();
    assertThat(swap().isCancelled()).isFalse();

    assertThat(stored()).isCloseTo(140D, within(0.001D));
  }

  private GolemHandle golem(double x) {
    MobRef body = mock(MobRef.class);
    when(body.uuid()).thenReturn(UUID.randomUUID());
    when(body.isValid()).thenReturn(true);
    when(body.location()).thenReturn(new Loc(platform.world(), x, 61D, 0.5D));
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.id()).thenReturn(UUID.randomUUID());
    when(handle.owner()).thenReturn(player.uuid());
    when(handle.entity()).thenReturn(body);
    golems.add(handle);
    return handle;
  }

  private void bottle(double seconds) {
    player.inventory().setItem(9, ChronoTimeBottle.withStoredSeconds(seconds));
  }

  private double stored() {
    return ChronoTimeBottle.getStoredSeconds(player.inventory().item(9));
  }

  private PlayerSwapHandItemsEvent swap() {
    player.setSneaking(true);
    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    player.setSneaking(false);
    return swap;
  }
}
