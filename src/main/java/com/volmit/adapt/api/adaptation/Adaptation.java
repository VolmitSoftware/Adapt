package com.volmit.adapt.api.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.Component;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptRecipe;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.MaterialBlock;
import com.volmit.adapt.util.UIElement;
import com.volmit.adapt.util.UIWindow;
import com.volmit.adapt.util.Window;
import com.volmit.adapt.util.WindowResolution;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

public interface Adaptation<T> extends Ticked, Component {
    int getMaxLevel();

    Class<T> getConfigurationClass();

    void registerConfiguration(Class<T> type);

    T getConfig();

    AdaptAdvancement buildAdvancements();

    void addStats(int level, Element v);

    int getBaseCost();

    String getDescription();

    Material getIcon();

    void setSkill(Skill skill);

    Skill getSkill();

    String getName();

    int getInitialCost();

    double getCostFactor();

    KList<AdaptRecipe> getRecipes();

    void onRegisterAdvancements(KList<AdaptAdvancement> advancements);

    default boolean hasAdaptation(Player p) {
        return getLevel(p) > 0;
    }

    default int getLevel(Player p) {
        return getPlayer(p).getData().getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
    }

    default double getLevelPercent(Player p) {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), getLevel(p))), 1);
    }

    default double getLevelPercent(int p) {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), p)), 1);
    }

    default int getCostFor(int level) {
        return (int) (Math.max(1, getBaseCost() + (getBaseCost() * (level * getCostFactor())))) + (level == 1 ? getInitialCost() : 0);
    }

    default int getCostFor(int level, int myLevel) {
        if(myLevel >= level) {
            return 0;
        }


        int c = 0;

        for(int i = myLevel + 1; i <= level; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default int getRefundCostFor(int level, int myLevel) {
        if(myLevel <= level) {
            return 0;
        }

        int c = 0;

        for(int i = level + 1; i <= myLevel; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default String getDisplayName() {
        return C.RESET + "" + C.BOLD + getSkill().getColor().toString() + Form.capitalizeWords(getName().replaceAll("\\Q-\\E", " "));
    }

    default String getDisplayName(int level) {
        if(level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
        }

        return getDisplayName();
    }

    default String getDisplayNameNoRoman(int level) {
        if(level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
        }

        return getDisplayName();
    }

    default void openGui(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        Window w = new UIWindow(player);
        w.setDecorator((window, position, row) -> new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE)));
        w.setResolution(WindowResolution.W9_H6);
        int o = 0;

        if(getMaxLevel() == 1 || getMaxLevel() == 2) {
            o = 4;
        }

        if(getMaxLevel() == 3 || getMaxLevel() == 4) {
            o = 3;
        }

        if(getMaxLevel() == 5 || getMaxLevel() == 6) {
            o = 2;
        }

        if(getMaxLevel() == 7 || getMaxLevel() == 8) {
            o = 1;
        }

        int mylevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());

        long k = getPlayer(player).getData().getSkillLine(getSkill().getName()).getKnowledge();
        for(int i = 1; i <= getMaxLevel(); i++) {
            int pos = w.getPosition(i - 1 + o);
            int row = 1;
            int c = getCostFor(i, mylevel);
            int rc = getRefundCostFor(i - 1, mylevel);
            int lvl = i;
            Element de = new UIElement("lp-" + i + "g")
                .setMaterial(new MaterialBlock(getIcon()))
                .setName(getDisplayName(i))
                .setEnchanted(mylevel >= lvl)
                .setProgress(1D)
                .addLore(Form.wrapWordsPrefixed(getDescription(), "" + C.GRAY, 40))
                .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " Knowledge Cost"))
                .addLore(mylevel >= lvl ? (C.GREEN + "Already Learned " + C.GRAY + "Click to Unlearn & Refund " + C.GREEN + rc + " Knowlege") : (k >= c ? (C.BLUE + "Click to Learn " + getDisplayName(i)) : (k == 0 ? (C.RED + "(You don't have any Knowledge)") : (C.RED + "(You only have " + C.WHITE + k + C.RED + " Knowledge)"))))
                .onLeftClick((e) -> {
                    if(mylevel >= lvl) {
                        getPlayer(player).getData().getSkillLine(getSkill().getName()).giveKnowledge(rc);
                        getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl - 1);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                        w.close();
                        player.sendTitle(" ", C.GRAY + "Unlearned " + getDisplayName(mylevel), 1, 5, 11);
                        J.s(() -> openGui(player), 14);
                        return;
                    }

                    if(k >= c) {
                        if(getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c)) {
                            getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                            w.close();
                            player.sendTitle(" ", C.GRAY + "Learned " + getDisplayName(lvl), 1, 5, 11);
                            J.s(() -> openGui(player), 14);
                        } else {
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);

                        }
                    } else {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);

                    }
                });
            de.addLore(" ");
            addStats(lvl, de);
            w.setElement(pos, row, de);
        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        w.setTitle(getDisplayName() + " " + C.DARK_GRAY + " " + Form.f(a.getSkillLine(getSkill().getName()).getKnowledge()) + " Knowledge");
        w.onClosed((vv) -> J.s(() -> {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
            getSkill().openGui(player);
        }));
        w.open();
    }

    default boolean isAdaptationRecipe(Recipe recipe) {
        for(AdaptRecipe i : getRecipes()) {
            if(i.is(recipe)) {
                return true;
            }
        }

        return false;
    }
}
