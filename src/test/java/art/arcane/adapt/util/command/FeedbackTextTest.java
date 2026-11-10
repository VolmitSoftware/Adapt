package art.arcane.adapt.util.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FeedbackTextTest {
  @Test
  void finishedTextWithPercentSignsIsShownAsIs() {
    assertEquals("tick 0.0% (12 samples)", FeedbackText.format("tick 0.0% (12 samples)"));
    assertEquals("100% done", FeedbackText.format("100% done"));
  }

  @Test
  void argumentsAreStillFormatted() {
    assertEquals("3 of 4", FeedbackText.format("%d of %d", 3, 4));
  }
}
