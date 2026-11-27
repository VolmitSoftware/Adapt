package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.ranged.RangedFetch;
import art.arcane.adapt.content.adaptation.rift.RiftMagnet;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Collection;

/**
 * Drops around the work spot go straight into the ender chest, through Void Magnet's pickup and deposit. It moves drops
 * the player could pick up there anyway and creates none, so it is not a payout and runs after farmed actions too.
 */
public class RiftWorkStrike extends StrikeBlock<RiftWorkStrike.Config> {
  public RiftWorkStrike() {
    super(SkillRift.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_chest"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    if (!canAccessChest(player, center)) {
      return Outcome.MISSED;
    }
    Collection<EntityRef> nearby = center.world().entitiesIn(Box.around(center.position(), radius, radius, radius),
        (EntityRef entity) -> entity instanceof ItemEntityRef);
    int moved = 0;
    int stacks = 0;
    for (EntityRef entity : nearby) {
      if (stacks >= getConfig().maxStacks) {
        break;
      }
      ItemEntityRef item = (ItemEntityRef) entity;
      if (!item.isValid() || item.isDead() || !AdaptApi.scheduler().owns(item)
          || item.location().distanceSquared(center) > radius * radius || !RangedFetch.collectable(item, player.uuid())
          || !canSnatchItem(player, item)) {
        continue;
      }
      ItemRef original = item.item().copy();
      if (original == null || original.type().isAir() || original.amount() <= 0) {
        continue;
      }
      int collected = RiftMagnet.collect(player, item, original, RiftVoidMagnet.Destination.CHEST_ONLY);
      if (collected > 0) {
        moved += collected;
        stacks++;
      }
    }
    if (moved <= 0) {
      return Outcome.MISSED;
    }
    RiftVoidMagnet.pullFx(this, player, moved);
    return Outcome.INSTANT;
  }

  @ConfigDescription("Drops within 6 blocks go to your ender chest.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach around the work spot at level 1, in blocks.", impact = "Higher values gather drops from farther away.")
    double radiusAtFirstLevel = 6D;
    @ConfigDoc(value = "Reach around the work spot at the top level, in blocks.", impact = "Higher values gather drops from farther away.")
    double radiusAtTopLevel = 8D;
    @ConfigDoc(value = "Most drops one strike gathers.", impact = "Lower values limit the work one strike does.")
    int maxStacks = 32;
  }
}
