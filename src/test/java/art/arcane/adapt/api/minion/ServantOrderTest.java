package art.arcane.adapt.api.minion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

class ServantOrderTest {
  private final World world = mock(World.class);

  @Test
  void aFreeServantHasNoModeAnchorOrCommand() {
    assertThat(ServantOrder.FREE.mode()).isEqualTo(ServantOrder.Mode.FREE);
    assertThat(ServantOrder.FREE.anchor()).isNull();
    assertThat(ServantOrder.FREE.commanded()).isFalse();
    assertThat(ServantOrder.FREE.isFree()).isTrue();
  }

  @Test
  void followAndHoldSetTheModeAndHoldKeepsACopyOfItsAnchor() {
    Location spot = new Location(world, 3D, 64D, -2D);
    ServantOrder hold = ServantOrder.hold(spot);
    spot.setX(99D);

    assertThat(ServantOrder.follow().mode()).isEqualTo(ServantOrder.Mode.FOLLOW);
    assertThat(hold.mode()).isEqualTo(ServantOrder.Mode.HOLD);
    assertThat(hold.anchor().getX()).isEqualTo(3D);
    hold.anchor().setX(50D);
    assertThat(hold.anchor().getX()).isEqualTo(3D);
    assertThat(hold.isFree()).isFalse();
    assertThatNullPointerException().isThrownBy(() -> ServantOrder.hold(null));
  }

  @Test
  void aCommandKeepsTheModeAndHoldsUntilItExpiresOrTheTargetIsLost() {
    LivingEntity target = mock(Zombie.class);
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
    LivingEntity target = mock(Zombie.class);
    ServantOrder hold = ServantOrder.hold(new Location(world, 1D, 64D, 1D));

    ServantOrder released = hold.command(target, 9_000L).released();

    assertThat(released.commanded()).isFalse();
    assertThat(released.target()).isNull();
    assertThat(released.untilMillis()).isZero();
    assertThat(released.mode()).isEqualTo(ServantOrder.Mode.HOLD);
    assertThat(released.anchor()).isEqualTo(new Location(world, 1D, 64D, 1D));
    assertThat(ServantOrder.FREE.command(target, 1L).released().isFree()).isTrue();
  }
}
