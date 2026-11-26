package art.arcane.adapt.content.adaptation.golemancy.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Set;

/**
 * Animus Binding, Node Weaving, every core and the four frames fused. From level 1 binding, nodes and cores run at their
 * top level, so a mutated Golemancer keeps everything their golems do; the frames gain levels with the upgrade and
 * reach their top at level 3. A frame whose golem this server's version lacks, such as copper before copper golems
 * existed, is left out with it.
 */
public class Forgemaster extends SkillUpgrade<Forgemaster.Config> {
  private static final Set<String> FRAMES = Set.of("golem-frost-frame", "golem-copper-frame", "golem-iron-frame",
      "golem-bone-frame");

  public Forgemaster() {
    super("upgraded-golemancy-forgemaster");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
  }

  @Override
  public int lend(Adaptation<?> original, int level) {
    if (level <= 0 || FRAMES.contains(original.getName())) {
      return UpgradeLoans.lent(original, this, level);
    }
    return original.getMaxLevel();
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
  }

  @ConfigDescription("Binding, nodes and every core at their top level; frames reach their top level at upgrade level 3.")
  public static class Config extends SkillUpgrade.ToolConfig {
  }
}
