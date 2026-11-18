package art.arcane.adapt.command;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;

/** The diagnostic report behind {@code /adapt debug dump}. {@link #NONE} has no report to offer. */
public interface DebugDumpPort {
  DebugDumpPort NONE = (CommandSource source, boolean upload) ->
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_ADAPT_COMMAND)).send(source);

  void request(CommandSource source, boolean upload);
}
