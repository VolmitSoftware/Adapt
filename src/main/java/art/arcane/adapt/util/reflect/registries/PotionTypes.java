package art.arcane.adapt.util.reflect.registries;

import org.bukkit.potion.PotionType;

public class PotionTypes {
    public static final PotionType UNCRAFTABLE = RegistryUtil.findNullable(PotionType.class, "uncraftable", "empty");

    public static final PotionType INSTANT_HEAL = RegistryUtil.find(PotionType.class, "instant_heal", "healing");
    public static final PotionType SPEED = RegistryUtil.find(PotionType.class, "speed", "swiftness");
    public static final PotionType REGEN = RegistryUtil.find(PotionType.class, "regen", "regeneration");
    public static final PotionType JUMP = RegistryUtil.find(PotionType.class, "jump", "leaping");
}
