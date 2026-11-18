package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTaskBoards;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.platform.value.Box;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardianTargetPolicyTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
  private static final Set<EntityRef> STRANGER_MINIONS = ConcurrentHashMap.newKeySet();

  private final List<EntityRef> nearby = new ArrayList<>();
  private final Map<EntityRef, AtomicReference<Loc>> positions = new HashMap<>();
  private final AtomicLong clock = new AtomicLong(10_000L);
  private final GolemGuardianCore.Config config = new GolemGuardianCore.Config();
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<Tasks> scheduling;
  private WorldRef world;
  private GolemGuardianCore core;
  private GuardianTargetPolicy policy;
  private PlayerRef owner;

  @BeforeAll
  static void registerStrangerMinions() {
    MinionIdentity.registerKind(new MinionKind() {
      @Override
      public boolean matches(EntityRef entity) {
        return STRANGER_MINIONS.contains(entity);
      }

      @Override
      public UUID ownerOf(EntityRef entity) {
        return STRANGER_MINIONS.contains(entity) ? STRANGER : null;
      }
    });
  }

  @BeforeEach
  void setUp() {
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(EntityRef.class))).thenReturn(true);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(Loc.class))).thenReturn(true);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.entitiesIn(any(Box.class), any())).thenAnswer(invocation -> {
      Predicate<EntityRef> filter = invocation.getArgument(1);
      return nearby.stream().filter(filter).toList();
    });
    core = mock(GolemGuardianCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.canEngage(any(PlayerRef.class), any(MinionTargeting.Candidate.class))).thenReturn(true);
    lenient().when(core.intercepts(OWNER)).thenReturn(true);
    lenient().when(core.retaliatesAgainstPlayers(OWNER)).thenReturn(true);
    level(1);
    policy = new GuardianTargetPolicy(core, new GuardNodeBehavior(core), clock::get);
    owner = player(OWNER, at(0, 64, -3));
  }

  @AfterEach
  void close() {
    scheduling.close();
    runtime.close();
  }

  @Test
  void selectsTheNearestHostileWithinPerceptionOfHome() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef closest = mob("zombie", true, at(3, 64, 0));
    mob("zombie", true, at(6, 64, 0));
    mob("zombie", true, at(9, 64, 0));
    mob("cow", false, at(1, 64, 0));

    assertThat(policy.select(golem, owner)).isSameAs(closest);
    assertThat(policy.idleBackoff()).isTrue();
  }

  @Test
  void deadMobsAndMobsBeyondPerceptionAreIgnored() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef dead = mob("zombie", true, at(2, 64, 0));
    when(dead.isDead()).thenReturn(true);
    mob("zombie", true, at(9, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void creepersAndNamedMobsAreLeftAloneUnlessTheConfigAllowsThem() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef creeper = mob("creeper", true, at(2, 64, 0));
    MobRef named = mob("zombie", true, at(3, 64, 0));
    when(named.customName()).thenReturn("Bob");
    MobRef plain = mob("zombie", true, at(7, 64, 0));

    assertThat(policy.select(golem, owner)).isSameAs(plain);

    config.ignoreNamedMobs = false;
    assertThat(policy.select(golem, owner)).isSameAs(named);

    config.targetCreepers = true;
    assertThat(policy.select(golem, owner)).isSameAs(creeper);
  }

  @Test
  void mobsChasingTheOwnerOrTheGolemAreTargetedEvenWhenNotHostile() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef wolf = mob("wolf", false, at(4, 64, 0));
    MobRef cow = mob("cow", false, at(2, 64, 0));
    when(wolf.target()).thenReturn(owner);

    assertThat(policy.select(golem, owner)).isSameAs(wolf);
    verify(core, never()).canEngage(any(PlayerRef.class), argThat(candidate -> candidate.entity() == cow));
  }

  @Test
  void perceptionGrowsWithTheOwnersLevel() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(14, 64, 0));

    level(2);
    assertThat(policy.select(golem, owner)).isNull();

    level(3);
    assertThat(policy.select(golem, owner)).isSameAs(zombie);
  }

  @Test
  void regionProtectionKeepsMobsSafe() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(2, 64, 0));
    when(core.canEngage(eq(owner), argThat(candidate -> candidate != null && candidate.entity() == zombie))).thenReturn(false);

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void followingGuardiansGuardAroundTheirOwner() {
    owner = player(OWNER, at(30, 64, 0));
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.FOLLOW, at(28, 64, 0));
    mob("zombie", true, at(2, 64, 0));
    MobRef nearOwner = mob("zombie", true, at(35, 64, 0));

    assertThat(policy.select(golem, owner)).isSameAs(nearOwner);
  }

  @Test
  void workingGuardiansAlsoDefendTheAreaOfEachMatchingGuardNode() {
    NodeKey node = new NodeKey(WORLD, 10, 64, 0, BlockFace.UP);
    NodeKey blue = new NodeKey(WORLD, 0, 64, 10, BlockFace.UP);
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(node, NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build());
    nodes.put(blue, NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).channel(DyeColor.BLUE).build());
    GolemTaskBoards boards = nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef inArea = mob("zombie", true, at(11, 65, 1));
    mob("zombie", true, at(0, 65, 11));

    assertThat(policy.select(golem, owner)).isSameAs(inArea);
    assertThat(boards.board(WORLD).status(node, clock.get())).isEqualTo(NodeStatus.WORKING);
    assertThat(boards.board(WORLD).status(blue, clock.get())).isEqualTo(NodeStatus.IDLE);
    assertThat(policy.holds(golem, owner, inArea)).isTrue();

    move(inArea, at(20, 65, 1));

    assertThat(policy.holds(golem, owner, inArea)).isFalse();
  }

  @Test
  void guardNodesOnlyCountInWorkMode() {
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(new NodeKey(WORLD, 10, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build());
    nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.HOME, at(0, 64, 0));
    mob("zombie", true, at(11, 65, 1));

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void retaliationGoesAfterWhateverHurtTheOwnerFirst() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(2, 64, 0));
    PlayerRef attacker = player(UUID.randomUUID(), at(5, 64, 0));
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(attacker);

    level(2);
    assertThat(policy.select(golem, owner)).isSameAs(zombie);

    level(3);
    assertThat(policy.select(golem, owner)).isSameAs(attacker);
    assertThat(policy.holds(golem, owner, attacker)).isTrue();

    when(core.canEngage(eq(owner), argThat(candidate -> candidate != null && candidate.entity() == attacker))).thenReturn(false);
    assertThat(policy.select(golem, owner)).isSameAs(zombie);

    when(core.recentAttacker(OWNER, clock.get())).thenReturn(null);
    assertThat(policy.holds(golem, owner, attacker)).isFalse();
  }

  @Test
  void retaliationSkipsAPlayerAttackerWhenTheOwnerSwitchesPlayerRetaliationOff() {
    level(3);
    when(core.retaliatesAgainstPlayers(OWNER)).thenReturn(false);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(2, 64, 0));
    PlayerRef attacker = player(UUID.randomUUID(), at(5, 64, 0));
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(attacker);

    assertThat(policy.select(golem, owner)).isSameAs(zombie);
    assertThat(policy.holds(golem, owner, attacker)).isFalse();

    MobRef biter = living(MobRef.class, at(6, 64, 0));
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(biter);
    assertThat(policy.select(golem, owner)).isSameAs(biter);
  }

  @Test
  void anAttackOnTheOwnerTakesOverFromTheCurrentTarget() {
    level(3);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(2, 64, 0));
    assertThat(policy.holds(golem, owner, zombie)).isTrue();

    PlayerRef attacker = player(UUID.randomUUID(), at(5, 64, 0));
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(attacker);

    assertThat(policy.holds(golem, owner, zombie)).isFalse();
    assertThat(policy.select(golem, owner)).isSameAs(attacker);
    assertThat(policy.holds(golem, owner, attacker)).isTrue();
  }

  @Test
  void stayingGuardiansGuardTheSpotWhereTheyWereToldToStay() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.STAY, at(6, 64, 0));
    GolemBrain brain = mock(GolemBrain.class);
    when(brain.stayAnchor()).thenReturn(Optional.of(at(0, 64, 0)));
    MobRef fleeing = mob("zombie", true, at(7, 64, 0));
    assertThat(policy.holds(golem, owner, fleeing)).isTrue();

    move(fleeing, at(9, 64, 0));
    assertThat(policy.holds(golem, owner, fleeing)).isTrue();

    MobRef body = golem.entity();
    runtime.when(() -> GolemancyRuntime.brain(body)).thenReturn(Optional.of(brain));
    assertThat(policy.holds(golem, owner, fleeing)).isFalse();

    move(fleeing, at(7, 64, 0));
    assertThat(policy.holds(golem, owner, fleeing)).isTrue();
  }

  @Test
  void guardNodeAreasCountOnlyWithinTheLeashOfHomeSoTheGolemNeverChasesPastIt() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 16D, 8, 10));
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    NodeKey node = new NodeKey(WORLD, 14, 64, 0, BlockFace.UP);
    nodes.put(node, NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(NodeCuboid.symmetric(4, 4, 4)).build());
    nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef pastLeash = mob("zombie", true, at(17, 65, 0));

    assertThat(policy.select(golem, owner)).isNull();
    assertThat(policy.holds(golem, owner, pastLeash)).isFalse();

    MobRef insideLeash = mob("zombie", true, at(12, 65, 0));
    assertThat(policy.select(golem, owner)).isSameAs(insideLeash);
  }

  @Test
  void guardNodeQueriesAreClippedToTheLeashOfHome() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 16D, 8, 10));
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(new NodeKey(WORLD, 14, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(NodeCuboid.symmetric(4, 4, 4)).build());
    nodes.put(new NodeKey(WORLD, 0, 64, 20, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(NodeCuboid.symmetric(2, 2, 2)).build());
    nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef insideLeash = mob("zombie", true, at(12, 65, 0));

    assertThat(policy.select(golem, owner)).isSameAs(insideLeash);

    ArgumentCaptor<Box> queries = ArgumentCaptor.forClass(Box.class);
    verify(world, atLeastOnce()).entitiesIn(queries.capture(), any());
    Box leash = Box.around(at(0, 64, 0).position(), 16D, 16D, 16D);
    assertThat(queries.getAllValues()).hasSize(2).allSatisfy(query -> assertThat(contains(leash, query)).isTrue());
  }

  @Test
  void tideRunesWidenTheGuardRadiusUpToTheLeash() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(11, 64, 0));
    assertThat(policy.select(golem, owner)).isNull();

    GolemRunes.Augments tide = mock(GolemRunes.Augments.class);
    when(tide.effects(golem)).thenReturn(new GolemRuneEffects(4D, 0, 1D, 0D));
    GolemRunes.register(tide);
    try {
      assertThat(policy.select(golem, owner)).isSameAs(zombie);
      assertThat(policy.holds(golem, owner, zombie)).isTrue();

      runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 10D, 8, 10));
      assertThat(policy.select(golem, owner)).isNull();
      assertThat(policy.holds(golem, owner, zombie)).isFalse();
    } finally {
      GolemRunes.unregister(tide);
    }
  }

  @Test
  void passiveMobsAreSkippedBeforeAnyCaptureOrNameLookup() {
    level(2);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef cow = mob("cow", false, at(2, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();
    policy.tick(golem, owner);

    verify(cow, never()).customName();
    verify(cow, never()).isInvulnerable();
    verify(cow, never()).location();
  }

  @Test
  void interceptAndRetaliateLeaveOtherPlayersMinionsAndTheOwnersPetsAlone() {
    level(3);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef minion = mob("zombie", true, at(3, 64, 0));
    STRANGER_MINIONS.add(minion);
    when(minion.target()).thenReturn(owner);
    MobRef pet = mob("wolf", false, at(4, 64, 0));
    when(pet.tameOwner()).thenReturn(OWNER);
    when(pet.target()).thenReturn(owner);

    policy.tick(golem, owner);
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(minion);
    LivingRef avengedMinion = policy.select(golem, owner);
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(pet);
    LivingRef avengedPet = policy.select(golem, owner);

    verify(minion, never()).setTarget(any());
    verify(pet, never()).setTarget(any());
    assertThat(avengedMinion).isNull();
    assertThat(avengedPet).isNull();
    STRANGER_MINIONS.remove(minion);
  }

  @Test
  void targetsAreHeldOnlyWhileTheyStayAliveAndInsideTheGuardedArea() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef zombie = mob("zombie", true, at(4, 64, 0));

    assertThat(policy.holds(golem, owner, zombie)).isTrue();

    move(zombie, at(12, 64, 0));
    assertThat(policy.holds(golem, owner, zombie)).isFalse();

    move(zombie, at(4, 64, 0));
    when(zombie.isDead()).thenReturn(true);
    assertThat(policy.holds(golem, owner, zombie)).isFalse();
  }

  @Test
  void frostGuardiansAreSentriesThatOnlyEngageWithinShootingRange() {
    level(5);
    GolemHandle golem = golem(GolemBody.FROST, GolemMode.WORK, at(0, 64, 0));
    mob("zombie", true, at(15, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();

    MobRef close = mob("zombie", true, at(9, 64, 0));
    assertThat(policy.select(golem, owner)).isSameAs(close);

    move(close, at(12, 64, 0));
    assertThat(policy.holds(golem, owner, close)).isFalse();
  }

  @Test
  void anOfflineOwnersGuardiansAttackOnlyHostilesWithinTheFirstLevelsPerception() {
    level(5);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    mob("zombie", true, at(10, 64, 0));
    MobRef wolf = mob("wolf", false, at(2, 64, 0));
    MobRef body = golem.entity();
    when(wolf.target()).thenReturn(body);

    assertThat(policy.select(golem, null)).isNull();

    MobRef close = mob("zombie", true, at(7, 64, 0));
    assertThat(policy.select(golem, null)).isSameAs(close);
    verify(core, never()).canEngage(any(PlayerRef.class), any(MinionTargeting.Candidate.class));
    verify(core, never()).recentAttacker(any(), anyLong());
  }

  @Test
  void interceptTurnsMobsChasingTheOwnerOntoTheGolemEveryThreeSeconds() {
    level(2);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef chaser = mob("zombie", true, at(4, 64, 0));
    when(chaser.target()).thenReturn(owner);
    MobRef elsewhere = living(MobRef.class, at(5, 64, 0));
    PlayerRef stranger = player(UUID.randomUUID(), at(20, 64, 0));
    when(elsewhere.target()).thenReturn(stranger);

    policy.tick(golem, owner);
    clock.addAndGet(2_999L);
    policy.tick(golem, owner);

    verify(chaser, times(1)).setTarget(golem.entity());
    verify(elsewhere, never()).setTarget(any());

    clock.addAndGet(1L);
    policy.tick(golem, owner);

    verify(chaser, times(2)).setTarget(golem.entity());
  }

  @Test
  void interceptNeedsItsLevelAndAnOnlineOwner() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef chaser = mob("zombie", true, at(4, 64, 0));
    when(chaser.target()).thenReturn(owner);

    policy.tick(golem, owner);
    level(5);
    policy.tick(golem, null);

    verify(chaser, never()).setTarget(any());
  }

  @Test
  void interceptStaysOffWhileTheOwnerSwitchesItOff() {
    level(2);
    when(core.intercepts(OWNER)).thenReturn(false);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    MobRef chaser = mob("zombie", true, at(4, 64, 0));
    when(chaser.target()).thenReturn(owner);

    policy.tick(golem, owner);

    verify(chaser, never()).setTarget(any());

    when(core.intercepts(OWNER)).thenReturn(true);
    policy.tick(golem, owner);

    verify(chaser).setTarget(golem.entity());
  }

  @Test
  void bodiesTheGuardianCoreIsNotAllowedOnNeverTarget() {
    GolemHandle golem = golem(GolemBody.COPPER, GolemMode.WORK, at(0, 64, 0));
    mob("zombie", true, at(2, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();
  }

  private GolemTaskBoards nodes(Map<NodeKey, NodeRecord> nodes) {
    NodeIndex index = new NodeIndex();
    Map<Long, Map<NodeKey, NodeRecord>> chunks = new HashMap<>();
    for (Map.Entry<NodeKey, NodeRecord> node : nodes.entrySet()) {
      chunks.computeIfAbsent(node.getKey().chunkKey(), ignored -> new HashMap<>()).put(node.getKey(), node.getValue());
    }
    for (Map.Entry<Long, Map<NodeKey, NodeRecord>> chunk : chunks.entrySet()) {
      long generation = index.beginLoad(WORLD, chunk.getKey());
      index.completeLoad(WORLD, chunk.getKey(), generation, chunk.getValue());
    }
    NodeService service = mock(NodeService.class);
    when(service.index()).thenReturn(index);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    GolemTaskBoards boards = new GolemTaskBoards(key -> Optional.ofNullable(nodes.get(key)));
    runtime.when(GolemancyRuntime::boards).thenReturn(Optional.of(boards));
    return boards;
  }

  private void level(int level) {
    lenient().when(core.ownerLevel(any(GolemHandle.class))).thenReturn(level);
  }

  private GolemHandle golem(GolemBody body, GolemMode mode, Loc location) {
    MobRef entity = switch (body) {
      case FROST -> living(MobRef.class, location);
      default -> living(MobRef.class, location);
    };
    nearby.remove(entity);
    nearby.add(entity);
    GolemState state = mock(GolemState.class);
    lenient().when(state.mode()).thenReturn(mode);
    lenient().when(state.home()).thenReturn(GolemAnchor.of(WORLD, 0, 64, 0));
    lenient().when(state.channels()).thenReturn(ChannelMask.NONE);
    lenient().when(state.cores()).thenReturn(List.of(GolemCoreType.GUARDIAN));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenReturn(body);
    lenient().when(handle.core()).thenReturn(GolemCoreType.GUARDIAN);
    lenient().when(handle.entity()).thenReturn(entity);
    lenient().when(handle.state()).thenReturn(state);
    return handle;
  }

  private PlayerRef player(UUID id, Loc location) {
    PlayerRef player = living(PlayerRef.class, location);
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    nearby.remove(player);
    return player;
  }

  private <T extends LivingRef> T living(Class<T> type, Loc location) {
    T entity = mock(type);
    AtomicReference<Loc> position = new AtomicReference<>(location);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.location()).thenAnswer(invocation -> position.get());
    lenient().when(entity.world()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.data()).thenReturn(new MemoryData());
    positions.put(entity, position);
    nearby.add(entity);
    return entity;
  }

  private MobRef mob(String type, boolean enemy, Loc location) {
    MobRef mob = living(MobRef.class, location);
    lenient().when(mob.type()).thenReturn(Id.minecraft(type));
    lenient().when(mob.has(EntityTrait.ENEMY)).thenReturn(enemy);
    return mob;
  }

  private static boolean contains(Box outer, Box inner) {
    return outer.minX() <= inner.minX() && outer.minY() <= inner.minY() && outer.minZ() <= inner.minZ()
        && inner.maxX() <= outer.maxX() && inner.maxY() <= outer.maxY() && inner.maxZ() <= outer.maxZ();
  }

  private void move(EntityRef entity, Loc location) {
    positions.get(entity).set(location);
  }

  private Loc at(double x, double y, double z) {
    return new Loc(world, x + 0.5D, y, z + 0.5D);
  }
}
