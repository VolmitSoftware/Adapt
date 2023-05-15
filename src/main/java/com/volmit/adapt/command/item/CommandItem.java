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

package com.volmit.adapt.command.item;

import com.volmit.adapt.command.CommandAdapt;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.Default;
import io.github.mqzn.commands.annotations.Syntax;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;

@SubCommandInfo(name = "item", parent = CommandAdapt.class, children = {CommandExperience.class, CommandKnowledge.class})
@Syntax(syntax = "", permission = "adapt.cheatitem")
public final class CommandItem {

    @Default
    public static void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt Item Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt item (this command)").send(sender);
        FConst.info("/adapt item experience <Skill> <Amount> [Player]").send(sender);
        FConst.info("/adapt item knowledge <Skill> <Amount> [Player]").send(sender);
    }

}