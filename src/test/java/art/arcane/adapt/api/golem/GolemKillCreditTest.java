package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemKillCreditTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Test
  void aBoundGolemsKillingBlowIsAGolemKill() {
    IronGolem golem = bound(GolemTestEntities.entity(IronGolem.class), GolemBody.IRON);

    assertThat(GolemKillCredit.isGolemKill(killedBy(golem))).isTrue();
  }

  @Test
  void aSnowballFromABoundFrostGolemIsAGolemKill() {
    Snowman snowman = bound(GolemTestEntities.entity(Snowman.class), GolemBody.FROST);
    Snowball snowball = mock(Snowball.class);
    when(snowball.getShooter()).thenReturn(snowman);

    assertThat(GolemKillCredit.isGolemKill(killedBy(snowball))).isTrue();
  }

  @Test
  void aPlayersKillingBlowIsNotAGolemKill() {
    assertThat(GolemKillCredit.isGolemKill(killedBy(mock(Player.class)))).isFalse();
  }

  @Test
  void anUnboundGolemsKillingBlowIsNotAGolemKill() {
    assertThat(GolemKillCredit.isGolemKill(killedBy(GolemTestEntities.entity(IronGolem.class)))).isFalse();
    Snowman unbound = GolemTestEntities.entity(Snowman.class);
    Snowball snowball = mock(Snowball.class);
    when(snowball.getShooter()).thenReturn(unbound);
    assertThat(GolemKillCredit.isGolemKill(killedBy(snowball))).isFalse();
  }

  @Test
  void deathsWithoutAnEntityKillingBlowAreNotGolemKills() {
    Zombie burned = mock(Zombie.class);
    when(burned.getLastDamageCause()).thenReturn(mock(EntityDamageEvent.class));

    assertThat(GolemKillCredit.isGolemKill(burned)).isFalse();
    assertThat(GolemKillCredit.isGolemKill(mock(Zombie.class))).isFalse();
    assertThat(GolemKillCredit.isGolemKill(null)).isFalse();
  }

  @Test
  void aGolemOwnedByAnotherRegionIsNeverReadFromThisThread() {
    IronGolem golem = bound(GolemTestEntities.entity(IronGolem.class), GolemBody.IRON);
    Zombie victim = killedBy(golem);
    clearInvocations(golem);
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(golem)).thenReturn(false);

      assertThat(GolemKillCredit.isGolemKill(victim)).isFalse();
      verify(golem, never()).getPersistentDataContainer();
    }
  }

  private static <T extends Entity> T bound(T golem, GolemBody body) {
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 64, 0)))
        .write(golem);
    return golem;
  }

  private static Zombie killedBy(Entity damager) {
    EntityDamageByEntityEvent lastDamage = mock(EntityDamageByEntityEvent.class);
    when(lastDamage.getDamager()).thenReturn(damager);
    Zombie victim = mock(Zombie.class);
    when(victim.getLastDamageCause()).thenReturn(lastDamage);
    return victim;
  }
}
