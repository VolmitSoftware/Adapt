package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.engine.skill.SkillHost;
import org.jetbrains.annotations.Nullable;

/** Finds a skill's built mutation blocks, the ones its kit declared. */
public final class MutationBlocks {
  private MutationBlocks() {
  }

  /** The skill's registered block in this slot, or null when it has none the server can run. */
  public static @Nullable MutationBlock<?> find(@Nullable Skill<?> skill, BlockSlot slot) {
    return skill == null ? null : find(SkillHost.of(skill).getBlocks(), slot);
  }

  /** The skill's declared block in this slot that the running Minecraft version cannot run, or null. */
  public static @Nullable MutationBlock<?> findUnavailable(@Nullable Skill<?> skill, BlockSlot slot) {
    return skill == null ? null : find(SkillHost.of(skill).getUnavailableBlocks(), slot);
  }

  private static @Nullable MutationBlock<?> find(Iterable<MutationBlock<?>> blocks, BlockSlot slot) {
    for (MutationBlock<?> block : blocks) {
      if (block.getSlot() == slot) {
        return block;
      }
    }
    return null;
  }
}
