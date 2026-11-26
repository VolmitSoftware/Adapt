package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/** The skills menu's inactive seats and its knowledge summary. */
public final class SeatMessages {
  public static final TextKey NOT_STARTED = TextKey.of("gui.seats.not_started", "Not started");
  public static final TextKey KNOWLEDGE = TextKey.of("gui.knowledge.title", "Knowledge");
  public static final TextKey EARNED = TextKey.of("gui.knowledge.earned", "Earned {value}");
  public static final TextKey SPENT = TextKey.of("gui.knowledge.spent", "Spent {value}");
  public static final TextKey UNSPENT = TextKey.of("gui.knowledge.unspent", "Unspent {value}");
  public static final TextKey SKILL = TextKey.of("gui.knowledge.skill", "{skill}: {spent} spent, {available} unspent");

  private SeatMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(NOT_STARTED);
    builder.add(KNOWLEDGE);
    builder.add(EARNED);
    builder.add(SPENT);
    builder.add(UNSPENT);
    builder.add(SKILL);
  }
}
