package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.HostileDamagePool;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterAll;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillGolemancyServantKillTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000e01");
  private static final DataKey<String> SERVANT_KEY = DataKey.of(Id.parse("adapt:tragoul_servant_owner"), DataCodecs.STRING);

  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final WorldRef world = mock(WorldRef.class);
  private final PlayerRef owner = mock(PlayerRef.class);
  private MockedStatic<AdaptationGate> gate;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<GolemVitals> vitals;
  private SkillGolemancy skill;

  @BeforeAll
  static void registerKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  @AfterAll
  static void unregisterKinds() {
    MinionIdentity.unregisterKind(TragoulSkeletalServant.MINION_KIND);
  }

  @BeforeEach
  void setUp() {
    gate = mockStatic(AdaptationGate.class);
    gate.when(() -> AdaptationGate.shouldSkipWorld(any(), any())).thenReturn(false);
    gate.when(() -> AdaptationGate.shouldSkipPlayer(any(), any(), anyBoolean())).thenReturn(false);
    lenient().when(AdaptCore.platform().host().player(OWNER)).thenReturn(owner);
    lenient().when(AdaptCore.platform().host().animals()).thenReturn(mock(AnimalOps.class));
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(20D);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    lenient().when(world.uid()).thenReturn(UUID.randomUUID());
    lenient().when(owner.location()).thenAnswer(call -> new Loc(world, 10D, 64D, 0D));
    skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);
    lenient().doReturn(new SkillGolemancy.Config()).when(skill).getConfig();
    lenient().doReturn(true).when(skill).isRuntimeRegistered();
    lenient().doNothing().when(skill).xp(any(PlayerRef.class), any(Loc.class), anyDouble(), anyString());
  }

  @AfterEach
  void tearDown() {
    ServantFrame.unregister(frame);
    vitals.close();
    scheduling.close();
    gate.close();
  }

  @Test
  void aServantKillPaysTheShareOfTheKillValueToTheOwner() {
    when(frame.share(OWNER)).thenReturn(0.35D);
    ServantFrame.register(frame);

    skill.on(death(zombie(), servant()));

    verify(skill).xp(eq(owner), any(Loc.class), doubleThat(xp -> Math.abs(xp - 12.25D) < 1e-9), eq("golemancy:servant:zombie"));
  }

  @Test
  void aDistantOwnerEarnsTheDistantShareOfTheServantKill() {
    when(frame.share(OWNER)).thenReturn(1D);
    when(owner.location()).thenAnswer(call -> new Loc(world, 200D, 64D, 0D));
    ServantFrame.register(frame);

    skill.on(death(zombie(), servant()));

    verify(skill).xp(eq(owner), any(Loc.class), doubleThat(xp -> Math.abs(xp - 8.75D) < 1e-9), eq("golemancy:servant:zombie"));
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

    skill.on(death(entity(MobRef.class, true, Map.of(SERVANT_KEY, UUID.randomUUID().toString())), servant()));

    verifyNoGrant();
  }

  @Test
  void onlyHostileVictimsAreConsidered() {
    when(frame.share(OWNER)).thenReturn(1D);
    ServantFrame.register(frame);

    skill.on(death(entity(MobRef.class, false, Map.of()), servant()));

    verify(frame, never()).share(any(UUID.class));
    verifyNoGrant();
  }

  @Test
  void hitsFromPlayerControlledHostilesNeverRefillTheRepairPool() {
    MobRef golem = entity(MobRef.class, false, Map.of());
    lenient().when(golem.type()).thenReturn(Id.minecraft("iron_golem"));
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.health()).thenReturn(20D);
    EntityDamageByEntityEvent servantHit = hit(golem, servant());
    EntityDamageByEntityEvent zombieHit = hit(golem, zombie());

    skill.on(servantHit);
    verify(golem.data(), never()).set(eq(HostileDamagePool.POOL_KEY), anyDouble());

    skill.on(zombieHit);
    verify(golem.data()).set(eq(HostileDamagePool.POOL_KEY), eq(5D));
  }

  @Test
  void aKillByAnythingButAServantNeverAsksForTheShare() {
    ServantFrame.register(frame);

    skill.on(death(zombie(), entity(MobRef.class, true, Map.of())));

    verify(frame, never()).share(any(UUID.class));
    verifyNoGrant();
  }

  private void verifyNoGrant() {
    verify(skill, never()).xp(any(PlayerRef.class), any(Loc.class), anyDouble(), anyString());
  }

  private MobRef zombie() {
    MobRef zombie = entity(MobRef.class, true, Map.of());
    lenient().when(zombie.type()).thenReturn(Id.minecraft("zombie"));
    lenient().when(zombie.world()).thenReturn(world);
    lenient().when(zombie.location()).thenAnswer(call -> new Loc(world, 0D, 64D, 0D));
    return zombie;
  }

  private static MobRef servant() {
    return entity(MobRef.class, true, Map.of(SERVANT_KEY, OWNER.toString()));
  }

  private static EntityDamageByEntityEvent hit(LivingRef victim, EntityRef damager) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.entity()).thenReturn(victim);
    when(event.damager()).thenReturn(damager);
    when(event.finalDamage()).thenReturn(5D);
    return event;
  }

  private static EntityDeathEvent death(LivingRef victim, EntityRef killer) {
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.directDamager()).thenReturn(killer);
    when(event.entity()).thenReturn(victim);
    return event;
  }

  private static <T extends LivingRef> T entity(Class<T> type, boolean hostile, Map<DataKey<String>, String> values) {
    T entity = mock(type);
    MemoryData data = spy(new MemoryData());
    values.forEach(data::set);
    lenient().when(entity.data()).thenReturn(data);
    lenient().when(entity.has(EntityTrait.ENEMY)).thenReturn(hostile);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    return entity;
  }
}
