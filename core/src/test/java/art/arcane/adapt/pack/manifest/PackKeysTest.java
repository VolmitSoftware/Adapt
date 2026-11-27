package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackKeysTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id STEALTH = Id.adapt("stealth");
  private static final Id POISONED_BLADE = Id.adapt("sword/poisoned_blade");
  private static final Id TRANSMUTE = Id.of("alchemist", "alchemy/transmute");
  private static final SkillPair MUTATION = new SkillPair(SWORDS, STEALTH);

  @Test
  void builtInSkillKeysStayInAdapt() {
    assertThat(PackKeys.kit(SWORDS)).isEqualTo("adapt:swords");
    assertThat(PackKeys.page(SWORDS, "single")).isEqualTo(Id.adapt("page/swords/single"));
    assertThat(PackKeys.toast(SWORDS)).isEqualTo(Id.adapt("adapt_ui/toast/swords"));
    assertThat(PackKeys.skillSprite(SWORDS)).isEqualTo("adapt:gui/skill/swords");
  }

  @Test
  void nestedPathsKeepTheirSegments() {
    assertThat(PackKeys.kit(POISONED_BLADE)).isEqualTo("adapt:sword/poisoned_blade");
    assertThat(PackKeys.page(POISONED_BLADE, "spread")).isEqualTo(Id.adapt("page/sword/poisoned_blade/spread"));
    assertThat(PackKeys.skillSprite(POISONED_BLADE)).isEqualTo("adapt:gui/skill/sword-poisoned-blade");
  }

  @Test
  void foreignIdsKeyInTheirOwnNamespace() {
    assertThat(PackKeys.kit(TRANSMUTE)).isEqualTo("alchemist:alchemy/transmute");
    assertThat(PackKeys.page(TRANSMUTE, "single")).isEqualTo(Id.of("alchemist", "page/alchemy/transmute/single"));
    assertThat(PackKeys.toast(TRANSMUTE)).isEqualTo(Id.of("alchemist", "adapt_ui/toast/alchemy/transmute"));
    assertThat(PackKeys.skillSprite(TRANSMUTE)).isEqualTo("alchemist:gui/skill/alchemy/transmute");
    assertThat(PackKeys.kitSkill(TRANSMUTE.toString(), 0)).isEqualTo(TRANSMUTE);
  }

  @Test
  void aMutatedLineKeyDressesEvenLeavesForTheLeadAndOddOnesForThePartner() {
    assertThat(PackKeys.kitSkill(MUTATION.key(), 0)).isEqualTo(SWORDS);
    assertThat(PackKeys.kitSkill(MUTATION.key(), 1)).isEqualTo(STEALTH);
    assertThat(PackKeys.kitSkill(MUTATION.key(), 2)).isEqualTo(SWORDS);
    SkillPair foreign = new SkillPair(Id.of("alchemist", "alchemy"), SWORDS);
    assertThat(PackKeys.kitSkill(foreign.key(), 0)).isEqualTo(Id.of("alchemist", "alchemy"));
    assertThat(PackKeys.kitSkill(foreign.key(), 1)).isEqualTo(SWORDS);
  }

  @Test
  void aMutatedSkillIdIsItsOwnKeyAndNeverSplit() {
    Id mutated = MUTATION.id();
    assertThat(mutated).isEqualTo(Id.adapt("mutated/swords/adapt/stealth"));
    assertThat(PackKeys.kitSkill(mutated.toString(), 1)).isEqualTo(mutated);
    assertThat(PackKeys.kit(mutated)).isEqualTo("adapt:mutated/swords/adapt/stealth");
    assertThat(PackKeys.page(mutated, "single")).isEqualTo(Id.adapt("page/mutated/swords/adapt/stealth/single"));
  }

  @Test
  void bareKeysAreAdaptAndNonIdsNameNothing() {
    assertThat(PackKeys.kitSkill("swords", 0)).isEqualTo(SWORDS);
    assertThat(PackKeys.kitSkill("swords+stealth", 1)).isEqualTo(STEALTH);
    assertThat(PackKeys.kitSkill("Not An Id", 0)).isNull();
  }
}
