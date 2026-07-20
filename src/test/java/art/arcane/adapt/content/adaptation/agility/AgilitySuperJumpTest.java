package art.arcane.adapt.content.adaptation.agility;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilitySuperJumpTest {
  @Test
  void defaultConfigHasExactlyFourLevelsAndARealTwoAndAHalfBlockMaximum() {
    AgilitySuperJump.Config config = new AgilitySuperJump.Config();

    assertThat(config.maxLevel).isEqualTo(4);
    assertThat(config.minimumJumpHeight).isCloseTo(1.5D, within(1.0E-9D));
    assertThat(config.maximumJumpHeight).isCloseTo(2.5D, within(1.0E-9D));
  }

  @Test
  void jumpHeightScalesFromOneAndAHalfToTwoAndAHalfBlocksAcrossFourLevels() {
    assertThat(AgilitySuperJump.jumpHeight(1.5D, 2.5D, 1)).isCloseTo(1.5D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpHeight(1.5D, 2.5D, 2)).isCloseTo(1.8333333333333333D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpHeight(1.5D, 2.5D, 3)).isCloseTo(2.1666666666666665D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpHeight(1.5D, 2.5D, 4)).isCloseTo(2.5D, within(1.0E-9D));
  }

  @Test
  void finalLevelConvertsBlockHeightToTheCorrectJumpStrength() {
    double bonus = AgilitySuperJump.jumpStrengthBonus(2.5D);
    double resultingStrength = AgilityJumpPhysics.VANILLA_JUMP_STRENGTH + bonus;

    assertThat(resultingStrength).isCloseTo(0.6177493682564199D, within(1.0E-12D));
    assertThat(AgilityJumpPhysics.heightForStrength(resultingStrength)).isCloseTo(2.5D, within(1.0E-9D));
  }

  @Test
  void configReloadCannotRestoreTheLegacyThreeLevelCap() {
    AgilitySuperJump adaptation = new AgilitySuperJump();
    AgilitySuperJump.Config config = new AgilitySuperJump.Config();
    config.maxLevel = 3;

    adaptation.onConfigReload(null, config);

    assertThat(config.maxLevel).isEqualTo(4);
    assertThat(adaptation.getMaxLevel()).isEqualTo(4);
  }

  @Test
  void sneakBoostIgnoresCancelledStateChanges() throws NoSuchMethodException {
    Method handler = AgilitySuperJump.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void jumpRewardsObserveTheFinalUncancelledJump() throws NoSuchMethodException {
    Method handler = AgilitySuperJump.class.getDeclaredMethod("on", PlayerJumpEvent.class);
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
