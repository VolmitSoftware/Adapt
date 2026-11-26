package art.arcane.adapt.api.mutation;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.blocks.MutationRecipes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * A skill made from two parents for the players who merged them. It is built on demand by the skill registry, has no
 * config file and is never part of the registered skill list. Its adaptations are the parents' upgrades, the lead's
 * first and then the partner's, which stay owned by their parent skill, followed by the mutation's own adaptations
 * (Graft, Echo, Barter, Attunement, Convergence) for every recipe whose blocks both parents provide on this server.
 */
public final class MutatedSkill extends SimpleSkill<MutatedSkill.Config> {
  private static final Config DEFAULTS = new Config();

  private final SkillMutation mutation;
  private final SimpleSkill<?> lead;
  private final Function<String, Skill<?>> parents;
  private volatile Parts parts = new Parts(null, null, new KList<>(), List.of());

  public MutatedSkill(SkillMutation mutation, SimpleSkill<?> lead, Function<String, Skill<?>> parents) {
    super(mutation.id(), lead.getPresentation());
    this.mutation = mutation;
    this.lead = lead;
    this.parents = parents;
    registerConfiguration(Config.class);
    setIcon(lead.getIcon());
    setColor(lead.getColor());
  }

  public SimpleSkill<?> getLead() {
    return lead;
  }

  public SkillMutation getMutation() {
    return mutation;
  }

  /** The parents' upgrades, the lead's then the partner's, then the mutation's own adaptations. */
  @Override
  public KList<Adaptation<?>> getAdaptations() {
    return parts().adaptations();
  }

  /** The parents' enabled originals this mutation keeps without learning or a power slot, the lead's first. */
  public List<Adaptation<?>> getKept() {
    List<Adaptation<?>> kept = new ArrayList<>();
    for (String parent : List.of(mutation.lead(), mutation.partner())) {
      for (Adaptation<?> adaptation : SkillUpgrades.kept(parents.apply(parent))) {
        if (adaptation.isEnabled()) {
          kept.add(adaptation);
        }
      }
    }
    return List.copyOf(kept);
  }

  /** What the mutation is, composed from both parents' names in the viewer's language. */
  @Override
  public String getDescription() {
    return AdaptLanguage.render(MutatedText.skillDescription(MutatedText.VIEWER, mutation.lead(), mutation.partner()));
  }

  /** The parents' original of that id this mutation keeps, enabled or not; null for any other id. */
  public Adaptation<?> findKept(String adaptationId) {
    for (String parent : List.of(mutation.lead(), mutation.partner())) {
      for (Adaptation<?> adaptation : SkillUpgrades.kept(parents.apply(parent))) {
        if (adaptation.getName().equals(adaptationId)) {
          return adaptation;
        }
      }
    }
    return null;
  }

  /** The skill's description followed by the originals it keeps, for views that show no separate list. */
  public String getDescriptionWithKept() {
    List<String> names = new ArrayList<>();
    for (Adaptation<?> adaptation : getKept()) {
      names.add(keptLine(adaptation));
    }
    if (names.isEmpty()) {
      return getDescription();
    }
    return getDescription() + " " + AdaptLanguage.text(MutatedMessages.KEPT_LIST,
        trusted("adaptations", String.join(", ", names)));
  }

  /** A kept original's name and the level it is kept at, such as "Elevator I". */
  public static String keptLine(Adaptation<?> adaptation) {
    return AdaptLanguage.text(MutatedMessages.KEPT_ADAPTATION,
        trusted("adaptation", C.stripColor(adaptation.getDisplayName())),
        trusted("level", Form.toRoman(SkillUpgrades.keptLevel(adaptation))));
  }

  /** The mutation's own adaptations whose blocks are all registered, in recipe order. */
  public List<MutationAdaptation> getMutationAdaptations() {
    return parts().mutated();
  }

  /** This mutation's adaptation for the recipe, or null when a block it needs is missing on this server. */
  public MutationAdaptation getMutationAdaptation(Recipe recipe) {
    for (MutationAdaptation adaptation : parts().mutated()) {
      if (adaptation.getRecipe() == recipe) {
        return adaptation;
      }
    }
    return null;
  }

