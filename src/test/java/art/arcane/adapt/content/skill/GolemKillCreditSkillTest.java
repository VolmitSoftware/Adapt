package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemState;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemKillCreditSkillTest {
  @Test
  void stealthSkipsKillCreditWhenABoundGolemLandsTheKillingBlow() {
    Player player = mock(Player.class);
    IronGolem golem = withData(mock(IronGolem.class));
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    SkillStealth stealth = mock(SkillStealth.class, CALLS_REAL_METHODS);

    stealth.on(death(player, golem));

    verify(player, never()).isSneaking();
  }

  @Test
  void stealthStillCreditsThePlayersOwnKillingBlow() {
    Player player = mock(Player.class);
    SkillStealth stealth = mock(SkillStealth.class, CALLS_REAL_METHODS);

    stealth.on(death(player, player));

    verify(player).isSneaking();
  }

  @Test
  void hunterPaysNothingForAnAnimalARancherCulledAfterTheOwnersHit() {
    Player owner = mock(Player.class);
    SkillHunter hunter = mock(SkillHunter.class, CALLS_REAL_METHODS);

    hunter.on(culled(owner, boundIronGolem()));

    verify(hunter, never()).getConfig();
  }

  @Test
  void tamingPaysNothingForARancherCullOrARancherBreeding() {
    SkillTaming taming = mock(SkillTaming.class, CALLS_REAL_METHODS);
    EntityBreedEvent breeding = mock(EntityBreedEvent.class);
    when(breeding.getBreeder()).thenReturn(null);

    taming.on(culled(null, boundIronGolem()));
    taming.on(breeding);

    verify(taming, never()).getConfig();
  }

  private static IronGolem boundIronGolem() {
    IronGolem golem = withData(mock(IronGolem.class));
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.RANCHER,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
    return golem;
  }

  private static EntityDeathEvent culled(Player killer, Entity golem) {
    EntityDamageByEntityEvent lastDamage = mock(EntityDamageByEntityEvent.class);
    when(lastDamage.getDamager()).thenReturn(golem);
    Cow victim = withData(mock(Cow.class));
    when(victim.getKiller()).thenReturn(killer);
    when(victim.getLastDamageCause()).thenReturn(lastDamage);
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private static EntityDeathEvent death(Player killer, Entity lastDamager) {
    EntityDamageByEntityEvent lastDamage = mock(EntityDamageByEntityEvent.class);
    when(lastDamage.getDamager()).thenReturn(lastDamager);
    Zombie victim = withData(mock(Zombie.class));
    when(victim.getKiller()).thenReturn(killer);
    when(victim.getLastDamageCause()).thenReturn(lastDamage);
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private static <T extends Entity> T withData(T entity) {
    Map<String, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(data.has(any(), any(PersistentDataType.class))).thenAnswer(invocation -> values.containsKey(invocation.getArgument(0).toString()));
    when(data.get(any(), any(PersistentDataType.class))).thenAnswer(invocation -> values.get(invocation.getArgument(0).toString()));
    doAnswer(invocation -> values.put(invocation.getArgument(0).toString(), invocation.getArgument(2)))
        .when(data).set(any(), any(PersistentDataType.class), any());
    when(entity.getPersistentDataContainer()).thenReturn(data);
    return entity;
  }
}
