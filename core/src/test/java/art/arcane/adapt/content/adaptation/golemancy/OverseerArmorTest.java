package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.attribute.AdaptAttributeKey;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverseerArmorTest {
  private static final AdaptAttributeKey KEY = AdaptAttributeKey.of("golem-overseer", OverseerArmor.SLOT);

  @Test
  void theModifierIsAppliedOnlyWhenItsAmountChanges() {
    LivingRef armor = armor(3D);

    assertThat(OverseerArmor.change(armor, KEY, 3D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
    assertThat(OverseerArmor.change(armor, KEY, 4D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, KEY, 0D)).isEqualTo(OverseerRules.ArmorChange.REMOVE);
  }

  @Test
  void aMissingModifierIsAppliedWhenArmorIsDueAndLeftAloneOtherwise() {
    LivingRef armor = armor(Double.NaN);

    assertThat(OverseerArmor.change(armor, KEY, 2D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, KEY, 0D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
  }

  @Test
  void theFormationKeyBelongsToTheOverseer() {
    assertThat(KEY.key().toString()).isEqualTo("adaptbuff:golem-overseer_formation");
  }

  private static LivingRef armor(double applied) {
    LivingRef armor = mock(LivingRef.class);
    AttributeMod modifier = Double.isNaN(applied) ? null : new AttributeMod(KEY.key(), applied, AttributeOperation.ADD_VALUE);
    when(armor.modifier(Attributes.ARMOR, KEY.key())).thenReturn(modifier);
    return armor;
  }
}
