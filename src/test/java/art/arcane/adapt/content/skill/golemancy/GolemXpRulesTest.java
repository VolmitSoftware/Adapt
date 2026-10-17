package art.arcane.adapt.content.skill.golemancy;

import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemXpRulesTest {
  @Test
  void killValueAddsTheVictimsScaledMaxHealthToTheBase() {
    assertThat(GolemXpRules.killValue(15D, 20D, 1.0D)).isCloseTo(35D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 100D, 0.5D)).isCloseTo(65D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 0D, 1.0D)).isCloseTo(15D, offset(1e-9));
  }

  @Test
  void witnessXpPaysTheWitnessShareOfTheKillValue() {
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, false, 0.3D)).isCloseTo(12.25D, offset(1e-9));
  }

  @Test
  void witnessXpAppliesTheSpawnerMultiplierToSpawnerMobs() {
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 0.3D)).isCloseTo(3.675D, offset(1e-9));
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 1.0D)).isCloseTo(12.25D, offset(1e-9));
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 0D)).isZero();
  }

  @Test
  void witnessXpRejectsInvalidInputs() {
    assertThat(GolemXpRules.witnessXp(-35D, 0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, -0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(Double.NaN, 0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, Double.POSITIVE_INFINITY, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, Double.NaN)).isZero();
    assertThat(GolemXpRules.killValue(Double.NaN, 20D, 1.0D)).isZero();
  }

  @Test
  void repairXpPaysPerPointOfPaidHealth() {
    assertThat(GolemXpRules.repairXp(25D, 2.0D)).isCloseTo(50D, offset(1e-9));
    assertThat(GolemXpRules.repairXp(0D, 2.0D)).isZero();
    assertThat(GolemXpRules.repairXp(-3D, 2.0D)).isZero();
    assertThat(GolemXpRules.repairXp(25D, Double.NaN)).isZero();
  }

  @Test
  void witnessGolemResolvesIronAndSnowGolemsAndSnowGolemSnowballs() {
    IronGolem iron = mock(IronGolem.class);
    Snowman snow = mock(Snowman.class);
    Snowball snowGolemShot = mock(Snowball.class);
    when(snowGolemShot.getShooter()).thenReturn(snow);
    Snowball playerShot = mock(Snowball.class);
    when(playerShot.getShooter()).thenReturn(mock(Player.class));

    assertThat(GolemXpRules.witnessGolem(iron)).isSameAs(iron);
    assertThat(GolemXpRules.witnessGolem(snow)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(snowGolemShot)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(playerShot)).isNull();
    assertThat(GolemXpRules.witnessGolem(mock(CopperGolem.class))).isNull();
    assertThat(GolemXpRules.witnessGolem(mock(Zombie.class))).isNull();
    assertThat(GolemXpRules.witnessGolem(null)).isNull();
  }
}
