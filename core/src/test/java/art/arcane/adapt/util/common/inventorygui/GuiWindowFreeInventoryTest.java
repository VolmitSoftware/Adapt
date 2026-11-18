package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GuiWindowFreeInventoryTest {
  @Test
  void aPickupInYourInventoryWhileAGolemancyMenuIsOpenGoesThrough() {
    GuiWindow window = new GuiWindow(mock(PlayerRef.class)).setViewportHeight(3).setFreePlayerInventory(true);

    ChestMenu menu = window.build();

    assertThat(menu.freePlayerInventory()).isTrue();
    assertThat(menu.toBuilder().build().freePlayerInventory()).isTrue();
  }

  @Test
  void clicksInTheMenuItselfOrInOtherMenusStayCancelled() {
    ChestMenu plain = new GuiWindow(mock(PlayerRef.class)).setViewportHeight(3).build();

    assertThat(plain.freePlayerInventory()).isFalse();
    assertThat(ChestMenu.builder("Plain", 1).build().freePlayerInventory()).isFalse();
  }
}
