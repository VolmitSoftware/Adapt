package art.arcane.adapt.content.adaptation.golemancy.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

/**
 * Animus Binding, Node Weaving, every core and the four frames fused. From level 1 binding, nodes and cores run at their
 * top level, so a mutated Golemancer keeps everything their golems do; the frames gain levels with the upgrade and
 * reach their top at level 3. A frame whose golem this server's version lacks, such as copper before copper golems
 * existed, is left out with it.
 */
public class Forgemaster extends SkillUpgrade<Forgemaster.Config> {
  public static final Id ID = Id.adapt("golemancy/upgrade/forgemaster");

  private static final Set<String> FRAMES = Set.of("adapt:golem/frost_frame", "adapt:golem/copper_frame", "adapt:golem/iron_frame",
      "adapt:golem/bone_frame");

  public Forgemaster() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
  }

  @Override
  public int lend(Adaptation<?> original, int level) {
    if (level <= 0 || FRAMES.contains(original.getId().toString())) {
      return super.lend(original, level);
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
