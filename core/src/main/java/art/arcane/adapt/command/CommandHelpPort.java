package art.arcane.adapt.command;

import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;

import java.util.List;

/**
 * Renders the Director help menu, chat menus and the version line in the platform's chat look. {@link #NONE} shows no
 * help (the words then route to Director), plain menu lines and a plain version line.
 */
public interface CommandHelpPort {
  CommandHelpPort NONE = new CommandHelpPort() {
    @Override
    public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments) {
      return false;
    }

    @Override
    public void menu(CommandSource source, CommandMenu menu) {
      source.sendLiteral("--- " + menu.title() + " ---");
      if (menu.entries().isEmpty()) {
        if (!menu.emptyLine().isBlank()) {
          source.sendMarkup(menu.emptyLine());
        }
        return;
      }
      for (String entry : menu.entries()) {
        source.sendMarkup(entry);
      }
    }

    @Override
    public String version(String plugin, String version) {
      return plugin + " v" + version;
    }
  };

  /** Sends the help page {@code arguments} ask for and returns true, or returns false when they ask for none. */
  boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments);

  /** Sends a chat menu page. */
  void menu(CommandSource source, CommandMenu menu);

  /** Adapt markup for the plugin version line. */
  String version(String plugin, String version);
}
