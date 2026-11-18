package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.command.BukkitCommandSource;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptLanguageEditor;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.BukkitLanguageEditorPresentation;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class BukkitLanguageSwitcherPort implements LanguageSwitcherPort {
  private final Plugin plugin;
  private volatile BukkitLanguageSwitcher switcher;

  BukkitLanguageSwitcherPort(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void command(CommandSource source, String[] arguments) {
    BukkitLanguageSwitcher current = switcher;
    if (current != null) {
      current.command(BukkitCommandSource.sender(source), arguments);
    }
  }

  @Override
  public List<String> complete(CommandSource source, String[] arguments) {
    BukkitLanguageSwitcher current = switcher;
    return current == null ? List.of() : current.complete(BukkitCommandSource.sender(source), arguments);
  }

  @Override
  public synchronized void open(PluginLanguageService selections) {
    close();
    switcher = BukkitLanguageSwitcher.register(plugin, selections,
        new BukkitLanguageSwitcher.Options("adapt", "adapt.configurator",
            DirectorMiniMenu.Theme.adaptRed(), AdaptLanguage.directorResolver(), AdaptLanguage.editorOptions(), null,
            AdaptLanguage::startUpstreamCheck, AdaptLanguage::displayName), presentation());
  }

  private static BukkitLanguageEditorPresentation presentation() {
    AdaptLanguageEditor editor = AdaptLanguageEditor.running();
    return new BukkitLanguageEditorPresentation(BukkitLanguageEditorPresentation.Layout.FOUR_ROWS,
        (String messageId) -> editor.icon(messageId).map(BukkitRefs::item));
  }

  @Override
  public synchronized void close() {
    BukkitLanguageSwitcher current = switcher;
    switcher = null;
    if (current != null) {
      current.close();
    }
  }
}
