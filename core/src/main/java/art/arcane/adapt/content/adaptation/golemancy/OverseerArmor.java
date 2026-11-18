package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeKey;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

final class OverseerArmor {
  static final String SLOT = "formation";

  private OverseerArmor() {
  }

  static void settle(Adaptation<?> source, PlayerRef player, double desired) {
    if (!player.hasAttribute(Attributes.ARMOR)) {
      return;
    }
    switch (change(player, AdaptAttributeKey.of(source.getName(), SLOT), desired)) {
      case KEEP -> {
      }
      case APPLY -> AdaptAttributeService.get().apply(player, source.getName(), SLOT, Attributes.ARMOR, desired, AttributeOperation.ADD_VALUE);
      case REMOVE -> remove(source, player);
    }
  }

  static OverseerRules.ArmorChange change(LivingRef armor, AdaptAttributeKey key, double desired) {
    AttributeMod applied = armor.modifier(Attributes.ARMOR, key.key());
    double current = applied == null ? Double.NaN : applied.amount();
    return OverseerRules.armorChange(current, desired);
  }

  static void remove(Adaptation<?> source, PlayerRef player) {
    AdaptAttributeService.get().remove(player, source.getName(), SLOT, Attributes.ARMOR);
  }
}
