package com.volmit.adapt.content.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.util.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SkillsGui {
    public static void open(Player player) {
        Window w = new UIWindow(player);
        w.setDecorator(new WindowDecorator() {
            @Override
            public Element onDecorateBackground(Window window, int position, int row) {
                return new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE));
            }
        });

        w.setResolution(WindowResolution.W9_H6);
        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        int ind = 0;
        for(PlayerSkillLine i : a.getData().getSkillLines().v())
        {
            int pos = w.getPosition(ind);
            int row = w.getRow(ind);
            Skill sk = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(i.getLine());
            w.setElement(pos, row, new UIElement("skill-" + sk.getName())
                    .setMaterial(new MaterialBlock(sk.getIcon()))
                    .setName(sk.getDisplayName(i.getLevel()))
                    .addLore(C.ITALIC + "" + C.GRAY + sk.getDescription())
                    .addLore(C.UNDERLINE + "" + C.WHITE + i.getKnowledge() + C.RESET + " " + C.GRAY + "Knowledge")
                    .onLeftClick((e) -> {
                        sk.openGui(player);
                    }));
            ind++;
        }
        w.setTitle("Skills");
        w.open();
    }
}
