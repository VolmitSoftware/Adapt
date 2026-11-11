package art.arcane.adapt.util.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.IllegalFormatConversionException;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      " ",
      "plain text",
      "%",
      "50%",
      "50% of 100%",
      "% d",
      "%s",
      "%d of %d",
      "%1$s and %2$s",
      "%%",
      "100%% sure",
      "line%nbreak",
      "{}",
      "{0}",
      "{0} of {1}",
      "<red>denied</red>",
      "<gradient:#ff0000:#00ff00>50%</gradient>",
      "<hover:show_text:'%s'>hover</hover>",
      "§c50% §7left"
  })
  void textWithoutArgumentsIsReturnedUntouched(String message) {
    assertSame(message, FeedbackText.format(message));
  }

  @Test
  void emptyArgumentArrayCountsAsNoArguments() {
    assertEquals("50% of %s", FeedbackText.format("50% of %s", new Object[0]));
  }

  @Test
  void nullArgumentArrayCountsAsNoArguments() {
    assertEquals("plain text", FeedbackText.format("plain text", (Object[]) null));
  }

  @Test
  void nullTextWithoutArgumentsStaysNull() {
    assertNull(FeedbackText.format(null));
  }

  @Test
  void nullTextWithArgumentsIsRejected() {
    assertThrows(NullPointerException.class, () -> FeedbackText.format(null, "value"));
  }

  @Test
  void stringArgumentsFillTheirPlaceholders() {
    assertEquals("hello Steve", FeedbackText.format("hello %s", "Steve"));
    assertEquals("b then a", FeedbackText.format("%2$s then %1$s", "a", "b"));
    assertEquals("value null", FeedbackText.format("value %s", (Object) null));
  }

  @Test
  void escapedPercentCollapsesOnlyWhenArgumentsArePresent() {
    assertEquals("75% of Steve", FeedbackText.format("75%% of %s", "Steve"));
    assertEquals("75%% of nobody", FeedbackText.format("75%% of nobody"));
  }

  @Test
  void bracePlaceholdersAreNeverFilled() {
    assertEquals("{} and {0}", FeedbackText.format("{} and {0}", "ignored"));
  }

  @Test
  void tagsSurviveFormattingWithArguments() {
    assertEquals("<red>Steve</red> <click:run_command:'/adapt'>go</click>",
        FeedbackText.format("<red>%s</red> <click:run_command:'/adapt'>go</click>", "Steve"));
  }

  @Test
  void surplusArgumentsAreIgnored() {
    assertEquals("plain text", FeedbackText.format("plain text", "unused"));
    assertEquals("one", FeedbackText.format("%s", "one", "two"));
  }

  @Test
  void emptyTextWithArgumentsStaysEmpty() {
    assertEquals("", FeedbackText.format("", "unused"));
  }

  @Test
  void bareTrailingPercentWithArgumentsIsStillAFormatError() {
    assertThrows(UnknownFormatConversionException.class, () -> FeedbackText.format("50%", "unused"));
  }

  @Test
  void missingArgumentsAreStillAFormatError() {
    assertThrows(MissingFormatArgumentException.class, () -> FeedbackText.format("%s and %s", "one"));
  }

  @Test
  void wrongArgumentTypeIsStillAFormatError() {
    assertThrows(IllegalFormatConversionException.class, () -> FeedbackText.format("%d", "text"));
  }
}
