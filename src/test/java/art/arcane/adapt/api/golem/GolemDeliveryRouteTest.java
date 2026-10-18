package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemDeliveryRouteTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final GolemState.Anchor CHEST = GolemState.Anchor.of(WORLD, 0, 64, 0);
  private static final NodeKey NEAR = new NodeKey(WORLD, 2, 64, 0, BlockFace.UP);
  private static final NodeKey FAR = new NodeKey(WORLD, 12, 64, 0, BlockFace.UP);

  @Test
  void aUsableDepositWinsOverTheHomeChest() {
    GolemDeliveryRoute.Route route = GolemDeliveryRoute.choose(List.of(usable(NEAR, 3, 4D)), home(true, true));

    assertThat(route.kind()).isEqualTo(GolemDeliveryRoute.Kind.DEPOSIT);
    assertThat(route.deposit()).isEqualTo(NEAR);
  }

  @Test
  void theHomeChestTakesItemsNoDepositCanTake() {
    List<GolemDeliveryRoute.DepositOption> deposits = List.of(
        new GolemDeliveryRoute.DepositOption(NEAR, 3, 4D, false, true, true, true),
        new GolemDeliveryRoute.DepositOption(FAR, 3, 9D, true, false, true, true),
        new GolemDeliveryRoute.DepositOption(new NodeKey(WORLD, 3, 64, 0, BlockFace.UP), 3, 16D, true, true, false, true),
        new GolemDeliveryRoute.DepositOption(new NodeKey(WORLD, 4, 64, 0, BlockFace.UP), 3, 25D, true, true, true, false));

    GolemDeliveryRoute.Route route = GolemDeliveryRoute.choose(deposits, home(true, true));

    assertThat(route.kind()).isEqualTo(GolemDeliveryRoute.Kind.HOME_CHEST);
    assertThat(route.chest()).isEqualTo(CHEST);
  }

  @Test
  void theGolemHoldsItsItemsWhenNothingCanTakeThem() {
    assertThat(GolemDeliveryRoute.choose(List.of(), GolemDeliveryRoute.HomeOption.NONE).kind()).isEqualTo(GolemDeliveryRoute.Kind.HOLD);
    assertThat(GolemDeliveryRoute.choose(List.of(), home(false, true)).kind()).isEqualTo(GolemDeliveryRoute.Kind.HOLD);
    assertThat(GolemDeliveryRoute.choose(List.of(), home(true, false)).kind()).isEqualTo(GolemDeliveryRoute.Kind.HOLD);
  }

  @Test
  void depositsAreChosenByPriorityThenDistance() {
    List<GolemDeliveryRoute.DepositOption> deposits = List.of(
        usable(NEAR, 2, 4D),
        usable(FAR, 4, 144D),
        usable(new NodeKey(WORLD, 6, 64, 0, BlockFace.UP), 4, 36D));

    assertThat(GolemDeliveryRoute.choose(deposits, GolemDeliveryRoute.HomeOption.NONE).deposit())
        .isEqualTo(new NodeKey(WORLD, 6, 64, 0, BlockFace.UP));
  }

  private static GolemDeliveryRoute.DepositOption usable(NodeKey key, int priority, double distanceSquared) {
    return new GolemDeliveryRoute.DepositOption(key, priority, distanceSquared, true, true, true, true);
  }

  private static GolemDeliveryRoute.HomeOption home(boolean room, boolean accessible) {
    return new GolemDeliveryRoute.HomeOption(CHEST, room, accessible);
  }
}
