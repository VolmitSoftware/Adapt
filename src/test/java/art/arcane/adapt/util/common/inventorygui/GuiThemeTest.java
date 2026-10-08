package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.inventorygui.UIElement;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GuiThemeTest {
  @Test
  void configuredItemsRetainTheirBaseStackAndMaterial() {
    CustomModel model = mock(CustomModel.class);
    ItemStack item = mock(ItemStack.class);
    when(model.material()).thenReturn(Material.PLAYER_HEAD);
    when(model.toItemStack()).thenReturn(item);
    when(item.clone()).thenReturn(item);
    try (MockedStatic<CustomModel> models = mockStatic(CustomModel.class)) {
      models.when(() -> CustomModel.get(Material.ARROW, "gui", "navigation", "back")).thenReturn(model);
      UIElement element = GuiTheme.element("back", Material.ARROW, "gui", "navigation", "back");
      assertThat(element.getMaterial().getMaterial()).isEqualTo(Material.PLAYER_HEAD);
      assertThat(element.getBaseItemStack()).isSameAs(item);
    }
  }

  @Test
  void backgroundVariantsHaveStableIndependentModelPaths() {
    assertThat(GuiTheme.backgroundKey(0, 0, 6)).isEqualTo("header-even");
    assertThat(GuiTheme.backgroundKey(0, 1, 6)).isEqualTo("header-odd");
    assertThat(GuiTheme.backgroundKey(1, 0, 6)).isEqualTo("separator");
    assertThat(GuiTheme.backgroundKey(5, 0, 6)).isEqualTo("body-even");
    assertThat(GuiTheme.backgroundKey(5, 1, 6)).isEqualTo("body-odd");
    assertThat(GuiTheme.backgroundKey(0, 0, 2)).isEqualTo("body-even");
  }

  @Test
  void fullHeightWindowsKeepTheHeaderBandAndCheckerboardBody() {
    assertThat(GuiTheme.background(0, 0, 6)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(0, 1, 6)).isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 0, 6)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 3, 6)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(5, 0, 6)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(5, 1, 6)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
  }

  @Test
  void shortWindowsDropTheHeaderBandInsteadOfPaintingSolidBlack() {
    assertThat(GuiTheme.background(0, 0, 3)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 0, 3)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 1, 3)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(2, 1, 3)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
  }

  @Test
  void twoRowWindowsAreAllBodyCheckerboard() {
    assertThat(GuiTheme.background(0, 0, 2)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(0, 1, 2)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 0, 2)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(1, 1, 2)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
  }

  @Test
  void outOfRangeHeightsAreClampedToTheSupportedWindow() {
    assertThat(GuiTheme.background(1, 0, 99)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(0, 0, 0)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    assertThat(GuiTheme.background(0, 0, -3)).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
  }
}
