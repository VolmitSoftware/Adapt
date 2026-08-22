package art.arcane.adapt.content.adaptation.taming;

import org.bukkit.GameMode;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class TamingAlphasCommandTest {
  @Test
  void commandGestureRequiresMainHandSneakLeftClickPath() {
    assertThat(TamingAlphasCommand.isCommandGesture(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND)).isTrue();
    assertThat(TamingAlphasCommand.isCommandGesture(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND)).isTrue();
    assertThat(TamingAlphasCommand.isCommandGesture(Action.LEFT_CLICK_AIR, EquipmentSlot.OFF_HAND)).isFalse();
    assertThat(TamingAlphasCommand.isCommandGesture(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND)).isFalse();
  }

  @Test
  void commandRegistersRaycastAndDirectHitBridgesAtTheSamePriority() throws NoSuchMethodException {
    Method raycast = TamingAlphasCommand.class.getDeclaredMethod("on", PlayerInteractEvent.class);
    Method directHit = TamingAlphasCommand.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler raycastHandler = raycast.getAnnotation(EventHandler.class);
    EventHandler directHitHandler = directHit.getAnnotation(EventHandler.class);

    assertThat(raycastHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(raycastHandler.ignoreCancelled()).isTrue();
    assertThat(directHitHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(directHitHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void onlyCombatCapableTameablesJoinTheCommand() {
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Wolf.class))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Cat.class))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Llama.class))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Parrot.class))).isFalse();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Horse.class))).isFalse();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(Player.class))).isFalse();
  }

  @Test
  void successfulCommandsConsumeExactlyOneBoneOutsideCreative() {
    assertThat(TamingAlphasCommand.boneAmountAfterSuccessfulCommand(GameMode.SURVIVAL, 3)).isEqualTo(2);
    assertThat(TamingAlphasCommand.boneAmountAfterSuccessfulCommand(GameMode.ADVENTURE, 1)).isZero();
    assertThat(TamingAlphasCommand.boneAmountAfterSuccessfulCommand(GameMode.CREATIVE, 3)).isEqualTo(3);
    assertThat(TamingAlphasCommand.boneAmountAfterSuccessfulCommand(GameMode.SURVIVAL, 0)).isZero();
  }

  @Test
  void focusBuffAmountsMatchPotionParityPerAmplifierTier() {
    assertThat(TamingAlphasCommand.focusDamageBonus(0)).isEqualTo(3.0D);
    assertThat(TamingAlphasCommand.focusDamageBonus(1)).isEqualTo(6.0D);
    assertThat(TamingAlphasCommand.focusDamageBonus(2)).isEqualTo(9.0D);
    assertThat(TamingAlphasCommand.focusSpeedScalar(0)).isCloseTo(0.2D, within(1e-9));
    assertThat(TamingAlphasCommand.focusSpeedScalar(1)).isCloseTo(0.4D, within(1e-9));
    assertThat(TamingAlphasCommand.focusSpeedScalar(2)).isCloseTo(0.6D, within(1e-9));
  }
}
