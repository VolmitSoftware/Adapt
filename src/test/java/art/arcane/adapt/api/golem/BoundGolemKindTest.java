package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.minion.MinionIdentity;
import org.bukkit.entity.IronGolem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BoundGolemKindTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID HANDLER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @BeforeEach
  void register() {
    BoundGolems.register();
  }

  @AfterEach
  void unregister() {
    BoundGolems.unregister();
  }

  @Test
  void aBoundGolemIsAMinionOfTheOwnerInItsStateWhileNoRegistryRuns() {
    IronGolem bound = bound(OWNER);
    IronGolem wild = GolemTestEntities.entity(IronGolem.class);

    assertThat(MinionIdentity.isMinion(bound)).isTrue();
    assertThat(MinionIdentity.ownerOf(bound)).isEqualTo(OWNER);
    assertThat(MinionIdentity.isMinion(wild)).isFalse();
    assertThat(MinionIdentity.sameOwner(bound, bound(OWNER))).isTrue();
    assertThat(MinionIdentity.sameOwner(bound, bound(HANDLER))).isFalse();
  }

  @Test
  void aRunningRegistryAnswersTheOwner() {
    IronGolem bound = bound(OWNER);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.ownerOf(bound)).thenReturn(Optional.of(HANDLER));

    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class)) {
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));

      assertThat(MinionIdentity.ownerOf(bound)).isEqualTo(HANDLER);
    }
  }

  @Test
  void anUnregisteredKindNoLongerIdentifiesBoundGolems() {
    IronGolem bound = bound(OWNER);

    BoundGolems.unregister();

    assertThat(MinionIdentity.isMinion(bound)).isFalse();
  }

  private static IronGolem bound(UUID owner) {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), owner, GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    return golem;
  }
}
