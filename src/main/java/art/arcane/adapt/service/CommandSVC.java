/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.command.CommandAdapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.cache.AtomicCache;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.director.DirectorSystem;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu.DirectorHelpPage;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class CommandSVC implements AdaptService, CommandExecutor, TabCompleter, DirectorInvocationHook {
  private static final String ROOT_COMMAND = "adapt";
  private static final String ROOT_PERMISSION = "adapt.main";

  private final ThreadLocal<Deque<LanguageAudience.Scope>> languageScopes = ThreadLocal.withInitial(ArrayDeque::new);

  private final transient AtomicCache<DirectorRuntimeEngine> directorCache = new AtomicCache<>();

  @Override
  public void onEnable() {
    Adapt.verbose("Initializing Commands...");
    PluginCommand command = Adapt.instance.getCommand(ROOT_COMMAND);
    if (command == null) {
      Adapt.warn("Failed to find command '" + ROOT_COMMAND + "'");
      return;
    }

    command.setExecutor(this);
    command.setTabCompleter(this);
    J.a(this::getDirector);
  }

  @Override
  public void onDisable() {

  }

  public DirectorRuntimeEngine getDirector() {
    return directorCache.aquireNastyPrint(() -> DirectorEngineFactory.create(
        new CommandAdapt(),
        DirectorEngineOptions.builder()
            .contexts(buildDirectorContexts())
            .dispatcher(this::dispatchDirector)
            .invocationHook(this)
            .legacyHandlers(DirectorSystem.handlers)
            .textResolver(AdaptLanguage.directorResolver())
            .build()
    ));
  }

  private DirectorContextRegistry buildDirectorContexts() {
    DirectorContextRegistry contexts = new DirectorContextRegistry();
    contexts.register(CommandSender.class, (invocation, map) -> {
      if (invocation.getSender() instanceof BukkitDirectorSender sender) {
        return sender.sender();
      }
      return null;
    });
    contexts.register(World.class, (invocation, map) -> {
      if (invocation.getSender() instanceof BukkitDirectorSender sender && sender.sender() instanceof Player player) {
        return player.getWorld();
      }

      return null;
    });

    return contexts;
  }

  private void dispatchDirector(DirectorExecutionMode mode, Runnable runnable) {
    if (mode == DirectorExecutionMode.SYNC) {
      J.s(runnable);
    } else {
      runnable.run();
    }
  }

  @Override
  public void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    if (invocation.getSender() instanceof BukkitDirectorSender sender) {
      languageScopes.get().push(LanguageAudience.open(sender.sender() instanceof Player player ? player.getUniqueId() : null));
      BukkitDirectorContext.touch(sender.sender());
    }
  }

  @Override
  public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    BukkitDirectorContext.remove();
    Deque<LanguageAudience.Scope> scopes = languageScopes.get();
    if (!scopes.isEmpty()) {
      scopes.pop().close();
    }
    if (scopes.isEmpty()) {
      languageScopes.remove();
    }
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
      return List.of();
    }

    List<String> v = args.length > 0 && args[0].equalsIgnoreCase("language")
        ? AdaptLanguage.completeLanguage(sender, Arrays.copyOfRange(args, 1, args.length))
        : runDirectorTab(sender, alias, args);

    if (sender instanceof Player p) {
      SoundPlayer sp = SoundPlayer.of(p);
      sp.play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, RNG.r.f(0.125f, 1.95f));
    }

    return v;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
      return false;
    }

    Adapt.verbose(() -> "Received command from %s: /%s %s"
        .formatted(sender.getName(), label, String.join(" ", args)));
    if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
      AdaptLanguage.language(sender, Arrays.copyOfRange(args, 1, args.length));
      return true;
    }
    if (!(args.length > 0 && args[0].equalsIgnoreCase("debugdump"))
        && !sender.hasPermission(ROOT_PERMISSION)) {
      ComponentMessenger.sendSection(sender, AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", ROOT_PERMISSION)
      ));
      return true;
    }

    executeCommand(sender, label, args);
    return true;
  }

  private void executeCommand(CommandSender sender, String label, String[] args) {
    if (sendHelpIfRequested(sender, args)) {
      playSuccessSound(sender);
      return;
    }

    DirectorExecutionResult result = runDirector(sender, label, args);

    if (result.isSuccess()) {
      playSuccessSound(sender);
      return;
    }

    playFailureSound(sender);
    if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
      ComponentMessenger.sendSection(sender, C.RED + AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_ADAPT_COMMAND));
    }
  }

  private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(sender instanceof Player player ? player.getUniqueId() : null)) {
      Optional<DirectorHelpPage> request = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args));
      if (request.isEmpty()) {
        return false;
      }

      DirectorMiniMenu.deliver(
          sender,
          request.get(),
          DirectorMiniMenu.Theme.adaptRed(),
          AdaptLanguage.directorResolver()
      );
      return true;
    }
  }

  private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(sender instanceof Player player ? player.getUniqueId() : null)) {
      return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
    } catch (Throwable e) {
      Adapt.warn("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(sender instanceof Player player ? player.getUniqueId() : null)) {
      return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
    } catch (Throwable e) {
      Adapt.warn("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
      return List.of();
    }
  }

  private void playFailureSound(CommandSender sender) {
    if (sender instanceof Player player) {
      SoundPlayer sp = SoundPlayer.of(player);
      sp.play(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.77f, 0.25f);
      sp.play(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.2f, 0.45f);
    }
  }

  private void playSuccessSound(CommandSender sender) {
    if (sender instanceof Player player) {
      SoundPlayer sp = SoundPlayer.of(player);
      sp.play(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.77f, 1.65f);
      sp.play(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.125f, 2.99f);
    }
  }

  private record BukkitDirectorSender(
      CommandSender sender) implements DirectorSender {
    @Override
    public String getName() {
      return sender.getName();
    }

    @Override
    public boolean isPlayer() {
      return sender instanceof Player;
    }

    @Override
    public void sendMessage(String message) {
      if (message != null && !message.trim().isEmpty()) {
        ComponentMessenger.sendLiteral(sender, message);
      }
    }
  }
}
