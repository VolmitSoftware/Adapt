package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
  private MockedStatic<AdaptationGate> gate;

  @BeforeAll
  static void registerKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  @BeforeEach
  void openTheWorldGate() {
    gate = mockStatic(AdaptationGate.class);
    gate.when(() -> AdaptationGate.shouldSkipWorld(any(), any())).thenReturn(false);
  }

  @AfterEach
  void tearDown() {
    ServantFrame.unregister(frame);
    gate.close();
  }

  @Test
  void aServantKillPaysNothingWithoutBoneFrame() {
    SkillGolemancy skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);

    skill.on(death(entity(Zombie.class, Map.of()), servant()));
    when(frame.share(OWNER)).thenReturn(0D);
    ServantFrame.register(frame);
    skill.on(death(entity(Zombie.class, Map.of()), servant()));

    verify(frame).share(OWNER);
    verify(skill, never()).getConfig();
  }

  @Test
  void killingAnotherMinionNeverPays() {
    SkillGolemancy skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);
    when(frame.share(OWNER)).thenReturn(1D);
    ServantFrame.register(frame);

    skill.on(death(entity(Skeleton.class, Map.of(SERVANT_KEY, UUID.randomUUID().toString())), servant()));

    verify(skill, never()).getConfig();
  }

  @Test
  void onlyHostileVictimsAreConsidered() {
    SkillGolemancy skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);
    when(frame.share(OWNER)).thenReturn(1D);
    ServantFrame.register(frame);

    skill.on(death(entity(Cow.class, Map.of()), servant()));

    verify(frame, never()).share(any(UUID.class));
    verify(skill, never()).getConfig();
  }

  @Test
  void aKillByAnythingButAServantNeverAsksForTheShare() {
    SkillGolemancy skill = mock(SkillGolemancy.class, CALLS_REAL_METHODS);
    ServantFrame.register(frame);

    skill.on(death(entity(Zombie.class, Map.of()), entity(Zombie.class, Map.of())));

    verify(frame, never()).share(any(UUID.class));
    verify(skill, never()).getConfig();
  }

  private static Skeleton servant() {
    return entity(Skeleton.class, Map.of(SERVANT_KEY, OWNER.toString()));
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
