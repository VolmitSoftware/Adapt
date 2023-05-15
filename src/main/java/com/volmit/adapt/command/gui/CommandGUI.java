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

package com.volmit.adapt.command.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.command.CommandAdapt;
import com.volmit.adapt.command.item.CommandExperience;
import com.volmit.adapt.command.item.CommandKnowledge;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.AdaptSuggestionProvider;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.*;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "gui", parent = CommandAdapt.class, children = {CommandExperience.class, CommandKnowledge.class})
@Syntax(syntax = "")
public final class CommandGUI {

    @Default
    public static void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt GUI Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt gui (this command)").send(sender);
        FConst.info("/adapt gui <Skill>").send(sender);
    }

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "skillamount", optional = true) @Suggest(provider = AdaptSuggestionProvider.class) String skillName,
                        @Arg(id = "player", optional = true) @Nullable Player player) {

        AdaptServer adaptServer = Adapt.instance.getAdaptServer();
        if (sender instanceof Player p) {
            if (player == null) {
                p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.72f);
                p.playSound(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            } else {
                player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.72f);
                player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            }
        } else {
            FConst.error("You must be a player to use this command, or specify a player argument").send(sender);
        }
    }
}

