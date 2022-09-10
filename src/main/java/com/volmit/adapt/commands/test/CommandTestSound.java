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

import com.volmit.adapt.util.MortarCommand;
import com.volmit.adapt.util.MortarSender;
import org.bukkit.Sound;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandTestSound extends MortarCommand {
    public CommandTestSound() {
        super("sound", "s");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        sender.player().playSound(sender.player(), Sound.valueOf(args[0])
                , Float.parseFloat(args.length > 1 ? args[1] : "1")
                , Float.parseFloat(args.length > 2 ? args[2] : "1"));
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {
        if (args.length < 2) {
            String query = args.length == 1 ? args[0] : null;
            list.addAll(Arrays.stream(Sound.values()).filter(i -> query != null ? i.name().contains(query.toUpperCase(Locale.ROOT)) : true).map(i -> i.name()).collect(Collectors.toList()));
        }
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
