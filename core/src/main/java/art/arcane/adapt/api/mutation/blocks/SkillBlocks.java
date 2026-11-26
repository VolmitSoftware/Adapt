package art.arcane.adapt.api.mutation.blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one skill brings to a mutation: the words for its name, its six blocks and its three upgrades.
 * {@code skillId} is the registry id of the skill. As lead the skill names the mutation with {@code leadNoun}; as
 * partner it adds {@code partnerAdjective}.
 */
public record SkillBlocks(String skillId, String skillName, String leadNoun, String partnerAdjective,
                          TriggerKind triggerKind, String trigger, String strikeFight, String strikeWork, String boon,
                          String fuel, String state, List<UpgradedAdaptation> upgrades) {
  public SkillBlocks {
    upgrades = List.copyOf(upgrades);
  }

  public String text(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> trigger;
      case STRIKE_FIGHT -> strikeFight;
      case STRIKE_WORK -> strikeWork;
      case BOON -> boon;
      case FUEL -> fuel;
      case STATE -> state;
    };
  }

  public BlockRef ref(BlockSlot slot) {
    return new BlockRef(skillId, slot);
  }

  public List<BlockRef> blocks() {
    List<BlockRef> blocks = new ArrayList<>();
    for (BlockSlot slot : BlockSlot.values()) {
      blocks.add(ref(slot));
    }
    return List.copyOf(blocks);
  }
}
