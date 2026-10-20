package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.GolemBrainMachine.Decision;
import art.arcane.adapt.api.golem.GolemBrainMachine.Movement;
import art.arcane.adapt.api.golem.GolemBrainMachine.Senses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemBrainMachineTest {
  private static final double NONE = Double.NaN;
  private static final GolemBrainSettings SETTINGS = GolemBrainSettings.defaults();

  private final GolemBrainMachine machine = new GolemBrainMachine();
  private long now = 10_000L;

  @Test
  void followStartsPathingOnlyBeyondFourBlocksAndStopsCloseToTheOwner() {
    assertThat(follow(3.9D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.NONE));
    assertThat(follow(4.1D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.OWNER));
    assertThat(follow(3.0D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.OWNER));
    assertThat(follow(2.4D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.STOP));
    assertThat(follow(2.4D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.NONE));
    assertThat(follow(3.9D)).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.NONE));
  }

  @Test
  void followTeleportsBeyondTheTeleportDistanceOnlyWhileTheOwnerStandsOnGround() {
    assertThat(step(senses(GolemMode.FOLLOW).owner(33D, false))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.OWNER));
    assertThat(step(senses(GolemMode.FOLLOW).owner(33D, true))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.TELEPORT));
    assertThat(step(senses(GolemMode.FOLLOW).owner(32D, true))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.OWNER));
  }

  @Test
  void followTeleportWaitsForItsCooldownBeforeRetrying() {
    assertThat(step(senses(GolemMode.FOLLOW).owner(40D, true))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.TELEPORT));
    now += GolemBrainMachine.TELEPORT_COOLDOWN_MILLIS - 1L;
    assertThat(step(senses(GolemMode.FOLLOW).owner(40D, true))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.OWNER));
    now += 1L;
    assertThat(step(senses(GolemMode.FOLLOW).owner(40D, true))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.TELEPORT));
  }

  @Test
  void followTeleportDistanceComesFromSettings() {
    GolemBrainSettings near = new GolemBrainSettings(5, 12D, 32D, 8, 10);

    assertThat(machine.decide(senses(GolemMode.FOLLOW).owner(13D, true).build(now), near))
        .isEqualTo(decision(GolemBrainState.FOLLOW, Movement.TELEPORT));
  }

  @Test
  void aFormationSlotReplacesTheOwnerAsTheFollowGoal() {
    assertThat(step(senses(GolemMode.FOLLOW).owner(3D, true).slot(1.6D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.SLOT));
    assertThat(step(senses(GolemMode.FOLLOW).owner(3D, true).slot(1.0D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.SLOT));
    assertThat(step(senses(GolemMode.FOLLOW).owner(3D, true).slot(0.7D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.STOP));
    assertThat(step(senses(GolemMode.FOLLOW).owner(3D, true).slot(1.4D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.NONE));
    assertThat(step(senses(GolemMode.FOLLOW).owner(6D, true).slot(1.4D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.NONE));
  }

  @Test
  void aDistantOwnerStillTeleportsAGolemThatHasAFormationSlot() {
    assertThat(step(senses(GolemMode.FOLLOW).owner(40D, true).slot(38D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.TELEPORT));
    assertThat(step(senses(GolemMode.FOLLOW).owner(20D, false).slot(18D))).isEqualTo(decision(GolemBrainState.FOLLOW, Movement.SLOT));
  }

  @Test
  void followWithoutAReachableOwnerIdlesInPlace() {
    follow(10D);

    assertThat(follow(NONE)).isEqualTo(decision(GolemBrainState.IDLE, Movement.STOP));
    assertThat(follow(NONE)).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
  }

  @Test
  void stayStopsAndHoldsItsSpot() {
    follow(10D);

    assertThat(step(senses(GolemMode.STAY).anchor(0D))).isEqualTo(decision(GolemBrainState.STAY, Movement.STOP));
    assertThat(step(senses(GolemMode.STAY).anchor(0.5D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
    assertThat(step(senses(GolemMode.STAY).anchor(2.4D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
  }

  @Test
  void stayNeverWalksBackToItsSpotOnItsOwn() {
    for (double anchor : new double[] {6D, 20D, 31D, 2D}) {
      for (int pulse = 0; pulse < 40; pulse++) {
        now += 250L;
        assertThat(step(senses(GolemMode.STAY).anchor(anchor))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
      }
    }
  }

  @Test
  void stayStopsARunningPathWhereverTheGolemStands() {
    assertThat(step(senses(GolemMode.HOME).home(20D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));

    assertThat(step(senses(GolemMode.STAY).anchor(6D))).isEqualTo(decision(GolemBrainState.STAY, Movement.STOP));
    assertThat(step(senses(GolemMode.STAY).anchor(6D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
  }

  @Test
  void stayStillRetaliatesAndStandsWhereTheFightEnds() {
    assertThat(step(senses(GolemMode.STAY).anchor(0D).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.NONE));
    assertThat(step(senses(GolemMode.STAY).anchor(9D).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.NONE));

    assertThat(step(senses(GolemMode.STAY).anchor(9D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
  }

  @Test
  void aStayGolemWhoseFightBrokeTheLeashWalksAllTheWayBackToItsSpot() {
    assertThat(step(senses(GolemMode.STAY).anchor(33D).target())).isEqualTo(dropping(GolemBrainState.STAY, Movement.ANCHOR));
    assertThat(step(senses(GolemMode.STAY).anchor(20D))).isEqualTo(dropping(GolemBrainState.STAY, Movement.ANCHOR));
    assertThat(step(senses(GolemMode.STAY).anchor(10D))).isEqualTo(decision(GolemBrainState.STAY, Movement.ANCHOR));
    assertThat(step(senses(GolemMode.STAY).anchor(1.4D))).isEqualTo(decision(GolemBrainState.STAY, Movement.STOP));
    assertThat(step(senses(GolemMode.STAY).anchor(6D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
  }

  @Test
  void aModeChangeEndsAStayGolemsWalkBack() {
    step(senses(GolemMode.STAY).anchor(33D).target());
    step(senses(GolemMode.STAY).anchor(10D));
    step(senses(GolemMode.HOME).home(1D));

    assertThat(step(senses(GolemMode.STAY).anchor(10D))).isEqualTo(decision(GolemBrainState.STAY, Movement.NONE));
  }

  @Test
  void homePathsHomeAndStopsOnArrival() {
    assertThat(step(senses(GolemMode.HOME).home(20D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.HOME).home(1.6D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.HOME).home(1.2D))).isEqualTo(decision(GolemBrainState.HOME, Movement.STOP));
    assertThat(step(senses(GolemMode.HOME).home(2.4D))).isEqualTo(decision(GolemBrainState.HOME, Movement.NONE));
    assertThat(step(senses(GolemMode.HOME).home(2.6D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
  }

  @Test
  void homeInAnotherWorldIdles() {
    assertThat(step(senses(GolemMode.HOME).home(NONE))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
    assertThat(step(senses(GolemMode.WORK).home(NONE))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
  }

  @Test
  void combatPreemptsEveryModeWhileATargetIsHeld() {
    follow(10D);
    assertThat(step(senses(GolemMode.FOLLOW).owner(10D, true).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.STOP));
    assertThat(step(senses(GolemMode.STAY).anchor(5D).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.NONE));
    assertThat(step(senses(GolemMode.HOME).home(10D).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.NONE));
    assertThat(step(senses(GolemMode.WORK).home(10D).target())).isEqualTo(decision(GolemBrainState.COMBAT, Movement.NONE));
    assertThat(step(senses(GolemMode.HOME).home(10D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
  }

  @Test
  void combatBeyondTheLeashDisengagesUntilTheGolemIsBackWithinHalfTheLeash() {
    assertThat(step(senses(GolemMode.HOME).home(31D).target()).state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(machine.combatSuppressed()).isFalse();

    assertThat(step(senses(GolemMode.HOME).home(33D).target())).isEqualTo(dropping(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isTrue();
    assertThat(step(senses(GolemMode.HOME).home(20D).target())).isEqualTo(dropping(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isTrue();

    assertThat(step(senses(GolemMode.HOME).home(16D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isFalse();
    assertThat(step(senses(GolemMode.HOME).home(15D).target()).state()).isEqualTo(GolemBrainState.COMBAT);
  }

  @Test
  void leashBreakDropsEveryTargetEachPulseUntilTheGolemIsBack() {
    for (GolemMode mode : GolemMode.values()) {
      GolemBrainMachine leashed = new GolemBrainMachine();
      assertThat(leashed.decide(leashedSenses(mode, 40D, true), SETTINGS).dropTarget()).as(mode + " break").isTrue();
      assertThat(leashed.decide(leashedSenses(mode, 30D, false), SETTINGS).dropTarget()).as(mode + " returning").isTrue();
      assertThat(leashed.decide(leashedSenses(mode, 10D, false), SETTINGS).dropTarget()).as(mode + " back").isFalse();
      assertThat(leashed.decide(leashedSenses(mode, 10D, true), SETTINGS)).as(mode + " fighting").satisfies(decision -> {
        assertThat(decision.state()).isEqualTo(GolemBrainState.COMBAT);
        assertThat(decision.dropTarget()).isFalse();
      });
    }
  }

  @Test
  void combatLeashFollowsTheModeAnchor() {
    assertThat(step(senses(GolemMode.FOLLOW).owner(33D, false).target()).state()).isEqualTo(GolemBrainState.FOLLOW);
    assertThat(machine.combatSuppressed()).isTrue();

    GolemBrainMachine stay = new GolemBrainMachine();
    stay.decide(senses(GolemMode.STAY).anchor(33D).target().build(now), SETTINGS);
    assertThat(stay.combatSuppressed()).isTrue();

    GolemBrainMachine ownerless = new GolemBrainMachine();
    assertThat(ownerless.decide(senses(GolemMode.FOLLOW).target().build(now), SETTINGS).state()).isEqualTo(GolemBrainState.COMBAT);
    assertThat(ownerless.combatSuppressed()).isFalse();
  }

  @Test
  void combatLeashRadiusComesFromSettings() {
    GolemBrainSettings tight = new GolemBrainSettings(5, 32D, 10D, 8, 10);

    assertThat(machine.decide(senses(GolemMode.HOME).home(11D).target().build(now), tight).state()).isEqualTo(GolemBrainState.HOME);
    assertThat(machine.combatSuppressed()).isTrue();
  }

  @Test
  void anIdleWorkingGolemNearHomeNeverMoves() {
    for (double home : new double[] {0D, 1D, 3D, GolemBrainMachine.NEAR_HOME_DISTANCE}) {
      for (int pulse = 0; pulse < 240; pulse++) {
        now += 250L;
        assertThat(step(senses(GolemMode.WORK).home(home))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
      }
    }
  }

  @Test
  void anIdleWorkingGolemStopsTheMomentItsJobEnds() {
    assertThat(step(senses(GolemMode.WORK).home(40D))).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
    machine.working(now);

    assertThat(step(senses(GolemMode.WORK).home(3D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
  }

  @Test
  void workWaitsIdleReturnSecondsAwayFromHomeBeforeWalkingBack() {
    step(senses(GolemMode.WORK).home(10D).target());

    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
    now += SETTINGS.idleReturnMillis() - 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
    now += 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.WORK).home(4D))).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.WORK).home(1.4D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.STOP));
    for (int pulse = 0; pulse < 240; pulse++) {
      now += 250L;
      assertThat(step(senses(GolemMode.WORK).home(1.4D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
    }
  }

  @Test
  void workIdleTimerRestartsWhenCombatInterruptsTheWait() {
    step(senses(GolemMode.WORK).home(10D));
    now += SETTINGS.idleReturnMillis() - 1_000L;
    step(senses(GolemMode.WORK).home(10D).target());
    now += 1_000L;

    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
  }

  @Test
  void theIdleTimerStartsWhenTheLastJobEnds() {
    step(senses(GolemMode.WORK).home(10D));
    now += SETTINGS.idleReturnMillis() * 2L;
    machine.working(now);

    now += SETTINGS.idleReturnMillis() - 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.SEEK, Movement.NONE));
    now += 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
  }

  @Test
  void workReturnsImmediatelyFromBeyondTheLeash() {
    assertThat(step(senses(GolemMode.WORK).home(33D))).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
  }

  @Test
  void idleReturnSecondsComeFromSettings() {
    GolemBrainSettings instant = new GolemBrainSettings(5, 32D, 32D, 0, 10);

    assertThat(machine.decide(senses(GolemMode.WORK).home(10D).build(now), instant)).isEqualTo(decision(GolemBrainState.RETURN_HOME, Movement.HOME));
  }

  @Test
  void resetForgetsMovementAndLeashState() {
    step(senses(GolemMode.HOME).home(40D).target());
    assertThat(machine.combatSuppressed()).isTrue();

    machine.reset();

    assertThat(machine.combatSuppressed()).isFalse();
    assertThat(machine.state()).isEqualTo(GolemBrainState.IDLE);
    assertThat(step(senses(GolemMode.HOME).home(1D))).isEqualTo(decision(GolemBrainState.HOME, Movement.NONE));
  }

  private Decision follow(double ownerDistance) {
    return step(senses(GolemMode.FOLLOW).owner(ownerDistance, true));
  }

  private Decision step(SensesBuilder senses) {
    return machine.decide(senses.build(now), SETTINGS);
  }

  private static Decision decision(GolemBrainState state, Movement movement) {
    return new Decision(state, movement, false);
  }

  private static Decision dropping(GolemBrainState state, Movement movement) {
    return new Decision(state, movement, true);
  }

  private Senses leashedSenses(GolemMode mode, double distance, boolean target) {
    SensesBuilder senses = senses(mode);
    switch (mode) {
      case FOLLOW -> senses.owner(distance, false);
      case STAY -> senses.anchor(distance);
      case HOME, WORK -> senses.home(distance);
    }
    if (target) {
      senses.target();
    }
    return senses.build(now);
  }

  private static SensesBuilder senses(GolemMode mode) {
    return new SensesBuilder(mode);
  }

  private static final class SensesBuilder {
    private final GolemMode mode;
    private double ownerDistance = Double.NaN;
    private boolean ownerGrounded;
    private double homeDistance = Double.NaN;
    private double anchorDistance = Double.NaN;
    private double slotDistance = Double.NaN;
    private boolean target;

    private SensesBuilder(GolemMode mode) {
      this.mode = mode;
    }

    private SensesBuilder owner(double distance, boolean grounded) {
      ownerDistance = distance;
      ownerGrounded = grounded;
      return this;
    }

    private SensesBuilder home(double distance) {
      homeDistance = distance;
      return this;
    }

    private SensesBuilder anchor(double distance) {
      anchorDistance = distance;
      return this;
    }

    private SensesBuilder slot(double distance) {
      slotDistance = distance;
      return this;
    }

    private SensesBuilder target() {
      target = true;
      return this;
    }

    private Senses build(long nowMillis) {
      return new Senses(nowMillis, mode, ownerDistance, ownerGrounded, homeDistance, anchorDistance, slotDistance, target);
    }
  }
}
