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
  void stayWalksBackToItsSpotAfterBeingPushedAway() {
    assertThat(step(senses(GolemMode.STAY).anchor(6D))).isEqualTo(decision(GolemBrainState.STAY, Movement.ANCHOR));
    assertThat(step(senses(GolemMode.STAY).anchor(2D))).isEqualTo(decision(GolemBrainState.STAY, Movement.ANCHOR));
    assertThat(step(senses(GolemMode.STAY).anchor(1.4D))).isEqualTo(decision(GolemBrainState.STAY, Movement.STOP));
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

    assertThat(step(senses(GolemMode.HOME).home(33D).target())).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isTrue();
    assertThat(step(senses(GolemMode.HOME).home(20D).target())).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isTrue();

    assertThat(step(senses(GolemMode.HOME).home(16D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(machine.combatSuppressed()).isFalse();
    assertThat(step(senses(GolemMode.HOME).home(15D).target()).state()).isEqualTo(GolemBrainState.COMBAT);
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
  void workWandersNearHomeOnAFixedInterval() {
    assertThat(step(senses(GolemMode.WORK).home(1D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.WANDER));
    now += GolemBrainMachine.WANDER_INTERVAL_MILLIS - 1L;
    assertThat(step(senses(GolemMode.WORK).home(3D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
    now += 1L;
    assertThat(step(senses(GolemMode.WORK).home(3D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.WANDER));
  }

  @Test
  void workWaitsIdleReturnSecondsAwayFromHomeBeforeWalkingBack() {
    step(senses(GolemMode.WORK).home(10D).target());

    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
    now += SETTINGS.idleReturnMillis() - 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
    now += 1L;
    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.WORK).home(4D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
    assertThat(step(senses(GolemMode.WORK).home(1.4D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.STOP));
    assertThat(step(senses(GolemMode.WORK).home(1.4D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.WANDER));
  }

  @Test
  void workIdleTimerRestartsWhenCombatInterruptsTheWait() {
    step(senses(GolemMode.WORK).home(10D));
    now += SETTINGS.idleReturnMillis() - 1_000L;
    step(senses(GolemMode.WORK).home(10D).target());
    now += 1_000L;

    assertThat(step(senses(GolemMode.WORK).home(10D))).isEqualTo(decision(GolemBrainState.IDLE, Movement.NONE));
  }

  @Test
  void workReturnsImmediatelyFromBeyondTheLeash() {
    assertThat(step(senses(GolemMode.WORK).home(33D))).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
  }

  @Test
  void idleReturnSecondsComeFromSettings() {
    GolemBrainSettings instant = new GolemBrainSettings(5, 32D, 32D, 0, 10);

    assertThat(machine.decide(senses(GolemMode.WORK).home(10D).build(now), instant)).isEqualTo(decision(GolemBrainState.HOME, Movement.HOME));
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
    return new Decision(state, movement);
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

    private SensesBuilder target() {
      target = true;
      return this;
    }

    private Senses build(long nowMillis) {
      return new Senses(nowMillis, mode, ownerDistance, ownerGrounded, homeDistance, anchorDistance, target);
    }
  }
}
