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

package com.volmit.adapt.content.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class CorruptionGui {
    public static void open(Player player) {
        Window w = new UIWindow(player);
        w.setResolution(WindowResolution.W9_H6);
        w.setDecorator((window, position, row) -> new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE)));

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        int ind = 0;

        w.setTitle(Adapt.dLocalize("Snippets", "GUI", "Level") + " " + (int) XP.getLevelForXp(a.getData().getMasterXp()) + " (" + a.getData().getUsedPower() + "/" + a.getData().getMaxPower() + " " + Adapt.dLocalize("Snippets", "GUI", "PowerUsed") + ")");
        w.open();
    }
}
