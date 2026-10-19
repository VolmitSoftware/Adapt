package art.arcane.adapt.api.golem;

import org.bukkit.entity.IronGolem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemMasteryTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final GolemSettings SETTINGS = GolemSettings.defaults();

  private final FakeMastery mastery = new FakeMastery();
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    handle = new GolemHandle(golem, state, new FakeGolemPlatform());
  }

  @AfterEach
  void tearDown() {
    GolemMastery.unregister(mastery);
  }

  @Test
  void onlyARegisteredSourceGivesAMasterworkItsPerks() {
    GolemMastery.Perks full = new GolemMastery.Perks(1, true, true, 0.25D);
    mastery.perks = full;

    assertThat(GolemMastery.perks(handle)).isEqualTo(GolemMastery.Perks.NONE);
    handle.setMasterwork(true);
    assertThat(GolemMastery.perks(handle)).isEqualTo(GolemMastery.Perks.NONE);
    GolemMastery.register(mastery);
    assertThat(GolemMastery.perks(handle)).isEqualTo(full);
    assertThat(mastery.asked).containsExactly(OWNER);
    handle.setMasterwork(false);
    assertThat(GolemMastery.perks(handle)).isEqualTo(GolemMastery.Perks.NONE);
    assertThat(mastery.asked).containsExactly(OWNER);
    assertThat(GolemMastery.unregister(mastery)).isTrue();
    handle.setMasterwork(true);
    assertThat(GolemMastery.perks(handle)).isEqualTo(GolemMastery.Perks.NONE);
  }

  @Test
  void aSecondCoreWorksOnlyWhileItsOwnerHoldsTheDualCorePerk() {
    GolemMastery.register(mastery);
    handle.setCores(List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER));
    handle.setMasterwork(true);

    assertThat(GolemMastery.cores(handle)).containsExactly(GolemCoreType.GUARDIAN);
    mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
    assertThat(GolemMastery.cores(handle)).containsExactly(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER);
    handle.setMasterwork(false);
    assertThat(GolemMastery.cores(handle)).containsExactly(GolemCoreType.GUARDIAN);
    handle.setMasterwork(true);
    GolemMastery.unregister(mastery);
    assertThat(GolemMastery.cores(handle)).containsExactly(GolemCoreType.GUARDIAN);
  }

  @Test
  void aSingleCoreGolemNeverAsksForPerks() {
    GolemMastery.register(mastery);
    handle.setMasterwork(true);

    assertThat(GolemMastery.cores(handle)).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(mastery.asked).isEmpty();
  }

  @Test
  void theCapAndTheHeartChanceFallBackWithoutASourceAndStayInRange() {
    assertThat(GolemMastery.maxMasterworks(OWNER)).isZero();
    assertThat(GolemMastery.heartDropChance()).isEqualTo(GolemMastery.DEFAULT_HEART_DROP_CHANCE);
    assertThat(GolemMastery.coreUnlocked(OWNER, GolemCoreType.LUMBER)).isFalse();

    GolemMastery.register(mastery);
    mastery.maxMasterworks = 2;
    mastery.heartDropChance = 0.4D;
    assertThat(GolemMastery.maxMasterworks(OWNER)).isEqualTo(2);
    assertThat(GolemMastery.heartDropChance()).isEqualTo(0.4D);
    assertThat(GolemMastery.coreUnlocked(OWNER, GolemCoreType.LUMBER)).isTrue();

    mastery.maxMasterworks = -3;
    mastery.heartDropChance = 7D;
    assertThat(GolemMastery.maxMasterworks(OWNER)).isZero();
    assertThat(GolemMastery.heartDropChance()).isEqualTo(1D);
    mastery.heartDropChance = Double.NaN;
    assertThat(GolemMastery.heartDropChance()).isZero();
  }

  @Test
  void perksKeepTheirValuesInRange() {
    GolemMastery.Perks wild = new GolemMastery.Perks(40, true, true, -1D);
    GolemMastery.Perks broken = new GolemMastery.Perks(-2, false, true, Double.NaN);

    assertThat(wild.extraRuneSlots()).isEqualTo(GolemRuneLayout.MAX_SLOTS);
    assertThat(wild.tierThreeBoost()).isZero();
    assertThat(broken.extraRuneSlots()).isZero();
    assertThat(broken.tierThreeBoost()).isZero();
  }

  @Test
  void aSecondCoreMustBeAnAllowedUnlockedRoleCoreTheGolemDoesNotHold() {
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.GUARDIAN), GolemCoreType.LUMBER)).isEmpty();
    assertThat(refusal(GolemBody.COPPER, List.of(GolemCoreType.SORTER), GolemCoreType.HARVESTER)).isEmpty();
    assertThat(refusal(GolemBody.FROST, List.of(GolemCoreType.HARVESTER), GolemCoreType.GUARDIAN)).isEmpty();
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.BLANK), GolemCoreType.GUARDIAN)).isEmpty();
    assertThat(refusal(GolemBody.FROST, List.of(GolemCoreType.GUARDIAN), GolemCoreType.LUMBER)).contains(GolemMastery.CoreRefusal.NOT_ALLOWED);
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.GUARDIAN), GolemCoreType.SORTER)).contains(GolemMastery.CoreRefusal.NOT_ALLOWED);
    assertThat(refusal(GolemBody.COPPER, List.of(GolemCoreType.SORTER), GolemCoreType.GUARDIAN)).contains(GolemMastery.CoreRefusal.NOT_ALLOWED);
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.GUARDIAN), GolemCoreType.GUARDIAN)).contains(GolemMastery.CoreRefusal.DUPLICATE);
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.GUARDIAN), GolemCoreType.BLANK)).contains(GolemMastery.CoreRefusal.BLANK);
    assertThat(refusal(GolemBody.IRON, List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER), GolemCoreType.BREAKER))
        .contains(GolemMastery.CoreRefusal.NO_SLOT);
    assertThat(GolemMastery.coreRefusal(List.of(GolemCoreType.GUARDIAN), GolemCoreType.LUMBER, false, true, true))
        .contains(GolemMastery.CoreRefusal.NO_SLOT);
    assertThat(GolemMastery.coreRefusal(List.of(GolemCoreType.GUARDIAN), GolemCoreType.LUMBER, true, true, false))
        .contains(GolemMastery.CoreRefusal.LOCKED);
  }

  private static Optional<GolemMastery.CoreRefusal> refusal(GolemBody body, List<GolemCoreType> cores, GolemCoreType candidate) {
    return GolemMastery.coreRefusal(cores, candidate, true, SETTINGS.allows(body, candidate), true);
  }
}
