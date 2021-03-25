package com.volmit.adapt.api.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public interface Adaptation extends Ticked {
    public int getMaxLevel();

    public void addStats(int level, Element v);

    public int getBaseCost();

    public String getDescription();

    public Material getIcon();

    public void setSkill(Skill skill);

    public Skill getSkill();

    public String getName();

    public int getInitialCost();

    public default void damageHand(Player p, int damage)
    {
        ItemStack is = p.getInventory().getItemInMainHand();

        if(is == null || !is.hasItemMeta())
        {
            return;
        }

        ItemMeta im = is.getItemMeta();
        if(im.isUnbreakable())
        {
            return;
        }
        Damageable dm = (Damageable) im;
        dm.setDamage(dm.getDamage() + damage);

        if(dm.getDamage() > is.getType().getMaxDurability())
        {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }

        is.setItemMeta(im);
        p.getInventory().setItemInMainHand(is);
    }

    public default int getLevel(Player p)
    {
        return getPlayer(p).getData().getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
    }

    public default double getLevelPercent(Player p)
    {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), getLevel(p))), 1);
    }

    public default double getLevelPercent(int p)
    {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), p)), 1);
    }

    public default int getCostFor(int level)
    {
        return (int) (Math.max(1, getBaseCost() + (getBaseCost() * (level * getCostFactor())))) + (level == 1 ? getInitialCost() : 0);
    }

    double getCostFactor();

    public default int getCostFor(int level, int myLevel)
    {
        if(myLevel >= level)
        {
            return 0;
        }

        int c = 0;

        for(int i = myLevel+1; i <= level; i++)
        {
            c+= getCostFor(i);
        }

        return c;
    }


    public default String getDisplayName() {
        return C.RESET + "" + C.BOLD + getSkill().getColor().toString() + Form.capitalizeWords(getName().replaceAll("\\Q-\\E", " "));
    }

    public default String getDisplayName(int level) {
        if(level >= 1)
        {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
        }

        return getDisplayName();
    }

    public default void openGui(Player player)
    {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        Window w = new UIWindow(player);
        w.setDecorator(new WindowDecorator() {
            @Override
            public Element onDecorateBackground(Window window, int position, int row) {
                return new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE));
            }
        });
        w.setResolution(WindowResolution.W9_H6);
        int o = 0;

        if(getMaxLevel() == 1 || getMaxLevel() == 2)
        {
            o = 4;
        }

        if(getMaxLevel() == 3 || getMaxLevel() == 4)
        {
            o = 3;
        }

        if(getMaxLevel() ==5 || getMaxLevel() == 6)
        {
            o = 2;
        }

        if(getMaxLevel() ==7 || getMaxLevel() == 8)
        {
            o = 1;
        }

        int mylevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());

        long k = getPlayer(player).getData().getSkillLine(getSkill().getName()).getKnowledge();
        for(int i = 1; i <= getMaxLevel(); i++)
        {
            int pos = w.getPosition(i-1+o);
            int row = 1;
            int c = getCostFor(i, mylevel);
            int lvl = i;
            Element de = new UIElement("lp-" + i + "g")
                    .setMaterial(new MaterialBlock(getIcon()))
                    .setName(getDisplayName(i))
                    .setEnchanted(mylevel >= lvl)
                    .setProgress(1D)
                    .addLore(C.GRAY + getDescription())
                    .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " Knowledge Cost"))
                    .addLore(mylevel >= lvl ? (C.GREEN + "Already Learned") : (k >= c ?( C.BLUE + "Click to Learn " + getDisplayName(i)) : (k == 0 ?(C.RED + "(You don't have any Knowledge)") : (C.RED + "(You only have " + C.WHITE + k + C.RED + " Knowledge)"))))
                    .onLeftClick((e) -> {
                        if(mylevel >= lvl)
                        {
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                            return;
                        }

                        if(k >= c)
                        {
                            if(getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c))
                            {
                                getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                                w.close();
                                player.sendTitle("", C.GRAY + "Learned " + getDisplayName(lvl), 1, 5, 11);
                                J.s(() -> {
                                    openGui(player);
                                }, 14);
                            }

                            else{
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);

                            }
                        }
                        else
                        {
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);

                        }
                    });
            de.addLore(" ");
            addStats(lvl, de);
            w.setElement(pos, row, de);
        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        w.setTitle(getDisplayName(a.getSkillLine(getSkill().getName()).getLevel()));
        w.onClosed((vv) -> {
            J.s(() -> {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
                getSkill().openGui(player);
            });
        });
        w.open();
    }

}
