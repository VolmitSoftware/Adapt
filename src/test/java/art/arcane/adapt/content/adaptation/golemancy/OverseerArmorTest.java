package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.attribute.AdaptAttributeKey;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverseerArmorTest {
  private static final AdaptAttributeKey KEY = AdaptAttributeKey.of("golem-overseer", OverseerArmor.SLOT);

  @Test
  void theModifierIsAppliedOnlyWhenItsAmountChanges() {
    IAttribute armor = armor(3D);

    assertThat(OverseerArmor.change(armor, KEY, 3D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
    assertThat(OverseerArmor.change(armor, KEY, 4D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, KEY, 0D)).isEqualTo(OverseerRules.ArmorChange.REMOVE);
  }

  @Test
  void aMissingModifierIsAppliedWhenArmorIsDueAndLeftAloneOtherwise() {
    IAttribute armor = armor(Double.NaN);

    assertThat(OverseerArmor.change(armor, KEY, 2D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerArmor.change(armor, KEY, 0D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
  }

  @Test
  void theFormationKeyBelongsToTheOverseer() {
    assertThat(KEY.key().toString()).isEqualTo("adaptbuff:golem-overseer_formation");
  }

  private static IAttribute armor(double applied) {
    IAttribute armor = mock(IAttribute.class);
    KList<IAttribute.Modifier> modifiers = new KList<>();
    if (!Double.isNaN(applied)) {
      modifiers.add(new IAttribute.Modifier(KEY.uuid(), KEY.key(), applied, AttributeModifier.Operation.ADD_NUMBER));
    }
    when(armor.getModifier(KEY.uuid(), KEY.key())).thenReturn(modifiers);
    return armor;
  }
}
