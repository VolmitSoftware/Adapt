/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.util;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 */
@Getter
public class MortarSender implements CommandSender {
    /**
     * -- GETTER --
     *  Get the origin sender this object is wrapping
     *
     */
    private final CommandSender s;
    /**
     * -- GETTER --
     *  Get the command tag
     * <p>
     *
     * -- SETTER --
     *  Set a command tag (prefix for sendMessage)
     *
     */
    @Setter
    private String tag;

    @Setter
    private String command;

    /**
     * Wrap a command sender
     *
     * @param s the command sender
     */
    public MortarSender(CommandSender s) {
        tag = "";
        this.s = s;
    }

    public MortarSender(CommandSender s, String tag) {
        this.tag = tag;
        this.s = s;
    }

    /**
     * Is this sender a player?
     *
     * @return true if it is
     */
    public boolean isPlayer() {
        return getS() instanceof Player;
    }

    /**
     * Force cast to player (be sure to check first)
     *
     * @return a casted player
     */
    public Player player() {
        return (Player) getS();
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return s.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return s.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        return s.hasPermission(name);
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        return s.hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return s.addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return s.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) {
        return s.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return s.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        s.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        s.recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return s.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return s.isOp();
    }

    @Override
    public void setOp(boolean value) {
        s.setOp(value);
    }

    public void hr() {
        s.sendMessage("========================================================");
    }

    @Override
    public void sendMessage(@NotNull String message) {
        s.sendMessage(C.translateAlternateColorCodes('&', getTag()) + message);
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String str : messages)
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + str));
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        s.sendMessage(sender, message);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        s.sendMessage(sender, messages);
    }

    @Override
    public @NotNull Server getServer() {
        return s.getServer();
    }

    @Override
    public @NotNull String getName() {
        return s.getName();
    }

    @Override
    public @NotNull Spigot spigot() {
        return s.spigot();
    }
}
