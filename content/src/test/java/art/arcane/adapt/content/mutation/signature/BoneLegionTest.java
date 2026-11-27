package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemWorkStatus;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TragOul and Golemancy in either order: servants take cores, age only while idle and never pass the cap. */
class BoneLegionTest {
  private static final DataKey<String> SERVANT = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final DataKey<Long> REMAINING = DataKey.adapt("bone-legion-remaining", DataCodecs.LONG);

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private GolemRegistry registry;
  private final List<GolemHandle> bound = new ArrayList<>();
  private MockedStatic<GolemancyRuntime> runtime;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Bloodbinder", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillTragOul.class, SkillGolemancy.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    registry = mock(GolemRegistry.class);
    when(registry.bindBlock(any(PlayerRef.class), any(MobRef.class), any(GolemBody.class), any(GolemCoreType.class)))
        .thenReturn(Optional.empty());
    when(registry.bind(any(PlayerRef.class), any(MobRef.class), eq(GolemBody.BONE), any(GolemCoreType.class)))
        .thenAnswer(invocation -> handle(invocation.getArgument(1), GolemWorkStatus.WAITING));
    when(registry.loaded()).thenAnswer(invocation -> List.copyOf(bound));
    when(registry.release(any(GolemHandle.class))).thenReturn(true);
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
  void aServantHandedACoreIsBoundAsABoneGolemAndTheCoreIsSpent() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 1);
    PairMob servant = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));

    enlist(servant);

    verify(registry).bind(player, servant, GolemBody.BONE, GolemCoreType.HARVESTER);
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(servant.data().get(REMAINING)).isEqualTo(30000L);
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void golemancyAsLeadEnlistsToo() {
    kit.mutate("golemancy", "tragoul");
    kit.learn("golemancy", "tragoul", Recipe.CONVERGENCE, 1);
    PairMob servant = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.LUMBER));

    enlist(servant);

    verify(registry).bind(player, servant, GolemBody.BONE, GolemCoreType.LUMBER);
  }

  @Test
  void someoneElsesServantOrAPlainSkeletonNeverTakesACore() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 3);
    PairMob theirs = servant(UUID.randomUUID());
    PairMob wild = platform.world().add(new PairMob(Id.minecraft("skeleton"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));

    enlist(theirs);
    enlist(wild);

    verify(registry, never()).bind(any(PlayerRef.class), any(MobRef.class), any(GolemBody.class), any(GolemCoreType.class));
    assertThat(player.inventory().mainHand().isEmpty()).isFalse();
  }

  @Test
  void theLevelCapsTheLegionAndEachLegionnaireStillCountsAsAServant() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 1);
    PairMob first = servant(player.uuid());
    PairMob second = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));
    enlist(first);
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));
    enlist(second);

    verify(registry, never()).bind(eq(player), eq(second), any(GolemBody.class), any(GolemCoreType.class));
    assertThat(ServantFrame.legion(player.uuid())).isEqualTo(1);
  }

  @Test
  void sneakingWithACoreLeavesTheServantToAnimusBinding() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 1);
    PairMob servant = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));
    player.setSneaking(true);

    enlist(servant);

    verify(registry, never()).bind(any(PlayerRef.class), any(MobRef.class), any(GolemBody.class), any(GolemCoreType.class));
  }

  @Test
  void theOtherOrdersCopyLeavesALegionnaireItDidNotEnlist() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 1);
    PairMob servant = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));
    enlist(servant);
    bound.add(registry.bind(player, servant, GolemBody.BONE, GolemCoreType.HARVESTER));

    BoneLegion sibling = (BoneLegion) kit.adaptation("golemancy", "tragoul", Recipe.CONVERGENCE).getSignature();
    sibling.getConfig().surveyMillis = 0L;
    sibling.onTick();
    platform.scheduler().advance(2);

    verify(registry, never()).release(any(GolemHandle.class));
    assertThat(ServantFrame.legion(player.uuid())).isEqualTo(1);
  }

  @Test
  void aLegionnaireOnlyAgesWhileIdleAndCrumblesWhenItsTimeIsUp() {
    kit.mutate("tragoul", "golemancy");
    kit.learn("tragoul", "golemancy", Recipe.CONVERGENCE, 1);
    PairMob body = platform.world().add(new PairMob(Id.minecraft("skeleton"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    body.data().set(REMAINING, 0L);
    GolemHandle working = handle(body, GolemWorkStatus.WORKING);
    bound.add(working);

    age();
    verify(registry, never()).release(working);

    when(working.workStatus()).thenReturn(GolemWorkStatus.WAITING);
    age();
    verify(registry).release(working);
  }

  @Test
  void aLegionnaireLoadedForAnOwnerWithoutTheSignatureIsReleased() {
    kit.mutate("tragoul", "golemancy");
    PairMob body = platform.world().add(new PairMob(Id.minecraft("skeleton"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    body.data().set(REMAINING, 60000L);
    GolemHandle working = handle(body, GolemWorkStatus.WORKING);
    bound.add(working);

    age();

    verify(registry).release(working);
  }

  @Test
  void nothingIsEnlistedWithoutTheLearnedAdaptation() {
    kit.mutate("tragoul", "golemancy");
    PairMob servant = servant(player.uuid());
    player.holding(GolemItems.core(GolemCoreType.HARVESTER));

    enlist(servant);

    verify(registry, never()).bind(any(PlayerRef.class), any(MobRef.class), any(GolemBody.class), any(GolemCoreType.class));
  }

  private void enlist(PairMob servant) {
    adapt.post(new PlayerInteractEntityEvent(player, servant, Hand.MAIN_HAND, new Vec3(servant.location().x(), 61D, 0.5D)));
    platform.scheduler().advance(2);
  }

  private void age() {
    BoneLegion legion = (BoneLegion) kit.adaptation("tragoul", "golemancy", Recipe.CONVERGENCE).getSignature();
    legion.getConfig().surveyMillis = 0L;
    legion.onTick();
    platform.scheduler().advance(2);
  }

  private PairMob servant(UUID owner) {
    PairMob servant = platform.world().add(new PairMob(Id.minecraft("skeleton"),
        new Loc(platform.world(), 2.5D + bound.size(), 61D, 0.5D)));
    servant.data().set(SERVANT, owner.toString());
    return servant;
  }

  private GolemHandle handle(MobRef body, GolemWorkStatus status) {
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(player.uuid());
    when(handle.entity()).thenReturn(body);
    when(handle.entityId()).thenReturn(body.uuid());
    when(handle.body()).thenReturn(GolemBody.BONE);
    when(handle.workStatus()).thenReturn(status);
    when(handle.state()).thenReturn(mock(GolemState.class));
    return handle;
  }
}
