package art.arcane.adapt.api.golem;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CopperTransportGuardTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000402");

  private final World world = mock(World.class);
  private final CopperTransportGuard guard = new CopperTransportGuard();

  @Test
  void unboundCopperGolemsKeepTheirVanillaTransport() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));

    guard.on(event);

    assertThat(event.isAllowed()).isTrue();
  }

  @Test
  void boundCopperGolemsNeverPickVanillaTransportTargetsEvenWhileTheBrainUsesAContainer() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.COPPER, GolemCoreType.SORTER,
        GolemState.Anchor.of(WORLD, 0, 64, 0))).write(golem);
    ItemTransportingEntityValidateTargetEvent first = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));
    ItemTransportingEntityValidateTargetEvent second = new ItemTransportingEntityValidateTargetEvent(golem, block(2, 64, 1));

    guard.on(first);
    guard.on(second);

    assertThat(first.isAllowed()).isFalse();
    assertThat(second.isAllowed()).isFalse();
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
