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

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.content.gui.SkillsGui;
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
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "gui")
@ExecutionMeta(permission = "adapt.gui", description = "Open the Adapt GUI", syntax = "<guiTarget> [player] [force]")
public final class CommandGUI {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "guiTarget") @Suggest(provider = AdaptSuggestionProviderListing.class) String guiTarget,
                        @Arg(id = "player", optional = true) @Nullable Player player,
                        @Arg(id = "force", optional = true, defaultValue = "false") boolean force
    ){
        Player targetPlayer = player;
        boolean forceOpen = force;
        if (targetPlayer == null && sender instanceof ConsoleCommandSender) {
            FConst.error("You must specify a player when using this command from console.").send(sender);
        } else if (targetPlayer == null) {
            targetPlayer = (Player) sender;
        }

        if (guiTarget.equals("[Main]")) {
            SkillsGui.open(targetPlayer);
            return;
        }

        if (guiTarget.startsWith("[Skill]")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                if (guiTarget.equals("[Skill]" + skill.getName())) {
                    if (forceOpen || skill.openGui(targetPlayer, true)) {
                        FConst.success("Opened GUI for " + skill.getName() + " for " + targetPlayer.getName()).send(sender);
                    } else {
                        FConst.error("Failed to open GUI for " + skill.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(sender);
                    }
                    return;
                }
            }
        }

        if (guiTarget.startsWith("[Adaptation]")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (guiTarget.equals("[Adaptation]" + adaptation.getName())) {
                        if (forceOpen || adaptation.openGui(targetPlayer, true)) {
                            FConst.success("Opened GUI for " + adaptation.getName() + " for " + targetPlayer.getName()).send(sender);
                        } else {
                            FConst.error("Failed to open GUI for " + adaptation.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(sender);
                        }
                        return;
                    }
                }
            }
        }

        info(sender);
    }


    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt GUI Help" + C.GRAY + "]: " + " === ---").send(sender);
        FConst.info("/adapt gui (this command)").send(sender);
        FConst.info("/adapt gui <GUI-Target>").send(sender);
        FConst.info("/adapt gui <GUI-Target> [Player] [Force]").send(sender);
    }
}