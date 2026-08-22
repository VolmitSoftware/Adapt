package art.arcane.adapt.util.common.inventorygui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuiThemeTest {
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
