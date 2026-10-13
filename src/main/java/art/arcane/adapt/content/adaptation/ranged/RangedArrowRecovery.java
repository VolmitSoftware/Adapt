package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RangedMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Enchantments;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;
import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
  private static final NamespacedKey RECOVERY_KEY = NamespacedKey.fromString("adapt:arrow-recovery");

  public RangedArrowRecovery() {
    super("ranged-recovery");
    registerConfiguration(RangedArrowRecovery.Config.class);
    setLocalizationKey("ranged.arrow_recovery");
    setIcon(Material.ARROW);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW)
        .key("challenge_ranged_arrow_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_ranged_arrow_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_arrow_500", "ranged.arrow-recovery.arrows-recovered", 500, 300);
    registerMilestone("challenge_ranged_arrow_10k", "ranged.arrow-recovery.arrows-recovered", 10000, 1000);
  }

  @EventHandler
  public void onEntityShootBow(EntityShootBowEvent event) {
    if (!RangedHeartseeker.isSeekingProjectile(event.getProjectile())
        && event.getEntity() instanceof Player player
        && hasActiveAdaptation(player)) {
      if (!event.getBow().containsEnchantment(Enchantments.ARROW_INFINITE)) {
        if (event.getProjectile() instanceof Arrow arrow) {
          arrow.getPersistentDataContainer().set(RECOVERY_KEY, PersistentDataType.BYTE, (byte) 1);
        }
      }
    }
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    if (RangedHeartseeker.isSeekingProjectile(event.getEntity())
        || !(event.getEntity() instanceof Arrow arrow)
        || !isRecoverable(arrow)) {
      return;
    }
    if (event.getHitEntity() == null || !(arrow.getShooter() instanceof Player shooter)) {
      return;
    }
    arrow.getPersistentDataContainer().remove(RECOVERY_KEY);
    AbstractArrow.PickupStatus pickupStatus = arrow.getPickupStatus();
    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    Location impact = arrow.getLocation().clone();
    if (!J.runEntity(shooter, () -> recoverArrow(shooter, arrow, impact, pickupStatus),
        () -> J.runEntity(arrow, () -> restorePickup(arrow, pickupStatus)))) {
      arrow.setPickupStatus(pickupStatus);
    }
  }

  private void recoverArrow(Player shooter, Arrow arrow, Location impact, AbstractArrow.PickupStatus pickupStatus) {
    int level = getActiveLevel(shooter);
    if (!shooter.isOnline() || level <= 0 || RANDOM.nextDouble() >= chancePerLevel(level)) {
      J.runEntity(arrow, () -> restorePickup(arrow, pickupStatus));
      return;
    }

    deliverRecoveredArrow(shooter, new ItemStack(Material.ARROW, 1));
    addStat(shooter, "ranged.arrow-recovery.arrows-recovered", 1);
    Location eye = shooter.getEyeLocation();
    fx(impact, FxPriority.AMBIENT)
        .dustBurst(Color.fromRGB(120, 220, 90), 5, 0.2D, 0.9F)
        .line(Particles.ENCHANTMENT_TABLE, eye.getX(), eye.getY(), eye.getZ(), 4)
        .chord(Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.4F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.8F);
  }

  static void restorePickup(Arrow arrow, AbstractArrow.PickupStatus pickupStatus) {
    if (arrow.isValid() && arrow.getPickupStatus() == AbstractArrow.PickupStatus.DISALLOWED) {
      arrow.setPickupStatus(pickupStatus);
    }
  }

  static void deliverRecoveredArrow(Player shooter, ItemStack recoveredArrow) {
    Map<Integer, ItemStack> leftovers = shooter.getInventory().addItem(recoveredArrow);
    for (ItemStack leftover : leftovers.values()) {
      shooter.getWorld().dropItemNaturally(shooter.getLocation(), leftover);
    }
  }

  private boolean isRecoverable(Arrow arrow) {
    return arrow.getPersistentDataContainer().getOrDefault(RECOVERY_KEY, PersistentDataType.BYTE, (byte) 0) == 1;
  }

  static double chanceFromTable(double[] chances, int level) {
    if (chances == null || chances.length == 0) {
      return 0;
    }
    int index = Math.max(0, Math.min(level, chances.length) - 1);
    return chances[index] / 100.0;
  }

  private double chancePerLevel(int level) {
    return chanceFromTable(getConfig().hitChance, level);
  }


  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(RangedMessages.ARROW_RECOVERY_LORE1));
    v.addLore(C.GREEN + AdaptLanguage.text(
        RangedMessages.ARROW_RECOVERY_CHANCE,
        trusted("chance", Form.pc(chancePerLevel(level), 0))
    ));
  }

  @ConfigDescription("Chance to recover arrows after hitting or killing an enemy.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hit Chance for the Ranged Arrow Recovery adaptation.", impact = "Add or remove entries to control which values are included.")
    double[] hitChance = {10, 20, 30, 40, 50, 60, 70, 80};

    public Config() {
      baseCost = 5;
      costFactor = 0.78;
      maxLevel = 8;
      initialCost = 5;
    }
  }
}
