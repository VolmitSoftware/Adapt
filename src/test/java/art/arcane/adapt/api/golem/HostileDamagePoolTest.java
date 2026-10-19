package art.arcane.adapt.api.golem;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostileDamagePoolTest {
  @Test
  void addAccumulatesUpToMaxHealth() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);

    assertThat(HostileDamagePool.add(golem, 30D, 100D)).isCloseTo(30D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(80D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(100D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(100D, offset(1e-9));
    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE))
        .isCloseTo(100D, offset(1e-9));
  }

  @Test
  void addIgnoresNonPositiveOrNonFiniteDamage() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    HostileDamagePool.add(golem, 10D, 100D);

    HostileDamagePool.add(golem, 0D, 100D);
    HostileDamagePool.add(golem, -5D, 100D);
    HostileDamagePool.add(golem, Double.NaN, 100D);
    HostileDamagePool.add(golem, Double.POSITIVE_INFINITY, 100D);

    assertThat(HostileDamagePool.pool(golem)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void consumePaysAtMostThePool() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(25D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isZero();
    assertThat(HostileDamagePool.consume(golem, 25D)).isZero();
  }

  @Test
  void consumeRejectsNonPositiveOrNonFiniteAmounts() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 0D)).isZero();
    assertThat(HostileDamagePool.consume(golem, -10D)).isZero();
    assertThat(HostileDamagePool.consume(golem, Double.NaN)).isZero();
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(40D, offset(1e-9));
  }

  @Test
  void poolTreatsMissingOrCorruptValuesAsEmpty() {
    IronGolem fresh = GolemTestEntities.entity(IronGolem.class);
    IronGolem negative = GolemTestEntities.entity(IronGolem.class);
    IronGolem notANumber = GolemTestEntities.entity(IronGolem.class);
    negative.getPersistentDataContainer().set(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE, -4D);
    notANumber.getPersistentDataContainer().set(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE, Double.NaN);

    assertThat(HostileDamagePool.pool(fresh)).isZero();
    assertThat(HostileDamagePool.pool(negative)).isZero();
    assertThat(HostileDamagePool.pool(notANumber)).isZero();
  }

  @Test
  void onlyTheThreeVanillaGolemsKeepAPool() {
    assertThat(HostileDamagePool.isPoolBody(EntityType.IRON_GOLEM)).isTrue();
    assertThat(HostileDamagePool.isPoolBody(EntityType.SNOW_GOLEM)).isTrue();
    assertThat(HostileDamagePool.isPoolBody(EntityType.COPPER_GOLEM)).isTrue();
    assertThat(HostileDamagePool.isPoolBody(EntityType.ZOMBIE)).isFalse();
    assertThat(HostileDamagePool.isPoolBody(EntityType.PLAYER)).isFalse();
    assertThat(HostileDamagePool.isPoolBody(null)).isFalse();
  }

  @Test
  void hostileDamagersAreEnemiesAndTheirProjectiles() {
    Arrow skeletonArrow = mock(Arrow.class);
    when(skeletonArrow.getShooter()).thenReturn(mock(Skeleton.class));
    Arrow playerArrow = mock(Arrow.class);
    when(playerArrow.getShooter()).thenReturn(mock(Player.class));
    Snowball snowmanSnowball = mock(Snowball.class);
    when(snowmanSnowball.getShooter()).thenReturn(mock(Snowman.class));

    assertThat(HostileDamagePool.isHostileDamager(mock(Zombie.class))).isTrue();
    assertThat(HostileDamagePool.isHostileDamager(skeletonArrow)).isTrue();
    assertThat(HostileDamagePool.isHostileDamager(mock(Player.class))).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(playerArrow)).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(snowmanSnowball)).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(mock(Wolf.class))).isFalse();
    assertThat(HostileDamagePool.isHostileDamager(null)).isFalse();
  }

  @Test
  void boundGolemsKeepThePoolInTheirStateInsteadOfTheSeparateKey() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    bind(golem);

    assertThat(HostileDamagePool.add(golem, 30D, 100D)).isCloseTo(30D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 90D, 100D)).isCloseTo(100D, offset(1e-9));

    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE)).isNull();
    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isCloseTo(100D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(100D, offset(1e-9));
  }

  @Test
  void boundGolemsPayRepairsFromTheirState() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    bind(golem);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(25D, offset(1e-9));
    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isZero();
  }

  @Test
  void corruptBoundStateFallsBackToAnEmptyPool() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    golem.getPersistentDataContainer().set(GolemState.KEY, PersistentDataType.STRING, "{broken");

    assertThat(HostileDamagePool.pool(golem)).isZero();
    assertThat(HostileDamagePool.add(golem, 10D, 100D)).isZero();
    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE)).isNull();
  }

  private static void bind(IronGolem golem) {
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(UUID.randomUUID(), 0, 64, 0))).write(golem);
  }
}
