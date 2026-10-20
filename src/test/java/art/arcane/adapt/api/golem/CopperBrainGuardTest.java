package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CopperBrainGuardTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000402");

  private final World world = mock(World.class);
  private final CopperBrainGuard guard = new CopperBrainGuard();

  @Test
  void unboundCopperGolemsKeepTheirVanillaTransport() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));

    guard.on(event);

    assertThat(event.isAllowed()).isTrue();
  }

  @Test
  void boundCopperGolemsNeverPickVanillaTransportTargetsEvenWhileTheBrainUsesAContainer() {
    CopperGolem golem = bound(CopperGolem.class, GolemBody.COPPER, GolemCoreType.SORTER);
    ItemTransportingEntityValidateTargetEvent first = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));
    ItemTransportingEntityValidateTargetEvent second = new ItemTransportingEntityValidateTargetEvent(golem, block(2, 64, 1));

    guard.on(first);
    guard.on(second);

    assertThat(first.isAllowed()).isFalse();
    assertThat(second.isAllowed()).isFalse();
  }

  @Test
  void unboundCopperGolemsKeepTheirVanillaWandering() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    EntityPathfindEvent event = new EntityPathfindEvent(golem, new Location(world, 3, 64, 3), null);

    guard.on(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void boundCopperGolemsStartNoPathOfTheirOwnSuchAsAStrollOrAPanic() {
    CopperGolem golem = bound(CopperGolem.class, GolemBody.COPPER, GolemCoreType.BLANK);
    EntityPathfindEvent stroll = new EntityPathfindEvent(golem, new Location(world, 3, 64, 3), null);
    EntityPathfindEvent panic = new EntityPathfindEvent(golem, new Location(world, -6, 64, 2), null);

    guard.on(stroll);
    guard.on(panic);

    assertThat(stroll.isCancelled()).isTrue();
    assertThat(panic.isCancelled()).isTrue();
  }

  @Test
  void boundGolemsOfOtherBodiesKeepTheirVanillaPathsForCombat() {
    IronGolem golem = bound(IronGolem.class, GolemBody.IRON, GolemCoreType.BLANK);
    EntityPathfindEvent chase = new EntityPathfindEvent(golem, new Location(world, 3, 64, 3), null);

    guard.on(chase);

    assertThat(chase.isCancelled()).isFalse();
  }

  private <T extends Mob> T bound(Class<T> type, GolemBody body, GolemCoreType core) {
    T golem = GolemTestEntities.entity(type);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, core, GolemState.Anchor.of(WORLD, 0, 64, 0))).write(golem);
    return golem;
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getWorld()).thenReturn(world);
    return block;
  }
}
