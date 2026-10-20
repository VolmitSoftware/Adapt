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
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GolemBrainTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

  private final AtomicReference<GolemBrainSettings> settings = new AtomicReference<>(new GolemBrainSettings(5, 32D, 32D, 8, 10));
  private final RecordingController controller = new RecordingController();
  private final ScriptedPolicy policy = new ScriptedPolicy();
  private FakeGolemPlatform platform;
  private World world;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    policy.clock = () -> platform.now;
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
    assertThat(controller.target).isSameAs(zombie);
    assertThat(controller.calls).containsSubsequence("target:zombie", "stop");

    controller.calls.clear();
    policy.holding = false;
    policy.next = null;
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(controller.target).isNull();
    assertThat(controller.calls).containsSubsequence("target:null", "move:10.5,64.0,0.5");
  }

  @Test
  void aCommandedTargetPreemptsThePolicyUntilItsHoldEnds() {
    owner(at(10, 64, 0));
    LivingEntity chosen = mock(Zombie.class);
    LivingEntity commanded = live(Zombie.class);
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    policy.next = chosen;
    policy.holding = true;
    brain.start();
    brain.command(commanded, platform.now + 20_000L);

    platform.runDelayed();

    assertThat(controller.target).isSameAs(commanded);
    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(policy.selections).isZero();

    platform.now += 19_999L;
    platform.runDelayed();
    assertThat(controller.target).isSameAs(commanded);

    platform.now += 1L;
    platform.runDelayed();
    assertThat(controller.target).isSameAs(chosen);
  }

  @Test
  void aCommandEndsWhenItsTargetDies() {
    owner(at(10, 64, 0));
    LivingEntity commanded = live(Zombie.class);
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    brain.command(commanded, platform.now + 20_000L);
    platform.runDelayed();
    assertThat(controller.target).isSameAs(commanded);

    lenient().when(commanded.isDead()).thenReturn(true);
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(controller.target).isNull();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
  }

  @Test
  void aCommandEndsWhenTheGolemBreaksItsLeash() {
    owner(at(0, 64, 0));
    AtomicReference<Location> position = new AtomicReference<>(at(40, 64, 0));
    LivingEntity commanded = live(Zombie.class);
    GolemBrain brain = brain(golem(IronGolem.class, position), GolemBody.IRON);
    brain.start();
    brain.command(commanded, platform.now + 20_000L);

    platform.runDelayed();
    position.set(at(1, 64, 0));
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(controller.target).isNull();
  }

  @Test
  void clearingACommandEndsTheSicAndDropsItsTarget() {
    owner(at(10, 64, 0));
    LivingEntity commanded = live(Zombie.class);
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    brain.command(commanded, platform.now + 20_000L);
    platform.runDelayed();
    assertThat(controller.target).isSameAs(commanded);

    brain.clearCommand();
    platform.now += 1_000L;
    platform.runDelayed();

    assertThat(controller.target).isNull();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
  }

  @Test
  void bodiesThatCannotFightIgnoreCommands() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(CopperGolem.class, at(0, 64, 0)), GolemBody.COPPER);
    brain.start();
    brain.command(live(Zombie.class), platform.now + 20_000L);

    platform.runDelayed();

    assertThat(controller.target).isNull();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
  }

  @Test
  void combatOffBlocksCompanionTargetingRetaliationAndTheCombatState() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.handle().setCombat(false);
    policy.next = live(Zombie.class);
    policy.holding = true;
    brain.start();

    pulseFor(3_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(controller.target).isNull();
    assertThat(policy.selections).isZero();
    assertThat(policy.ticks).isZero();
    assertThat(controller.calls).doesNotContain("target:zombie");
  }

  @Test
  void combatOffIgnoresACommandedTarget() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.handle().setCombat(false);
    brain.start();
    brain.command(live(Zombie.class), platform.now + 20_000L);

    pulseFor(1_000L);

    assertThat(controller.target).isNull();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
  }

  @Test
  void combatOffNeverStopsAWorkingGolemForAFight() {
    owner(at(0, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(2, 64, 1)), GolemBody.IRON);
    brain.handle().setMode(GolemMode.WORK);
    brain.handle().setCombat(false);
    policy.next = live(Zombie.class);
    policy.holding = true;
    brain.start();

    pulseFor(3_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
    assertThat(controller.target).isNull();
  }

  @Test
  void aWorkingGolemWithCombatOnStillFightsATarget() {
    owner(at(0, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(2, 64, 1)), GolemBody.IRON);
    brain.handle().setMode(GolemMode.WORK);
    LivingEntity zombie = live(Zombie.class);
    policy.next = zombie;
    policy.holding = true;
    brain.start();

    pulseFor(1_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(controller.target).isSameAs(zombie);
  }

  @Test
  void switchingCombatOffDropsTheTargetAndDetachesTheAttackGoalsOnce() {
    owner(at(10, 64, 0));
    LivingEntity zombie = live(Zombie.class);
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    lenient().when(golem.getTarget()).thenReturn(zombie);
    GolemBrain brain = brain(golem, GolemBody.IRON);
    policy.next = zombie;
    policy.holding = true;
    brain.start();
    pulseFor(1_000L);
    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(controller.target).isSameAs(zombie);
    assertThat(controller.combat).isTrue();
    controller.calls.clear();

    brain.handle().setCombat(false);
    pulseFor(2_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(controller.target).isNull();
    assertThat(controller.combat).isFalse();
    assertThat(controller.calls).containsOnlyOnce("combat:false");
    assertThat(controller.calls).doesNotContain("combat:true");
    verify(golem).setTarget(null);
  }

  @Test
  void switchingCombatBackOnReattachesTheAttackGoalsAndFightsAgain() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.handle().setCombat(false);
    LivingEntity zombie = live(Zombie.class);
    policy.next = zombie;
    policy.holding = true;
    brain.start();
    pulseFor(1_000L);
    assertThat(controller.combat).isFalse();
    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);

    brain.handle().setCombat(true);
    pulseFor(1_000L);

    assertThat(controller.combat).isTrue();
    assertThat(controller.calls).containsSubsequence("combat:false", "combat:true");
    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(controller.target).isSameAs(zombie);
  }

  @Test
  void aSleepingBrainAppliesAnOffChangedWhileAsleepWhenItWakes() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.start();
    pulseFor(500L);
    brain.sleep();

    brain.handle().setCombat(false);
    brain.start();
    pulseFor(500L);

    assertThat(controller.combat).isFalse();
  }

  @Test
  void aGuardianKeepsFightingEvenWhenItsStoredCombatIsOff() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON, GolemCoreType.GUARDIAN);
    brain.handle().state().setCombatOff(true);
    LivingEntity zombie = live(Zombie.class);
    policy.next = zombie;
    policy.holding = true;
    brain.start();

    pulseFor(1_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(controller.target).isSameAs(zombie);
    assertThat(controller.calls).doesNotContain("combat:false");
  }

  @Test
  void combatOffIsHarmlessForACopperGolem() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(CopperGolem.class, at(0, 64, 0)), GolemBody.COPPER);
    brain.handle().setCombat(false);
    brain.start();

    pulseFor(1_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(controller.target).isNull();
    assertThat(policy.selections).isZero();
  }

  @Test
  void detachingTheBrainReleasesTheControllerWithCombatBackOn() {
    owner(at(10, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    brain.handle().setCombat(false);
    brain.start();
    pulseFor(500L);
    assertThat(controller.combat).isFalse();

    brain.detach();
    brain.start();
    pulseFor(500L);

    assertThat(controller.calls).containsSubsequence("combat:false", "release", "attach", "combat:false");
  }

  @Test
  void aCommandHoldsBeforeItsDeadlineWhileTheTargetLivesInTheGolemsWorld() {
    assertThat(GolemBrain.commandHolds(1_999L, 2_000L, true, true)).isTrue();
    assertThat(GolemBrain.commandHolds(2_000L, 2_000L, true, true)).isFalse();
    assertThat(GolemBrain.commandHolds(1_000L, 2_000L, false, true)).isFalse();
    assertThat(GolemBrain.commandHolds(1_000L, 2_000L, true, false)).isFalse();
  }

  @Test
  void followingGolemsInAFormationWalkToTheirRingSlotInsteadOfTheOwner() {
    owner(at(10, 64, 0));
    GolemFormation.Formation ring = (handle, ownerAt) -> ownerAt.clone().add(2.5D, 0D, 0D);
    GolemFormation.register(ring);
    try {
      GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
      brain.start();

      platform.runDelayed();

      assertThat(controller.calls).contains("move:13.0,64.0,0.5");
      assertThat(controller.calls).doesNotContain("move:10.5,64.0,0.5");
    } finally {
      GolemFormation.unregister(ring);
    }
  }

  @Test
  void onlyFollowingGolemsAskTheFormationForASlot() {
    owner(at(10, 64, 0));
    List<GolemHandle> asked = new ArrayList<>();
    GolemFormation.Formation ring = (handle, ownerAt) -> {
      asked.add(handle);
      return ownerAt.clone();
    };
    GolemFormation.register(ring);
    try {
      GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
      brain.handle().setMode(GolemMode.HOME);
      brain.start();

      platform.runDelayed();

      assertThat(asked).isEmpty();
    } finally {
      GolemFormation.unregister(ring);
    }
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
  void policiesThatAskBackOffIdleScansToTwoSecondsAndAnAlertScansAtOnce() {
    policy.backoff = true;
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    long start = platform.now;
    brain.start();

    pulseFor(6_000L);

    assertThat(policy.selectedAt).extracting(at -> at - start).containsExactly(0L, 500L, 1_500L, 3_500L, 5_500L);

    brain.alert();
    pulseFor(1_000L);

    assertThat(policy.selectedAt).extracting(at -> at - start).containsExactly(0L, 500L, 1_500L, 3_500L, 5_500L, 6_000L, 6_500L);
  }

  @Test
  void aScanThatFindsATargetAfterBackingOffScansAgainHalfASecondLater() {
    policy.backoff = true;
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    long start = platform.now;
    brain.start();
    pulseFor(6_000L);

    policy.next = mock(Zombie.class);
    pulseFor(1_750L);
    policy.next = null;
    pulseFor(1_000L);

    assertThat(policy.selectedAt).extracting(at -> at - start).containsExactly(0L, 500L, 1_500L, 3_500L, 5_500L, 7_500L, 8_000L, 8_500L);
  }

  @Test
  void policiesWithoutBackoffKeepScanningEveryHalfSecond() {
    GolemBrain brain = brain(golem(IronGolem.class, at(0, 64, 0)), GolemBody.IRON);
    long start = platform.now;
    brain.start();

    pulseFor(2_000L);

    assertThat(policy.selectedAt).extracting(at -> at - start).containsExactly(0L, 500L, 1_000L, 1_500L);
  }

  @Test
  void theStayAnchorIsTheSpotWhereStayBeganUntilTheModeChanges() {
    AtomicReference<Location> position = new AtomicReference<>(at(0, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, position), GolemBody.IRON);
    brain.handle().setMode(GolemMode.STAY);
    brain.start();

    platform.runDelayed();
    position.set(at(5, 64, 0));
    platform.runDelayed();

    assertThat(brain.stayAnchor()).hasValueSatisfying(anchor -> assertThat(anchor.getX()).isEqualTo(0.5D));

    brain.handle().setMode(GolemMode.HOME);
    platform.runDelayed();

    assertThat(brain.stayAnchor()).isEmpty();
  }

  @Test
  void aStayGolemPushedOffItsSpotIssuesNoMovement() {
    AtomicReference<Location> position = new AtomicReference<>(at(0, 64, 0));
    GolemBrain brain = brain(golem(IronGolem.class, position), GolemBody.IRON);
    brain.handle().setMode(GolemMode.STAY);
    brain.start();

    platform.runDelayed();
    position.set(at(6, 64, 0));
    pulseFor(30_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.STAY);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:"));
  }

  @Test
  void aWorkingGolemWithNothingToDoNearHomeIssuesNoMovement() {
    GolemBrain brain = brain(golem(CopperGolem.class, at(2, 64, 1)), GolemBody.COPPER);
    brain.handle().setMode(GolemMode.WORK);
    brain.start();

    pulseFor(30_000L);

    assertThat(brain.state()).isEqualTo(GolemBrainState.SEEK);
    assertThat(controller.calls).noneMatch(call -> call.startsWith("move:"));
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

  @Test
  void regenWaitsOneIntervalThenHealsOnTheMenderCadence() {
    owner(at(3, 64, 0));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    RecordingMender mender = new RecordingMender();
    mender.regenAmount = 0.5D;
    GolemMend.register(mender);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();

      platform.runDelayed();
      assertThat(platform.heals).isEmpty();
      platform.now += 4_999L;
      platform.runDelayed();
      assertThat(platform.heals).isEmpty();
      platform.now += 1L;
      platform.runDelayed();
      assertThat(platform.heals).containsExactly(0.5D);
      platform.now += 250L;
      platform.runDelayed();
      assertThat(platform.heals).containsExactly(0.5D);
      platform.now += 5_000L;
      platform.runDelayed();
      assertThat(platform.heals).containsExactly(0.5D, 0.5D);
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void regenSeesTheOwnerAndHomeDistancesAndWhetherTheGolemIsIdle() {
    owner(at(3, 64, 4));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    RecordingMender mender = new RecordingMender();
    mender.intervalMillis = 1_000L;
    GolemMend.register(mender);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();
      platform.runDelayed();
      platform.now += 1_000L;
      platform.runDelayed();

      assertThat(mender.senses).singleElement().satisfies(senses -> {
        assertThat(senses.ownerDistance()).isEqualTo(5D);
        assertThat(senses.homeDistance()).isZero();
        assertThat(senses.idle()).isTrue();
      });

      policy.next = mock(Zombie.class);
      policy.holding = true;
      platform.now += 1_000L;
      platform.runDelayed();

      assertThat(mender.senses).hasSize(2);
      assertThat(mender.senses.get(1).idle()).isFalse();
      assertThat(platform.heals).isEmpty();
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void anOfflineOwnerLeavesNoOwnerDistance() {
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    RecordingMender mender = new RecordingMender();
    mender.intervalMillis = 1_000L;
    GolemMend.register(mender);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();
      platform.runDelayed();
      platform.now += 1_000L;
      platform.runDelayed();

      assertThat(mender.senses).singleElement().satisfies(senses -> assertThat(senses.ownerDistance()).isNaN());
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void aSleepingBrainNeverRegensAndNoMenderMeansNoRegen() {
    owner(at(1, 64, 0));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    GolemBrain brain = brain(golem, GolemBody.IRON);
    brain.start();
    platform.runDelayed();
    platform.now += 60_000L;
    platform.runDelayed();
    assertThat(platform.heals).isEmpty();

    RecordingMender mender = new RecordingMender();
    mender.regenAmount = 1D;
    mender.intervalMillis = 1_000L;
    GolemMend.register(mender);
    try {
      brain.sleep();
      platform.now += 60_000L;
      platform.runDelayed();
      platform.runDelayed();

      assertThat(mender.senses).isEmpty();
      assertThat(platform.heals).isEmpty();
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void aFailingMenderHealsNothingAndLeavesThePulseRunning() {
    owner(at(1, 64, 0));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    GolemMend.Mender failing = failingMender(false);
    GolemMend.register(failing);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();
      platform.runDelayed();
      platform.now += 1_000L;
      platform.runDelayed();
      platform.now += 1_000L;
      platform.runDelayed();

      assertThat(platform.heals).isEmpty();
      assertThat(GolemMend.regen(brain.handle(), new GolemMend.RegenSenses(1D, 1D, true))).isZero();
      assertThat(controller.calls.stream().filter("tick"::equals).count()).isEqualTo(3L);
      assertThat(platform.delayed).singleElement();
    } finally {
      GolemMend.unregister(failing);
    }
  }

  @Test
  void aMenderWhoseIntervalFailsTurnsRegenOff() {
    owner(at(1, 64, 0));
    IronGolem golem = golem(IronGolem.class, at(0, 64, 0));
    GolemMend.Mender failing = failingMender(true);
    GolemMend.register(failing);
    try {
      GolemBrain brain = brain(golem, GolemBody.IRON);
      brain.start();
      platform.runDelayed();
      platform.now += 60_000L;
      platform.runDelayed();

      assertThat(GolemMend.regenIntervalMillis()).isZero();
      assertThat(platform.heals).isEmpty();
    } finally {
      GolemMend.unregister(failing);
    }
  }

  private static GolemMend.Mender failingMender(boolean intervalFails) {
    return new GolemMend.Mender() {
      @Override
      public long regenIntervalMillis() {
        if (intervalFails) {
          throw new IllegalStateException("interval unavailable");
        }
        return 1_000L;
      }

      @Override
      public double regen(GolemHandle handle, GolemMend.RegenSenses senses) {
        throw new IllegalStateException("regen unavailable");
      }

      @Override
      public boolean shatters(GolemHandle handle) {
        return false;
      }

      @Override
      public void shattered(GolemHandle handle, Location at) {
      }
    };
  }

  private void pulseFor(long millis) {
    long end = platform.now + millis;
    while (platform.now < end) {
      platform.runDelayed();
      platform.now += 250L;
    }
  }

  private GolemBrain brain(Mob golem, GolemBody body) {
    return brain(golem, body, GolemCoreType.BLANK);
  }

  private GolemBrain brain(Mob golem, GolemBody body, GolemCoreType slotted) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, slotted,
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

  private <T extends LivingEntity> T live(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    return entity;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private static final class RecordingController implements GolemController {
    private final List<String> calls = new ArrayList<>();
    private LivingEntity target;
    private boolean combat = true;
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
      this.target = target;
      calls.add(target == null ? "target:null" : "target:zombie");
    }

    @Override
    public void setCombat(boolean enabled) {
      combat = enabled;
      calls.add("combat:" + enabled);
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
    private boolean backoff;
    private LongSupplier clock;
    private final List<Long> selectedAt = new ArrayList<>();

    @Override
    public boolean idleBackoff() {
      return backoff;
    }

    @Override
    public void tick(GolemHandle golem, Player owner) {
      ticks++;
      tickedOwner = owner;
    }

    @Override
    public LivingEntity select(GolemHandle golem, Player owner) {
      selections++;
      selectedAt.add(clock.getAsLong());
      return next;
    }

    @Override
    public boolean holds(GolemHandle golem, Player owner, LivingEntity target) {
      return holding;
    }
  }
}
