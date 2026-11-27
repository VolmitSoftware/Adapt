package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

/** The skills menu's inactive seats and its knowledge summary. */
public final class SeatMessages {
  public static final TextKey NOT_STARTED = AdaptKeys.text("gui.seats.not_started", "Not started");
  public static final TextKey KNOWLEDGE = AdaptKeys.text("gui.knowledge.title", "Knowledge");
  public static final TextKey EARNED = AdaptKeys.text("gui.knowledge.earned", "Earned {value}");
  public static final TextKey SPENT = AdaptKeys.text("gui.knowledge.spent", "Spent {value}");
  public static final TextKey UNSPENT = AdaptKeys.text("gui.knowledge.unspent", "Unspent {value}");
  public static final TextKey SKILL = AdaptKeys.text("gui.knowledge.skill", "{skill}: {spent} spent, {available} unspent");

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
