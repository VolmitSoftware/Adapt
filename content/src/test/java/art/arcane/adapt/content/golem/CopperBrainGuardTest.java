package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CopperBrainGuardTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000402");

  private final WorldRef world = mock(WorldRef.class);
  private final CopperBrainGuard guard = new CopperBrainGuard();

  @Test
  void unboundCopperGolemsKeepTheirVanillaTransport() {
    MobRef golem = GolemTestEntities.golem(GolemBody.COPPER);
    ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1), true);

    guard.on(event);

    assertThat(event.allowed()).isTrue();
  }

  @Test
  void boundCopperGolemsNeverPickVanillaTransportTargetsEvenWhileTheBrainUsesAContainer() {
    MobRef golem = bound(GolemBody.COPPER, GolemCoreType.SORTER);
    ItemTransportingEntityValidateTargetEvent first = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1), true);
    ItemTransportingEntityValidateTargetEvent second = new ItemTransportingEntityValidateTargetEvent(golem, block(2, 64, 1), true);

    guard.on(first);
    guard.on(second);

    assertThat(first.allowed()).isFalse();
    assertThat(second.allowed()).isFalse();
  }

  @Test
  void unboundCopperGolemsKeepTheirVanillaWandering() {
    MobRef golem = GolemTestEntities.golem(GolemBody.COPPER);
    EntityPathfindEvent event = new EntityPathfindEvent(golem, new Loc(world, 3, 64, 3), null);

    guard.on(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void boundCopperGolemsStartNoPathOfTheirOwnSuchAsAStrollOrAPanic() {
    MobRef golem = bound(GolemBody.COPPER, GolemCoreType.BLANK);
    EntityPathfindEvent stroll = new EntityPathfindEvent(golem, new Loc(world, 3, 64, 3), null);
    EntityPathfindEvent panic = new EntityPathfindEvent(golem, new Loc(world, -6, 64, 2), null);

    guard.on(stroll);
    guard.on(panic);

    assertThat(stroll.isCancelled()).isTrue();
    assertThat(panic.isCancelled()).isTrue();
  }

  @Test
  void boundGolemsOfOtherBodiesKeepTheirVanillaPathsForCombat() {
    MobRef golem = bound(GolemBody.IRON, GolemCoreType.BLANK);
    EntityPathfindEvent chase = new EntityPathfindEvent(golem, new Loc(world, 3, 64, 3), null);

    guard.on(chase);

    assertThat(chase.isCancelled()).isFalse();
  }

  private MobRef bound(GolemBody body, GolemCoreType core) {
    MobRef golem = GolemTestEntities.golem(body);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, core, GolemAnchor.of(WORLD, 0, 64, 0))).write(golem);
    return golem;
  }

  private BlockRef block(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.world()).thenReturn(world);
    return block;
  }
}
