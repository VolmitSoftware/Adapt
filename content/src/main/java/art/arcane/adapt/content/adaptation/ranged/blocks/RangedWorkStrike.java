package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.ranged.RangedFetch;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/**
 * Dropped items around the spot where the work happened fly into the inventory, through Fetch Shot's pull; set off by
 * Barter or Attunement, around the player. It moves drops the player could
 * pick up there anyway and creates none, so it is not a payout and runs after farmed actions too.
 */
public class RangedWorkStrike extends StrikeBlock<RangedWorkStrike.Config> {
  private final RangedFetch fetch = new RangedFetch(this, new RangedFetch.Listener() {
    @Override
    public void delivered(PlayerRef player, Loc from) {
      Loc eye = player.eyeLocation();
      fx(from, FxPriority.TRAIL).line(Particles.ENCHANT, eye.x(), eye.y(), eye.z(), 5);
    }

    @Override
    public void finished(PlayerRef player, int fetched) {
      addStat(player, "ranged.mutated.drops-fetched", fetched);
    }
  });

  public RangedWorkStrike() {
    super(SkillRanged.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("hopper"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    if (!canAccessChest(player, center) || center.world().entitiesIn(Box.around(center.position(), radius, radius, radius),
        (EntityRef entity) -> entity instanceof ItemEntityRef).isEmpty()) {
      return Outcome.MISSED;
    }
    RangedFetch.Pull pull = new RangedFetch.Pull(radius, getConfig().maxDrops, getConfig().maxDrops,
        getConfig().maxTrails, (ItemRef stack) -> true);
    return fetch.fetch(player, center, pull) ? Outcome.INSTANT : Outcome.MISSED;
  }

  @ConfigDescription("Drops within 8 blocks fly to you.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach around the work spot in blocks at level 1.", impact = "Higher values gather drops from farther away.")
    double radiusAtFirstLevel = 8D;
    @ConfigDoc(value = "Reach around the work spot in blocks at the top level.", impact = "Higher values gather drops from farther away.")
    double radiusAtTopLevel = 12D;
    @ConfigDoc(value = "Most dropped stacks one strike gathers.", impact = "Lower values limit the work one strike does.")
    int maxDrops = 16;
    @ConfigDoc(value = "Most gathered stacks that draw a trail to you.", impact = "Lower values show fewer particles.")
    int maxTrails = 4;
  }
}
