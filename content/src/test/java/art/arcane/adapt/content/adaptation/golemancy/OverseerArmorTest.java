package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverseerArmorTest {
  private Id key;

  @BeforeEach
  void startCore() {
    TestCores.install();
    key = AdaptAttributeService.get().modifierKey("adapt:golem/overseer", OverseerArmor.SLOT);
  }

  @AfterEach
  void stopCore() {
    TestCores.clear();
  }

  @Test
  void theModifierIsAppliedOnlyWhenItsAmountChanges() {
    LivingRef armor = armor(3D);

    assertThat(OverseerArmor.change(armor, key, 3D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
    assertThat(OverseerArmor.change(armor, key, 4D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, key, 0D)).isEqualTo(OverseerRules.ArmorChange.REMOVE);
  }

  @Test
  void aMissingModifierIsAppliedWhenArmorIsDueAndLeftAloneOtherwise() {
    LivingRef armor = armor(Double.NaN);

    assertThat(OverseerArmor.change(armor, key, 2D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, key, 0D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
  }

  @Test
  void theFormationKeyBelongsToTheOverseer() {
    assertThat(key.toString()).isEqualTo("adaptbuff:adapt/golem/overseer_formation");
  }

  private LivingRef armor(double applied) {
    LivingRef armor = mock(LivingRef.class);
    AttributeMod modifier = Double.isNaN(applied) ? null : new AttributeMod(key, applied, AttributeOperation.ADD_VALUE);
    when(armor.modifier(Attributes.ARMOR, key)).thenReturn(modifier);
    return armor;
  }
}
