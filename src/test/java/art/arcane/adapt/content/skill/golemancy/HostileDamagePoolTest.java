package art.arcane.adapt.content.skill.golemancy;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostileDamagePoolTest {
  @Test
  void addAccumulatesUpToMaxHealth() {
    IronGolem golem = StoredEntityData.entity(IronGolem.class);

    assertThat(HostileDamagePool.add(golem, 30D, 100D)).isCloseTo(30D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(80D, offset(1e-9));
    assertThat(HostileDamagePool.add(golem, 50D, 100D)).isCloseTo(100D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(100D, offset(1e-9));
    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE))
        .isCloseTo(100D, offset(1e-9));
  }

  @Test
  void addIgnoresNonPositiveOrNonFiniteDamage() {
    IronGolem golem = StoredEntityData.entity(IronGolem.class);
    HostileDamagePool.add(golem, 10D, 100D);

    HostileDamagePool.add(golem, 0D, 100D);
    HostileDamagePool.add(golem, -5D, 100D);
    HostileDamagePool.add(golem, Double.NaN, 100D);
    HostileDamagePool.add(golem, Double.POSITIVE_INFINITY, 100D);

    assertThat(HostileDamagePool.pool(golem)).isCloseTo(10D, offset(1e-9));
  }

  @Test
  void consumePaysAtMostThePool() {
    IronGolem golem = StoredEntityData.entity(IronGolem.class);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(25D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.consume(golem, 25D)).isCloseTo(15D, offset(1e-9));
    assertThat(HostileDamagePool.pool(golem)).isZero();
    assertThat(HostileDamagePool.consume(golem, 25D)).isZero();
  }

  @Test
  void consumeRejectsNonPositiveOrNonFiniteAmounts() {
    IronGolem golem = StoredEntityData.entity(IronGolem.class);
    HostileDamagePool.add(golem, 40D, 100D);

    assertThat(HostileDamagePool.consume(golem, 0D)).isZero();
    assertThat(HostileDamagePool.consume(golem, -10D)).isZero();
    assertThat(HostileDamagePool.consume(golem, Double.NaN)).isZero();
    assertThat(HostileDamagePool.pool(golem)).isCloseTo(40D, offset(1e-9));
  }

  @Test
  void poolTreatsMissingOrCorruptValuesAsEmpty() {
    IronGolem fresh = StoredEntityData.entity(IronGolem.class);
    IronGolem negative = StoredEntityData.entity(IronGolem.class);
    IronGolem notANumber = StoredEntityData.entity(IronGolem.class);
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
}
