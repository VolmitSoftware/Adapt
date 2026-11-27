package art.arcane.adapt.content.minion;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

class ServantOrderTest {
  private final WorldRef world = mock(WorldRef.class);

  @Test
  void aFreeServantHasNoModeAnchorOrCommand() {
    assertThat(ServantOrder.FREE.mode()).isEqualTo(ServantOrder.Mode.FREE);
    assertThat(ServantOrder.FREE.anchor()).isNull();
    assertThat(ServantOrder.FREE.commanded()).isFalse();
    assertThat(ServantOrder.FREE.isFree()).isTrue();
  }

  @Test
  void followAndHoldSetTheModeAndHoldKeepsACopyOfItsAnchor() {
    Loc spot = new Loc(world, 3D, 64D, -2D);
    ServantOrder hold = ServantOrder.hold(spot);
    Loc moved = spot.add(96D, 0D, 0D);

    assertThat(ServantOrder.follow().mode()).isEqualTo(ServantOrder.Mode.FOLLOW);
    assertThat(hold.mode()).isEqualTo(ServantOrder.Mode.HOLD);
    assertThat(hold.anchor().x()).isEqualTo(3D);
    assertThat(moved.x()).isEqualTo(99D);
    Loc shifted = hold.anchor().add(47D, 0D, 0D);
    assertThat(shifted.x()).isEqualTo(50D);
    assertThat(hold.anchor().x()).isEqualTo(3D);
    assertThat(hold.isFree()).isFalse();
    assertThatNullPointerException().isThrownBy(() -> ServantOrder.hold(null));
  }

  @Test
  void aCommandKeepsTheModeAndHoldsUntilItExpiresOrTheTargetIsLost() {
    LivingRef target = mock(MobRef.class);
    ServantOrder commanded = ServantOrder.follow().command(target, 20_000L);

    assertThat(commanded.mode()).isEqualTo(ServantOrder.Mode.FOLLOW);
    assertThat(commanded.target()).isSameAs(target);
    assertThat(commanded.commanded()).isTrue();
    assertThat(commanded.commandHolds(19_999L, true)).isTrue();
    assertThat(commanded.commandHolds(20_000L, true)).isFalse();
    assertThat(commanded.commandHolds(10_000L, false)).isFalse();
    assertThat(ServantOrder.FREE.command(target, 5_000L).isFree()).isFalse();
  }

  @Test
  void releasingACommandKeepsTheModeAndAnchor() {
    LivingRef target = mock(MobRef.class);
    ServantOrder hold = ServantOrder.hold(new Loc(world, 1D, 64D, 1D));

    ServantOrder released = hold.command(target, 9_000L).released();

    assertThat(released.commanded()).isFalse();
    assertThat(released.target()).isNull();
    assertThat(released.untilMillis()).isZero();
    assertThat(released.mode()).isEqualTo(ServantOrder.Mode.HOLD);
    assertThat(released.anchor()).isEqualTo(new Loc(world, 1D, 64D, 1D));
    assertThat(ServantOrder.FREE.command(target, 1L).released().isFree()).isTrue();
  }
}
