package art.arcane.adapt.bukkit.feature.menu;

import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class MenuFreeInventoryTest {
  @ParameterizedTest
  @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE", "PLACE_ALL", "PLACE_SOME", "PLACE_ONE", "SWAP_WITH_CURSOR"})
  void pickingUpAndPlacingInYourOwnInventoryIsAllowed(InventoryAction action) {
    assertThat(MenuWindow.allowsFreeInventoryAction(action)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = InventoryAction.class, names = {"MOVE_TO_OTHER_INVENTORY", "COLLECT_TO_CURSOR", "HOTBAR_SWAP", "DROP_ALL_SLOT", "CLONE_STACK", "NOTHING"})
  void movesThatCouldReachTheMenuStayBlocked(InventoryAction action) {
    assertThat(MenuWindow.allowsFreeInventoryAction(action)).isFalse();
  }
}
