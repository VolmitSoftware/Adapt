package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RangedArrowRecoveryChanceTest {
  @Test
  void chanceUsesConfiguredEntryForInBoundsLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 1)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 2)).isEqualTo(0.20D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 3)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToLastEntryWhenOperatorShrinksTable() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 8)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToFirstEntryForNonPositiveLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 0)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, -3)).isEqualTo(0.10D);
  }

  @Test
  void chanceIsZeroForMissingOrEmptyTable() {
    assertThat(RangedArrowRecovery.chanceFromTable(new double[0], 1)).isEqualTo(0.0D);
    assertThat(RangedArrowRecovery.chanceFromTable(null, 1)).isEqualTo(0.0D);
  }

  @Test
  void fullInventoryDropsOnlyTheReturnedLeftoverStack() {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    WorldRef world = mock(WorldRef.class);
    Loc location = new Loc(world, 1D, 64D, 2D);
    ItemRef recovered = mock(ItemRef.class);
    ItemRef leftover = mock(ItemRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(player.world()).thenReturn(world);
    when(player.location()).thenReturn(location);
    when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of(leftover));

    RangedArrowRecovery.deliverRecoveredArrow(player, recovered);

    verify(inventory).addItem(recovered);
    verify(world).dropItemNaturally(location.position(), leftover);
  }

  @Test
  void successfulInventoryDeliveryDoesNotDropAnotherArrow() {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    WorldRef world = mock(WorldRef.class);
    ItemRef recovered = mock(ItemRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(player.world()).thenReturn(world);
    when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());

    RangedArrowRecovery.deliverRecoveredArrow(player, recovered);

    verify(inventory).addItem(recovered);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }
}
