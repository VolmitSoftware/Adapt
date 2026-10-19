package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.HostileDamagePool;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGolemancyServantKillTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000e01");
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");

  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final World world = mock(World.class);
  private final Player owner = mock(Player.class);
  private MockedStatic<AdaptationGate> gate;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemVitals> vitals;
  private SkillGolemancy skill;

  @BeforeAll
  static void registerKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  @BeforeEach
  void setUp() {
    gate = mockStatic(AdaptationGate.class);
    gate.when(() -> AdaptationGate.shouldSkipWorld(any(), any())).thenReturn(false);
    gate.when(() -> AdaptationGate.shouldSkipPlayer(any(), any(), anyBoolean())).thenReturn(false);
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getPlayer(OWNER)).thenReturn(owner);
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(20D);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    lenient().when(owner.getLocation()).thenAnswer(call -> new Location(world, 10D, 64D, 0D));
    skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);
    lenient().doReturn(new SkillGolemancy.Config()).when(skill).getConfig();
    lenient().doReturn(true).when(skill).isRuntimeRegistered();
    lenient().doNothing().when(skill).xp(any(Player.class), any(Location.class), anyDouble(), anyString());
  }

  @AfterEach
  void tearDown() {
    ServantFrame.unregister(frame);
    vitals.close();
    scheduling.close();
    bukkit.close();
    gate.close();
  }

  @Test
  void aServantKillPaysTheShareOfTheKillValueToTheOwner() {
    when(frame.share(OWNER)).thenReturn(0.35D);
    ServantFrame.register(frame);

    skill.on(death(zombie(), servant()));

    verify(skill).xp(eq(owner), any(Location.class), doubleThat(xp -> Math.abs(xp - 12.25D) < 1e-9), eq("golemancy:servant:zombie"));
  }

  @Test
  void aDistantOwnerEarnsTheDistantShareOfTheServantKill() {
    when(frame.share(OWNER)).thenReturn(1D);
    when(owner.getLocation()).thenAnswer(call -> new Location(world, 200D, 64D, 0D));
    ServantFrame.register(frame);

    skill.on(death(zombie(), servant()));

    verify(skill).xp(eq(owner), any(Location.class), doubleThat(xp -> Math.abs(xp - 8.75D) < 1e-9), eq("golemancy:servant:zombie"));
  }

  @Test
  void aServantKillPaysNothingWithoutBoneFrame() {
    skill.on(death(zombie(), servant()));
    when(frame.share(OWNER)).thenReturn(0D);
    ServantFrame.register(frame);
    skill.on(death(zombie(), servant()));

    verify(frame).share(OWNER);
    verifyNoGrant();
  }

  @Test
  void killingAnotherMinionNeverPays() {
    when(frame.share(OWNER)).thenReturn(1D);
    ServantFrame.register(frame);

    skill.on(death(entity(Skeleton.class, Map.of(SERVANT_KEY, UUID.randomUUID().toString())), servant()));

    verifyNoGrant();
  }

  @Test
  void onlyHostileVictimsAreConsidered() {
    when(frame.share(OWNER)).thenReturn(1D);
    ServantFrame.register(frame);

    skill.on(death(entity(Cow.class, Map.of()), servant()));

    verify(frame, never()).share(any(UUID.class));
    verifyNoGrant();
  }

  @Test
  void hitsFromPlayerControlledHostilesNeverRefillTheRepairPool() {
    IronGolem golem = entity(IronGolem.class, Map.of());
    lenient().when(golem.getType()).thenReturn(EntityType.IRON_GOLEM);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getHealth()).thenReturn(20D);
    EntityDamageByEntityEvent servantHit = hit(golem, servant());
    EntityDamageByEntityEvent zombieHit = hit(golem, zombie());

    skill.on(servantHit);
    verify(golem.getPersistentDataContainer(), never()).set(eq(HostileDamagePool.POOL_KEY), eq(PersistentDataType.DOUBLE), anyDouble());

    skill.on(zombieHit);
    verify(golem.getPersistentDataContainer()).set(eq(HostileDamagePool.POOL_KEY), eq(PersistentDataType.DOUBLE), eq(5D));
  }

  @Test
  void aKillByAnythingButAServantNeverAsksForTheShare() {
    ServantFrame.register(frame);

    skill.on(death(zombie(), entity(Zombie.class, Map.of())));

    verify(frame, never()).share(any(UUID.class));
    verifyNoGrant();
  }

  private void verifyNoGrant() {
    verify(skill, never()).xp(any(Player.class), any(Location.class), anyDouble(), anyString());
  }

  private Zombie zombie() {
    Zombie zombie = entity(Zombie.class, Map.of());
    lenient().when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
    lenient().when(zombie.getWorld()).thenReturn(world);
    lenient().when(zombie.getLocation()).thenAnswer(call -> new Location(world, 0D, 64D, 0D));
    return zombie;
  }

  private static Skeleton servant() {
    return entity(Skeleton.class, Map.of(SERVANT_KEY, OWNER.toString()));
  }

  private static EntityDamageByEntityEvent hit(LivingEntity victim, Entity damager) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getEntity()).thenReturn(victim);
    when(event.getDamager()).thenReturn(damager);
    when(event.getFinalDamage()).thenReturn(5D);
    return event;
  }

  private static EntityDeathEvent death(LivingEntity victim, Entity killer) {
    EntityDamageByEntityEvent lastDamage = mock(EntityDamageByEntityEvent.class);
    when(lastDamage.getDamager()).thenReturn(killer);
    when(victim.getLastDamageCause()).thenReturn(lastDamage);
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private static <T extends LivingEntity> T entity(Class<T> type, Map<NamespacedKey, String> values) {
    T entity = mock(type);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.has(any(NamespacedKey.class))).thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(entity.getPersistentDataContainer()).thenReturn(data);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
