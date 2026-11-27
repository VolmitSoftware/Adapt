package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Durability from the held shield, off hand first, worn the way the guard adaptations wear it; never breaks it. */
public class BlockingFuel extends FuelBlock<BlockingFuel.Config> {
  public BlockingFuel() {
    super(SkillBlocking.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    ItemRef shield = BlockingShields.preferred(player);
    return BlockingShields.held(this, player) && BlockingShields.SHIELD.equals(shield.type())
        && !shield.isUnbreakable() && shield.maxDamage() - shield.damage() > getConfig().durability;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int durability = getConfig().durability;
    return use.payer().payDurabilityCost(player, "fuel", durability, () -> {
      BlockingShields.wear(this, player, durability);
      return true;
    });
  }

  @ConfigDescription("10 shield durability.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Durability taken from the shield.", impact = "Higher values wear the shield faster.")
    int durability = 10;
  }
}
