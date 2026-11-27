package art.arcane.adapt.api.command;

import art.arcane.adapt.api.text.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shapes an addon command may take, checked when it is built. */
class AdaptCommandTest {
  private static final Text HELP = Text.literal("help");
  private static final CommandArgument<Integer> COUNT =
      CommandArgument.required("count", ArgumentTypes.integer(1, 5), HELP);
  private static final CommandArgument<String> NOTE = CommandArgument.optional("note", ArgumentTypes.text(), HELP);

  @Test
  void aGroupWithARunnableChildBuilds() {
    AdaptCommand command = AdaptCommand.named("rocks").alias("rk").help(HELP)
        .then(AdaptCommand.named("count").help(HELP).argument(COUNT).argument(NOTE).executes(invocation -> {
        }).build())
        .build();

    assertThat(command.isGroup()).isTrue();
    assertThat(command.words()).containsExactly("rocks", "rk");
    assertThat(command.children().get(0).arguments()).containsExactly(COUNT, NOTE);
  }

  @Test
  void invalidShapesAreRefused() {
    assertThatThrownBy(() -> AdaptCommand.named("Rocks")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdaptCommand.named("rocks").executes(invocation -> {
    }).build()).isInstanceOf(IllegalStateException.class).hasMessageContaining("help");
    assertThatThrownBy(() -> AdaptCommand.named("rocks").help(HELP).build())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> AdaptCommand.named("rocks").help(HELP).argument(COUNT)
        .then(AdaptCommand.named("a").help(HELP).executes(invocation -> {
        }).build()).build()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> AdaptCommand.named("rocks").help(HELP)
        .then(AdaptCommand.named("a").help(HELP).executes(invocation -> {
        }).build())
        .then(AdaptCommand.named("b").alias("a").help(HELP).executes(invocation -> {
        }).build()).build()).isInstanceOf(IllegalStateException.class).hasMessageContaining("a");
  }

  @Test
  void argumentOrderIsChecked() {
    assertThatThrownBy(() -> AdaptCommand.named("rocks").argument(NOTE).argument(COUNT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdaptCommand.named("rocks").argument(COUNT).argument(COUNT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdaptCommand.named("rocks")
        .argument(CommandArgument.required("rest", ArgumentTypes.text(), HELP)).argument(COUNT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builtInTypesParseAndOffer() {
    assertThat(ArgumentTypes.integer(1, 5).parse(null, "3")).contains(3);
    assertThat(ArgumentTypes.integer(1, 5).parse(null, "6")).isEmpty();
    assertThat(ArgumentTypes.decimal(0D, 1D).parse(null, "NaN")).isEmpty();
    assertThat(ArgumentTypes.bool().parse(null, "TRUE")).contains(true);
    assertThat(ArgumentTypes.choice("small", "large").parse(null, "LARGE")).contains("large");
    assertThat(ArgumentTypes.enumeration(Thread.State.class).parse(null, "blocked")).contains(Thread.State.BLOCKED);
    assertThat(ArgumentTypes.enumeration(Thread.State.class).suggest(null, "")).contains("blocked");
  }
}
