package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;

/** The shared anti-farm rules a Trigger uses to mark its action as farmed. */
public final class MutationPayouts {
  private static final Set<Id> PLACED_AT_LAST_STAGE = Set.of(Id.minecraft("bamboo"), Id.minecraft("mangrove_propagule"));

  private MutationPayouts() {
  }

  /** A spawner, egg, bucket or bred mob, a minion, a grave mob, or the player's own pet. */
  public static boolean farmed(PlayerRef player, EntityRef entity) {
    return entity != null && !MobOrigin.paysKillReward(player, entity);
  }

  /** A block a player placed, one broken and replaced in the deny window, or a crop grown with bone meal. */
  public static boolean farmed(BlockRef block) {
    return block != null
        && (XpProvenance.isPlayerPlaced(block) || XpProvenance.isReplaceDenied(block) || XpProvenance.isBonemealed(block));
  }

  /**
   * A crop grown with bone meal or by Adapt, or a placed plant that a player can place already at its last stage.
   * Other planted crops are always player-placed, so placement alone does not mark them.
   */
  public static boolean farmedHarvest(BlockRef crop) {
    return crop != null && (XpProvenance.isBonemealed(crop)
        || (PLACED_AT_LAST_STAGE.contains(crop.type()) && XpProvenance.isPlayerPlaced(crop)));
  }
}
