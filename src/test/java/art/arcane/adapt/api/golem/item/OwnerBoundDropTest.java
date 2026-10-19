package art.arcane.adapt.api.golem.item;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
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
    World world = mock(World.class);
    when(world.getMinHeight()).thenReturn(-64);
    ItemStack core = mock(ItemStack.class);
    Location at = new Location(world, 4.5D, 70D, -2.5D);

    OwnerBoundDrop.drop(at, core, OWNER);

    ArgumentCaptor<Location> spot = ArgumentCaptor.forClass(Location.class);
    ArgumentCaptor<Consumer<? super Item>> setup = consumerCaptor();
    verify(world).dropItem(spot.capture(), eq(core), setup.capture());
    assertThat(spot.getValue()).isEqualTo(at);
    Item drop = mock(Item.class);
    setup.getValue().accept(drop);
    verify(drop).setOwner(OWNER);
    verify(drop).setInvulnerable(true);
    verify(drop).setUnlimitedLifetime(true);
    verify(drop).setCanMobPickup(false);
  }

  @Test
  void aDropBelowTheWorldIsRaisedJustAboveItsFloor() {
    World world = mock(World.class);
    when(world.getMinHeight()).thenReturn(-64);

    OwnerBoundDrop.drop(new Location(world, 1D, -130D, 1D), mock(ItemStack.class), OWNER);

    ArgumentCaptor<Location> spot = ArgumentCaptor.forClass(Location.class);
    verify(world).dropItem(spot.capture(), any(ItemStack.class), any());
    assertThat(spot.getValue().getY()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Location(world, 0D, -63D, 0D), -64).getY()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Location(world, 0D, Double.NaN, 0D), -64).getY()).isEqualTo(-63D);
    assertThat(OwnerBoundDrop.spot(new Location(world, 0D, 12.25D, 0D), -64).getY()).isEqualTo(12.25D);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<? super Item>> consumerCaptor() {
    return ArgumentCaptor.forClass((Class<Consumer<? super Item>>) (Class<?>) Consumer.class);
  }
}
