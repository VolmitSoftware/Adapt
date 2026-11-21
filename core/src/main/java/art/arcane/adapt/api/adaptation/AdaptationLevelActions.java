package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.misc.SoundPlayer;

import java.util.Objects;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Learning and unlearning one adaptation level outside the chest GUI, through the same requirement gate, transaction,
 * sounds and messages as a click on a level tile. A permanent learn counts as already confirmed by the caller. Call on
 * the player's owning thread.
 */
public final class AdaptationLevelActions {
  private AdaptationLevelActions() {
  }

  /** True when the chest GUI would show this adaptation to the player and let them change it. */
  public static boolean available(Adaptation<?> adaptation, PlayerRef player) {
    if (adaptation == null || player == null || !player.isOnline() || !adaptation.isEnabled()
        || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()) {
      return false;
    }
    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    return adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && adaptation.getSkill().hasUsePermission(player, adaptation.getSkill())
        && adaptation.hasUsePermission(player, adaptation);
  }

  /** Learns up to {@code level}. */
  public static Outcome learn(Adaptation<?> adaptation, PlayerRef player, int level) {
    Objects.requireNonNull(adaptation, "adaptation");
    Objects.requireNonNull(player, "player");
    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    PlayerSkillLine line = adaptPlayer == null ? null : adaptPlayer.getSkillLine(adaptation.getSkill().getName());
    SoundPlayer sounds = SoundPlayer.of(player.world());
    if (line == null) {
      AdaptationGuiSupport.playRefused(player, sounds);
      return new Outcome(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE, "");
    }
    boolean debugLearning = AdaptDebugMode.isActive(player);
    AdaptationGuiSupport.LearnGate gate =
        AdaptationGuiSupport.learnGate(adaptation, player, adaptPlayer, level, debugLearning, true);
    AdaptationLearningTransaction.Result result = gate == AdaptationGuiSupport.LearnGate.REQUIREMENTS_MISSING
        ? AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING
        : AdaptationLearningTransaction.learn(adaptation, player, level, debugLearning);
    if (result == AdaptationLearningTransaction.Result.LEARNED) {
      AdaptationGuiSupport.playLearned(adaptation, player, sounds);
      return new Outcome(result, AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_LEARNED_TITLE,
          trusted("adaptation", adaptation.getDisplayName(level))));
    }
    AdaptationGuiSupport.playRefused(player, sounds);
    return new Outcome(result, learnFailureText(adaptation, adaptPlayer, line, result));
  }

  /** Unlearns down to {@code level}; 0 removes the adaptation. */
  public static Outcome unlearn(Adaptation<?> adaptation, PlayerRef player, int level) {
    Objects.requireNonNull(adaptation, "adaptation");
    Objects.requireNonNull(player, "player");
    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    PlayerSkillLine line = adaptPlayer == null ? null : adaptPlayer.getSkillLine(adaptation.getSkill().getName());
    SoundPlayer sounds = SoundPlayer.of(player.world());
    if (line == null) {
      AdaptationGuiSupport.playRefused(player, sounds);
      return new Outcome(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE, "");
    }
    int currentLevel = AdaptationGuiSupport.paidLevel(line, adaptation.getName());
    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(adaptation, player, level, AdaptDebugMode.isActive(player));
    if (result == AdaptationLearningTransaction.Result.UNLEARNED) {
      AdaptationGuiSupport.playUnlearned(adaptation, player, sounds);
      return new Outcome(result, AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_UNLEARNED_TITLE,
          trusted("adaptation", adaptation.getDisplayName(currentLevel))));
    }
    AdaptationGuiSupport.playUnlearnRefused(player, sounds);
    String text = switch (result) {
      case REQUIRED_BY_DEPENDENTS -> AdaptationGuiSupport.dependentsText(adaptation, adaptPlayer, level);
      case PERMANENT -> AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_MAY_NOT_UNLEARN_TITLE,
          trusted("adaptation", adaptation.getDisplayName(currentLevel)));
      default -> "";
    };
    return new Outcome(result, text);
  }

  private static String learnFailureText(Adaptation<?> adaptation, AdaptPlayer adaptPlayer, PlayerSkillLine line,
                                         AdaptationLearningTransaction.Result result) {
    return switch (result) {
      case INSUFFICIENT_KNOWLEDGE -> line.getKnowledge() <= 0
          ? AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_NO_KNOWLEDGE)
          : AdaptLanguage.text(GuiMessages.KNOWLEDGE_SHORTAGE, trusted("knowledge", line.getKnowledge()));
      case INSUFFICIENT_POWER -> AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_NOT_ENOUGH_POWER);
      default -> AdaptationGuiSupport.learningFailureText(adaptation, adaptPlayer, result);
    };
  }

  /** The transaction result and the text the chest GUI would show for it; empty when it shows none. */
  public record Outcome(AdaptationLearningTransaction.Result result, String message) {
    public Outcome {
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(message, "message");
    }

    public boolean changed() {
      return result == AdaptationLearningTransaction.Result.LEARNED
          || result == AdaptationLearningTransaction.Result.UNLEARNED;
    }
  }
}
