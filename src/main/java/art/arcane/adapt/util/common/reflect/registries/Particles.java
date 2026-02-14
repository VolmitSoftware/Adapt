package art.arcane.adapt.util.reflect.registries;

import org.bukkit.Particle;

public class Particles {
    public static final Particle REDSTONE = RegistryUtil.find(Particle.class, "redstone", "dust");
    public static final Particle ENCHANTMENT_TABLE = RegistryUtil.find(Particle.class, "enchantment_table", "enchant");
    public static final Particle CRIT_MAGIC = RegistryUtil.find(Particle.class, "crit_magic", "crit");
    public static final Particle TOTEM = RegistryUtil.find(Particle.class, "totem", "totem_of_undying");
    public static final Particle BLOCK_CRACK = RegistryUtil.find(Particle.class, "block_crack", "block");
    public static final Particle VILLAGER_HAPPY = RegistryUtil.find(Particle.class, "villager_happy", "happy_villager");
    public static final Particle ITEM_CRACK = RegistryUtil.find(Particle.class, "item_crack", "item");
}
