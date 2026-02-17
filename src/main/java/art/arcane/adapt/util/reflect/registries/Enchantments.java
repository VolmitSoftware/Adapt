package art.arcane.adapt.util.reflect.registries;

import org.bukkit.enchantments.Enchantment;

public class Enchantments {
    public static final Enchantment DURABILITY = RegistryUtil.find(Enchantment.class, "unbreaking", "durability");
    public static final Enchantment ARROW_INFINITE = RegistryUtil.find(Enchantment.class, "infinity", "arrow_infinite");
    public static final Enchantment LOOT_BONUS_BLOCKS = RegistryUtil.find(Enchantment.class, "fortune", "loot_bonus_blocks");
}
