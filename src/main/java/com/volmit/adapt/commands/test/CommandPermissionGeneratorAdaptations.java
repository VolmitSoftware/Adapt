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

package com.volmit.adapt.commands.test;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;

import java.util.List;

public class CommandPermissionGeneratorAdaptations extends MortarCommand {
    private static final List<String> permission = List.of("adapt.idontknowwhatimdoingiswear");

    public CommandPermissionGeneratorAdaptations() {
        super("permissionsa", "pa");
        this.setDescription("This Prints all skill blacklistable permissions (Adaptations)");
    }

    @Override
    public List<String> getRequiredPermissions() {
        return permission;
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if (!sender.hasPermission("adapt.idontknowwhatimdoingiswear")) {
            sender.sendMessage("You do not have permission to use this command.");
            Adapt.info("Player: " + sender.getName() + " attempted to use command " + this + " without permission.");
            return true;
        }
        StringBuilder builder = new StringBuilder();
        Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> builder.append("adapt.blacklist." + adaptation.getName().replaceAll("-", "") + "\n")));

        Adapt.info("Permissions: \n" + builder);
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
