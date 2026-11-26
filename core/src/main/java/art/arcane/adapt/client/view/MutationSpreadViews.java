package art.arcane.adapt.client.view;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolOwner;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.gui.MutationGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Builds the mutation spread for one lead and an optional partner from {@link MutationOffers#preview}, the same numbers
 * the merge produces, with every text in the current audience's language.
 */
public final class MutationSpreadViews {
  private static final String SEPARATOR = " · ";

  private MutationSpreadViews() {
  }

  /**
   * The spread of {@code lead} with {@code partner}; a null partner, or one the rules refuse, shows the lead alone.
   * {@code mutatedSkillId} marks the answer to a confirm that went through, empty otherwise.
   */
  public static MutationSpreadView build(SkillRegistry registry, PlayerData data, Skill<?> lead, Skill<?> partner,
                                         AdaptConfig.Mutations config, String mutatedSkillId) {
    List<MutationCandidate> candidates = candidates(registry, data, lead, config);
    boolean paired = partner != null && SkillMutations.check(data, lead, partner, config) == SkillMutations.Result.MUTATED;
    MutationWords words = words();
    if (!paired) {
      return new MutationSpreadView(lead.getName(), "", "", "", List.of(), side(data, lead, null),
          MutationSide.NONE, candidates, words, mutatedSkillId);
    }
    MutationOffers.Preview preview = MutationOffers.preview(data, lead, partner);
    List<MutationEntry> mutated = new ArrayList<>();
    for (Adaptation<?> adaptation : preview.mutated()) {
      mutated.add(priced(adaptation));
    }
    String summary = C.stripColor(AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", preview.level())))
        + SEPARATOR + C.stripColor(AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", preview.knowledge())));
    return new MutationSpreadView(lead.getName(), partner.getName(), preview.name(), summary, mutated,
        side(data, lead, preview), side(data, partner, preview), candidates, words, mutatedSkillId);
  }

  /** Every skill that could ever join {@code lead}, in registry order, with the rule that stops it now. */
  static List<MutationCandidate> candidates(SkillRegistry registry, PlayerData data, Skill<?> lead,
                                            AdaptConfig.Mutations config) {
    List<MutationCandidate> candidates = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (skill == lead) {
        continue;
      }
      SkillMutations.Result result = SkillMutations.check(data, lead, skill, config);
      MutationCandidate.State state = switch (result) {
        case MUTATED -> MutationCandidate.State.ELIGIBLE;
        case LEVEL_TOO_LOW -> MutationCandidate.State.LEVEL_TOO_LOW;
        case ALREADY_MUTATED -> MutationCandidate.State.ALREADY_MUTATED;
        case LIMIT_REACHED -> MutationCandidate.State.LIMIT_REACHED;
        case UNKNOWN_SKILL, UNAVAILABLE_SKILL, SAME_SKILL -> null;
      };
      if (state != null) {
        candidates.add(new MutationCandidate(skill.getName(), level(data, skill), state, reason(state, config)));
      }
    }
    return List.copyOf(candidates);
  }

  private static String reason(MutationCandidate.State state, AdaptConfig.Mutations config) {
    return switch (state) {
      case ELIGIBLE -> "";
      case LEVEL_TOO_LOW -> AdaptLanguage.text(MutationMessages.NEEDS_LEVEL, trusted("level", config.getRequiredSkillLevel()));
      case ALREADY_MUTATED -> AdaptLanguage.text(MutationMessages.IN_MUTATION);
      case LIMIT_REACHED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LIMIT_REACHED,
          trusted("limit", config.getMaxMutations()));
    };
  }

  private static MutationWords words() {
    return new MutationWords(text(MutationMessages.MUTATE), text(MutationMessages.LEAD_ROLE),
        text(MutationMessages.PARTNER_ROLE), text(MutationMessages.GAINS), text(MutationMessages.LOSES),
        text(MutationMessages.HOLD), text(MutationMessages.PERMANENT), text(MutationMessages.SWAP_LEAD),
        C.stripColor(AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", "{level}"))));
  }

  private static String text(TextKey key) {
    return C.stripColor(AdaptLanguage.text(key));
  }

  /**
   * One parent's side: its upgrades, the originals it keeps, the tools its upgrades (and, for the lead, its pair
   * signatures) hand out, every learned adaptation the merge unlearns and every original that goes away, and the
   * surplus it gives up when it is the higher parent.
   */
  private static MutationSide side(PlayerData data, Skill<?> skill, MutationOffers.Preview preview) {
    List<MutationEntry> upgrades = new ArrayList<>();
    List<MutationEntry> tools = new ArrayList<>();
    for (Adaptation<?> upgrade : skill.getUpgrades()) {
      upgrades.add(priced(upgrade));
      tools(upgrade, tools);
    }
    if (preview != null && preview.mutation().lead().equals(skill.getName())) {
      for (Adaptation<?> own : preview.mutated()) {
        if (own instanceof MutationAdaptation pair) {
          Signature<?> signature = pair.getSignature();
          if (signature != null) {
            tools(signature, tools);
          }
        }
      }
    }
    List<MutationEntry> kept = new ArrayList<>();
    Set<String> keptIds = new HashSet<>();
    for (Adaptation<?> adaptation : SkillUpgrades.kept(skill)) {
      if (adaptation.isEnabled()) {
        keptIds.add(adaptation.getName());
        kept.add(new MutationEntry(adaptation.getName(), ViewIcons.adaptation(adaptation), MutatedSkill.keptLine(adaptation),
            C.stripColor(adaptation.getDescription()), SkillUpgrades.keptLevel(adaptation)));
      }
    }
    List<MutationEntry> lost = new ArrayList<>();
    Set<String> listed = new HashSet<>();
    for (SkillMutations.HeldAdaptation held : SkillMutations.held(skill, data.getSkillLineNullable(skill.getName()))) {
      Adaptation<?> adaptation = held.adaptation();
      listed.add(adaptation.getName());
      String name = AdaptLanguage.text(adaptation.isPermanent() ? MutationMessages.LOST_PERMANENT_ADAPTATION
              : MutationMessages.LOST_ADAPTATION, trusted("adaptation", C.stripColor(adaptation.getShortName())),
          trusted("level", Form.toRoman(held.level())));
      lost.add(new MutationEntry(adaptation.getName(), ViewIcons.adaptation(adaptation), C.stripColor(name), "", held.level()));
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.isEnabled() && !keptIds.contains(adaptation.getName()) && listed.add(adaptation.getName())) {
        lost.add(new MutationEntry(adaptation.getName(), ViewIcons.adaptation(adaptation), C.stripColor(adaptation.getShortName()),
            "", 0));
      }
    }
    return new MutationSide(skill.getName(), level(data, skill), upgrades, kept, tools, lost, surplus(skill, preview));
  }

  private static String surplus(Skill<?> skill, MutationOffers.Preview preview) {
    if (preview == null || preview.higher() != skill) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    if (preview.lostLevels() > 0) {
      parts.add(C.stripColor(AdaptLanguage.text(MutationMessages.LEVELS, trusted("count", preview.lostLevels()))));
    }
    if (preview.lostKnowledge() > 0) {
      parts.add(C.stripColor(AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", preview.lostKnowledge()))));
    }
    return String.join(SEPARATOR, parts);
  }

  private static void tools(Adaptation<?> owner, List<MutationEntry> tools) {
    if (!(owner instanceof ToolOwner holder)) {
      return;
    }
    for (MutationTool<?> tool : holder.getTools()) {
      tools.add(new MutationEntry(tool.id(), ToolViews.icon(tool), C.stripColor(AdaptLanguage.text(tool.nameKey())),
          text(MutationMessages.TOOL) + "\n" + requires(owner, tool.requiredLevel()), 0));
    }
  }

  /** "Requires {owner} Lv {level} from {skill}": what unlocks a tool's recipe. */
  private static String requires(Adaptation<?> owner, int level) {
    String adaptation = AdaptLanguage.text(GuiMessages.REQUIREMENT_LEVEL,
        trusted("adaptation", C.stripColor(owner.getShortName())), trusted("level", level));
    return C.stripColor(AdaptLanguage.text(RuntimeMessages.REQUIRES_ADAPTATION, trusted("adaptation", adaptation),
        trusted("skill", C.stripColor(owner.getSkill().getLocalizedName()))));
  }

  /** An adaptation with its description and what each level costs. */
  private static MutationEntry priced(Adaptation<?> adaptation) {
    String costs = C.stripColor(AdaptLanguage.text(MutationMessages.KNOWLEDGE,
        trusted("knowledge", MutationGui.costs(adaptation))));
    return new MutationEntry(adaptation.getName(), ViewIcons.adaptation(adaptation), C.stripColor(adaptation.getShortName()),
        C.stripColor(adaptation.getDescription()) + "\n" + costs, 0);
  }

  private static int level(PlayerData data, Skill<?> skill) {
    PlayerSkillLine line = data.getSkillLineNullable(skill.getName());
    return line == null ? 0 : line.getLevel();
  }
}
