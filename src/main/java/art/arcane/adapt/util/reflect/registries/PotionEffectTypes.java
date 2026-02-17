package art.arcane.adapt.util.reflect.registries;

import org.bukkit.potion.PotionEffectType;

public class PotionEffectTypes {
    public static final PotionEffectType FAST_DIGGING = RegistryUtil.find(PotionEffectType.class, "fast_digging", "haste");
    public static final PotionEffectType DAMAGE_RESISTANCE = RegistryUtil.find(PotionEffectType.class, "damage_resistance", "resistance");
    public static final PotionEffectType JUMP = RegistryUtil.find(PotionEffectType.class, "jump", "jump_boost");
    public static final PotionEffectType SLOW_DIGGING = RegistryUtil.find(PotionEffectType.class, "slow_digging", "mining_fatigue");
    public static final PotionEffectType CONFUSION = RegistryUtil.find(PotionEffectType.class, "confusion", "nausea");
    public static final PotionEffectType INCREASE_DAMAGE = RegistryUtil.find(PotionEffectType.class, "increase_damage", "strength");
}
