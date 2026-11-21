package art.arcane.adapt.localization;

import art.arcane.adapt.command.CommandSource;
import art.arcane.volmlib.util.localization.PluginLanguageService;

import java.util.List;

/**
 * The platform's per-player language chooser and in-game editor (the {@code /adapt language} command, join prompts
 * and the editor GUI). {@link AdaptLanguage#start()} opens it over the shared selection service and
 * {@link AdaptLanguage#shutdown()} closes it. {@link #NONE} offers no chooser.
 */
public interface LanguageSwitcherPort {
  LanguageSwitcherPort NONE = new LanguageSwitcherPort() {
    @Override
    public void open(PluginLanguageService selections) {
    }

    @Override
    public void close() {
    }

    @Override
    public void command(CommandSource source, String[] arguments) {
    }

    @Override
    public List<String> complete(CommandSource source, String[] arguments) {
      return List.of();
    }
  };

  void open(PluginLanguageService selections);

  void close();

  /** Runs {@code /adapt language} with the words after {@code language}. */
  void command(CommandSource source, String[] arguments);

  /** Completions for the words after {@code /adapt language}. */
  List<String> complete(CommandSource source, String[] arguments);

  /** True when some players edit messages in {@link LanguageEditorGui}, which takes chat prompts from the core bus. */
  default boolean usesCoreEditor() {
    return false;
  }
}
