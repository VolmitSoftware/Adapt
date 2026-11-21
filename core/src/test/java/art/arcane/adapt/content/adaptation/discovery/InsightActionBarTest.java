package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightStatKind;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightActionBarTest {
  @Test
  void levelOneReadoutIsNameHealthArmorAndAttack() {
    InsightView view = new InsightView(1, "minecraft:zombie", "", InsightKind.HOSTILE, 17.5D, 20D, 0D, 2D,
        InsightBond.NONE, "", false, List.of(new InsightStat(InsightStatKind.ATTACK, 3D)), List.of());

    String text = strip(InsightActionBar.text(view, "Zombie"));

    assertThat(text).isEqualTo("Zombie | ❤ 17.5/20 | Armor 2 | Attack 3");
  }

  @Test
  void higherLevelsAddNonZeroStatsEffectsAndOwnership() {
    InsightView view = new InsightView(1, "minecraft:wolf", "Rex", InsightKind.PASSIVE, 20D, 20D, 4D, 0D,
        InsightBond.OWNED, "Steve", true,
        List.of(new InsightStat(InsightStatKind.ATTACK, 4D), new InsightStat(InsightStatKind.SPEED, 0.3D),
            new InsightStat(InsightStatKind.TOUGHNESS, 0D)),
        List.of(new InsightEffect("minecraft:speed", 0, 100, false)));

    String text = strip(InsightActionBar.text(view, "Wolf"));

    assertThat(text).startsWith("Rex | ❤ 20/20 +4 | Attack 4 | Speed 0")
        .contains("Effects 1", "Tamed by Steve", "Stable Hand enhanced")
        .doesNotContain("Armor", "Toughness");
  }

  @Test
  void ownershipStatesReadPlainly() {
    assertThat(strip(InsightActionBar.text(bond(InsightBond.YOURS, ""), "Cat"))).endsWith("| Yours");
    assertThat(strip(InsightActionBar.text(bond(InsightBond.WILD, ""), "Cat"))).endsWith("| Untamed");
    assertThat(strip(InsightActionBar.text(bond(InsightBond.OWNED, ""), "Cat"))).endsWith("| Tamed");
  }

  private static InsightView bond(InsightBond bond, String owner) {
    return new InsightView(1, "minecraft:cat", "", InsightKind.PASSIVE, 10D, 10D, 0D, 0D, bond, owner, false,
        List.of(), List.of());
  }

  private static String strip(String text) {
    return text.replaceAll(C.COLOR_CHAR + "[0-9a-fk-or]", "");
  }
}
