package art.arcane.adapt.content.adaptation.golemancy.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.value.Id;

/**
 * Overseer, Golem Mending and Stasis fused: Crook signals, Sic, regeneration near you and the packing channel all reach
 * farther than the originals do.
 */
public class GrandOverseer extends SkillUpgrade<GrandOverseer.Config> {
  public static final Id ID = Id.adapt("golemancy/upgrade/grand_overseer");

  public GrandOverseer() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lightning_rod"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, Format.pc(getReach(level) - 1D, 0), 2);
  }

  @Override
  public double strength(Adaptation<?> original, int upgradeLevel) {
    return getReach(upgradeLevel);
  }

  double getReach(int level) {
    return 1D + Math.max(0D, getConfig().reachBonusBase + (level * getConfig().reachBonusPerLevel));
  }

  @ConfigDescription("Command, heal and pack your golems from farther away.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Fraction added to the signal radius, Sic range, regeneration radius near you and packing reach, before level scaling.", impact = "Higher values let you reach golems farther away.")
    double reachBonusBase = 0.25;
    @ConfigDoc(value = "Fraction added to the reach per level.", impact = "Higher values reward higher levels more.")
    double reachBonusPerLevel = 0.25;
  }
}
