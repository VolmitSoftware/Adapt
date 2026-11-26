package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The naming rules for mutation blocks. The {@code swords} Trigger has the id {@code block-swords-trigger}, the
 * localization prefix {@code mutated.block.swords.trigger} and the class
 * {@code art.arcane.adapt.content.adaptation.sword.blocks.SwordsTrigger}; the other slots end in {@code FightStrike},
 * {@code WorkStrike}, {@code Boon}, {@code Fuel} and {@code State}.
 */
public final class MutationBlocks {
  public static final String ID_PREFIX = "block-";
  private static final String CONTENT_PACKAGE = "art.arcane.adapt.content.adaptation.";

  private MutationBlocks() {
  }

  public static String id(String skillId, BlockSlot slot) {
    return ID_PREFIX + skillId + "-" + slug(slot);
  }

  /** The block an id names, or null when it is not a block id of a catalog skill. */
  public static @Nullable BlockRef parse(String id) {
    if (id == null || !id.startsWith(ID_PREFIX)) {
      return null;
    }
    for (BlockSlot slot : BlockSlot.values()) {
      String suffix = "-" + slug(slot);
      if (id.endsWith(suffix)) {
        String skillId = id.substring(ID_PREFIX.length(), id.length() - suffix.length());
        return SkillBlockCatalog.byId(skillId).isPresent() ? new BlockRef(skillId, slot) : null;
      }
    }
    return null;
  }

  public static String className(String skillId, BlockSlot slot) {
    return CONTENT_PACKAGE + SkillUpgrades.contentPackage(skillId) + ".blocks."
        + SkillUpgrades.className(SkillBlockCatalog.require(skillId).skillName()) + classSuffix(slot);
  }

  public static String localizationKey(BlockRef ref) {
    return "mutated.block." + ref.skillId() + "." + ref.slot().name().toLowerCase(Locale.ROOT);
  }

  /** The skill's registered block in this slot, or null when it has none the server can run. */
  public static @Nullable MutationBlock<?> find(@Nullable Skill<?> skill, BlockSlot slot) {
    return skill == null ? null : find(skill.getBlocks(), slot);
  }

  /** The skill's declared block in this slot that the running Minecraft version cannot run, or null. */
  public static @Nullable MutationBlock<?> findUnavailable(@Nullable Skill<?> skill, BlockSlot slot) {
    return skill == null ? null : find(skill.getUnavailableBlocks(), slot);
  }

  private static @Nullable MutationBlock<?> find(Iterable<MutationBlock<?>> blocks, BlockSlot slot) {
    for (MutationBlock<?> block : blocks) {
      if (block.getSlot() == slot) {
        return block;
      }
    }
    return null;
  }

  private static String slug(BlockSlot slot) {
    return slot.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private static String classSuffix(BlockSlot slot) {
    return switch (slot) {
      case TRIGGER -> "Trigger";
      case STRIKE_FIGHT -> "FightStrike";
      case STRIKE_WORK -> "WorkStrike";
      case BOON -> "Boon";
      case FUEL -> "Fuel";
      case STATE -> "State";
    };
  }
}
