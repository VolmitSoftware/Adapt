package art.arcane.adapt.content.golem;

import art.arcane.adapt.engine.platform.EngineLastDamage;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
    MobRef golem = bound(GolemTestEntities.golem(GolemBody.IRON), GolemBody.IRON);

    assertThat(GolemKillCredit.isGolemKill(killedBy(golem))).isTrue();
  }

  @Test
  void aSnowballFromABoundFrostGolemIsAGolemKill() {
    MobRef snowman = bound(GolemTestEntities.golem(GolemBody.FROST), GolemBody.FROST);
    ProjectileRef snowball = mock(ProjectileRef.class);
    when(snowball.shooter()).thenReturn(snowman);

    assertThat(GolemKillCredit.isGolemKill(killedBy(snowball))).isTrue();
  }

  @Test
  void aPlayersKillingBlowIsNotAGolemKill() {
    assertThat(GolemKillCredit.isGolemKill(killedBy(GolemTestEntities.entity(PlayerRef.class, Id.minecraft("player"))))).isFalse();
  }

  @Test
  void anUnboundGolemsKillingBlowIsNotAGolemKill() {
    assertThat(GolemKillCredit.isGolemKill(killedBy(GolemTestEntities.golem(GolemBody.IRON)))).isFalse();
    MobRef unbound = GolemTestEntities.golem(GolemBody.FROST);
    ProjectileRef snowball = mock(ProjectileRef.class);
    when(snowball.shooter()).thenReturn(unbound);
    assertThat(GolemKillCredit.isGolemKill(killedBy(snowball))).isFalse();
  }

  @Test
  void deathsWithoutAnEntityKillingBlowAreNotGolemKills() {
    MobRef burned = mock(MobRef.class);
    when(burned.lastDamage()).thenReturn(new EngineLastDamage(DamageCause.FIRE_TICK, null, 2D));

    assertThat(GolemKillCredit.isGolemKill(burned)).isFalse();
    assertThat(GolemKillCredit.isGolemKill(mock(MobRef.class))).isFalse();
    assertThat(GolemKillCredit.isGolemKill(null)).isFalse();
  }

  @Test
  void aGolemOwnedByAnotherRegionIsNeverReadFromThisThread() {
    MobRef golem = bound(GolemTestEntities.golem(GolemBody.IRON), GolemBody.IRON);
    LivingRef victim = killedBy(golem);
    clearInvocations(golem);
    AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
    try (MockedStatic<AdaptApi> scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS)) {
      scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
      when(adaptScheduler.isRegionized()).thenReturn(true);
      when(adaptScheduler.owns(golem)).thenReturn(false);

      assertThat(GolemKillCredit.isGolemKill(victim)).isFalse();
      verify(golem, never()).data();
    }
  }

  private static <T extends EntityRef> T bound(T golem, GolemBody body) {
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, body, GolemCoreType.BLANK, GolemAnchor.of(WORLD, 0, 64, 0)))
        .write(golem);
    return golem;
  }

  private static LivingRef killedBy(EntityRef damager) {
    LivingRef victim = mock(MobRef.class);
    when(victim.lastDamage()).thenReturn(new EngineLastDamage(DamageCause.ENTITY_ATTACK, damager, 4D));
    return victim;
  }
}
