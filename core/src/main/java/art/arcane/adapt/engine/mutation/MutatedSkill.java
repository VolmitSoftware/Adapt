package art.arcane.adapt.engine.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.blocks.MutationRecipes;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationBlocks;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * A skill made from two parents for the players who merged them. It is built on demand by the skill registry, has no
 * config file and is never part of the registered skill list. Its adaptations are the parents' upgrades, the lead's
 * first and then the partner's, which stay owned by their parent skill, followed by the pair's own adaptations
 * (Graft, Echo, Barter, Attunement, Convergence): a claimed signature where an addon registered one for the slot,
 * otherwise the recipe built from both parents' kit blocks. They are rebuilt, and the ones they replace retired, when
 * a parent or a pair claim changes.
 */
public final class MutatedSkill extends SimpleSkill<MutatedSkill.Config> {
  private static final Config DEFAULTS = new Config();

  private final SkillPair pair;
  private final SimpleSkill<?> lead;
  private final Function<Id, Skill<?>> parents;
  private final PairRegistry claims;
  private final SignatureListener signaturesChanged;
  private volatile Parts parts = new Parts(null, null, -1L, List.of(), List.of(), List.of());

  public MutatedSkill(SkillPair pair, SimpleSkill<?> lead, Function<Id, Skill<?>> parents, PairRegistry claims,
                      SignatureListener signaturesChanged) {
    super(pair.id());
    this.pair = pair;
    this.lead = lead;
    this.parents = parents;
    this.claims = claims;
    this.signaturesChanged = signaturesChanged;
    registerConfiguration(Config.class);
    setIcon(lead.getIcon());
    setColor(lead.getColor());
  }

  public SimpleSkill<?> getLead() {
    return lead;
  }

  public SkillPair getPair() {
    return pair;
  }

  /** A mutated line is keyed by its pair, such as {@code adapt:swords+adapt:stealth}. */
  public String lineKey() {
    return pair.key();
  }

  /** Both parents as registered now, with the pair claims, for the composed texts. */
  public MutatedText.Parents parents() {
    return new MutatedText.Parents(parents.apply(pair.lead()), parents.apply(pair.partner()), claims);
  }

  /** The parents' upgrades, the lead's then the partner's, then the pair's own adaptations. */
  public List<Adaptation<?>> combinedAdaptations() {
    return parts().adaptations();
  }

