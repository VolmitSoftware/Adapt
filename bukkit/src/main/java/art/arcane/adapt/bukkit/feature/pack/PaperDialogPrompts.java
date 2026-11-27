package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Prompts as Paper Dialogs over the open window; Escape is off so exactly one of Confirm and Cancel answers. Paper only. */
final class PaperDialogPrompts {
  private static final String KEY = "value";
  private static final int BUTTON_WIDTH = 100;

  void text(Player player, String title, String current, int maxLength, Consumer<String> answer, Runnable cancelled) {
    DialogInput input = DialogInput.text(KEY, label(title))
        .labelVisible(false)
        .initial(current == null ? "" : current)
        .maxLength(Math.max(1, maxLength))
        .build();
    show(player, title, input, (DialogResponseView response) -> {
      String typed = response.getText(KEY);
      answer.accept(PromptValues.clip(typed == null ? "" : typed, maxLength));
    }, cancelled);
  }

  void number(Player player, String title, double current, Double min, Double max, Double step, DoubleConsumer answer,
              Runnable cancelled) {
    if (min == null || max == null) {
      DialogInput input = DialogInput.text(KEY, label(title)).labelVisible(false).initial(format(current)).build();
      show(player, title, input, (DialogResponseView response) -> {
        String typed = response.getText(KEY);
        Double value = typed == null ? null : PromptValues.number(typed, min, max);
        if (value == null) {
          number(player, title, current, min, max, step, answer, cancelled);
        } else {
          answer.accept(value);
        }
      }, cancelled);
      return;
    }
    DialogInput input = DialogInput.numberRange(KEY, label(title), min.floatValue(), max.floatValue())
        .initial((float) Math.max(min, Math.min(max, current)))
        .step(step == null ? null : step.floatValue())
        .build();
    show(player, title, input, (DialogResponseView response) -> {
      Float value = response.getFloat(KEY);
      answer.accept(value == null ? current : value.doubleValue());
    }, cancelled);
  }

  void choice(Player player, String title, List<String> options, int current, IntConsumer answer, Runnable cancelled) {
    List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>(options.size());
    for (int index = 0; index < options.size(); index++) {
      entries.add(SingleOptionDialogInput.OptionEntry.create(Integer.toString(index), label(options.get(index)), index == current));
    }
    DialogInput input = DialogInput.singleOption(KEY, label(title), entries).labelVisible(false).build();
    show(player, title, input, (DialogResponseView response) -> {
      String picked = response.getText(KEY);
      answer.accept(picked == null ? current : Integer.parseInt(picked));
    }, cancelled);
  }

  private void show(Player player, String title, DialogInput input, Consumer<DialogResponseView> apply, Runnable cancelled) {
    AtomicBoolean answered = new AtomicBoolean();
    ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
    ActionButton confirm = ActionButton.create(label(AdaptLanguage.text(GuiMessages.CONFIRM)), null, BUTTON_WIDTH,
        DialogAction.customClick((DialogResponseView response, Audience audience) -> {
          if (answered.compareAndSet(false, true)) {
            J.runEntity(player, () -> apply.accept(response));
          }
        }, once));
    ActionButton cancel = ActionButton.create(label(AdaptLanguage.text(GuiMessages.CANCEL)), null, BUTTON_WIDTH,
        DialogAction.customClick((DialogResponseView response, Audience audience) -> {
          if (answered.compareAndSet(false, true)) {
            J.runEntity(player, cancelled);
          }
        }, once));
    Dialog dialog = Dialog.create(factory -> factory.empty()
        .base(DialogBase.builder(label(title)).canCloseWithEscape(false).inputs(List.of(input)).build())
        .type(DialogType.confirmation(confirm, cancel)));
    player.showDialog(dialog);
  }

  private static Component label(String markup) {
    return BukkitText.component(markup);
  }

  private static String format(double value) {
    return value == Math.rint(value) && Math.abs(value) < 1.0E15 ? Long.toString((long) value) : Double.toString(value);
  }
}
