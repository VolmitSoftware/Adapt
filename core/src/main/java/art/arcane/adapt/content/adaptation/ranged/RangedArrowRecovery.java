package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
  private static final DataKey<Byte> RECOVERY_KEY = DataKey.adapt("arrow-recovery", DataCodecs.BYTE);
  private static final Id INFINITY = Id.minecraft("infinity");

  public RangedArrowRecovery() {
    super("ranged-recovery");
    registerConfiguration(RangedArrowRecovery.Config.class);
    setLocalizationKey("ranged.arrow_recovery");
    setIcon(Id.minecraft("arrow"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow"))
        .key("challenge_ranged_arrow_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_ranged_arrow_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_arrow_500", "ranged.arrow-recovery.arrows-recovered", 500, 300);
    registerMilestone("challenge_ranged_arrow_10k", "ranged.arrow-recovery.arrows-recovered", 10000, 1000);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void onEntityShootBow(EntityShootBowEvent event) {
    if (!RangedHeartseeker.isSeekingProjectile(event.projectile())
        && event.shooter() instanceof PlayerRef player
        && hasActiveAdaptation(player)) {
      if (event.bow().enchantmentLevel(INFINITY) <= 0) {
        if (event.projectile() instanceof ProjectileRef arrow && arrow.type().equals(RangedProjectiles.ARROW)) {
          arrow.data().set(RECOVERY_KEY, (byte) 1);
        }
      }
    }
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void onProjectileHit(ProjectileHitEvent event) {
    ProjectileRef arrow = event.projectile();
    if (RangedHeartseeker.isSeekingProjectile(arrow)
        || !arrow.type().equals(RangedProjectiles.ARROW)
        || !isRecoverable(arrow)) {
      return;
    }
    if (event.hitEntity() == null || !(arrow.shooter() instanceof PlayerRef shooter)) {
      return;
    }
    arrow.data().remove(RECOVERY_KEY);
    ArrowPickup pickupStatus = arrow.pickupRule();
    arrow.setPickupRule(ArrowPickup.DISALLOWED);
    Loc impact = arrow.location();
    if (!Tasks.runEntity(shooter, () -> recoverArrow(shooter, arrow, impact, pickupStatus),
        () -> Tasks.runEntity(arrow, () -> restorePickup(arrow, pickupStatus)))) {
      arrow.setPickupRule(pickupStatus);
    }
  }

  private void recoverArrow(PlayerRef shooter, ProjectileRef arrow, Loc impact, ArrowPickup pickupStatus) {
    int level = getActiveLevel(shooter);
    if (!shooter.isOnline() || level <= 0 || ThreadLocalRandom.current().nextDouble() >= chancePerLevel(level)) {
      Tasks.runEntity(arrow, () -> restorePickup(arrow, pickupStatus));
      return;
    }

    deliverRecoveredArrow(shooter, AdaptCore.platform().host().items().create(RangedProjectiles.ARROW, 1));
    addStat(shooter, "ranged.arrow-recovery.arrows-recovered", 1);
    Loc eye = shooter.eyeLocation();
    fx(impact, FxPriority.AMBIENT)
        .dustBurst(new Rgb(120, 220, 90), 5, 0.2D, 0.9F)
        .line(Particles.ENCHANTMENT_TABLE, eye.x(), eye.y(), eye.z(), 4)
        .chord(Sounds.ENTITY_ITEM_PICKUP, 0.5F, 1.4F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.8F);
  }

  static void restorePickup(ProjectileRef arrow, ArrowPickup pickupStatus) {
    if (arrow.isValid() && arrow.pickupRule() == ArrowPickup.DISALLOWED) {
      arrow.setPickupRule(pickupStatus);
    }
  }

  static void deliverRecoveredArrow(PlayerRef shooter, ItemRef recoveredArrow) {
    List<ItemRef> leftovers = shooter.inventory().addItem(recoveredArrow);
    for (ItemRef leftover : leftovers) {
      shooter.world().dropItemNaturally(shooter.location().position(), leftover);
    }
  }

  private boolean isRecoverable(ProjectileRef arrow) {
    Byte value = arrow.data().get(RECOVERY_KEY);
    return value != null && value == 1;
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
  public void addStats(int level, StatLines v) {
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
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 8;
      initialCost = 1;
    }
  }
}
