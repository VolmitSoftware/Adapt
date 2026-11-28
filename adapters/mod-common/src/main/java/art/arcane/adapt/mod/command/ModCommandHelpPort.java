package art.arcane.adapt.mod.command;

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

/**
 * Director help and chat menus in the same VolmLib menu look as on Bukkit: VolmLib delivers pages only to Bukkit
 * senders, so the rendered MiniMessage lines are sent here. A player gets blank lines first so the page re-renders in
 * place; the console gets the plain form.
 */
public final class ModCommandHelpPort implements CommandHelpPort {
    @Override
    public boolean help(CommandSource source, DirectorRuntimeEngine engine, List<String> arguments, Predicate<DirectorRuntimeNode> visible) {
        Optional<DirectorMiniMenu.DirectorHelpPage> resolved = DirectorMiniMenu.resolveHelp(engine, arguments);
        if (resolved.isEmpty()) {
            return false;
        }
        DirectorMiniMenu.DirectorHelpPage request = resolved.get().withEntries(
                resolved.get().entries().stream().filter(visible).toList());
        if (source.player().isEmpty()) {
            for (String line : DirectorMiniMenu.renderConsole(request, AdaptLanguage.directorResolver())) {
                if (line != null && !line.isBlank()) {
                    source.sendLiteral(line);
                }
            }
            return true;
        }
        sendPage(source, DirectorMiniMenu.render(request, DirectorMiniMenu.Theme.adaptRed(), AdaptLanguage.directorResolver()));
        return true;
    }

    @Override
    public void menu(CommandSource source, CommandMenu menu) {
        DirectorMiniMenu.ContentMenu content = new DirectorMiniMenu.ContentMenu(menu.title(), menu.command(),
                menu.parentCommand(), menu.entries(), menu.emptyLine(), menu.requestedPage(), menu.pageSize());
        if (source.player().isEmpty()) {
            for (String line : DirectorMiniMenu.renderContentConsole(content)) {
                if (line != null && !line.isBlank()) {
                    source.sendLiteral(line);
                }
            }
            return;
        }
        sendPage(source, DirectorMiniMenu.renderContent(content, DirectorMiniMenu.Theme.adaptRed(), AdaptLanguage.directorResolver()));
    }

    private static void sendPage(CommandSource source, List<String> lines) {
        source.sendLiteral("\n".repeat(DirectorMiniMenu.CHAT_LINE_COUNT - 1));
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                source.sendMarkup(line);
            }
        }
    }

    @Override
    public String version(String plugin, String version) {
        return DirectorMiniMenu.version(plugin, version, DirectorMiniMenu.Theme.adaptRed());
    }
}
