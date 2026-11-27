package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a pair's five mutated adaptations from the two parents' trigger kinds. A fight trigger always uses the fight
 * form of a Strike, a work trigger the work form, and an either trigger names both. A signature claimed for a slot
 * leaves it without blocks; a pair text may replace its blocks and give its State a window.
 */
public final class MutationRecipes {
  private MutationRecipes() {
  }

  /** The five adaptations with the pair's claims applied. */
  public static List<MutatedAdaptation> compose(SkillPair pair, TriggerKind lead, TriggerKind partner,
                                                PairRegistry claims) {
    List<MutatedAdaptation> composed = new ArrayList<>(Recipe.values().length);
    for (MutatedAdaptation generated : generate(pair, lead, partner)) {
      composed.add(apply(generated, claims));
    }
    return List.copyOf(composed);
  }

  /** The five adaptations exactly as the recipes produce them, before any claim. */
  public static List<MutatedAdaptation> generate(SkillPair pair, TriggerKind lead, TriggerKind partner) {
    List<MutatedAdaptation> generated = new ArrayList<>(Recipe.values().length);
    for (Recipe recipe : Recipe.values()) {
      generated.add(generate(pair, lead, partner, recipe));
    }
    return List.copyOf(generated);
  }

  public static MutatedAdaptation generate(SkillPair pair, TriggerKind lead, TriggerKind partner, Recipe recipe) {
    return new MutatedAdaptation(new MutationSlot(pair, recipe), blocks(pair, lead, partner, recipe),
        form(lead, partner, recipe), 0);
  }

  private static MutatedAdaptation apply(MutatedAdaptation generated, PairRegistry claims) {
    if (claims.signature(generated.slot()).isPresent()) {
      return new MutatedAdaptation(generated.slot(), List.of(), generated.form(), 0);
    }
    Optional<PairRegistry.Claim<PairText>> text = claims.text(generated.slot());
    if (text.isEmpty()) {
      return generated;
    }
    PairText row = text.get().value();
    return new MutatedAdaptation(generated.slot(), row.blocks().orElse(generated.blocks()), generated.form(),
        row.stateWindowSeconds() > 0 ? row.stateWindowSeconds() : generated.stateWindowSeconds());
  }

  private static StrikeForm form(TriggerKind lead, TriggerKind partner, Recipe recipe) {
    return switch (recipe) {
      case GRAFT, CONVERGENCE -> StrikeForm.of(lead);
      case ECHO -> StrikeForm.of(partner);
      case BARTER, ATTUNEMENT -> StrikeForm.NONE;
    };
  }

  private static List<BlockKey> blocks(SkillPair pair, TriggerKind leadKind, TriggerKind partnerKind, Recipe recipe) {
    Side lead = new Side(pair.lead(), leadKind);
    Side partner = new Side(pair.partner(), partnerKind);
    return switch (recipe) {
      case GRAFT -> join(List.of(lead.key(BlockSlot.TRIGGER)), strikes(lead, partner));
      case ECHO -> join(List.of(partner.key(BlockSlot.TRIGGER)), strikes(partner, lead));
      case BARTER -> List.of(partner.key(BlockSlot.FUEL), lead.key(BlockSlot.BOON));
      case ATTUNEMENT -> List.of(lead.key(BlockSlot.STATE), partner.key(BlockSlot.BOON));
      case CONVERGENCE -> join(List.of(lead.key(BlockSlot.TRIGGER), partner.key(BlockSlot.STATE)),
          strikes(lead, lead), strikes(lead, partner));
    };
  }

  @SafeVarargs
  private static List<BlockKey> join(List<BlockKey>... parts) {
    List<BlockKey> joined = new ArrayList<>();
    for (List<BlockKey> part : parts) {
      joined.addAll(part);
    }
    return joined;
  }

  private static List<BlockKey> strikes(Side trigger, Side source) {
    return switch (trigger.kind()) {
      case FIGHT -> List.of(source.key(BlockSlot.STRIKE_FIGHT));
      case WORK -> List.of(source.key(BlockSlot.STRIKE_WORK));
      case EITHER -> List.of(source.key(BlockSlot.STRIKE_FIGHT), source.key(BlockSlot.STRIKE_WORK));
    };
  }

  private record Side(Id skill, TriggerKind kind) {
    BlockKey key(BlockSlot slot) {
      return new BlockKey(skill, slot);
    }
  }
}
