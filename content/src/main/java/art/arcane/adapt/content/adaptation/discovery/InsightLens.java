package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.insight.InsightView;
import art.arcane.adapt.content.adaptation.taming.TamingStableHand;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.RayHit;

import java.util.Locale;
import java.util.UUID;

/** How Insight finds the creature a player looks at and reads it. */
public final class InsightLens {
  private InsightLens() {
  }

  /** The living entity in the player's line of sight within {@code range}, or null when none or a block is in the way. */
  public static LivingRef lookTarget(PlayerRef player, Loc eye, double range) {
    if (!Double.isFinite(range) || range <= 0D) {
      return null;
    }
    Vec3 direction = eye.direction();
    UUID playerId = player.uuid();
    RayHit entityHit = player.world().rayTraceEntityHit(eye.position(), direction, range, 0.3D,
        (EntityRef entity) -> entity instanceof LivingRef && !entity.uuid().equals(playerId) && !entity.has(EntityTrait.ARMOR_STAND));
    if (entityHit == null || !(entityHit.entity() instanceof LivingRef target)) {
      return null;
    }
    double entityDistance = eye.position().distance(entityHit.position());
    BlockHit blockHit = player.world().rayTraceBlockHit(eye.position(), direction, entityDistance, false);
    if (blockHit != null && DiscoveryInsight.isOccluded(eye.position(), entityHit.position(), blockHit.position())) {
      return null;
    }
    return target;
  }

  static DiscoveryInsight.InsightStats stats(LivingRef target) {
    String species = Format.capitalizeWords(DiscoveryNames.enumName(target.type()).toLowerCase(Locale.ROOT).replace('_', ' '));
    return new DiscoveryInsight.InsightStats(species,
        attributeValue(target, Attributes.MOVEMENT_SPEED),
        attributeValue(target, Attributes.JUMP_STRENGTH),
        attributeValue(target, Attributes.ARMOR_TOUGHNESS),
        attributeValue(target, Attributes.KNOCKBACK_RESISTANCE),
        attributeValue(target, Attributes.FOLLOW_RANGE),
        target.has(EntityTrait.TAMEABLE) && TamingStableHand.hasAppliedBias(target));
  }

  static InsightView view(LivingRef target, UUID viewerId, InsightTier tier, DiscoveryInsight.InsightStats stats) {
    RegistryLookup registries = AdaptApi.platform().host().registries();
    return InsightReadout.read(target, viewerId, tier, stats.stableHandEnhanced(), InsightLens::onlineName,
        (Id effect) -> registries.effectCategory(effect) == EffectCategory.HARMFUL);
  }

  /** The one-line action bar readout of {@code target} as {@code viewer} sees it at {@code tier}. */
  public static String actionBar(PlayerRef viewer, LivingRef target, InsightTier tier) {
    DiscoveryInsight.InsightStats stats = stats(target);
    return InsightActionBar.text(view(target, viewer.uuid(), tier, stats), stats.species());
  }

  private static double attributeValue(LivingRef target, Id attribute) {
    return target.hasAttribute(attribute) ? target.attributeValue(attribute) : Double.NaN;
  }

  private static String onlineName(UUID playerId) {
    PlayerRef player = AdaptApi.platform().host().player(playerId);
    return player == null ? null : player.name();
  }
}
