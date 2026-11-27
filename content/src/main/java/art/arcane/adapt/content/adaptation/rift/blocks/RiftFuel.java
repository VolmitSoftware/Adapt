package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.rift.RiftPearls;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.platform.value.Id;

/** Plain ender pearls from anywhere in the inventory; bound and taglocked pearls are never spent. */
public class RiftFuel extends FuelBlock<RiftFuel.Config> {
  public RiftFuel() {
    super(SkillRift.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_pearl"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return RiftPearls.plainPearls(use.player()) >= getConfig().pearls;
  }

  @Override
  public boolean pay(BlockUse use) {
    return RiftPearls.payPlainPearls(use.payer(), use.player(), "fuel", getConfig().pearls);
  }

  @ConfigDescription("1 ender pearl.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Ender pearls taken per use.", impact = "Higher values cost more pearls.")
    int pearls = 1;
  }
}
