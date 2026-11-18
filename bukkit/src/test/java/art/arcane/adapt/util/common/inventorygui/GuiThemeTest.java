package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.value.Id;
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
    when(model.material()).thenReturn(Id.minecraft("player_head"));
    try (MockedStatic<CustomModel> models = mockStatic(CustomModel.class)) {
      models.when(() -> CustomModel.get(Id.minecraft("arrow"), "gui", "navigation", "back")).thenReturn(model);
      GuiElement element = GuiTheme.element("back", Id.minecraft("arrow"), "gui", "navigation", "back");
      assertThat(element.getMaterial()).isEqualTo(Id.minecraft("player_head"));
      assertThat(element.getModel()).isSameAs(model);
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
    assertThat(GuiTheme.background(0, 0, 6)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    assertThat(GuiTheme.background(0, 1, 6)).isEqualTo(Id.minecraft("light_gray_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 0, 6)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 3, 6)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(5, 0, 6)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(5, 1, 6)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
  }

  @Test
  void shortWindowsDropTheHeaderBandInsteadOfPaintingSolidBlack() {
    assertThat(GuiTheme.background(0, 0, 3)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 0, 3)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 1, 3)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    assertThat(GuiTheme.background(2, 1, 3)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
  }

  @Test
  void twoRowWindowsAreAllBodyCheckerboard() {
    assertThat(GuiTheme.background(0, 0, 2)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(0, 1, 2)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 0, 2)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(1, 1, 2)).isEqualTo(Id.minecraft("gray_stained_glass_pane"));
  }

  @Test
  void outOfRangeHeightsAreClampedToTheSupportedWindow() {
    assertThat(GuiTheme.background(1, 0, 99)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(0, 0, 0)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
    assertThat(GuiTheme.background(0, 0, -3)).isEqualTo(Id.minecraft("black_stained_glass_pane"));
  }
}
