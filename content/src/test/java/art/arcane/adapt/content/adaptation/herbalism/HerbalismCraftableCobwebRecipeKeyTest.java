package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismCraftableCobwebRecipeKeyTest {
  @Test
  void recipeKeyMatchesNamespacedKeyLowercaseNormalization() {
    assertThat(HerbalismCraftableCobweb.RECIPE_KEY)
        .isEqualTo(HerbalismCraftableCobweb.RECIPE_KEY.toLowerCase(Locale.ROOT));
  }

  @Test
  void recipeKeyUsesOnlyValidNamespacedKeyCharacters() {
    assertThat(HerbalismCraftableCobweb.RECIPE_KEY).matches("[a-z0-9._-]+");
  }
}
