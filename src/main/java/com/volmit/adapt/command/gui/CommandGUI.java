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
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.AdaptSuggestionProviderListing;
import com.volmit.adapt.util.command.FConst;
import io.github.mqzn.commands.annotations.base.Arg;
import io.github.mqzn.commands.annotations.base.Default;
import io.github.mqzn.commands.annotations.base.ExecutionMeta;
import io.github.mqzn.commands.annotations.base.Suggest;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "gui", aliases = "g")
@ExecutionMeta(permission = "adapt.gui", description = "Open the Adapt GUI", syntax = "<skillname> [player]")
public final class CommandGUI {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "skillname") @Suggest(provider = AdaptSuggestionProviderListing.class) String skillName,
                        @Arg(id = "player", optional = true) @Nullable Player player) {
        Player p = null;
        if (player == null && sender instanceof Player) {
            p = (Player) sender;
        } else if (player != null) {
            p = player;
        }
        if (skillName == null || skillName.equals("[Main]")) {
            if (p != null) {
                Adapt.instance.getAdaptServer().openAdaptGui(p);
            } else {
                FConst.error("You must be a player to use this command").send(sender);
            }
            return;
        }
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getSkills()) {
            if (skill.getName().equalsIgnoreCase(skillName)) {
                if (p != null) {
                    Adapt.instance.getAdaptServer().openSkillGUI(skill, p);
                } else {
                    FConst.error("You must be a player to use this command").send(sender);
                }
                return;
            }
        }
        FConst.error(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt GUI Usage" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt gui <Skill>").send(sender);
        FConst.info("/adapt gui <Skill/[Main]> [Player]").send(sender);
    }

    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt GUI Help" + C.GRAY + "]: " + " === ---");
        FConst.info("/adapt gui (this command)").send(sender);
        FConst.info("/adapt gui <Skill>").send(sender);
        FConst.info("/adapt gui <Skill> [Player]").send(sender);
    }
}