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
    assertThat(TamePackCommand.isCommandGesture(InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND)).isTrue();
    assertThat(TamePackCommand.isCommandGesture(InteractAction.LEFT_CLICK_BLOCK, Hand.MAIN_HAND)).isTrue();
    assertThat(TamePackCommand.isCommandGesture(InteractAction.LEFT_CLICK_AIR, Hand.OFF_HAND)).isFalse();
    assertThat(TamePackCommand.isCommandGesture(InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND)).isFalse();
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
    assertThat(TamePackCommand.isCombatCapableTameable(tameableMob("wolf"))).isTrue();
    assertThat(TamePackCommand.isCombatCapableTameable(tameableMob("cat"))).isTrue();
    assertThat(TamePackCommand.isCombatCapableTameable(tameableMob("llama"))).isTrue();
    assertThat(TamePackCommand.isCombatCapableTameable(tameableMob("parrot"))).isFalse();
    assertThat(TamePackCommand.isCombatCapableTameable(tameableMob("horse"))).isFalse();
    assertThat(TamePackCommand.isCombatCapableTameable(mock(PlayerRef.class))).isFalse();
  }

  @Test
  void successfulCommandsConsumeExactlyOneBoneOutsideCreative() {
    assertThat(TamePackCommand.boneAmountAfterSuccessfulCommand(GameMode.SURVIVAL, 3)).isEqualTo(2);
    assertThat(TamePackCommand.boneAmountAfterSuccessfulCommand(GameMode.ADVENTURE, 1)).isZero();
    assertThat(TamePackCommand.boneAmountAfterSuccessfulCommand(GameMode.CREATIVE, 3)).isEqualTo(3);
    assertThat(TamePackCommand.boneAmountAfterSuccessfulCommand(GameMode.SURVIVAL, 0)).isZero();
  }

  @Test
  void focusBuffAmountsMatchPotionParityPerAmplifierTier() {
    assertThat(TamePackCommand.focusDamageBonus(0)).isEqualTo(3.0D);
    assertThat(TamePackCommand.focusDamageBonus(1)).isEqualTo(6.0D);
    assertThat(TamePackCommand.focusDamageBonus(2)).isEqualTo(9.0D);
    assertThat(TamePackCommand.focusSpeedScalar(0)).isCloseTo(0.2D, within(1e-9));
    assertThat(TamePackCommand.focusSpeedScalar(1)).isCloseTo(0.4D, within(1e-9));
    assertThat(TamePackCommand.focusSpeedScalar(2)).isCloseTo(0.6D, within(1e-9));
  }

  private static MobRef tameableMob(String type) {
    MobRef mob = mock(MobRef.class);
    when(mob.type()).thenReturn(Id.minecraft(type));
    when(mob.has(EntityTrait.TAMEABLE)).thenReturn(true);
    return mob;
  }
}
