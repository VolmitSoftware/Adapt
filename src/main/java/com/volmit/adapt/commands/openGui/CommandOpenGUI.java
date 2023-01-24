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

package com.volmit.adapt.commands.openGui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Sound;

import java.util.List;
import java.util.Objects;

public class CommandOpenGUI extends MortarCommand {
    public CommandOpenGUI() {
        super("gui", "g");
        this.setDescription("Opens the GUI");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        Skill<?> selectedSk = null;
        Adaptation<?> selectedAdpt = null;

        try {
            sender.player().playSound(sender.player().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 0.72f);
            sender.player().playSound(sender.player().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            if (args.length == 0) {
                SkillsGui.open(sender.player());
                return true;
            }

            if (args.length == 1) {
                for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                    if (Objects.equals(skill.getName(), args[0])) {
                        selectedSk = skill;
                    }
                }
                if (selectedSk == null) {
                    printHelp(sender);
                    return true;
                }
                selectedSk.openGui(sender.player());
                return true;
            }

            if (args.length == 2) {
                for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                    if (Objects.equals(skill.getName(), args[0])) {
                        selectedSk = skill;
                    }
                }
                if (selectedSk == null) {
                    printHelp(sender);
                    return true;
                }

                for (Adaptation<?> adaptation : selectedSk.getAdaptations()) {
                    if (adaptation.getName().equals(args[1])) {
                        selectedAdpt = adaptation;
                    }
                }
                if (selectedAdpt == null) {
                    printHelp(sender);
                    return true;
                }
                selectedAdpt.openGui(sender.player());
                return true;
            }

            return true;
        } catch (Exception ignored) {
            Adapt.error("Error while opening GUI");
            printHelp(sender);
            return true;
        }
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

        if (args.length == 0) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                list.add(skill.getName());
            }
        } else {
            Skill<?> skillObj = null;
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                if (Objects.equals(skill.getName(), args[0])) {
                    skillObj = skill;
                }
            }
            if (skillObj == null) {
                return;
            }
            for (Adaptation<?> adaptation : skillObj.getAdaptations()) {
                list.add(adaptation.getName());
            }
        }
    }

    @Override
    protected String getArgsUsage() {
        return "/adapt open gui <optionalSkill> <optionalAdaptation>";
    }
}
