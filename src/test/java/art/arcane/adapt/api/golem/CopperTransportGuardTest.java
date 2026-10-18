package art.arcane.adapt.api.golem;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class CopperTransportGuardTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000402");

  private final World world = mock(World.class);

  @Test
  void unboundCopperGolemsKeepTheirVanillaTransport() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    CopperTransportGuard guard = new CopperTransportGuard(entity -> Optional.empty());
    ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));

    guard.on(event);

    assertThat(event.isAllowed()).isTrue();
  }

  @Test
  void boundCopperGolemsNeverPickVanillaTransportTargets() {
    CopperGolem golem = bound();
    CopperTransportGuard guard = new CopperTransportGuard(entity -> Optional.empty());
    ItemTransportingEntityValidateTargetEvent event = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));

    guard.on(event);

    assertThat(event.isAllowed()).isFalse();
  }

  @Test
  void theContainerTheBrainIsWorkingIsLeftToTheBrain() {
    CopperGolem golem = bound();
    InteractingController controller = new InteractingController();
    GolemBrain brain = brain(golem, controller);
    CopperTransportGuard guard = new CopperTransportGuard(entity -> entity == golem ? Optional.of(brain) : Optional.empty());
    controller.container = block(1, 64, 1);
    ItemTransportingEntityValidateTargetEvent driven = new ItemTransportingEntityValidateTargetEvent(golem, block(1, 64, 1));
    ItemTransportingEntityValidateTargetEvent other = new ItemTransportingEntityValidateTargetEvent(golem, block(2, 64, 1));

    guard.on(driven);
    guard.on(other);

    assertThat(driven.isAllowed()).isTrue();
    assertThat(other.isAllowed()).isFalse();
  }

  private CopperGolem bound() {
    CopperGolem golem = GolemTestEntities.entity(CopperGolem.class);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.COPPER, GolemCoreType.SORTER,
        GolemState.Anchor.of(WORLD, 0, 64, 0))).write(golem);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    return golem;
  }

  private static GolemBrain brain(CopperGolem golem, GolemController controller) {
    FakeGolemPlatform platform = new FakeGolemPlatform();
    GolemState state = GolemState.read(golem).orElseThrow();
    GolemHandle handle = new GolemHandle(golem, state, platform);
    return new GolemBrain(handle, controller, new GolemBrain.Context(platform, GolemBrainSettings::defaults, core -> null,
        GolemWorkSettings::defaults, id -> new GolemTaskBoard(id, key -> Optional.empty())));
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getWorld()).thenReturn(world);
    return block;
  }

  private static final class InteractingController implements GolemController {
    private Block container;

    @Override
    public void attach() {
    }

    @Override
    public void moveTo(Location destination, double speed) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void setTarget(LivingEntity target) {
    }

    @Override
    public void interact(Block next, int ticks) {
      container = next;
    }

    @Override
    public boolean interacting(Block block) {
      return container != null && container.getX() == block.getX() && container.getY() == block.getY() && container.getZ() == block.getZ();
    }

    @Override
    public void tick() {
    }

    @Override
    public void release() {
    }
  }
}
