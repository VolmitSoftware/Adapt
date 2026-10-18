package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GolemBrainTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

  private final AtomicReference<GolemBrainSettings> settings = new AtomicReference<>(GolemBrainSettings.defaults());
  private final RecordingController controller = new RecordingController();
  private final ScriptedPolicy policy = new ScriptedPolicy();
  private FakeGolemPlatform platform;
  private World world;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
  }

  @Test
  void startAttachesTheControllerAndSchedulesTheFirstPulse() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);

    brain.start();

    assertThat(controller.calls).containsExactly("attach");
    assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(1));
    assertThat(brain.isRunning()).isTrue();
  }

  @Test
  void pulsesRescheduleThemselvesAtTheBrainInterval() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();

    platform.runDelayed();
    platform.runDelayed();

    assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(5));
  }

  @Test
  void pulsesRecomputeStatsAtMostOncePerSecondSoOwnerLevelChangesApply() {
    platform.join(OWNER, new GolemRoster());
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AtomicReference<Double> bonus = new AtomicReference<>(2D);
    GolemStatSource frame = new GolemStatSource() {
      @Override
      public String id() {
        return "iron_frame";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle handle) {
        return List.of(GolemStatDelta.add(GolemStat.ARMOR, bonus.get()));
      }
    };
    GolemStats.register(frame);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();

      platform.runDelayed();
      assertThat(armor.value()).isEqualTo(2D);

      bonus.set(4D);
      platform.now += 500L;
      platform.runDelayed();
      assertThat(armor.value()).isEqualTo(2D);

      platform.now += 500L;
      platform.runDelayed();
      assertThat(armor.value()).isEqualTo(4D);
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void pulsesSettleColossusGolemsAtTheStatsCadence() {
    platform.join(OWNER, new GolemRoster());
    GolemColossus.Form form = mock(GolemColossus.Form.class);
    GolemColossus.register(form);
    try {
      GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
      GolemHandle handle = brain.handle();
      handle.setColossus(true);
      brain.start();

      platform.runDelayed();
      platform.now += 500L;
      platform.runDelayed();
      verify(form, times(1)).settle(handle);

      platform.now += 500L;
      platform.runDelayed();
      verify(form, times(2)).settle(handle);
    } finally {
      GolemColossus.unregister(form);
    }
  }

  @Test
  void pulsesNeverRevertAGrownGolemWhenNoFormIsRegistered() {
    platform.join(OWNER, new GolemRoster());
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    GolemBrain brain = brain(golem, GolemBody.IRON);
    brain.handle().setColossus(true);
    brain.start();

    platform.runDelayed();
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(brain.handle().state().colossus()).isTrue();
    assertThat(platform.scale(golem)).isEmpty();
  }

  @Test
  void statsRefreshEvenWhenTheRestOfThePulseFails() {
    platform.join(OWNER, new GolemRoster());
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    GolemStatSource frame = new GolemStatSource() {
      @Override
      public String id() {
        return "iron_frame";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle handle) {
        return List.of(GolemStatDelta.add(GolemStat.ARMOR, 2D));
      }
    };
    controller.failTick = true;
    GolemStats.register(frame);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();

      platform.runDelayed();

      assertThat(controller.calls).contains("tick");
      assertThat(armor.value()).isEqualTo(2D);
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void aCopperGolemsPatinaChangeRecomputesItsStatsOnTheNextPulse() {
    platform.join(OWNER, new GolemRoster());
    CopperGolem golem = golem(CopperGolem.class, at(0, 64, 0));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    GolemStatSource patina = new GolemStatSource() {
      @Override
      public String id() {
        return "patina";
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle handle) {
        return List.of(GolemStatDelta.add(GolemStat.ARMOR, handle.patinaStage() * 2D));
      }
    };
    GolemStats.register(patina);
    try {
      GolemBrain brain = brain(golem, GolemBody.COPPER);
      brain.start();
      platform.runDelayed();
      assertThat(armor.value()).isZero();

      platform.patina.put(golem.getUniqueId(), 2);
      platform.now += 250L;
      platform.runDelayed();

      assertThat(brain.handle().patinaStage()).isEqualTo(2);
      assertThat(armor.value()).isEqualTo(4D);
    } finally {
      GolemStats.unregister(patina);
    }
  }

  @Test
  void startingARunningBrainKeepsASinglePulseChain() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);

    brain.start();
    brain.start();

    assertThat(platform.delayed).hasSize(1);
    assertThat(controller.calls).containsExactly("attach");
  }

  @Test
  void sleepCancelsThePendingPulseAndStopsTheBody() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    controller.calls.clear();

    brain.sleep();
    platform.runDelayed();

    assertThat(controller.calls).containsExactly("target:null", "stop");
    assertThat(platform.delayed).isEmpty();
    assertThat(brain.isRunning()).isFalse();
  }

  @Test
  void wakingAfterSleepStartsANewGenerationAndDropsTheOldPulse() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    brain.sleep();
    brain.start();

    platform.runDelayed();

    assertThat(platform.delayed).hasSize(1);
    assertThat(controller.calls).containsOnlyOnce("attach");
  }

  @Test
  void detachReleasesTheControllerAndCancelsThePulse() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    controller.calls.clear();

    brain.detach();
    platform.runDelayed();

    assertThat(controller.calls).containsExactly("release");
    assertThat(platform.delayed).isEmpty();
  }

  @Test
  void detachOffTheGolemThreadOnlyCancelsThePulse() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    controller.calls.clear();
    platform.ownsEntities = false;

    brain.detach();

    assertThat(controller.calls).isEmpty();
    assertThat(brain.isRunning()).isFalse();
  }

  @Test
  void aControllerThatCannotAttachLeavesTheGolemAlone() {
    controller.failAttach = true;
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);

    brain.start();
    brain.detach();

    assertThat(brain.isRunning()).isFalse();
    assertThat(platform.delayed).isEmpty();
    assertThat(controller.calls).containsExactly("attach");
  }

  @Test
  void aFailedReleaseDoesNotEscapeDetach() {
    controller.failRelease = true;
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();

    brain.detach();

    assertThat(controller.calls).containsExactly("attach", "release");
    assertThat(brain.isRunning()).isFalse();
  }

  @Test
  void ownerLookupRetiredBeforeItRunsIsRequestedAgain() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    platform.ownsEntities = false;

    platform.runDelayed();
    assertThat(platform.retirable()).isEqualTo(1);
    platform.retireDelayed();
    platform.runDelayed();

    assertThat(platform.retirable()).isEqualTo(1);
  }

  @Test
  void sleepAndWakeForgetAPendingOwnerLookup() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    platform.ownsEntities = false;
    platform.runDelayed();
    platform.delayed.removeIf(task -> task.retired() != null);

    brain.sleep();
    platform.ownsEntities = true;
    brain.start();
    platform.ownsEntities = false;
    platform.runDelayed();

    assertThat(platform.retirable()).isEqualTo(1);
  }

  @Test
  void leashBreakAlsoClearsAVanillaTarget() {
    LivingEntity zombie = mock(Zombie.class);
    IronGolem golem = golem(IronGolem.class, at(40, 64, 0));
    lenient().when(golem.getTarget()).thenReturn(zombie);
    GolemBrain brain = brain(golem, GolemBody.IRON);
    brain.handle().setMode(GolemMode.HOME);
    policy.next = zombie;
    policy.holding = true;
    brain.start();

    platform.runDelayed();

    verify(golem).setTarget(null);
    assertThat(brain.state()).isEqualTo(GolemBrainState.HOME);
  }

  @Test
  void followModePathsTowardTheOwner() {
    Player owner = owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();

    platform.runDelayed();

    assertThat(controller.calls).contains("move:10.5,64.0,0.5", "tick");
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(owner.getUniqueId()).isEqualTo(OWNER);
  }

  @Test
  void followTeleportKeepsPassengersSuchAsLabelsMounted() {
    owner(at(40, 64, 0));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    GolemBrain brain = brain(golem, GolemBody.IRON);
    brain.start();

    platform.runDelayed();

    verify(golem).teleportAsync(at(40, 64, 0), PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    assertThat(controller.calls).contains("stop");
  }

  @Test
  void companionTargetPreemptsFollowingAndClearsWhenThePolicyLetsGo() {
    owner(at(10, 64, 0));
    LivingEntity zombie = mock(Zombie.class);
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    platform.runDelayed();
    controller.calls.clear();

    policy.next = zombie;
    policy.holding = true;
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(brain.combatTarget()).contains(zombie);
    assertThat(controller.calls).containsSubsequence("target:zombie", "stop");

    controller.calls.clear();
    policy.holding = false;
    policy.next = null;
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(brain.combatTarget()).isEmpty();
    assertThat(controller.calls).containsSubsequence("target:null", "move:10.5,64.0,0.5");
  }

  @Test
  void bodiesThatCannotFightNeverAskThePolicy() {
    owner(at(10, 64, 0));
    policy.next = mock(Zombie.class);
    GolemBrain brain = brain(golem(CopperGolem.class, at(0, 64, 0)), GolemBody.COPPER);
    brain.start();

    platform.runDelayed();

    assertThat(policy.selections).isZero();
    assertThat(policy.ticks).isZero();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
  }

  @Test
  void fightingBodiesTickTheirTargetPolicyOnEveryPulseWithTheOwner() {
    Player owner = owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();

    platform.runDelayed();
    platform.runDelayed();

    assertThat(policy.ticks).isEqualTo(2);
    assertThat(policy.tickedOwner).isSameAs(owner);
  }

  @Test
  void copperPulsesNoSlowerThanItsRepathInterval() {
    settings.set(new GolemBrainSettings(20, 32D, 32D, 8, 10));
    GolemBrain brain = brain(golem(CopperGolem.class, at(0, 64, 0)), GolemBody.COPPER);
    brain.start();

    platform.runDelayed();

    assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(10));
  }

  @Test
  void pulsesKeepTheQuitPositionFresh() {
    AtomicReference<Location> location = new AtomicReference<>(at(0, 64, 0));
    Mob golem = golem(IronGolem.class, location);
    GolemBrain brain = brain(golem, GolemBody.IRON);
    brain.start();

    location.set(at(40, 64, 40));
    platform.runDelayed();

    assertThat(brain.handle().position().chunkKey()).isEqualTo(GolemRoster.chunkKey(2, 2));
  }

  private GolemBrain brain(Mob golem, GolemBody body) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    GolemHandle handle = new GolemHandle(golem, state, platform);
    return new GolemBrain(handle, controller, new GolemBrain.Context(platform, settings::get, core -> policy, GolemWorkSettings::defaults,
        world -> new GolemTaskBoard(world, key -> Optional.empty())));
  }

  private <T extends Mob> T golem(Class<T> type, Location location) {
    return golem(type, new AtomicReference<>(location));
  }

  private <T extends Mob> T golem(Class<T> type, AtomicReference<Location> location) {
    T golem = GolemTestEntities.entity(type);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> location.get().clone());
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    return golem;
  }

  private Player owner(Location location) {
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    lenient().when(owner.isOnline()).thenReturn(true);
    lenient().when(owner.isOnGround()).thenReturn(true);
    lenient().when(owner.getLocation()).thenAnswer(invocation -> location.clone());
    platform.players.put(OWNER, owner);
    return owner;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private static final class RecordingController implements GolemController {
    private final List<String> calls = new ArrayList<>();
    private boolean failAttach;
    private boolean failRelease;
    private boolean failTick;

    @Override
    public void attach() {
      calls.add("attach");
      if (failAttach) {
        throw new IllegalStateException("attach refused");
      }
    }

    @Override
    public void moveTo(Location destination, double speed) {
      calls.add("move:" + destination.getX() + "," + destination.getY() + "," + destination.getZ());
    }

    @Override
    public void stop() {
      calls.add("stop");
    }

    @Override
    public void setTarget(LivingEntity target) {
      calls.add(target == null ? "target:null" : "target:zombie");
    }

    @Override
    public void interact(Block container, int ticks) {
      calls.add("interact");
    }


    @Override
    public void tick() {
      calls.add("tick");
      if (failTick) {
        throw new IllegalStateException("tick refused");
      }
    }

    @Override
    public void release() {
      calls.add("release");
      if (failRelease) {
        throw new IllegalStateException("release refused");
      }
    }
  }

  private static final class ScriptedPolicy implements GolemTargetPolicy {
    private LivingEntity next;
    private boolean holding;
    private int selections;
    private int ticks;
    private Player tickedOwner;

    @Override
    public void tick(GolemHandle golem, Player owner) {
      ticks++;
      tickedOwner = owner;
    }

    @Override
    public LivingEntity select(GolemHandle golem, Player owner) {
      selections++;
      return next;
    }

    @Override
    public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
      return holding;
    }
  }
}
