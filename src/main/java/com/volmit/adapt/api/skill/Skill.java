package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public interface Skill extends Ticked {
    String getName();

    Material getIcon();

    String getDescription();

    void registerAdaptation(Adaptation a);

    KList<Adaptation> getAdaptations();

    C getColor();

    BarColor getBarColor();

    BarStyle getBarStyle();

    default String getDisplayName() {
        return C.RESET + "" + C.BOLD + getColor().toString() + Form.capitalize(getName());
    }

    default String getDisplayName(int level) {
        return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    default void xp(Player p, double xp) {
        XP.xp(p, this, xp);
    }

    default void xpSilent(Player p, double xp) {
        XP.xpSilent(p, this, xp);
    }

    default void xp(Location at, double xp, int rad, long duration) {
        XP.spatialXP(at, this, xp, rad, duration);
    }

    default void knowledge(Player p, long k) {
        XP.knowledge(p, this, k);
    }

    default void wisdom(Player p, long w) {
        XP.wisdom(p, w);
    }


    default BossBar newBossBar() {
        return Bukkit.createBossBar(getDisplayName(), getBarColor(), getBarStyle());
    }

    double getMinXp();

    default void openGui(Player player)
    {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        Window w = new UIWindow(player);
        w.setDecorator((window, position, row) -> new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE)));

        int ind = 0;

        for(Adaptation i : getAdaptations())
        {
            int pos = w.getPosition(ind);
            int row = w.getRow(ind);
            int lvl = getPlayer(player).getData().getSkillLine(getName()).getAdaptationLevel(i.getName());
            w.setElement(pos, row, new UIElement("ada-" + i.getName())
                    .setMaterial(new MaterialBlock(i.getIcon()))
                    .setName(i.getDisplayName(lvl))
                    .addLore(Form.wrapWordsPrefixed(getDescription(), "" + C.GRAY, 40))
                    .addLore(lvl == 0 ? (C.DARK_GRAY + "Not Learned") : (C.GRAY + "Level " + C.WHITE + Form.toRoman(lvl)))
                    .setProgress(1D)
                    .onLeftClick((e) -> {
                        w.close();
                        i.openGui(player);
                    }));
            ind++;
        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        w.setTitle(getDisplayName(a.getSkillLine(getName()).getLevel()));
        w.onClosed((vv) -> J.s(() -> {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);

            SkillsGui.open(player);
        }));
        w.open();
    }
}
