package art.arcane.adapt.content.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemGuardListenerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a5");
  private static final UUID RIVAL = UUID.fromString("00000000-0000-0000-0000-0000000000a6");

  private final Map<UUID, PlayerRef> online = new HashMap<>();
  private final Map<UUID, GolemHandle> handles = new HashMap<>();
  private final GolemGuardListener listener = new GolemGuardListener(online::get, entity -> Optional.ofNullable(handles.get(entity.uuid())));
  private WorldRef world;

  @BeforeEach
  void setUp() {
    BoundGolems.register();
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    BoundGolems.unregister();
  }

  @Test
  void snowballFromABoundFrostGolemNeverHitsItsOwner() {
    MobRef golem = bound(OWNER, GolemBody.FROST);
    ProjectileRef snowball = mock(ProjectileRef.class);
    when(snowball.shooter()).thenReturn(golem);
    EntityDamageByEntityEvent damage = damage(snowball, player(OWNER));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void ownerCannotHitTheirOwnGolem() {
    EntityDamageByEntityEvent damage = damage(player(OWNER), bound(OWNER, GolemBody.IRON));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void boundGolemNeverHitsSameOwnerGolems() {
    EntityDamageByEntityEvent damage = damage(bound(OWNER, GolemBody.IRON), bound(OWNER, GolemBody.FROST));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void boundGolemHitsOtherPlayersOnlyWhileItsOwnerIsOnlineAndThePolicyAllows() {
    PlayerRef rival = player(RIVAL);
    EntityDamageByEntityEvent offline = damage(bound(OWNER, GolemBody.IRON), rival);
    listener.on(offline);
    verify(offline).setCancelled(true);

    PlayerRef owner = player(OWNER);
    online.put(OWNER, owner);
    Adaptation<AdaptationConfig> policy = policyAdaptation();
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(GolemancyRuntime::protectionPolicy).thenReturn(Optional.<Adaptation<?>>of(policy));
      when(policy.canPVP(eq(owner), any(Loc.class))).thenReturn(true, false);

      EntityDamageByEntityEvent allowed = damage(bound(OWNER, GolemBody.IRON), rival);
      listener.on(allowed);
      verify(allowed, never()).setCancelled(anyBoolean());

      EntityDamageByEntityEvent denied = damage(bound(OWNER, GolemBody.IRON), rival);
      listener.on(denied);
      verify(denied).setCancelled(true);
    }
  }

  @Test
  void damageWithoutABoundGolemIsLeftAlone() {
    EntityDamageByEntityEvent damage = damage(living(MobRef.class, "zombie"), player(OWNER));

    listener.on(damage);

    verify(damage, never()).setCancelled(anyBoolean());
  }

  @Test
  void boundGolemNeverTargetsItsOwnerOrSameOwnerMinions() {
    MobRef golem = bound(OWNER, GolemBody.IRON);
    EntityTargetEvent owner = target(golem, player(OWNER));
    EntityTargetEvent sibling = target(golem, bound(OWNER, GolemBody.FROST));
    EntityTargetEvent zombie = target(golem, living(MobRef.class, "zombie"));

    listener.on(owner);
    listener.on(sibling);
    listener.on(zombie);

    verify(owner).setCancelled(true);
    verify(sibling).setCancelled(true);
    verify(zombie, never()).setCancelled(anyBoolean());
  }

  @Test
  void aBoundGolemWithCombatOffNeverTargetsAnything() {
    MobRef quiet = bound(OWNER, GolemBody.IRON);
    MobRef fighting = bound(OWNER, GolemBody.IRON);
    MobRef untracked = bound(OWNER, GolemBody.IRON);
    trackCombat(quiet, GolemCoreType.BLANK, true);
    trackCombat(fighting, GolemCoreType.BLANK, false);
    EntityTargetEvent quietZombie = target(quiet, living(MobRef.class, "zombie"));
    EntityTargetEvent fightingZombie = target(fighting, living(MobRef.class, "zombie"));
    EntityTargetEvent untrackedZombie = target(untracked, living(MobRef.class, "zombie"));

    listener.on(quietZombie);
    listener.on(fightingZombie);
    listener.on(untrackedZombie);

    verify(quietZombie).setCancelled(true);
    verify(fightingZombie, never()).setCancelled(anyBoolean());
    verify(untrackedZombie, never()).setCancelled(anyBoolean());
  }

  @Test
  void aGuardianWithAStoredOffCombatStillTargets() {
    MobRef guardian = bound(OWNER, GolemBody.IRON);
    trackCombat(guardian, GolemCoreType.GUARDIAN, true);
    EntityTargetEvent zombie = target(guardian, living(MobRef.class, "zombie"));

    listener.on(zombie);

    verify(zombie, never()).setCancelled(anyBoolean());
  }

  @Test
  void clearingATargetIsNeverCancelledForACombatOffGolem() {
    MobRef quiet = bound(OWNER, GolemBody.IRON);
    trackCombat(quiet, GolemCoreType.BLANK, true);
    EntityTargetEvent cleared = target(quiet, null);

    listener.on(cleared);

    verify(cleared, never()).setCancelled(anyBoolean());
  }

  @Test
  void snowballsFromAGolemWithCombatOffAreNeverThrown() {
    MobRef quiet = bound(OWNER, GolemBody.FROST);
    MobRef fighting = bound(OWNER, GolemBody.FROST);
    MobRef untracked = bound(OWNER, GolemBody.FROST);
    trackCombat(quiet, GolemCoreType.BLANK, true);
    trackCombat(fighting, GolemCoreType.BLANK, false);
    ProjectileLaunchEvent fromQuiet = launch(quiet);
    ProjectileLaunchEvent fromFighting = launch(fighting);
    ProjectileLaunchEvent fromUntracked = launch(untracked);

    listener.on(fromQuiet);
    listener.on(fromFighting);
    listener.on(fromUntracked);

    verify(fromQuiet).setCancelled(true);
    verify(fromFighting, never()).setCancelled(anyBoolean());
    verify(fromUntracked, never()).setCancelled(anyBoolean());
  }

  @Test
  void projectilesFromPlayersAndWildMobsAreLeftAlone() {
    ProjectileLaunchEvent fromPlayer = launch(player(OWNER));
    ProjectileLaunchEvent fromSkeleton = launch(living(MobRef.class, "zombie"));
    ProjectileLaunchEvent fromDispenser = launch(null);

    listener.on(fromPlayer);
    listener.on(fromSkeleton);
    listener.on(fromDispenser);

    verify(fromPlayer, never()).setCancelled(anyBoolean());
    verify(fromSkeleton, never()).setCancelled(anyBoolean());
    verify(fromDispenser, never()).setCancelled(anyBoolean());
  }

  @Test
  void boundCopperGolemsNeverBecomeStatues() {
    EntityChangeBlockEvent bound = blockChange(bound(OWNER, GolemBody.COPPER), Id.minecraft("oxidized_copper_golem_statue"));
    EntityChangeBlockEvent unbound = blockChange(living(MobRef.class, "zombie"), Id.minecraft("oxidized_copper_golem_statue"));
    EntityChangeBlockEvent other = blockChange(bound(OWNER, GolemBody.COPPER), Id.minecraft("copper_block"));

    listener.on(bound);
    listener.on(unbound);
    listener.on(other);

    verify(bound).setCancelled(true);
    verify(unbound, never()).setCancelled(anyBoolean());
    verify(other, never()).setCancelled(anyBoolean());
  }

  @Test
  void anEmptyHandCannotTakeTheToolABoundCopperGolemHolds() {
    MobRef holding = holding(bound(OWNER, GolemBody.COPPER), Id.minecraft("fishing_rod"));
    MobRef unbound = holding(living(MobRef.class, "zombie"), Id.minecraft("fishing_rod"));
    MobRef emptyHanded = holding(bound(OWNER, GolemBody.COPPER), Id.minecraft("air"));
    PlayerInteractEntityEvent bare = interact(holding, Id.minecraft("air"));
    PlayerInteractEntityEvent vanilla = interact(unbound, Id.minecraft("air"));
    PlayerInteractEntityEvent nothingHeld = interact(emptyHanded, Id.minecraft("air"));
    PlayerInteractEntityEvent withItem = interact(holding, Id.minecraft("copper_ingot"));

    listener.on(bare);
    listener.on(vanilla);
    listener.on(nothingHeld);
    listener.on(withItem);

    verify(bare).setCancelled(true);
    verify(vanilla, never()).setCancelled(anyBoolean());
    verify(nothingHeld, never()).setCancelled(anyBoolean());
    verify(withItem, never()).setCancelled(anyBoolean());
  }

  @Test
  void theEmptyOffHandCannotTakeAToolWhenTheMainHandHoldsSomethingElse() {
    MobRef golem = holding(bound(OWNER, GolemBody.COPPER), Id.minecraft("fishing_rod"));
    PlayerInteractEntityEvent event = interact(golem, Id.minecraft("stick"));
    ItemRef empty = GolemTestItems.stack(Id.minecraft("air"), 1);
    when(event.hand()).thenReturn(Hand.OFF_HAND);
    when(event.player().inventory().hand(Hand.OFF_HAND)).thenReturn(empty);

    listener.on(event);

    verify(event).setCancelled(true);
  }

  @Test
  void aPreciseEntityInteractionUsesTheSameToolProtection() {
    MobRef golem = holding(bound(OWNER, GolemBody.COPPER), Id.minecraft("fishing_rod"));
    PlayerInteractEntityEvent normal = interact(golem, Id.minecraft("air"));
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(normal.player(), golem, Hand.MAIN_HAND, new Vec3(0D, 0D, 0D));

    listener.on(event);

    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void boundFrostGolemsLeaveSnowOnlyWhenTheirTrailIsOn() {
    MobRef unloaded = bound(OWNER, GolemBody.FROST);
    MobRef trailOff = bound(OWNER, GolemBody.FROST);
    MobRef trailOn = bound(OWNER, GolemBody.FROST);
    track(trailOff, false);
    track(trailOn, true);
    BlockFormEvent fromUnloaded = blockForm(unloaded);
    BlockFormEvent fromTrailOff = blockForm(trailOff);
    BlockFormEvent fromTrailOn = blockForm(trailOn);
    BlockFormEvent fromWild = blockForm(living(MobRef.class, "zombie"));

    listener.on(fromUnloaded);
    listener.on(fromTrailOff);
    listener.on(fromTrailOn);
    listener.on(fromWild);

    verify(fromUnloaded).setCancelled(true);
    verify(fromTrailOff).setCancelled(true);
    verify(fromTrailOn, never()).setCancelled(anyBoolean());
    verify(fromWild, never()).setCancelled(anyBoolean());
  }

  @Test
  void everyCopperGolemStatueVariantCounts() {
    for (String stage : List.of("", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_")) {
      Id statue = Id.minecraft(stage + "copper_golem_statue");
      assertThat(GolemGuardListener.isCopperStatue(statue)).as(statue.toString()).isTrue();
    }
    for (Id material : List.of(Id.minecraft("copper_block"), Id.minecraft("copper_golem_spawn_egg"), Id.minecraft("stone"), Id.minecraft("air"))) {
      assertThat(GolemGuardListener.isCopperStatue(material)).as(material.toString()).isFalse();
    }
    assertThat(GolemGuardListener.isCopperStatue(Id.minecraft("waxed_weathered_copper_golem_statue"))).isTrue();
    assertThat(GolemGuardListener.isCopperStatue((Id) null)).isFalse();
  }

  private MobRef bound(UUID owner, GolemBody body) {
    MobRef golem = living(MobRef.class, body.entityType().path());
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), owner, body, GolemCoreType.BLANK, GolemAnchor.of(world.uid(), 0, 64, 0)))
        .write(golem);
    return golem;
  }

  private void track(MobRef golem, boolean snowTrail) {
    GolemState state = GolemState.read(golem).orElseThrow();
    state.setSnowTrail(snowTrail);
    handles.put(golem.uuid(), new GolemHandle(golem, state, new FakeGolemPlatform()));
  }

  private void trackCombat(MobRef golem, GolemCoreType core, boolean off) {
    GolemState state = GolemState.read(golem).orElseThrow();
    state.setCores(List.of(core));
    state.setCombatOff(off);
    handles.put(golem.uuid(), new GolemHandle(golem, state, new FakeGolemPlatform()));
  }

  private PlayerRef player(UUID id) {
    PlayerRef player = living(PlayerRef.class, "player");
    lenient().when(player.uuid()).thenReturn(id);
    return player;
  }

  @SuppressWarnings("unchecked")
  private static Adaptation<AdaptationConfig> policyAdaptation() {
    return mock(Adaptation.class);
  }

  private <T extends LivingRef> T living(Class<T> type, String entityType) {
    T entity = GolemTestEntities.entity(type, Id.minecraft(entityType));
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.location()).thenReturn(new Loc(world, 0.5D, 64D, 0.5D));
    return entity;
  }

  private static EntityDamageByEntityEvent damage(EntityRef damager, EntityRef victim) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.damager()).thenReturn(damager);
    when(event.entity()).thenReturn(victim);
    return event;
  }

  private static EntityTargetEvent target(EntityRef golem, LivingRef target) {
    EntityTargetEvent event = mock(EntityTargetEvent.class);
    when(event.entity()).thenReturn(golem);
    when(event.target()).thenReturn(target);
    return event;
  }

  private static ProjectileLaunchEvent launch(EntityRef shooter) {
    ProjectileRef snowball = mock(ProjectileRef.class);
    lenient().when(snowball.shooter()).thenReturn(shooter);
    ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
    lenient().when(event.projectile()).thenReturn(snowball);
    return event;
  }

  private static BlockFormEvent blockForm(EntityRef entity) {
    BlockFormEvent event = mock(BlockFormEvent.class);
    lenient().when(event.entity()).thenReturn(entity);
    return event;
  }

  private static MobRef holding(MobRef golem, Id held) {
    ItemRef shown = GolemTestItems.stack(held, 1);
    lenient().when(golem.equipment(EquipmentSlot.MAIN_HAND)).thenReturn(shown);
    return golem;
  }

  private PlayerInteractEntityEvent interact(EntityRef clicked, Id hand) {
    PlayerRef player = player(OWNER);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef item = GolemTestItems.stack(hand, 1);
    lenient().when(inventory.hand(Hand.MAIN_HAND)).thenReturn(item);
    lenient().when(player.inventory()).thenReturn(inventory);
    PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
    lenient().when(event.clicked()).thenReturn(clicked);
    lenient().when(event.player()).thenReturn(player);
    lenient().when(event.hand()).thenReturn(Hand.MAIN_HAND);
    return event;
  }

  private static EntityChangeBlockEvent blockChange(EntityRef entity, Id to) {
    EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
    BlockStateRef state = mock(BlockStateRef.class);
    lenient().when(state.type()).thenReturn(to);
    lenient().when(event.entity()).thenReturn(entity);
    lenient().when(event.to()).thenReturn(state);
    return event;
  }
}
