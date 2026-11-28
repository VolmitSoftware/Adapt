package art.arcane.adapt.bukkit.command;

import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.CommandMenu;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Director help and the version line in the shared VolmLib menu look. */
public final class BukkitCommandHelpPort implements CommandHelpPort {
  @Override
  public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments, Predicate<DirectorRuntimeNode> visible) {
    Optional<DirectorMiniMenu.DirectorHelpPage> request = DirectorMiniMenu.resolveHelp(engine, arguments);
    if (request.isEmpty()) {
      return false;
    }
    DirectorMiniMenu.deliver(BukkitCommandSource.sender(source), request.get(), DirectorMiniMenu.Theme.adaptRed(),
        AdaptLanguage.directorResolver(), visible);
    return true;
  }

  @Override
  public void menu(CommandSource source, CommandMenu menu) {
    DirectorMiniMenu.deliverContent(BukkitCommandSource.sender(source), new DirectorMiniMenu.ContentMenu(menu.title(), menu.command(),
        menu.parentCommand(), menu.entries(), menu.emptyLine(), menu.requestedPage(), menu.pageSize()),
        DirectorMiniMenu.Theme.adaptRed(), AdaptLanguage.directorResolver());
  }

  @Override
  public String version(String plugin, String version) {
    return DirectorMiniMenu.version(plugin, version, DirectorMiniMenu.Theme.adaptRed());
  }
}
