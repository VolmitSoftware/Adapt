package art.arcane.adapt.util.reflect.registries;

import org.bukkit.inventory.ItemFlag;

public class ItemFlags {
  public static final ItemFlag HIDE_POTION_EFFECTS = RegistryUtil.find(ItemFlag.class, "hide_potion_effects", "hide_additional_tooltip");
  public static final ItemFlag HIDE_ADDITIONAL_TOOLTIP = RegistryUtil.findNullable(ItemFlag.class, "hide_additional_tooltip", "hide_potion_effects");
}
