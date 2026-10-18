package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.IronGolem;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemColossusTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private FakeGolemPlatform platform;
  private IronGolem golem;
  private GolemHandle handle;
  private GolemColossus.Form form;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    golem = GolemTestEntities.entity(IronGolem.class);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    handle = new GolemHandle(golem, state, platform);
    form = mock(GolemColossus.Form.class);
  }

  @AfterEach
  void tearDown() {
    GolemColossus.unregister(form);
  }

  private static MockedStatic<GolemancyRuntime> runtimeWith(GolemHandle... loaded) {
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loaded()).thenReturn(List.of(loaded));
    MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    return runtime;
  }

  @Test
  void onlyTheRegisteredFormCanUnregisterItself() {
    GolemColossus.Form second = mock(GolemColossus.Form.class);
    try {
      GolemColossus.register(form);
      GolemColossus.register(second);

      assertThat(GolemColossus.unregister(form)).isFalse();
      assertThat(GolemColossus.form()).contains(second);
      assertThat(GolemColossus.unregister(second)).isTrue();
      assertThat(GolemColossus.form()).isEmpty();
    } finally {
      GolemColossus.unregister(second);
    }
  }

  @Test
  void aGolemInColossusFormWithNoRegisteredFormSnapsBackToItsBoundSize() {
    handle.setColossus(true);
    platform.scale(golem).add(new AttributeModifier(GolemScale.RITUAL_KEY, -0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

    GolemColossus.reconcile(handle);

    assertThat(handle.state().colossus()).isFalse();
    assertThat(GolemState.read(golem).orElseThrow().colossus()).isFalse();
    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> {
      assertThat(modifier.getKey()).isEqualTo(GolemScale.MODIFIER_KEY);
      assertThat(modifier.getAmount()).isCloseTo(-0.64D, offset(1e-9));
    });
  }

  @Test
  void theLevelSettleNeverRevertsAGrownGolemWithoutAForm() {
    handle.setColossus(true);
    platform.join(OWNER, new GolemRoster());

    GolemColossus.settle(handle);

    assertThat(handle.state().colossus()).isTrue();
    assertThat(platform.scale(golem)).isEmpty();
  }

  @Test
  void unregisteringWaitsBeforeReconcilingSoAReplacementFormKeepsTheGolemGrown() {
    GolemColossus.Form replacement = mock(GolemColossus.Form.class);
    platform.join(OWNER, new GolemRoster());
    handle.setDormancyReason(DormancyReason.NONE);
    handle.setColossus(true);
    GolemColossus.register(form);
    try (MockedStatic<GolemancyRuntime> runtime = runtimeWith(handle)) {
      assertThat(GolemColossus.unregister(form)).isTrue();

      assertThat(handle.state().colossus()).isTrue();
      assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(GolemColossus.RECONCILE_DELAY_TICKS));
      GolemColossus.register(replacement);
      platform.runDelayed();
    } finally {
      GolemColossus.unregister(replacement);
    }

    verify(replacement).settle(handle);
    assertThat(handle.state().colossus()).isTrue();
    assertThat(platform.scale(golem)).isEmpty();
  }

  @Test
  void anOrphanedGolemRevertsOnlyAfterTheDelayEvenWhenZeroDelayTasksRunInline() {
    handle.setDormancyReason(DormancyReason.NONE);
    handle.setColossus(true);
    IronGolem sleeping = GolemTestEntities.entity(IronGolem.class);
    GolemHandle dormant = new GolemHandle(sleeping, GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON,
        GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 64, 0))), platform);
    dormant.setColossus(true);
    GolemColossus.register(form);
    try (MockedStatic<GolemancyRuntime> runtime = runtimeWith(handle, dormant)) {
      GolemColossus.unregister(form);

      assertThat(platform.ownsEntities).isTrue();
      assertThat(handle.state().colossus()).isTrue();
      assertThat(platform.scale(golem)).isEmpty();
      assertThat(platform.delayed).hasSize(1);

      platform.runDelayed();
    }

    assertThat(handle.state().colossus()).isFalse();
    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> assertThat(modifier.getAmount()).isCloseTo(-0.64D, offset(1e-9)));
    assertThat(dormant.state().colossus()).isTrue();
  }

  @Test
  void theRegisteredFormSettlesColossusGolemsOnlyWhileTheOwnerIsReady() {
    GolemColossus.register(form);
    handle.setColossus(true);

    GolemColossus.settle(handle);
    verify(form, never()).settle(handle);

    platform.join(OWNER, new GolemRoster());
    GolemColossus.settle(handle);
    handle.setColossus(false);
    GolemColossus.settle(handle);

    verify(form, times(1)).settle(handle);
    assertThat(platform.scale(golem)).isEmpty();
  }
}