  /** The parents' enabled originals this mutation keeps without learning or a power slot, the lead's first. */
  public List<Adaptation<?>> getKept() {
    List<Adaptation<?>> kept = new ArrayList<>();
    for (Id parent : List.of(pair.lead(), pair.partner())) {
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
    return AdaptLanguage.render(MutatedText.skillDescription(MutatedText.VIEWER, parents()));
  }

  /** The parents' original of that id this mutation keeps, enabled or not; null for any other id. */
  public Adaptation<?> findKept(String adaptationId) {
    for (Id parent : List.of(pair.lead(), pair.partner())) {
      for (Adaptation<?> adaptation : SkillUpgrades.kept(parents.apply(parent))) {
        if (adaptation.getId().toString().equals(adaptationId)) {
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

  /** The pair's own adaptations whose blocks or signature this server can run, in recipe order. */
  public List<MutationAdaptation> getMutationAdaptations() {
    return parts().mutated();
  }

  /** The signatures the pair's adaptations run, as currently claimed. */
  public List<Signature<?>> getSignatures() {
    return parts().signatures();
  }

  /** The pair's adaptation for the recipe, or null when a block or signature it needs is missing on this server. */
  public @Nullable MutationAdaptation getMutationAdaptation(Recipe recipe) {
    for (MutationAdaptation adaptation : parts().mutated()) {
      if (adaptation.getRecipe() == recipe) {
        return adaptation;
      }
    }
    return null;
  }

  /** The lead's upgrades on the first row, the partner's on the second, the pair's own on the third. */
  public List<List<String>> rowGroups() {
    List<List<String>> rows = new ArrayList<>(3);
    for (Id parent : List.of(pair.lead(), pair.partner())) {
      Skill<?> skill = parents.apply(parent);
      if (skill == null || SkillHost.of(skill).getUpgrades().isEmpty()) {
        continue;
      }
      List<String> row = new ArrayList<>();
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        row.add(upgrade.getId().toString());
      }
      rows.add(List.copyOf(row));
    }
    List<String> mutated = new ArrayList<>();
    for (MutationAdaptation adaptation : getMutationAdaptations()) {
      mutated.add(adaptation.getId().toString());
    }
    if (!mutated.isEmpty()) {
      rows.add(List.copyOf(mutated));
    }
    return List.copyOf(rows);
  }

  /** The pair's own adaptations and signatures built so far, without rebuilding them; the skill retires these. */
  public List<Adaptation<?>> builtParts() {
    Parts current = parts;
    List<Adaptation<?>> built = new ArrayList<>(current.mutated());
    built.addAll(current.signatures());
    return built;
  }

  private Parts parts() {
    Skill<?> leadSkill = parents.apply(pair.lead());
    Skill<?> partnerSkill = parents.apply(pair.partner());
    long revision = claims.revision();
    Parts current = parts;
    if (current.lead() == leadSkill && current.partner() == partnerSkill && current.revision() == revision) {
      return current;
    }
    synchronized (this) {
      current = parts;
      if (current.lead() == leadSkill && current.partner() == partnerSkill && current.revision() == revision) {
        return current;
      }
      List<Signature<?>> signatures = new ArrayList<>();
      List<MutationAdaptation> mutated = mutated(leadSkill, partnerSkill, signatures);
      List<Adaptation<?>> combined = new ArrayList<>();
      if (leadSkill != null) {
        combined.addAll(SkillHost.of(leadSkill).getUpgrades());
      }
      if (partnerSkill != null) {
        combined.addAll(SkillHost.of(partnerSkill).getUpgrades());
      }
      combined.addAll(mutated);
      Parts rebuilt = new Parts(leadSkill, partnerSkill, revision, List.copyOf(combined), mutated,
          List.copyOf(signatures));
      parts = rebuilt;
      retire(current);
      MutationTools.forget();
      signaturesChanged.changed(this, current.signatures());
      return rebuilt;
    }
  }

  private static void retire(Parts replaced) {
    for (MutationAdaptation adaptation : replaced.mutated()) {
      ComponentHost.of(adaptation).retireComponent();
    }
    for (Signature<?> signature : replaced.signatures()) {
      ComponentHost.of(signature).retireComponent();
    }
  }

  private List<MutationAdaptation> mutated(@Nullable Skill<?> leadSkill, @Nullable Skill<?> partnerSkill,
                                           List<Signature<?>> signatures) {
    if (leadSkill == null || partnerSkill == null) {
      return List.of();
    }
    List<MutationAdaptation> mutated = new ArrayList<>();
    for (MutatedAdaptation recipe : MutationRecipes.compose(pair, Kits.of(leadSkill).triggerKind(),
        Kits.of(partnerSkill).triggerKind(), claims)) {
      Optional<PairRegistry.Claim<Function<MutationSlot, ? extends Signature<?>>>> claim =
          claims.signature(recipe.slot());
      if (claim.isPresent()) {
        Signature<?> signature = signature(recipe.slot(), claim.get());
        if (signature != null) {
          signatures.add(signature);
          mutated.add(BuildScope.run(claim.get().owner(),
              () -> ComponentBuilder.buildFor(SkillHost.of(this), () -> new MutationAdaptation(this, recipe, signature))));
        }
        continue;
      }
      List<MutationBlock<?>> blocks = blocks(recipe, leadSkill, partnerSkill);
      if (blocks != null) {
        mutated.add(BuildScope.run(getOwner(),
            () -> ComponentBuilder.buildFor(SkillHost.of(this), () -> new MutationAdaptation(this, recipe, blocks))));
      }
    }
    return List.copyOf(mutated);
  }

  /** The recipe's blocks as both parents registered them, or null when one is missing on this server. */
  private @Nullable List<MutationBlock<?>> blocks(MutatedAdaptation recipe, Skill<?> leadSkill, Skill<?> partnerSkill) {
    List<MutationBlock<?>> blocks = new ArrayList<>();
    for (BlockKey key : recipe.blocks()) {
      Skill<?> owner = key.skill().equals(pair.lead()) ? leadSkill : partnerSkill;
      MutationBlock<?> block = MutationBlocks.find(owner, key.slot());
      if (block == null) {
        MutationBlock<?> unavailable = MutationBlocks.findUnavailable(owner, key.slot());
        if (unavailable != null) {
          PlatformSupport.leaveOut("adaptation", lineKey(), MutationAdaptation.id(pair, recipe.recipe()).toString(),
              PlatformSupport.check(unavailable.getPlatformNeeds()));
        }
        return null;
      }
      blocks.add(block);
    }
    return blocks;
  }

  /** The claimed signature built in its addon's scope; a version-gated one is logged as left out and retired. */
  private @Nullable Signature<?> signature(MutationSlot slot,
                                           PairRegistry.Claim<Function<MutationSlot, ? extends Signature<?>>> claim) {
    Signature<?> signature = BuildScope.run(claim.owner(),
        () -> ComponentBuilder.buildFor(SkillHost.of(this), () -> claim.value().apply(slot)));
    if (!signature.getId().equals(Signature.id(slot))) {
      ComponentHost.of(signature).retireComponent();
      throw new IllegalStateException(claim.owner() + " registered a signature for " + slot + " that reports "
          + signature.getId());
    }
    PlatformSupport.Verdict verdict = PlatformSupport.check(signature.getPlatformNeeds());
    if (!verdict.usable()) {
      PlatformSupport.leaveOut("signature", pair.lead().toString(), signature.getId().toString(), verdict);
      ComponentHost.of(signature).retireComponent();
      return null;
    }
    return signature;
  }

  /** Told when the pair replaces its signatures, so their recipes follow. */
  @FunctionalInterface
  public interface SignatureListener {
    void changed(MutatedSkill skill, List<Signature<?>> retired);
  }

  private record Parts(@Nullable Skill<?> lead, @Nullable Skill<?> partner, long revision,
                       List<Adaptation<?>> adaptations, List<MutationAdaptation> mutated,
                       List<Signature<?>> signatures) {
  }

  /** A mutated skill has no config file; every mutation runs on the defaults. */
  public static Config defaults() {
    return DEFAULTS;
  }

  /** Players may use a mutated skill when they may use its lead. */
  public boolean leadAllows(PlayerRef player) {
    return lead.hasUsePermission(player);
  }

  public String mutatedName() {
    return AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, parents()));
  }

  public String mutatedDisplayName(int level) {
    String name = C.RESET + "" + C.BOLD + SkillHost.of(this).colorPrefix() + getEmojiName() + " " + mutatedName();
    return level > 0 ? name + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET : name;
  }

  public static final class Config extends SkillConfig {
  }
}
