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

package com.volmit.adapt.service;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.commandv2.CommandAdapt;
import com.volmit.adapt.util.AdaptService;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.cache.AtomicCache;
import com.volmit.adapt.util.collection.KMap;
import com.volmit.adapt.util.decree.DecreeSystem;
import com.volmit.adapt.util.decree.virtual.VirtualDecreeCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class CommandSVC implements AdaptService, DecreeSystem {
    private final KMap<String, CompletableFuture<String>> futures = new KMap<>();
    private final transient AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private CompletableFuture<String> consoleFuture = null;

    @Override
    public void onEnable() {
        Adapt.instance.getCommand("adapt").setExecutor(this);
        J.a(() -> getRoot().cacheAll());
    }

    @Override
    public void onDisable() {

    }

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();

        if (msg.startsWith("adaptdecree ")) {
            String[] args = msg.split("\\Q \\E");
            CompletableFuture<String> future = futures.get(args[1]);

            if (future != null) {
                future.complete(args[2]);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void on(ServerCommandEvent e) {
        if (consoleFuture != null && !consoleFuture.isCancelled() && !consoleFuture.isDone()) {
            if (!e.getCommand().contains(" ")) {
                String pick = e.getCommand().trim().toLowerCase(Locale.ROOT);
                consoleFuture.complete(pick);
                e.setCancelled(true);
            }
        }
    }

    @Override
    public VirtualDecreeCommand getRoot() {
        return commandCache.aquireNastyPrint(() -> VirtualDecreeCommand.createRoot(new CommandAdapt()));
    }

    public void post(String password, CompletableFuture<String> future) {
        futures.put(password, future);
    }

    public void postConsole(CompletableFuture<String> future) {
        consoleFuture = future;
    }
}
