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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.cache.AtomicCache;
import art.arcane.adapt.util.decree.DecreeContext;
import art.arcane.adapt.util.decree.DecreeContextHandler;
import art.arcane.adapt.util.decree.DecreeSystem;
import art.arcane.volmlib.util.director.compat.DirectorDecreeEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CommandSVC implements AdaptService, CommandExecutor, TabCompleter, DirectorInvocationHook {
    private static final String ROOT_COMMAND = "adapt";
    private static final String ROOT_PERMISSION = "adapt.main";

    private final transient AtomicCache<DirectorRuntimeEngine> directorCache = new AtomicCache<>();
    private final transient AtomicCache<DirectorVisualCommand> helpCache = new AtomicCache<>();

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
        return directorCache.aquireNastyPrint(() -> DirectorDecreeEngineFactory.create(
                new CommandAdapt(),
                null,
                buildDirectorContexts(),
                this::dispatchDirector,
                this,
                DecreeSystem.handlers
        ));
    }

    private DirectorContextRegistry buildDirectorContexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();

        for (Map.Entry<Class<?>, DecreeContextHandler<?>> entry : DecreeContextHandler.contextHandlers.entrySet()) {
            registerContextHandler(contexts, entry.getKey(), entry.getValue());
        }

        return contexts;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerContextHandler(DirectorContextRegistry contexts, Class<?> type, DecreeContextHandler<?> handler) {
        contexts.register((Class) type, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return ((DecreeContextHandler) handler).handle(new VolmitSender(sender.sender()));
            }

            return null;
        });
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
            DecreeContext.touch(new VolmitSender(sender.sender()));
        }
    }

    @Override
    public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
        DecreeContext.remove();
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase(ROOT_COMMAND)) {
            return List.of();
        }

        List<String> v = runDirectorTab(sender, alias, args);

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

        Adapt.verbose("Received Command from %s: /%s".formatted(sender.getName(), label + String.join(" ", args)));
        if (!sender.hasPermission(ROOT_PERMISSION)) {
            sender.sendMessage("You lack the Permission '" + ROOT_PERMISSION + "'");
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
            sender.sendMessage(C.RED + "Unknown Adapt Command");
        }
    }

    private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
        Optional<DirectorVisualCommand.HelpRequest> request = DirectorVisualCommand.resolveHelp(getHelpRoot(), Arrays.asList(args));
        if (request.isEmpty()) {
            return false;
        }

        VolmitSender volmitSender = new VolmitSender(sender);
        volmitSender.sendDecreeHelp(request.get().command(), request.get().page());
        return true;
    }

    private DirectorVisualCommand getHelpRoot() {
        return helpCache.aquireNastyPrint(() -> DirectorVisualCommand.createRoot(getDirector()));
    }

    private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
        try {
            return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
        } catch (Throwable e) {
            Adapt.warn("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return DirectorExecutionResult.notHandled();
        }
    }

    private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
        try {
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

    private record BukkitDirectorSender(CommandSender sender) implements DirectorSender {
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
                sender.sendMessage(message);
            }
        }
    }
}
