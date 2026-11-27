package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerBoundDropTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000d0a1");

  @Test
  void theDropBelongsToTheOwnerNeverDespawnsAndIsNotForMobs() {
    WorldRef world = mock(WorldRef.class);
    when(world.minHeight()).thenReturn(-64);
    ItemRef core = mock(ItemRef.class);
    Loc at = new Loc(world, 4.5D, 70D, -2.5D);

    OwnerBoundDrop.drop(at, core, OWNER);

    ArgumentCaptor<Vec3> spot = ArgumentCaptor.forClass(Vec3.class);
    ArgumentCaptor<Consumer<ItemEntityRef>> setup = consumerCaptor();
    verify(world).dropItem(spot.capture(), eq(core), setup.capture());
    assertThat(spot.getValue()).isEqualTo(at.position());
    ItemEntityRef drop = mock(ItemEntityRef.class);
    setup.getValue().accept(drop);
    verify(drop).setOwner(OWNER);
    verify(drop).setInvulnerable(true);
    verify(drop).setUnlimitedLifetime(true);
    verify(drop).setCanMobPickup(false);
  }

  @Test
  void aDropBelowTheWorldIsRaisedJustAboveItsFloor() {
    WorldRef world = mock(WorldRef.class);
    when(world.minHeight()).thenReturn(-64);

    OwnerBoundDrop.drop(new Loc(world, 1D, -130D, 1D), mock(ItemRef.class), OWNER);

    ArgumentCaptor<Vec3> spot = ArgumentCaptor.forClass(Vec3.class);
    verify(world).dropItem(spot.capture(), any(ItemRef.class), any());
    assertThat(spot.getValue().y()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Loc(world, 0D, -63D, 0D), -64).y()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Loc(world, 0D, Double.NaN, 0D), -64).y()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Loc(world, 0D, 12.25D, 0D), -64).y()).isEqualTo(12.25D);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<ItemEntityRef>> consumerCaptor() {
    return ArgumentCaptor.forClass((Class<Consumer<ItemEntityRef>>) (Class<?>) Consumer.class);
  }
}
