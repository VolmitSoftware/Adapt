package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightEffect;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.api.insight.InsightStat;
import art.arcane.adapt.api.insight.InsightView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.localization.BuiltInText;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InsightActionBarTest {
  private MockedEngine engine;

  @BeforeEach
  void startEngine() {
    engine = new MockedEngine(TestCores.install());
    BuiltInText.install();
  }

  @AfterEach
  void stopEngine() {
    BuiltInText.uninstall();
    engine.close();
    TestCores.clear();
  }

  @Test
  void levelOneReadoutIsNameHealthArmorAndAttack() {
    InsightView view = InsightView.builder(1, Id.minecraft("zombie"), InsightKind.HOSTILE).health(17.5D, 20D)
        .armor(2D).stat(InsightStat.of(Attributes.ATTACK_DAMAGE, 3D)).build();

    String text = strip(InsightActionBar.text(view, "Zombie"));

    assertThat(text).isEqualTo("Zombie | ❤ 17.5/20 | Armor 2 | Attack 3");
  }

  @Test
  void higherLevelsAddNonZeroStatsEffectsAndOwnership() {
    InsightView view = InsightView.builder(1, Id.minecraft("wolf"), InsightKind.PASSIVE).name("Rex")
        .health(20D, 20D).absorption(4D).bond(InsightBond.OWNED, "Steve").tag(InsightReadout.STABLE_HAND)
        .stats(List.of(InsightStat.of(Attributes.ATTACK_DAMAGE, 4D), InsightStat.of(Attributes.MOVEMENT_SPEED, 0.3D),
            InsightStat.of(Attributes.ARMOR_TOUGHNESS, 0D)))
        .effect(InsightEffect.beneficial(Id.minecraft("speed"), 0, 100))
        .build();

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
    return InsightView.builder(1, Id.minecraft("cat"), InsightKind.PASSIVE).health(10D, 10D).bond(bond, owner).build();
  }

  private static String strip(String text) {
    return text.replaceAll(C.COLOR_CHAR + "[0-9a-fk-or]", "");
  }
}
