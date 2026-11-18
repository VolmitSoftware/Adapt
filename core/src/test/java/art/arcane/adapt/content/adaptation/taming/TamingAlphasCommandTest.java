package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TamingAlphasCommandTest {
  @Test
  void commandGestureRequiresMainHandSneakLeftClickPath() {
    assertThat(TamingAlphasCommand.isCommandGesture(InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND)).isTrue();
    assertThat(TamingAlphasCommand.isCommandGesture(InteractAction.LEFT_CLICK_BLOCK, Hand.MAIN_HAND)).isTrue();
    assertThat(TamingAlphasCommand.isCommandGesture(InteractAction.LEFT_CLICK_AIR, Hand.OFF_HAND)).isFalse();
    assertThat(TamingAlphasCommand.isCommandGesture(InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND)).isFalse();
  }

  @Test
  void commandRegistersRaycastAndDirectHitBridgesAtTheSamePriority() throws NoSuchMethodException {
    Method raycast = TamingAlphasCommand.class.getDeclaredMethod("on", PlayerInteractEvent.class);
    Method directHit = TamingAlphasCommand.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    Handle raycastHandler = raycast.getAnnotation(Handle.class);
    Handle directHitHandler = directHit.getAnnotation(Handle.class);

    assertThat(raycastHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(raycastHandler.ignoreCancelled()).isTrue();
    assertThat(directHitHandler.priority()).isEqualTo(EventPriority.HIGH);
    assertThat(directHitHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void onlyCombatCapableTameablesJoinTheCommand() {
    assertThat(TamingAlphasCommand.isCombatCapableTameable(tameableMob("wolf"))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(tameableMob("cat"))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(tameableMob("llama"))).isTrue();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(tameableMob("parrot"))).isFalse();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(tameableMob("horse"))).isFalse();
    assertThat(TamingAlphasCommand.isCombatCapableTameable(mock(PlayerRef.class))).isFalse();
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

  private static MobRef tameableMob(String type) {
    MobRef mob = mock(MobRef.class);
    when(mob.type()).thenReturn(Id.minecraft(type));
    when(mob.has(EntityTrait.TAMEABLE)).thenReturn(true);
    return mob;
  }
}
