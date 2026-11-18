package art.arcane.adapt.util.command;

import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FConstTest {
  private static final List<String> FINISHED_TEXT = List.of(
      "",
      "plain text",
      "tick 0.0% (12 samples)",
      "50%",
      "%s",
      "%d of %d",
      "%%",
      "{}",
      "{0}",
      "<red>denied</red>",
      "<gradient:#ff0000:#00ff00>50%</gradient>"
  );

  @Test
  void teleportFeedbackIsAvailable() {
    assertThat(FConst.TELEPORT).isNotNull();
  }

  @ParameterizedTest(name = "{0} text: \"{3}\"")
  @MethodSource("textFactoriesAndFinishedText")
  void textFactoryKeepsFinishedTextAsWritten(String name, TextFactory factory, C color, String message) {
    String text = factory.apply(message, new Object[0]);

    assertThat(content(text, color)).isEqualTo(message);
    assertThat(text).startsWith(color.toString());
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryFormatsWhenArgumentsAreGiven(String name, TextFactory factory, C color) {
    String text = factory.apply("%s has %d%% of <b>%s</b> {0}", new Object[]{"Steve", 75, "xp"});

    assertThat(content(text, color)).isEqualTo("Steve has 75% of <b>xp</b> {0}");
    assertThat(text).startsWith(color.toString());
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryStillRejectsMissingArguments(String name, TextFactory factory, C color) {
    assertThatThrownBy(() -> factory.apply("%s and %s", new Object[]{"one"}))
        .isInstanceOf(MissingFormatArgumentException.class);
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryRejectsNullText(String name, TextFactory factory, C color) {
    assertThatThrownBy(() -> factory.apply(null, new Object[0])).isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest(name = "{0} feedback: \"{3}\"")
  @MethodSource("feedbackFactoriesAndFinishedText")
  void feedbackFactoryKeepsFinishedTextAsWritten(String name, FeedbackFactory factory, int sounds, String message) {
    Feedback feedback = factory.apply(message, new Object[0]);

    assertThat(feedback.messages()).hasSize(1);
    assertThat(stripColor(feedback.messages().get(0))).isEqualTo(message);
    assertThat(feedback.sounds()).hasSize(sounds);
  }

  @ParameterizedTest(name = "{0} feedback")
  @MethodSource("feedbackFactories")
  void feedbackFactoryFormatsWhenArgumentsAreGiven(String name, FeedbackFactory factory, int sounds) {
    Feedback feedback = factory.apply("%d of %d (%s%%)", new Object[]{3, 4, "75"});

    assertThat(feedback.messages()).hasSize(1);
    assertThat(stripColor(feedback.messages().get(0))).isEqualTo("3 of 4 (75%)");
    assertThat(feedback.sounds()).hasSize(sounds);
  }

  @Test
  void feedbackTextMatchesTheStandaloneTextFactories() {
    assertThat(FConst.error("50%").messages()).containsExactly(FConst.errorText("50%"));
    assertThat(FConst.success("50%").messages()).containsExactly(FConst.successText("50%"));
    assertThat(FConst.warning("50%").messages()).containsExactly(FConst.warningText("50%"));
    assertThat(FConst.info("50%").messages()).containsExactly(FConst.infoText("50%"));
  }

  @ParameterizedTest(name = "sent: \"{0}\"")
  @MethodSource("finishedText")
  void sentFeedbackShowsFinishedTextAsWritten(String message) {
    CommandSource sender = mock(CommandSource.class);

    FConst.info(message).send(sender);

    List<String> sent = sentText(sender);
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0)).isEqualTo("[Adapt] " + message);
  }

  private static List<String> sentText(CommandSource sender) {
    List<String> sent = new ArrayList<>();
    for (Invocation invocation : Mockito.mockingDetails(sender).getInvocations()) {
      if (invocation.getMethod().getName().equals("sendSection") && invocation.getArgument(0) instanceof String text) {
        sent.add(text.replaceAll(C.COLOR_CHAR + ".", ""));
      }
    }
    return sent;
  }

  private static String content(String text, C color) {
    assertThat(text).startsWith(color.toString());
    return text.substring(color.toString().length());
  }

  private static String stripColor(String text) {
    return text.length() >= 2 && text.charAt(0) == C.COLOR_CHAR ? text.substring(2) : text;
  }

  static Stream<String> finishedText() {
    return FINISHED_TEXT.stream();
  }

  static Stream<Arguments> textFactories() {
    return Stream.of(
        Arguments.of("error", (TextFactory) FConst::errorText, FConst.COLOR_ERROR),
        Arguments.of("success", (TextFactory) FConst::successText, FConst.COLOR_SUCCESS),
        Arguments.of("warning", (TextFactory) FConst::warningText, FConst.COLOR_WARNING),
        Arguments.of("info", (TextFactory) FConst::infoText, FConst.COLOR_INFO)
    );
  }

  static Stream<Arguments> feedbackFactories() {
    return Stream.of(
        Arguments.of("error", (FeedbackFactory) FConst::error, 1),
        Arguments.of("success", (FeedbackFactory) FConst::success, 2),
        Arguments.of("warning", (FeedbackFactory) FConst::warning, 1),
        Arguments.of("info", (FeedbackFactory) FConst::info, 1)
    );
  }

  static Stream<Arguments> textFactoriesAndFinishedText() {
    return textFactories().flatMap(factory -> FINISHED_TEXT.stream()
        .map(message -> Arguments.of(factory.get()[0], factory.get()[1], factory.get()[2], message)));
  }

  static Stream<Arguments> feedbackFactoriesAndFinishedText() {
    return feedbackFactories().flatMap(factory -> FINISHED_TEXT.stream()
        .map(message -> Arguments.of(factory.get()[0], factory.get()[1], factory.get()[2], message)));
  }

  private interface TextFactory extends BiFunction<String, Object[], String> {
  }

  private interface FeedbackFactory extends BiFunction<String, Object[], Feedback> {
  }
}
