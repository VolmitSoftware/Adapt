package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.minion.MinionIdentity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
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

  private final Map<UUID, Player> online = new HashMap<>();
  private final Map<UUID, GolemHandle> handles = new HashMap<>();
  private final GolemGuardListener listener = new GolemGuardListener(online::get, entity -> Optional.ofNullable(handles.get(entity.getUniqueId())));
  private World world;

  @BeforeAll
  static void registerGolemKind() {
    MinionIdentity.registerKind(GolemancyRuntime.MINION_KIND);
  }

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.randomUUID());
  }

  @Test
  void snowballFromABoundFrostGolemNeverHitsItsOwner() {
    Snowman golem = bound(Snowman.class, OWNER, GolemBody.FROST);
    Snowball snowball = mock(Snowball.class);
    when(snowball.getShooter()).thenReturn(golem);
    EntityDamageByEntityEvent damage = damage(snowball, player(OWNER));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void ownerCannotHitTheirOwnGolem() {
    EntityDamageByEntityEvent damage = damage(player(OWNER), bound(IronGolem.class, OWNER, GolemBody.IRON));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void boundGolemNeverHitsSameOwnerGolems() {
    EntityDamageByEntityEvent damage = damage(bound(IronGolem.class, OWNER, GolemBody.IRON), bound(Snowman.class, OWNER, GolemBody.FROST));

    listener.on(damage);

    verify(damage).setCancelled(true);
  }

  @Test
  void boundGolemHitsOtherPlayersOnlyWhileItsOwnerIsOnlineAndThePolicyAllows() {
    Player rival = player(RIVAL);
    EntityDamageByEntityEvent offline = damage(bound(IronGolem.class, OWNER, GolemBody.IRON), rival);
    listener.on(offline);
    verify(offline).setCancelled(true);

    Player owner = player(OWNER);
    online.put(OWNER, owner);
    Adaptation<Object> policy = policyAdaptation();
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(GolemancyRuntime::protectionPolicy).thenReturn(Optional.of(policy));
      when(policy.canPVP(eq(owner), any(Location.class))).thenReturn(true, false);

      EntityDamageByEntityEvent allowed = damage(bound(IronGolem.class, OWNER, GolemBody.IRON), rival);
      listener.on(allowed);
      verify(allowed, never()).setCancelled(anyBoolean());

      EntityDamageByEntityEvent denied = damage(bound(IronGolem.class, OWNER, GolemBody.IRON), rival);
      listener.on(denied);
      verify(denied).setCancelled(true);
    }
  }

  @Test
  void damageWithoutABoundGolemIsLeftAlone() {
    EntityDamageByEntityEvent damage = damage(living(Zombie.class), player(OWNER));

    listener.on(damage);

    verify(damage, never()).setCancelled(anyBoolean());
  }

  @Test
  void boundGolemNeverTargetsItsOwnerOrSameOwnerMinions() {
    IronGolem golem = bound(IronGolem.class, OWNER, GolemBody.IRON);
    EntityTargetLivingEntityEvent owner = target(golem, player(OWNER));
    EntityTargetLivingEntityEvent sibling = target(golem, bound(Snowman.class, OWNER, GolemBody.FROST));
    EntityTargetLivingEntityEvent zombie = target(golem, living(Zombie.class));

    listener.on(owner);
    listener.on(sibling);
    listener.on(zombie);

    verify(owner).setCancelled(true);
    verify(sibling).setCancelled(true);
    verify(zombie, never()).setCancelled(anyBoolean());
  }

  @Test
  void boundCopperGolemsNeverBecomeStatues() {
    EntityChangeBlockEvent bound = blockChange(bound(CopperGolem.class, OWNER, GolemBody.COPPER), Material.OXIDIZED_COPPER_GOLEM_STATUE);
    EntityChangeBlockEvent unbound = blockChange(living(CopperGolem.class), Material.OXIDIZED_COPPER_GOLEM_STATUE);
    EntityChangeBlockEvent other = blockChange(bound(CopperGolem.class, OWNER, GolemBody.COPPER), Material.COPPER_BLOCK);

    listener.on(bound);
    listener.on(unbound);
    listener.on(other);

    verify(bound).setCancelled(true);
    verify(unbound, never()).setCancelled(anyBoolean());
    verify(other, never()).setCancelled(anyBoolean());
  }

  @Test
  void boundFrostGolemsLeaveSnowOnlyWhenTheirTrailIsOn() {
    Snowman unloaded = bound(Snowman.class, OWNER, GolemBody.FROST);
    Snowman trailOff = bound(Snowman.class, OWNER, GolemBody.FROST);
    Snowman trailOn = bound(Snowman.class, OWNER, GolemBody.FROST);
    track(trailOff, false);
    track(trailOn, true);
    EntityBlockFormEvent fromUnloaded = blockForm(unloaded);
    EntityBlockFormEvent fromTrailOff = blockForm(trailOff);
    EntityBlockFormEvent fromTrailOn = blockForm(trailOn);
    EntityBlockFormEvent fromWild = blockForm(living(Snowman.class));

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
    for (Material material : Material.values()) {
      boolean statue = material.name().contains("COPPER_GOLEM_STATUE");
      assertThat(GolemGuardListener.isCopperStatue(material)).as(material.name()).isEqualTo(statue);
    }
    assertThat(GolemGuardListener.isCopperStatue(Material.WAXED_WEATHERED_COPPER_GOLEM_STATUE)).isTrue();
    assertThat(GolemGuardListener.isCopperStatue(null)).isFalse();
  }

  private <T extends Mob> T bound(Class<T> type, UUID owner, GolemBody body) {
    T golem = living(type);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), owner, body, GolemCoreType.BLANK, GolemState.Anchor.of(world.getUID(), 0, 64, 0)))
        .write(golem);
    return golem;
  }

  private void track(Snowman golem, boolean snowTrail) {
    GolemState state = GolemState.read(golem).orElseThrow();
    state.setSnowTrail(snowTrail);
    handles.put(golem.getUniqueId(), new GolemHandle(golem, state, new FakeGolemPlatform()));
  }

  private Player player(UUID id) {
    Player player = living(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(id);
    return player;
  }

  @SuppressWarnings("unchecked")
  private static Adaptation<Object> policyAdaptation() {
    return mock(Adaptation.class);
  }

  private <T extends LivingEntity> T living(Class<T> type) {
    T entity = GolemTestEntities.entity(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    return entity;
  }

  private static EntityDamageByEntityEvent damage(Entity damager, Entity victim) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getDamager()).thenReturn(damager);
    when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private static EntityTargetLivingEntityEvent target(Entity golem, LivingEntity target) {
    EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
    when(event.getEntity()).thenReturn(golem);
    when(event.getTarget()).thenReturn(target);
    return event;
  }

  private static EntityBlockFormEvent blockForm(Entity entity) {
    EntityBlockFormEvent event = mock(EntityBlockFormEvent.class);
    lenient().when(event.getEntity()).thenReturn(entity);
    return event;
  }

  private static EntityChangeBlockEvent blockChange(Entity entity, Material to) {
    EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
    lenient().when(event.getEntity()).thenReturn(entity);
    lenient().when(event.getTo()).thenReturn(to);
    return event;
  }
}
