package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpgradeDeclarationTest {
  private static final Id UPGRADE = Id.of("kit", "forge/upgrade/anvil");
  private static final Id HAMMER = Id.of("kit", "forge/hammer");
  private static final Id TONGS = Id.of("kit", "forge/tongs");

  @Test
  void anUpgradeFusesSomeOfItsSources() {
    UpgradeDeclaration declaration = new UpgradeDeclaration(UPGRADE, () -> null, List.of(HAMMER, TONGS),
        Set.of(HAMMER), MutationPrice.UPGRADED);

    assertThat(declaration.sources()).containsExactly(HAMMER, TONGS);
    assertThat(declaration.fuses()).containsExactly(HAMMER);
  }

  @Test
  void anUpgradeCannotFuseAnAdaptationThatIsNotOneOfItsSources() {
    assertThatThrownBy(() -> new UpgradeDeclaration(UPGRADE, () -> null, List.of(HAMMER), Set.of(TONGS),
        MutationPrice.UPGRADED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(UPGRADE.toString())
        .hasMessageContaining(TONGS.toString());
  }
}
