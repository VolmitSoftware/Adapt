package art.arcane.adapt.api.damage;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

public interface PlayerDeathSave {
    default boolean protectsFromDamage(EntityDamageEvent event) {
        return false;
    }

    void tryPreventDeath(EntityDamageEvent event);

    int deathSavePriority(Player player);

    int defaultOrder();
}
