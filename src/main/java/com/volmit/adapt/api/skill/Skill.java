package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerSkillLine;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;

public interface Skill extends Ticked {
    public String getName();

    public Material getIcon();

    public String getDescription();

    public void registerAdaptation(Adaptation a);

    public KList<Adaptation> getAdaptations();

    public C getColor();

    public BarColor getBarColor();

    public BarStyle getBarStyle();

    public default String getDisplayName() {
        return C.RESET + "" + C.BOLD + getColor().toString() + Form.capitalize(getName());
    }

    public default String getDisplayName(int level) {
        return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    public default void xp(Player p, double xp) {
        XP.xp(p, this, xp);
    }

    public default void xpSilent(Player p, double xp) {
        XP.xpSilent(p, this, xp);
    }

    public default void xp(Location at, double xp, int rad, long duration) {
        XP.spatialXP(at, this, xp, rad, duration);
    }

    public default void knowledge(Player p, long k) {
        XP.knowledge(p, this, k);
    }

    public default void wisdom(Player p, long w) {
        XP.wisdom(p, w);
    }


    public default BossBar newBossBar() {
        return Bukkit.createBossBar(getDisplayName(), getBarColor(), getBarStyle());
    }

    public double getMinXp();

    public default void openGui(Player player)
    {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        Window w = new UIWindow(player);
        w.setDecorator(new WindowDecorator() {
            @Override
            public Element onDecorateBackground(Window window, int position, int row) {
                return new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE));
            }
        });

        int ind = 0;

        for(Adaptation i : getAdaptations())
        {
            int pos = w.getPosition(ind);
            int row = w.getRow(ind);
            int lvl = getPlayer(player).getData().getSkillLine(getName()).getAdaptationLevel(i.getName());
            w.setElement(pos, row, new UIElement("ada-" + i.getName())
                    .setMaterial(new MaterialBlock(i.getIcon()))
                    .setName(i.getDisplayName(lvl))
                    .addLore(C.GRAY + i.getDescription())
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
        w.onClosed((vv) -> {
            J.s(() -> {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);

                SkillsGui.open(player);
            });
        });
        w.open();
    }
}
