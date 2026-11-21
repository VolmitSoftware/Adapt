package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.entity.DamageReplay;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;

/** Reads the damage source, potion type and anvil view. */
final class Paper121Access {
  private static final Id GENERIC_DAMAGE = Id.minecraft("generic");

  private Paper121Access() {
  }

  @SuppressWarnings("deprecation")
  static Id damageType(EntityDamageEvent event) {
    DamageSource source = event.getDamageSource();
    if (source == null) {
      return GENERIC_DAMAGE;
    }
    DamageType type = source.getDamageType();
    if (type == null) {
      return GENERIC_DAMAGE;
    }
    return BukkitRefs.id(type.getKey());
  }

  static Entity causingEntity(EntityDamageEvent event) {
    return event.getDamageSource().getCausingEntity();
  }

  static Location damageLocation(EntityDamageEvent event) {
    return event.getDamageSource().getDamageLocation();
  }

  static DamageReplay replay(EntityDamageEvent event) {
    DamageSource source = event.getDamageSource();
    return (LivingRef target, double amount) -> BukkitRefs.living(target).damage(amount, source);
  }

  static Entity directEntity(EntityDeathEvent event) {
    return event.getDamageSource().getDirectEntity();
  }

  static Entity causingEntity(EntityDeathEvent event) {
    return event.getDamageSource().getCausingEntity();
  }

  static Id basePotion(AreaEffectCloud cloud) {
    return GapEventMaps.key(cloud.getBasePotionType());
  }

  static String renameText(PrepareAnvilEvent event) {
    return event.getView().getRenameText();
  }

  static int repairCost(PrepareAnvilEvent event) {
    return event.getView().getRepairCost();
  }

  static void setRepairCost(PrepareAnvilEvent event, int cost) {
    event.getView().setRepairCost(cost);
  }
}
