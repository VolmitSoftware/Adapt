package art.arcane.adapt.bukkit.natives;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Attribute modifiers of a status effect read from the server. Paper 1.20.1's PotionEffectType#getEffectAttributes
 * throws for every effect with modifiers (it builds attribute keys from the attribute's toString).
 */
public final class ServerEffects {
  private ServerEffects() {
  }

  /** One modifier: attribute key, operation ordinal (add, multiply base, multiply total) and amount at an amplifier. */
  public record Modifier(NamespacedKey attribute, int operation, double amount) {
  }

  public static List<Modifier> modifiers(NamespacedKey effect, int amplifier) {
    MobEffect type = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effect.getNamespace(), effect.getKey()));
    if (type == null) {
      return List.of();
    }
    List<Modifier> modifiers = new ArrayList<>();
    for (Map.Entry<Attribute, AttributeModifier> entry : type.getAttributeModifiers().entrySet()) {
      ResourceLocation attribute = BuiltInRegistries.ATTRIBUTE.getKey(entry.getKey());
      if (attribute != null) {
        modifiers.add(new Modifier(new NamespacedKey(attribute.getNamespace(), attribute.getPath()),
            entry.getValue().getOperation().ordinal(), type.getAttributeModifierValue(amplifier, entry.getValue())));
      }
    }
    return modifiers;
  }
}
