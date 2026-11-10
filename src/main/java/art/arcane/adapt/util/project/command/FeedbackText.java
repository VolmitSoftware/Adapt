package art.arcane.adapt.util.command;

/** Feedback text: finished text, such as a translated line with a percent sign in it, is shown as written. */
public final class FeedbackText {
  private FeedbackText() {}

  public static String format(String message, Object... args) {
    return args.length == 0 ? message : message.formatted(args);
  }
}
