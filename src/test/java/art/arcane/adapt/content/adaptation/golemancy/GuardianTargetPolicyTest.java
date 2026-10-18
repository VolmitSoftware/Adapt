package art.arcane.adapt.content.adaptation.golemancy;

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
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeStatus;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
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
  private static final Set<Entity> STRANGER_MINIONS = ConcurrentHashMap.newKeySet();

  private final List<Entity> nearby = new ArrayList<>();
  private final Map<Entity, AtomicReference<Location>> positions = new HashMap<>();
  private final AtomicLong clock = new AtomicLong(10_000L);
  private final GolemGuardianCore.Config config = new GolemGuardianCore.Config();
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private World world;
  private GolemGuardianCore core;
  private GuardianTargetPolicy policy;
  private Player owner;

  @BeforeAll
  static void registerStrangerMinions() {
    MinionIdentity.registerKind(new MinionKind() {
      @Override
      public boolean matches(Entity entity) {
        return STRANGER_MINIONS.contains(entity);
      }

      @Override
      public UUID ownerOf(Entity entity) {
        return STRANGER_MINIONS.contains(entity) ? STRANGER : null;
      }
    });
  }

  @BeforeEach
  void setUp() {
    runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS);
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      Predicate<Entity> filter = invocation.getArgument(1);
      return nearby.stream().filter(filter).toList();
    });
    core = mock(GolemGuardianCore.class);
    lenient().when(core.config()).thenReturn(config);
    lenient().when(core.canEngage(any(Player.class), any(MinionTargeting.Candidate.class))).thenReturn(true);
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
    Zombie closest = living(Zombie.class, at(3, 64, 0));
    living(Zombie.class, at(6, 64, 0));
    living(Zombie.class, at(9, 64, 0));
    living(Cow.class, at(1, 64, 0));

    assertThat(policy.select(golem, owner)).isSameAs(closest);
    assertThat(policy.idleBackoff()).isTrue();
  }

  @Test
  void deadMobsAndMobsBeyondPerceptionAreIgnored() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie dead = living(Zombie.class, at(2, 64, 0));
    when(dead.isDead()).thenReturn(true);
    living(Zombie.class, at(9, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void creepersAndNamedMobsAreLeftAloneUnlessTheConfigAllowsThem() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Creeper creeper = living(Creeper.class, at(2, 64, 0));
    Zombie named = living(Zombie.class, at(3, 64, 0));
    when(named.customName()).thenReturn(Component.text("Bob"));
    Zombie plain = living(Zombie.class, at(7, 64, 0));

    assertThat(policy.select(golem, owner)).isSameAs(plain);

    config.ignoreNamedMobs = false;
    assertThat(policy.select(golem, owner)).isSameAs(named);

    config.targetCreepers = true;
    assertThat(policy.select(golem, owner)).isSameAs(creeper);
  }

  @Test
  void mobsChasingTheOwnerOrTheGolemAreTargetedEvenWhenNotHostile() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Wolf wolf = living(Wolf.class, at(4, 64, 0));
    Cow cow = living(Cow.class, at(2, 64, 0));
    when(wolf.getTarget()).thenReturn(owner);

    assertThat(policy.select(golem, owner)).isSameAs(wolf);
    verify(core, never()).canEngage(any(Player.class), argThat(candidate -> candidate.entity() == cow));
  }

  @Test
  void perceptionGrowsWithTheOwnersLevel() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(14, 64, 0));

    level(2);
    assertThat(policy.select(golem, owner)).isNull();

    level(3);
    assertThat(policy.select(golem, owner)).isSameAs(zombie);
  }

  @Test
  void regionProtectionKeepsMobsSafe() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    when(core.canEngage(eq(owner), argThat(candidate -> candidate != null && candidate.entity() == zombie))).thenReturn(false);

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void followingGuardiansGuardAroundTheirOwner() {
    owner = player(OWNER, at(30, 64, 0));
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.FOLLOW, at(28, 64, 0));
    living(Zombie.class, at(2, 64, 0));
    Zombie nearOwner = living(Zombie.class, at(35, 64, 0));

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
    Zombie inArea = living(Zombie.class, at(11, 65, 1));
    living(Zombie.class, at(0, 65, 11));

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
    living(Zombie.class, at(11, 65, 1));

    assertThat(policy.select(golem, owner)).isNull();
  }

  @Test
  void retaliationGoesAfterWhateverHurtTheOwnerFirst() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    Player attacker = player(UUID.randomUUID(), at(5, 64, 0));
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
  void anAttackOnTheOwnerTakesOverFromTheCurrentTarget() {
    level(3);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    assertThat(policy.holds(golem, owner, zombie)).isTrue();

    Player attacker = player(UUID.randomUUID(), at(5, 64, 0));
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
    Zombie fleeing = living(Zombie.class, at(7, 64, 0));
    assertThat(policy.holds(golem, owner, fleeing)).isTrue();

    move(fleeing, at(9, 64, 0));
    assertThat(policy.holds(golem, owner, fleeing)).isTrue();

    Mob body = golem.entity();
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
    nodes.put(node, NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).radii(4, 4, 4).build());
    nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie pastLeash = living(Zombie.class, at(17, 65, 0));

    assertThat(policy.select(golem, owner)).isNull();
    assertThat(policy.holds(golem, owner, pastLeash)).isFalse();

    Zombie insideLeash = living(Zombie.class, at(12, 65, 0));
    assertThat(policy.select(golem, owner)).isSameAs(insideLeash);
  }

  @Test
  void guardNodeQueriesAreClippedToTheLeashOfHome() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 16D, 8, 10));
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    nodes.put(new NodeKey(WORLD, 14, 64, 0, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).radii(4, 4, 4).build());
    nodes.put(new NodeKey(WORLD, 0, 64, 20, BlockFace.UP), NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).radii(2, 2, 2).build());
    nodes(nodes);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie insideLeash = living(Zombie.class, at(12, 65, 0));

    assertThat(policy.select(golem, owner)).isSameAs(insideLeash);

    ArgumentCaptor<BoundingBox> queries = ArgumentCaptor.forClass(BoundingBox.class);
    verify(world, atLeastOnce()).getNearbyEntities(queries.capture(), any());
    BoundingBox leash = BoundingBox.of(at(0, 64, 0), 16D, 16D, 16D);
    assertThat(queries.getAllValues()).hasSize(2).allSatisfy(query -> assertThat(leash.contains(query)).isTrue());
  }

  @Test
  void tideRunesWidenTheGuardRadiusUpToTheLeash() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(11, 64, 0));
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
    Cow cow = living(Cow.class, at(2, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();
    policy.tick(golem, owner);

    verify(cow, never()).customName();
    verify(cow, never()).isInvulnerable();
    verify(cow, never()).getLocation();
  }

  @Test
  void interceptAndRetaliateLeaveOtherPlayersMinionsAndTheOwnersPetsAlone() {
    level(3);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie minion = living(Zombie.class, at(3, 64, 0));
    STRANGER_MINIONS.add(minion);
    when(minion.getTarget()).thenReturn(owner);
    Wolf pet = living(Wolf.class, at(4, 64, 0));
    when(pet.isTamed()).thenReturn(true);
    when(pet.getOwner()).thenReturn(owner);
    when(pet.getTarget()).thenReturn(owner);

    policy.tick(golem, owner);
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(minion);
    LivingEntity avengedMinion = policy.select(golem, owner);
    when(core.recentAttacker(OWNER, clock.get())).thenReturn(pet);
    LivingEntity avengedPet = policy.select(golem, owner);

    verify(minion, never()).setTarget(any());
    verify(pet, never()).setTarget(any());
    assertThat(avengedMinion).isNull();
    assertThat(avengedPet).isNull();
    STRANGER_MINIONS.remove(minion);
  }

  @Test
  void targetsAreHeldOnlyWhileTheyStayAliveAndInsideTheGuardedArea() {
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie zombie = living(Zombie.class, at(4, 64, 0));

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
    living(Zombie.class, at(15, 64, 0));

    assertThat(policy.select(golem, owner)).isNull();

    Zombie close = living(Zombie.class, at(9, 64, 0));
    assertThat(policy.select(golem, owner)).isSameAs(close);

    move(close, at(12, 64, 0));
    assertThat(policy.holds(golem, owner, close)).isFalse();
  }

  @Test
  void anOfflineOwnersGuardiansAttackOnlyHostilesWithinTheFirstLevelsPerception() {
    level(5);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    living(Zombie.class, at(10, 64, 0));
    Wolf wolf = living(Wolf.class, at(2, 64, 0));
    Mob body = golem.entity();
    when(wolf.getTarget()).thenReturn(body);

    assertThat(policy.select(golem, null)).isNull();

    Zombie close = living(Zombie.class, at(7, 64, 0));
    assertThat(policy.select(golem, null)).isSameAs(close);
    verify(core, never()).canEngage(any(Player.class), any(MinionTargeting.Candidate.class));
    verify(core, never()).recentAttacker(any(), anyLong());
  }

  @Test
  void interceptTurnsMobsChasingTheOwnerOntoTheGolemEveryThreeSeconds() {
    level(2);
    GolemHandle golem = golem(GolemBody.IRON, GolemMode.WORK, at(0, 64, 0));
    Zombie chaser = living(Zombie.class, at(4, 64, 0));
    when(chaser.getTarget()).thenReturn(owner);
    Skeleton elsewhere = living(Skeleton.class, at(5, 64, 0));
    Player stranger = player(UUID.randomUUID(), at(20, 64, 0));
    when(elsewhere.getTarget()).thenReturn(stranger);

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
    Zombie chaser = living(Zombie.class, at(4, 64, 0));
    when(chaser.getTarget()).thenReturn(owner);

    policy.tick(golem, owner);
    level(5);
    policy.tick(golem, null);

    verify(chaser, never()).setTarget(any());
  }

  @Test
  void bodiesTheGuardianCoreIsNotAllowedOnNeverTarget() {
    GolemHandle golem = golem(GolemBody.COPPER, GolemMode.WORK, at(0, 64, 0));
    living(Zombie.class, at(2, 64, 0));

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

  private GolemHandle golem(GolemBody body, GolemMode mode, Location location) {
    Mob entity = switch (body) {
      case FROST -> living(Snowman.class, location);
      default -> living(IronGolem.class, location);
    };
    nearby.remove(entity);
    nearby.add(entity);
    GolemState state = mock(GolemState.class);
    lenient().when(state.mode()).thenReturn(mode);
    lenient().when(state.home()).thenReturn(GolemState.Anchor.of(WORLD, 0, 64, 0));
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

  private Player player(UUID id, Location location) {
    Player player = living(Player.class, location);
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    nearby.remove(player);
    return player;
  }

  private <T extends LivingEntity> T living(Class<T> type, Location location) {
    T entity = mock(type);
    AtomicReference<Location> position = new AtomicReference<>(location);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.getLocation()).thenAnswer(invocation -> position.get().clone());
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    positions.put(entity, position);
    nearby.add(entity);
    return entity;
  }

  private void move(Entity entity, Location location) {
    positions.get(entity).set(location);
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }
}
