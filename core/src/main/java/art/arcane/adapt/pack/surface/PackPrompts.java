package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Typed input for pack viewers, which a chest window cannot take: a Dialog on 26.x, an anvil or chat where Dialogs
 * do not exist. A Dialog opens over the pack window, which stays open on server and client; where a prompt has to
 * close it, the platform reopens nothing. Exactly one of the answer or {@code cancelled} runs afterwards on the viewer's
 * owning thread, and the caller shows its page again (updating the window when it is still open).
 */
public interface PackPrompts {
  /** False when the viewer's client can show no prompt at all. */
  boolean available(PlayerRef viewer);

  void text(PlayerRef viewer, String title, String current, int maxLength, Consumer<String> answer, Runnable cancelled);

  /** {@code min}, {@code max} and {@code step} may be null when unknown. */
  void number(PlayerRef viewer, String title, double current, Double min, Double max, Double step, DoubleConsumer answer,
              Runnable cancelled);

  /** Picks one of {@code options}; the answer is its index. */
  void choice(PlayerRef viewer, String title, List<String> options, int current, IntConsumer answer, Runnable cancelled);
}
