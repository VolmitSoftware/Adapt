package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeKey;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

final class OverseerArmor {
  static final String SLOT = "formation";

  private OverseerArmor() {
  }

  static void settle(Adaptation<?> source, Player player, double desired) {
    IAttribute armor = Version.get().getAttribute(player, Attributes.ARMOR);
    if (armor == null) {
      return;
    }
    AdaptAttributeKey key = AdaptAttributeKey.of(source.getName(), SLOT);
    KList<IAttribute.Modifier> applied = armor.getModifier(key.uuid(), key.key());
    double current = applied == null || applied.isEmpty() ? Double.NaN : applied.getFirst().getAmount();
    switch (OverseerRules.armorChange(current, desired)) {
      case KEEP -> {
      }
      case APPLY -> AdaptAttributeService.get().apply(player, source.getName(), SLOT, Attributes.ARMOR, desired, AttributeModifier.Operation.ADD_NUMBER);
      case REMOVE -> remove(source, player);
    }
  }

  static void remove(Adaptation<?> source, Player player) {
    AdaptAttributeService.get().remove(player, source.getName(), SLOT, Attributes.ARMOR);
  }
}
