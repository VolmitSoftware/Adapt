package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * The state lines of one level on an adaptation's chest menu, first in its tooltip: learned and what unlearning it
 * refunds, or what learning it costs, with what stands in the way first when something does.
 */
final class LevelLore {
  private static final String SEPARATOR = " · ";

  private LevelLore() {
  }

  /** The lines, and whether the player can learn (or already holds) the level now. */
  record State(boolean reachable, List<String> lines) {
  }

  static State state(Adaptation<?> adaptation, AdaptPlayer player, int current, int level, int cost, int powerCost,
                     boolean debug, int keptFloor) {
    List<String> lines = new ArrayList<>(4);
    boolean hardcore = AdaptConfig.get().isHardcoreNoRefunds();
    if (current >= level) {
      lines.add(C.GREEN + AdaptLanguage.text(GuiMessages.PACK_LEARNED));
      if (!adaptation.isPermanent() && !hardcore) {
        int refund = AdaptationHost.refundCostFor(adaptation, level - 1, current);
        lines.add(C.GRAY + AdaptLanguage.text(GuiMessages.PACK_UNLEARN) + SEPARATOR
            + AdaptLanguage.text(GuiMessages.PACK_REFUND, trusted("refund", refund)));
        addLine(lines, AdaptationGuiSupport.powerLore(adaptation, player, current, level, powerCost, keptFloor));
        addLine(lines, vault(adaptation, player, current, level, cost));
      }
      if (adaptation.isPermanent()) {
        lines.add(C.RED + AdaptLanguage.text(GuiMessages.PACK_PERMANENT));
      }
      return new State(true, lines);
    }
    PlayerSkillLine line = player.getSkillLine(SkillHost.lineKey(adaptation.getSkill()));
    long knowledge = line == null ? 0L : line.getKnowledge();
    List<AdaptationRequirement> missing = AdaptationRequirements.missing(adaptation, player.getData());
    boolean reachable = debug || (knowledge >= cost && (powerCost <= 0 || player.getData().hasPowerAvailable(powerCost)) && missing.isEmpty());
    for (AdaptationRequirement requirement : missing) {
      lines.add(C.RED + AdaptLanguage.text(GuiMessages.REQUIRES, untrusted("requirement", AdaptationRequirements.describe(requirement))));
    }
    if (!debug && knowledge < cost) {
      lines.add(C.RED + AdaptLanguage.text(GuiMessages.PACK_NEED_KNOWLEDGE, trusted("knowledge", cost - knowledge)));
    }
    lines.add((reachable ? C.GREEN : C.GRAY) + AdaptLanguage.text(GuiMessages.PACK_LEARN_FOR,
        trusted("level", Form.toRoman(level)), trusted("cost", debug ? 0 : cost)));
    addLine(lines, AdaptationGuiSupport.powerLore(adaptation, player, current, level, powerCost, keptFloor));
    addLine(lines, vault(adaptation, player, current, level, cost));
    if (adaptation.isPermanent()) {
      lines.add(C.RED + AdaptLanguage.text(GuiMessages.PACK_PERMANENT));
    }
    return new State(reachable, lines);
  }

  private static void addLine(List<String> lines, String line) {
    if (!line.isEmpty()) {
      lines.add(line);
    }
  }

  private static String vault(Adaptation<?> adaptation, AdaptPlayer player, int current, int level, int cost) {
    if (!AdaptationLearningTransaction.isConfigured()) {
      return "";
    }
    if (current >= level) {
      String refund = AdaptationLearningTransaction.formattedRefund(adaptation, player.getPlayer(), level - 1, current);
      return refund.isBlank() ? "" : C.GREEN + AdaptLanguage.text(GuiMessages.VAULT_REFUND, trusted("amount", refund));
    }
    String amount = AdaptationLearningTransaction.formattedLearnCost(cost);
    return amount.isBlank() ? "" : C.GRAY + AdaptLanguage.text(GuiMessages.VAULT_LEARN_COST, trusted("amount", amount));
  }
}
