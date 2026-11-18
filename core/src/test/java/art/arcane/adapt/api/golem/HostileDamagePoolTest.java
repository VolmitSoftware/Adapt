package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostileDamagePoolTest {
  @Test
  void addAccumulatesUpToMaxHealth() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);

    assertThat(HostileDamagePool.add(golem, 30D, 100D)).isCloseTo(30D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(80D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(100D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(100D, offset(1e-9));
    assertThat(golem.data().get(HostileDamagePool.POOL_KEY))
        .isCloseTo(100D, offset(1e-9));
  }

  @Test
  void addIgnoresNonPositiveOrNonFiniteDamage() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    HostileDamagePool.add(golem, 10D, 100D);

    HostileDamagePool.add(golem, 0D, 100D);
    HostileDamagePool.add(golem, -5D, 100D);
    HostileDamagePool.add(golem, Double.NaN, 100D);
    HostileDamagePool.add(golem, Double.POSITIVE_INFINITY, 100D);

    assertThat(HostileDamagePool.pool(golem)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void consumePaysAtMostThePool() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(25D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isZero();
    assertThat(HostileDamagePool.consume(golem, 25D)).isZero();
  }

  @Test
  void consumeRejectsNonPositiveOrNonFiniteAmounts() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 0D)).isZero();
    assertThat(HostileDamagePool.consume(golem, -10D)).isZero();
    assertThat(HostileDamagePool.consume(golem, Double.NaN)).isZero();
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(40D, offset(1e-9));
  }

  @Test
  void poolTreatsMissingOrCorruptValuesAsEmpty() {
    MobRef fresh = GolemTestEntities.golem(GolemBody.IRON);
    MobRef negative = GolemTestEntities.golem(GolemBody.IRON);
    MobRef notANumber = GolemTestEntities.golem(GolemBody.IRON);
    negative.data().set(HostileDamagePool.POOL_KEY, -4D);
    notANumber.data().set(HostileDamagePool.POOL_KEY, Double.NaN);

    assertThat(HostileDamagePool.pool(fresh)).isZero();
    assertThat(HostileDamagePool.pool(negative)).isZero();
    assertThat(HostileDamagePool.pool(notANumber)).isZero();
  }

  @Test
  void onlyTheThreeVanillaGolemsKeepAPool() {
    assertThat(HostileDamagePool.isPoolBody(Id.minecraft("iron_golem"))).isTrue();
    assertThat(HostileDamagePool.isPoolBody(Id.minecraft("snow_golem"))).isTrue();
    assertThat(HostileDamagePool.isPoolBody(Id.minecraft("copper_golem"))).isTrue();
    assertThat(HostileDamagePool.isPoolBody(Id.minecraft("zombie"))).isFalse();
    assertThat(HostileDamagePool.isPoolBody(Id.minecraft("player"))).isFalse();
    assertThat(HostileDamagePool.isPoolBody(null)).isFalse();
  }

  @Test
  void hostileDamagersAreEnemiesAndTheirProjectiles() {
    MobRef skeleton = living("skeleton", true);
    MobRef snowman = living("snow_golem", false);
    PlayerRef player = mock(PlayerRef.class);
    MobRef zombie = living("zombie", true);
    MobRef wolf = living("wolf", false);
    ProjectileRef skeletonArrow = mock(ProjectileRef.class);
    when(skeletonArrow.shooter()).thenReturn(skeleton);
    ProjectileRef playerArrow = mock(ProjectileRef.class);
    when(playerArrow.shooter()).thenReturn(player);
    ProjectileRef snowmanSnowball = mock(ProjectileRef.class);
    when(snowmanSnowball.shooter()).thenReturn(snowman);

    assertThat(HostileDamagePool.isHostileDamager(zombie)).isTrue();
    assertThat(HostileDamagePool.isHostileDamager(skeletonArrow)).isTrue();
    assertThat(HostileDamagePool.isHostileDamager(mock(PlayerRef.class))).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(playerArrow)).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(snowmanSnowball)).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(wolf)).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(null)).isFalse();
  }

  @Test
  void boundGolemsKeepThePoolInTheirStateInsteadOfTheSeparateKey() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    bind(golem);

    assertThat(HostileDamagePool.add(golem, 30D, 100D)).isCloseTo(30D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 90D, 100D)).isCloseTo(100D, offset(1e-9));

    assertThat(golem.data().get(HostileDamagePool.POOL_KEY)).isNull();
    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isCloseTo(100D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(100D, offset(1e-9));
  }

  @Test
  void boundGolemsPayRepairsFromTheirState() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    bind(golem);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(25D, offset(1e-9));
    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isZero();
  }

  @Test
  void corruptBoundStateFallsBackToAnEmptyPool() {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    golem.data().set(GolemState.KEY, "{broken");

    assertThat(HostileDamagePool.pool(golem)).isZero();
    assertThat(HostileDamagePool.add(golem, 10D, 100D)).isZero();
    assertThat(golem.data().get(HostileDamagePool.POOL_KEY)).isNull();
  }

  private static MobRef living(String type, boolean enemy) {
    MobRef mob = GolemTestEntities.mob(type);
    when(mob.has(EntityTrait.ENEMY)).thenReturn(enemy);
    return mob;
  }

  private static void bind(MobRef golem) {
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
  }
}
