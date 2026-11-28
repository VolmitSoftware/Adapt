package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

/**
 * The engine's text of the "Earning XP" guide: its headings and the rules a mutated skill adds. The lines a skill
 * declares for its XP sources belong to the skill's own messages.
 */
public final class XpGuideMessages {
  public static final TextKey TITLE = AdaptKeys.text("xp_guide.title", "Earning XP");
  public static final TextKey MUTATION = AdaptKeys.text("xp_guide.mutation_title", "Mutation");
  public static final TextKey ONE_BAR = AdaptKeys.text("xp_guide.mutation.one_bar", "Both parents' actions fill this one bar.");
  public static final TextKey SAME_ACTION = AdaptKeys.text("xp_guide.mutation.same_action",
      "An action that pays both parents pays the larger award plus {share}% of the smaller.");

  private XpGuideMessages() {
  }

  /** The mutation's rules, in order. */
  public static List<TextKey> rules() {
    return List.of(ONE_BAR, SAME_ACTION);
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(TITLE);
    builder.add(MUTATION);
    for (TextKey rule : rules()) {
      builder.add(rule);
    }
  }
}
