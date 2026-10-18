package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemBrainsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

  private final List<String> calls = new ArrayList<>();
  private FakeGolemPlatform platform;
  private World world;
  private boolean steerable = true;
  private GolemBrains brains;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.randomUUID());
    GolemBrain.Context context = new GolemBrain.Context(platform, GolemBrainSettings::defaults, core -> null, GolemWorkSettings::defaults,
        world -> new GolemTaskBoard(world, key -> Optional.empty()));
    brains = new GolemBrains(context, handle -> steerable ? Optional.of(new LoggingController(handle)) : Optional.empty());
  }

  @Test
  void awakeStartsOneBrainPerHandle() {
    GolemHandle handle = handle();

    brains.awake(handle);
    brains.awake(handle);

    assertThat(brains.brain(handle.entity())).hasValueSatisfying(brain -> assertThat(brain.isRunning()).isTrue());
    assertThat(calls).containsExactly("attach:" + handle.id());
  }

  @Test
  void asleepStopsTheBrainButKeepsItsControllerAttached() {
    GolemHandle handle = handle();
    brains.awake(handle);

    brains.asleep(handle);
    brains.awake(handle);

    assertThat(calls).containsExactly("attach:" + handle.id(), "target:" + handle.id(), "stop:" + handle.id());
    assertThat(brains.brain(handle.entity())).hasValueSatisfying(brain -> assertThat(brain.isRunning()).isTrue());
  }

  @Test
  void detachedReleasesAndForgetsTheBrain() {
    GolemHandle handle = handle();
    brains.awake(handle);

    brains.detached(handle);

    assertThat(calls).containsExactly("attach:" + handle.id(), "release:" + handle.id());
    assertThat(brains.brain(handle.entity())).isEmpty();
  }

  @Test
  void aNewHandleForTheSameEntityReplacesTheOldBrain() {
    IronGolem golem = golem();
    GolemHandle first = handle(golem);
    GolemHandle second = handle(golem);
    brains.awake(first);

    brains.awake(second);
    brains.detached(first);

    assertThat(calls).containsExactly("attach:" + first.id(), "release:" + first.id(), "attach:" + second.id());
    assertThat(brains.brain(golem)).hasValueSatisfying(brain -> assertThat(brain.handle()).isSameAs(second));
  }

  @Test
  void bodiesWithoutAControllerGetNoBrain() {
    steerable = false;
    GolemHandle handle = handle();

    brains.awake(handle);
    brains.asleep(handle);
    brains.detached(handle);

    assertThat(brains.brain(handle.entity())).isEmpty();
    assertThat(calls).isEmpty();
  }

  private GolemHandle handle() {
    return handle(golem());
  }

  private GolemHandle handle(IronGolem golem) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(world.getUID(), 0, 64, 0)));
    return new GolemHandle(golem, state, platform);
  }

  private IronGolem golem() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    lenient().when(golem.isValid()).thenReturn(true);
    return golem;
  }

  private final class LoggingController implements GolemController {
    private final GolemHandle handle;

    private LoggingController(GolemHandle handle) {
      this.handle = handle;
    }

    @Override
    public void attach() {
      calls.add("attach:" + handle.id());
    }

    @Override
    public void moveTo(Location destination, double speed) {
      calls.add("move:" + handle.id());
    }

    @Override
    public void stop() {
      calls.add("stop:" + handle.id());
    }

    @Override
    public void setTarget(LivingEntity target) {
      calls.add("target:" + handle.id());
    }

    @Override
    public void interact(Block container, int ticks) {
      calls.add("interact:" + handle.id());
    }

    @Override
    public boolean interacting(Block block) {
      return false;
    }

    @Override
    public void tick() {
      calls.add("tick:" + handle.id());
    }

    @Override
    public void release() {
      calls.add("release:" + handle.id());
    }
  }
}
