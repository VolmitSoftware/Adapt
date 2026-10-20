package art.arcane.adapt.api.golem;

import org.bukkit.entity.IronGolem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemCombatTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

  private final FakeMastery mastery = new FakeMastery();

  @BeforeEach
  void setUp() {
    GolemMastery.register(mastery);
  }

  @AfterEach
  void tearDown() {
    GolemMastery.unregister(mastery);
  }

  @Test
  void aNewGolemFightsAndTheOwnerMayTurnCombatOffAndOn() {
    IronGolem body = GolemTestEntities.entity(IronGolem.class);
    GolemHandle handle = handle(body, GolemCoreType.GATHERER);

    assertThat(GolemCombat.enabled(handle)).isTrue();
    assertThat(GolemCombat.locked(handle)).isFalse();

    assertThat(handle.setCombat(false)).isTrue();

    assertThat(GolemCombat.enabled(handle)).isFalse();
    assertThat(handle.state().combatOff()).isTrue();
    assertThat(GolemState.read(body).orElseThrow().combatOff()).isTrue();

    assertThat(handle.setCombat(true)).isTrue();

    assertThat(GolemCombat.enabled(handle)).isTrue();
    assertThat(GolemState.read(body).orElseThrow().combatOff()).isFalse();
  }

  @Test
  void aCompanionMayTurnCombatOff() {
    GolemHandle handle = handle(GolemTestEntities.entity(IronGolem.class), GolemCoreType.BLANK);

    assertThat(handle.setCombat(false)).isTrue();

    assertThat(GolemCombat.enabled(handle)).isFalse();
  }

  @Test
  void aGuardianCoreLocksCombatOn() {
    IronGolem body = GolemTestEntities.entity(IronGolem.class);
    GolemHandle handle = handle(body, GolemCoreType.GUARDIAN);

    assertThat(GolemCombat.locked(handle)).isTrue();
    assertThat(handle.setCombat(false)).isFalse();

    assertThat(GolemCombat.enabled(handle)).isTrue();
    assertThat(handle.state().combatOff()).isFalse();
    assertThat(GolemState.read(body)).isEmpty();
  }

  @Test
  void aMasterworkWithGuardianAsEitherCoreLocksCombatOn() {
    mastery.perks = new GolemMastery.Perks(0, true, false, 0D);
    GolemHandle first = masterwork(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER);
    GolemHandle second = masterwork(GolemCoreType.GATHERER, GolemCoreType.GUARDIAN);

    assertThat(GolemCombat.locked(first)).isTrue();
    assertThat(GolemCombat.locked(second)).isTrue();
    assertThat(first.setCombat(false)).isFalse();
    assertThat(second.setCombat(false)).isFalse();
    assertThat(GolemCombat.enabled(first)).isTrue();
    assertThat(GolemCombat.enabled(second)).isTrue();
    assertThat(second.state().combatOff()).isFalse();
  }

  @Test
  void aSecondGuardianCoreOnlyLocksWhileTheMasterworkKeepsDualCores() {
    mastery.perks = GolemMastery.Perks.NONE;
    GolemHandle handle = masterwork(GolemCoreType.GATHERER, GolemCoreType.GUARDIAN);

    assertThat(GolemCombat.locked(handle)).isFalse();
    assertThat(handle.setCombat(false)).isTrue();
    assertThat(GolemCombat.enabled(handle)).isFalse();
  }

  @Test
  void slottingAGuardianCoreIntoACombatOffGolemTurnsCombatBackOn() {
    GolemHandle handle = handle(GolemTestEntities.entity(IronGolem.class), GolemCoreType.GATHERER);
    handle.setCombat(false);

    handle.setCores(List.of(GolemCoreType.GUARDIAN));

    assertThat(GolemCombat.locked(handle)).isTrue();
    assertThat(handle.state().combatOff()).isFalse();
    handle.setCores(List.of(GolemCoreType.GATHERER));
    assertThat(GolemCombat.enabled(handle)).isTrue();
  }

  @Test
  void aStoredOffStateNeverTurnsAGuardianOff() {
    GolemHandle handle = handle(GolemTestEntities.entity(IronGolem.class), GolemCoreType.GUARDIAN);
    handle.state().setCombatOff(true);

    assertThat(GolemCombat.enabled(handle)).isTrue();
  }

  private GolemHandle masterwork(GolemCoreType first, GolemCoreType second) {
    GolemHandle handle = handle(GolemTestEntities.entity(IronGolem.class), first);
    handle.state().setCores(List.of(first, second));
    handle.state().setMasterwork(true);
    return handle;
  }

  private static GolemHandle handle(IronGolem body, GolemCoreType core) {
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, core, GolemState.Anchor.of(WORLD, 0, 64, 0)));
    return new GolemHandle(body, state, new FakeGolemPlatform());
  }
}
