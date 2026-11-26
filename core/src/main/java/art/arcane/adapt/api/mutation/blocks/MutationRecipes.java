package art.arcane.adapt.api.mutation.blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a mutation's name and its five mutated adaptations from the lead's and partner's blocks. A fight trigger
 * always uses the fight form of a Strike, a work trigger the work form, and an either trigger names both.
 */
public final class MutationRecipes {
  private MutationRecipes() {
  }

  public static String mutationName(SkillBlocks lead, SkillBlocks partner) {
    return MutatedText.mutationName(MutatedText.ENGLISH, lead.skillId(), partner.skillId());
  }

  /**
   * The five adaptations of this mutation with the override table applied.
   */
  public static List<MutatedAdaptation> compose(SkillBlocks lead, SkillBlocks partner) {
    List<MutatedAdaptation> composed = new ArrayList<>(Recipe.values().length);
    for (MutatedAdaptation generated : generate(lead, partner)) {
      composed.add(MutationOverrides.apply(generated));
    }
    return List.copyOf(composed);
  }

  /**
   * The five adaptations exactly as the recipes produce them, before overrides.
   */
  public static List<MutatedAdaptation> generate(SkillBlocks lead, SkillBlocks partner) {
    if (lead.skillId().equals(partner.skillId())) {
      throw new IllegalArgumentException("A skill cannot mutate with itself: " + lead.skillId());
    }
    List<MutatedAdaptation> generated = new ArrayList<>(Recipe.values().length);
    for (Recipe recipe : Recipe.values()) {
      generated.add(generate(lead, partner, recipe));
    }
    return List.copyOf(generated);
  }

  public static MutatedAdaptation generate(SkillBlocks lead, SkillBlocks partner, Recipe recipe) {
    return new MutatedAdaptation(lead.skillId(), partner.skillId(), recipe,
        MutatedText.adaptationName(MutatedText.ENGLISH, partner.skillId(), recipe),
        MutatedText.generated(MutatedText.ENGLISH, lead, partner, recipe), blocks(lead, partner, recipe),
        form(lead, partner, recipe), 0);
  }

  private static StrikeForm form(SkillBlocks lead, SkillBlocks partner, Recipe recipe) {
    return switch (recipe) {
      case GRAFT, CONVERGENCE -> StrikeForm.of(lead.triggerKind());
      case ECHO -> StrikeForm.of(partner.triggerKind());
      case BARTER, ATTUNEMENT -> StrikeForm.NONE;
    };
  }

  private static List<BlockRef> blocks(SkillBlocks lead, SkillBlocks partner, Recipe recipe) {
    return switch (recipe) {
      case GRAFT -> join(List.of(lead.ref(BlockSlot.TRIGGER)), strikeBlocks(lead, partner));
      case ECHO -> join(List.of(partner.ref(BlockSlot.TRIGGER)), strikeBlocks(partner, lead));
      case BARTER -> List.of(partner.ref(BlockSlot.FUEL), lead.ref(BlockSlot.BOON));
      case ATTUNEMENT -> List.of(lead.ref(BlockSlot.STATE), partner.ref(BlockSlot.BOON));
      case CONVERGENCE -> join(List.of(lead.ref(BlockSlot.TRIGGER), partner.ref(BlockSlot.STATE)),
          strikeBlocks(lead, lead), strikeBlocks(lead, partner));
    };
  }

  @SafeVarargs
  private static List<BlockRef> join(List<BlockRef>... parts) {
    List<BlockRef> joined = new ArrayList<>();
    for (List<BlockRef> part : parts) {
      joined.addAll(part);
    }
    return joined;
  }

  private static List<BlockRef> strikeBlocks(SkillBlocks trigger, SkillBlocks source) {
    return switch (trigger.triggerKind()) {
      case FIGHT -> List.of(source.ref(BlockSlot.STRIKE_FIGHT));
      case WORK -> List.of(source.ref(BlockSlot.STRIKE_WORK));
      case EITHER -> List.of(source.ref(BlockSlot.STRIKE_FIGHT), source.ref(BlockSlot.STRIKE_WORK));
    };
  }
}
