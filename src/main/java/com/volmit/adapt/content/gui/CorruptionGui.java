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

        w.setTitle("Level"+ " " + (int) XP.getLevelForXp(a.getData().getMasterXp()) + " (" + a.getData().getUsedPower() + "/" + a.getData().getMaxPower() + " " + "Power Used" + ")");
        w.open();
    }
}
