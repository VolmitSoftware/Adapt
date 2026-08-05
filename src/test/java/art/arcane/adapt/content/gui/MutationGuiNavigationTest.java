package art.arcane.adapt.content.gui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MutationGuiNavigationTest {
  @Test
  void escapeReturnsOnlyWhenCloseAllIsDisabledAndAParentExists() {
    assertThat(MutationGui.shouldReturnToParent(false, true)).isTrue();
    assertThat(MutationGui.shouldReturnToParent(true, true)).isFalse();
    assertThat(MutationGui.shouldReturnToParent(false, false)).isFalse();
  }

  @Test
  void backButtonRequiresBothConfigurationAndAParent() {
    assertThat(MutationGui.shouldShowBackButton(true, true)).isTrue();
    assertThat(MutationGui.shouldShowBackButton(false, true)).isFalse();
    assertThat(MutationGui.shouldShowBackButton(true, false)).isFalse();
  }
}
