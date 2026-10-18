package art.arcane.adapt.api.golem.plinth;

import org.bukkit.inventory.ItemStack;

@FunctionalInterface
public interface InfusionIngredient {
  boolean matches(ItemStack item);
}
