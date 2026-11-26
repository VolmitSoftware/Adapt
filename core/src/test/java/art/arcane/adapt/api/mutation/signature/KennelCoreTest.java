package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Golemancy leading Taming: Rancher golems keep the animals around their home. */
class KennelCoreTest {
  private static final Id COW = Id.minecraft("cow");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final DataKey<String> KEPT_BY = DataKey.adapt("kennel-kept-by", DataCodecs.STRING);

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private final List<GolemHandle> golems = new ArrayList<>();
  private MockedStatic<GolemancyRuntime> runtime;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().animals().peaceful(COW, WHEAT);
    player = platform.player("Artificer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillGolemancy.class, SkillTaming.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(player.uuid())).thenAnswer(invocation -> List.copyOf(golems));
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
  }

  @AfterEach
  void stop() {
    runtime.close();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aRancherKeeperHealsYourPetsAndTheAnimalsYouFedAroundItsHome() {
    kit.mutate("golemancy", "taming");
    kit.learn("golemancy", "taming", Recipe.CONVERGENCE, 1);
    keeper(GolemCoreType.RANCHER, 40.5D);
    PairMob wolf = PairMob.pet(platform, player, 44.5D, 0.5D);
    PairMob cow = PairMob.animal(platform, COW, 36.5D, 0.5D);
    feed(cow);
    wolf.setHealth(10D);
    cow.setHealth(10D);

    round();

    assertThat(wolf.health()).isEqualTo(13D);
    assertThat(cow.health()).isEqualTo(13D);
    assertThat(cow.data().get(KEPT_BY)).isEqualTo(player.uuid().toString());
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void wildAndNeighboursAnimalsAroundTheHomeAreNeitherClaimedNorTended() {
    kit.mutate("golemancy", "taming");
    kit.learn("golemancy", "taming", Recipe.CONVERGENCE, 1);
    keeper(GolemCoreType.RANCHER, 40.5D);
    PairMob wild = PairMob.animal(platform, COW, 36.5D, 0.5D);
    PairMob neighbours = PairMob.animal(platform, COW, 38.5D, 0.5D);
    String neighbour = UUID.randomUUID().toString();
    neighbours.data().set(KEPT_BY, neighbour);
    feed(neighbours);
    wild.setHealth(10D);
    neighbours.setHealth(10D);

    round();

    assertThat(wild.health()).isEqualTo(10D);
    assertThat(wild.data().get(KEPT_BY)).isNull();
    assertThat(neighbours.health()).isEqualTo(10D);
    assertThat(neighbours.data().get(KEPT_BY)).isEqualTo(neighbour);
  }

  @Test
  void aCalfYouBredIsYoursToKeep() {
    kit.mutate("golemancy", "taming");
    kit.learn("golemancy", "taming", Recipe.CONVERGENCE, 1);
    PairMob mother = PairMob.animal(platform, COW, 36.5D, 0.5D);
    PairMob father = PairMob.animal(platform, COW, 37.5D, 0.5D);
    PairMob calf = PairMob.animal(platform, COW, 36.5D, 1.5D);

    adapt.post(new EntityBreedEvent(calf, mother, father, player, new FakeItem(WHEAT, 1), 1));

    assertThat(calf.data().get(KEPT_BY)).isEqualTo(player.uuid().toString());
  }

  @Test
  void aStrayItKeptIsLedHomeButUnkeptAnimalsOutsideAreLeftAlone() {
    kit.mutate("golemancy", "taming");
    kit.learn("golemancy", "taming", Recipe.CONVERGENCE, 1);
    keeper(GolemCoreType.RANCHER, 40.5D);
    PairMob stray = PairMob.animal(platform, COW, 60.5D, 0.5D);
    stray.data().set(KEPT_BY, player.uuid().toString());
    PairMob wild = PairMob.animal(platform, COW, 62.5D, 0.5D);
    PairMob neighbours = PairMob.animal(platform, COW, 61.5D, 0.5D);
    neighbours.data().set(KEPT_BY, UUID.randomUUID().toString());

    round();

    assertThat(stray.pathedTo()).isNotNull();
    assertThat(stray.pathedTo().x()).isEqualTo(40.5D);
    assertThat(wild.pathedTo()).isNull();
    assertThat(neighbours.pathedTo()).isNull();
  }

  @Test
  void otherGolemsOtherPlayersPetsAndTheRoundCooldownAreRespected() {
    kit.mutate("golemancy", "taming");
    kit.learn("golemancy", "taming", Recipe.CONVERGENCE, 1);
    keeper(GolemCoreType.GUARDIAN, 40.5D);
    PairMob cow = PairMob.animal(platform, COW, 38.5D, 0.5D);
    feed(cow);
    cow.setHealth(10D);
    round();
    assertThat(cow.health()).isEqualTo(10D);

    keeper(GolemCoreType.RANCHER, 40.5D);
    PairMob stranger = PairMob.pet(platform, player, 42.5D, 0.5D);
    stranger.setTameOwner(UUID.randomUUID());
    stranger.setHealth(10D);
    round();
    round();
    assertThat(cow.health()).isEqualTo(13D);
    assertThat(stranger.health()).isEqualTo(10D);
  }

  @Test
  void nothingIsTendedWithoutTheLearnedAdaptation() {
    kit.mutate("golemancy", "taming");
    keeper(GolemCoreType.RANCHER, 40.5D);
    PairMob cow = PairMob.animal(platform, COW, 38.5D, 0.5D);
    cow.data().set(KEPT_BY, player.uuid().toString());
    cow.setHealth(10D);

    round();

    assertThat(cow.health()).isEqualTo(10D);
  }

  @Test
  void higherLevelsReachFartherHealMoreAndTendMoreOften() {
    kit.mutate("golemancy", "taming");
    KennelCore kennel = (KennelCore) kit.adaptation("golemancy", "taming", Recipe.CONVERGENCE).getSignature();

    assertThat(kennel.radius(3)).isGreaterThan(kennel.radius(2)).isGreaterThan(kennel.radius(1));
    assertThat(kennel.heal(3)).isGreaterThan(kennel.heal(1));
    assertThat(kennel.animals(3)).isGreaterThan(kennel.animals(1));
    assertThat(kennel.roundMillis(3)).isLessThan(kennel.roundMillis(1));
  }

  private void feed(PairMob animal) {
    player.holding(new FakeItem(WHEAT, 1));
    adapt.post(new PlayerInteractEntityEvent(player, animal, Hand.MAIN_HAND, new Vec3(animal.location().x(), 61D, 0.5D)));
    player.holding(FakeItem.empty());
  }

  private void round() {
    kit.adaptation("golemancy", "taming", Recipe.CONVERGENCE).getSignature().onTick();
    platform.scheduler().advance(2);
  }

  private void keeper(GolemCoreType core, double x) {
    PairMob body = platform.world().add(new PairMob(Id.minecraft("iron_golem"), new Loc(platform.world(), x, 61D, 0.5D)));
    GolemState state = mock(GolemState.class);
    when(state.cores()).thenReturn(List.of(core));
    when(state.home()).thenReturn(GolemAnchor.at(new Loc(platform.world(), x, 61D, 0.5D)));
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(player.uuid());
    when(handle.entity()).thenReturn(body);
    when(handle.core()).thenReturn(core);
    when(handle.state()).thenReturn(state);
    golems.add(handle);
  }
}
