package art.arcane.adapt.util.command;

import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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

  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.BLOCK_DEEPSLATE_BREAK).isNotNull();
      assertThat(FConst.TELEPORT).isNotNull();
    }
  }

  @ParameterizedTest(name = "{0} text: \"{3}\"")
  @MethodSource("textFactoriesAndFinishedText")
  void textFactoryKeepsFinishedTextAsWritten(String name, TextFactory factory, Color color, String message) {
    TextComponent component = factory.apply(message, new Object[0]);

    assertThat(component.content()).isEqualTo(message);
    assertThat(component.children()).isEmpty();
    assertThat(component.color()).isEqualTo(TextColor.color(color.getRGB()));
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryFormatsWhenArgumentsAreGiven(String name, TextFactory factory, Color color) {
    TextComponent component = factory.apply("%s has %d%% of <b>%s</b> {0}", new Object[]{"Steve", 75, "xp"});

    assertThat(component.content()).isEqualTo("Steve has 75% of <b>xp</b> {0}");
    assertThat(component.color()).isEqualTo(TextColor.color(color.getRGB()));
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryStillRejectsMissingArguments(String name, TextFactory factory, Color color) {
    assertThatThrownBy(() -> factory.apply("%s and %s", new Object[]{"one"}))
        .isInstanceOf(MissingFormatArgumentException.class);
  }

  @ParameterizedTest(name = "{0} text")
  @MethodSource("textFactories")
  void textFactoryRejectsNullText(String name, TextFactory factory, Color color) {
    assertThatThrownBy(() -> factory.apply(null, new Object[0])).isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest(name = "{0} feedback: \"{3}\"")
  @MethodSource("feedbackFactoriesAndFinishedText")
  void feedbackFactoryKeepsFinishedTextAsWritten(String name, FeedbackFactory factory, int sounds, String message) {
    Feedback feedback = factory.apply(message, new Object[0]);

    assertThat(feedback.messages()).hasSize(1);
    assertThat(feedback.messages().get(0).content()).isEqualTo(message);
    assertThat(feedback.sounds()).hasSize(sounds);
  }

  @ParameterizedTest(name = "{0} feedback")
  @MethodSource("feedbackFactories")
  void feedbackFactoryFormatsWhenArgumentsAreGiven(String name, FeedbackFactory factory, int sounds) {
    Feedback feedback = factory.apply("%d of %d (%s%%)", new Object[]{3, 4, "75"});

    assertThat(feedback.messages()).hasSize(1);
    assertThat(feedback.messages().get(0).content()).isEqualTo("3 of 4 (75%)");
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
    CommandSender sender = mock(CommandSender.class);

    FConst.info(message).send(sender);

    List<String> sent = sentText(sender);
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0)).isEqualTo("[Adapt] " + message);
  }

  private static List<String> sentText(CommandSender sender) {
    List<String> sent = new ArrayList<>();
    for (Invocation invocation : Mockito.mockingDetails(sender).getInvocations()) {
      String method = invocation.getMethod().getName();
      if (invocation.getArguments().length != 1 || !(invocation.getArgument(0) instanceof String text)) {
        continue;
      }
      if (method.equals("sendRichMessage")) {
        sent.add(PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(text)));
      } else if (method.equals("sendMessage")) {
        sent.add(text);
      }
    }
    return sent;
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

  private interface TextFactory extends BiFunction<String, Object[], TextComponent> {
  }

  private interface FeedbackFactory extends BiFunction<String, Object[], Feedback> {
  }
}