  /** The lead's upgrades on the first row, the partner's on the second, the mutation's own on the third. */
  @Override
  public List<List<String>> getAdaptationRowGroups() {
    List<List<String>> rows = new ArrayList<>(3);
    for (String parent : List.of(mutation.lead(), mutation.partner())) {
      Skill<?> skill = parents.apply(parent);
      if (skill == null || skill.getUpgrades().isEmpty()) {
        continue;
      }
      List<String> row = new ArrayList<>();
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        row.add(upgrade.getName());
      }
      rows.add(List.copyOf(row));
    }
    List<String> mutated = new ArrayList<>();
    for (MutationAdaptation adaptation : getMutationAdaptations()) {
      mutated.add(adaptation.getName());
    }
    if (!mutated.isEmpty()) {
      rows.add(List.copyOf(mutated));
    }
    return List.copyOf(rows);
  }

  private Parts parts() {
    Skill<?> leadSkill = parents.apply(mutation.lead());
    Skill<?> partnerSkill = parents.apply(mutation.partner());
    Parts current = parts;
    if (current.lead() != leadSkill || current.partner() != partnerSkill) {
      List<MutationAdaptation> mutated = mutated(leadSkill, partnerSkill);
      KList<Adaptation<?>> combined = new KList<>();
      if (leadSkill != null) {
        combined.addAll(leadSkill.getUpgrades());
      }
      if (partnerSkill != null) {
        combined.addAll(partnerSkill.getUpgrades());
      }
      combined.addAll(mutated);
      current = new Parts(leadSkill, partnerSkill, combined, mutated);
      parts = current;
    }
    return current;
  }

  private List<MutationAdaptation> mutated(Skill<?> leadSkill, Skill<?> partnerSkill) {
    SkillBlocks leadBlocks = SkillBlockCatalog.byId(mutation.lead()).orElse(null);
    SkillBlocks partnerBlocks = SkillBlockCatalog.byId(mutation.partner()).orElse(null);
    if (leadSkill == null || partnerSkill == null || leadBlocks == null || partnerBlocks == null) {
      return List.of();
    }
    List<MutationAdaptation> mutated = new ArrayList<>();
    for (MutatedAdaptation recipe : MutationRecipes.compose(leadBlocks, partnerBlocks)) {
      MutationOverride row = MutationOverrides.find(mutation.lead(), mutation.partner(), recipe.recipe());
      if (row != null && row.isSignature()) {
        Signature<?> signature = signature(leadSkill, row);
        if (signature != null) {
          mutated.add(new MutationAdaptation(this, recipe, signature));
        }
        continue;
      }
      List<MutationBlock<?>> blocks = new ArrayList<>();
      for (BlockRef ref : recipe.blocks()) {
        Skill<?> owner = ref.skillId().equals(mutation.lead()) ? leadSkill : partnerSkill;
        MutationBlock<?> block = MutationBlocks.find(owner, ref.slot());
        if (block == null) {
          MutationBlock<?> unavailable = MutationBlocks.findUnavailable(owner, ref.slot());
          if (unavailable != null) {
            PlatformSupport.leaveOut("adaptation", getName(), MutationAdaptation.id(mutation, recipe.recipe()),
                PlatformSupport.check(unavailable.getPlatformNeeds()));
          }
          blocks = null;
          break;
        }
        blocks.add(block);
      }
      if (blocks != null) {
        mutated.add(new MutationAdaptation(this, recipe, blocks));
      }
    }
    return List.copyOf(mutated);
  }

  /** The lead's registered signature for the row; a version-gated one is logged as left out. */
  private Signature<?> signature(Skill<?> leadSkill, MutationOverride row) {
    String id = Signature.id(row);
    for (Signature<?> signature : leadSkill.getSignatures()) {
      if (signature.getName().equals(id)) {
        return signature;
      }
    }
    if (leadSkill instanceof SimpleSkill<?> simple) {
      for (Signature<?> unavailable : simple.getUnavailableSignatures()) {
        if (unavailable.getName().equals(id)) {
          PlatformSupport.leaveOut("adaptation", getName(), MutationAdaptation.id(mutation, row.recipe()),
              PlatformSupport.check(unavailable.getPlatformNeeds()));
        }
      }
    }
    return null;
  }

  private record Parts(Skill<?> lead, Skill<?> partner, KList<Adaptation<?>> adaptations,
                       List<MutationAdaptation> mutated) {
  }

  @Override
  public Config getConfig() {
    return DEFAULTS;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  /** Players may use a mutated skill when they may use its lead. */
  @Override
  public boolean hasUsePermission(PlayerRef player, Skill<?> skill) {
    return lead.hasUsePermission(player, lead);
  }

  @Override
  public String getLocalizedName() {
    return AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, mutation.lead(), mutation.partner()));
  }

  @Override
  public String getDisplayName() {
    return C.RESET + "" + C.BOLD + getColor() + getEmojiName() + " " + getLocalizedName();
  }

  @Override
  public String getDisplayName(int level) {
    return level > 0 ? getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET : getDisplayName();
  }

  public static final class Config extends SkillConfig {
  }
}
