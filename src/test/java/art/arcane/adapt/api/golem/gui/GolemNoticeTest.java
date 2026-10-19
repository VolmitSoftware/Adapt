package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GolemNoticeTest extends AdaptTestBase {
  private static final GolemRuneLayout LAYOUT = new GolemRuneLayout(2, List.of(1, 3, 5), 1, List.of(1, 3, 0), Set.of(GolemRuneType.EMBER));

  @Test
  void everyRuneStatusNoteRendersItsEnglishText() {
    GolemRune swiftness = new GolemRune(GolemRuneType.SWIFTNESS, 1);
    GolemRune might = new GolemRune(GolemRuneType.MIGHT, 2);
    GolemRune tide = new GolemRune(GolemRuneType.TIDE, 1);
    GolemRune ember = new GolemRune(GolemRuneType.EMBER, 1);
    GolemRune order = new GolemRune(GolemRuneType.ORDER, 1);
    GolemRune bulwark = new GolemRune(GolemRuneType.BULWARK, 3);

    assertThat(render(LAYOUT, List.of(swiftness, might, tide), 0, GolemBody.IRON)).isEqualTo("Active");
    assertThat(render(LAYOUT, List.of(swiftness, might, tide), 1, GolemBody.IRON)).isEqualTo("Inert: tier 2 needs Runic Augments level 3");
    assertThat(render(LAYOUT, List.of(swiftness, might, tide), 2, GolemBody.IRON)).isEqualTo("Inert: this slot needs Runic Augments level 5");
    assertThat(render(LAYOUT, List.of(ember), 0, GolemBody.FROST)).isEqualTo("Inert: Ember runes don't work in a Frost golem");
    assertThat(render(LAYOUT, List.of(order, order), 1, GolemBody.COPPER)).isEqualTo("Inert: this golem already has a Order rune");
    assertThat(render(LAYOUT, List.of(bulwark), 0, GolemBody.BONE)).isEqualTo("Inert: your Runic Augments can't power this rune");
  }

  @Test
  void everyRefusalNoticeRendersItsEnglishText() {
    assertThat(refusal(GolemRegistry.Designation.LOCKED)).isEqualTo("You need Masterwork to designate a masterwork golem");
    assertThat(refusal(GolemRegistry.Designation.CAP)).isEqualTo("You already have 3 masterwork golems");
    assertThat(refusal(GolemRegistry.Designation.BODY)).isEqualTo("A Bone golem can't become a masterwork");
    assertThat(refusal(GolemRegistry.Designation.ALREADY)).isEqualTo("This golem is already a masterwork");
    assertThat(refusal(GolemRegistry.Designation.UNAVAILABLE)).isEqualTo("Your golem roster isn't ready yet");
    assertThat(refusal(GolemMastery.CoreRefusal.UNAVAILABLE)).isEqualTo("Your golem roster isn't ready yet");
    assertThat(refusal(GolemMastery.CoreRefusal.NO_SLOT)).isEqualTo("This golem has no open core slot");
    assertThat(refusal(GolemMastery.CoreRefusal.BLANK)).isEqualTo("The second core must be a role core");
    assertThat(refusal(GolemMastery.CoreRefusal.DUPLICATE)).isEqualTo("This golem already holds a Guardian core");
    assertThat(refusal(GolemMastery.CoreRefusal.NOT_ALLOWED)).isEqualTo("A Bone golem can't take a Guardian core");
    assertThat(refusal(GolemMastery.CoreRefusal.LOCKED)).isEqualTo("You haven't learned to bind Guardian cores yet");
    assertThat(refusal(GolemRuneLayout.Refusal.NO_SLOT)).isEqualTo("This golem has no open rune slot");
    assertThat(refusal(GolemRuneLayout.Refusal.DUPLICATE)).isEqualTo("This golem already has a Might rune");
    assertThat(refusal(GolemRuneLayout.Refusal.BLOCKED)).isEqualTo("Might runes don't work in a Bone golem");
  }

  @Test
  void everyRefusalValueHasACoveredNotice() {
    for (GolemRegistry.Designation designation : GolemRegistry.Designation.values()) {
      assertThat(refusal(designation)).isNotBlank();
    }
    for (GolemMastery.CoreRefusal refusal : GolemMastery.CoreRefusal.values()) {
      assertThat(refusal(refusal)).isNotBlank();
    }
    for (GolemRuneLayout.Refusal refusal : GolemRuneLayout.Refusal.values()) {
      assertThat(refusal(refusal)).isNotBlank();
    }
  }

  private static String refusal(GolemRegistry.Designation designation) {
    return C.stripColor(GolemGuiActions.designationNotice(designation, 3, GolemBody.BONE).text());
  }

  private static String refusal(GolemMastery.CoreRefusal refusal) {
    return C.stripColor(GolemGuiActions.coreRefusalNotice(refusal, GolemCoreType.GUARDIAN, GolemBody.BONE).text());
  }

  private static String refusal(GolemRuneLayout.Refusal refusal) {
    return C.stripColor(GolemGuiActions.refusalNotice(refusal, GolemRuneType.MIGHT, GolemBody.BONE).text());
  }

  private static String render(GolemRuneLayout layout, List<GolemRune> runes, int index, GolemBody body) {
    return C.stripColor(GolemGuiActions.runeNote(layout, runes, index, body).notice().text());
  }
}
