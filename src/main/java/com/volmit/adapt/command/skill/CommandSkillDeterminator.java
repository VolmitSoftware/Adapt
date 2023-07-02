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

package com.volmit.adapt.command.skill;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.command.suggest.AdaptAdaptationProvider;
import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.command.suggest.BooleanSuggestionProvider;
import io.github.mqzn.commands.annotations.base.*;
import io.github.mqzn.commands.annotations.subcommands.SubCommandExecution;
import io.github.mqzn.commands.annotations.subcommands.SubCommandInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

@SubCommandInfo(name = "determine")
@ExecutionMeta(permission = "adapt.determine", description = "Assign a skill, or UnAssign a skill as if you are learning / unlearning a skill.",
        syntax = "<adaptationTarget> <assign> <force> <level> [player]")
public final class CommandSkillDeterminator {

    @SubCommandExecution
    public void execute(CommandSender sender,
                        @Arg(id = "adaptationTarget") @Suggest(provider = AdaptAdaptationProvider.class) String adaptationTarget,
                        @Arg(id = "assign", defaultValue = "true") @Suggest(provider = BooleanSuggestionProvider.class) boolean assign,
                        @Arg(id = "force", defaultValue = "false") @Suggest(provider = BooleanSuggestionProvider.class) boolean force,
                        @Arg(id = "level", defaultValue = "1") @Range(min = "1", max = "54") Integer level,
                        @Arg(id = "player", optional = true) @Nullable Player player
    ) {
        Player targetPlayer = player;
        if (targetPlayer == null && sender instanceof ConsoleCommandSender) {
            FConst.error("You must specify a player when using this command from console.").send(sender);
        } else if (targetPlayer == null) {
            targetPlayer = (Player) sender;
        }

        //the format is skillname:adaptationname
        String[] split = adaptationTarget.split(":");
        String skillname = split[0];
        String adaptationname = split[1];

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            if (skill.getName().equalsIgnoreCase(skillname)) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (adaptation.getName().equalsIgnoreCase(adaptationname)) {
                        if (targetPlayer != null) {
                            if (assign) {
                                adaptation.learn(player, level, force);
                            } else {
                                adaptation.unlearn(player, level, force);
                            }
                        } else {
                            FConst.error("You must specify a player when using this command from console.").send(sender);
                        }
                        return;
                    }
                }
                return;
            }
        }
        info(sender);
    }


    @Default
    public void info(CommandSender sender) {
        FConst.success(" --- === " + C.GRAY + "[" + C.DARK_RED + "Adapt GUI Help" + C.GRAY + "]: " + " === ---").send(sender);
        FConst.info("/adapt determine <adaptationTarget> <assign> <level> [player] [force]").send(sender);
    }
}