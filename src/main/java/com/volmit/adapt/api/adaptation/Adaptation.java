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

package com.volmit.adapt.api.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.Component;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.Recipe;

import java.util.List;

public interface Adaptation<T> extends Ticked, Component {
    int getMaxLevel();

    default void xp(Player p, double amount) {
        getSkill().xp(p, amount);
    }

    default void xp(Player p, Location l, double amount) {
        getSkill().xp(p, l, amount);
    }

    default <F> F getStorage(Player p, String key, F defaultValue) {
        PlayerData data = getPlayer(p).getData();
        if (data.getSkillLines().containsKey(getSkill().getName()) && data.getSkillLines().get(getSkill().getName()).getAdaptations().containsKey(getName())) {
            Object o = data.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getStorage().get(key);
            return o == null ? defaultValue : (F) o;
        }

        return defaultValue;
    }

    default <F> F getStorage(Player p, String key) {
        return getStorage(p, key, null);
    }

    default boolean setStorage(Player p, String key, Object value) {
        PlayerData data = getPlayer(p).getData();
        if (data.getSkillLines().containsKey(getSkill().getName()) && data.getSkillLines().get(getSkill().getName()).getAdaptations().containsKey(getName())) {
            data.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getStorage().put(key, value);
            return true;
        }

        return false;
    }

    default String getStorageString(Player p, String key, String defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default String getStorageString(Player p, String key) {
        return getStorage(p, key);
    }

    default Integer getStorageInt(Player p, String key, Integer defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Integer getStorageInt(Player p, String key) {
        return getStorage(p, key);
    }

    default Double getStorageDouble(Player p, String key, Double defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Double getStorageDouble(Player p, String key) {
        return getStorage(p, key);
    }

    default Boolean getStorageBoolean(Player p, String key, Boolean defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Boolean getStorageBoolean(Player p, String key) {
        return getStorage(p, key);
    }

    default Long getStorageLong(Player p, String key, Long defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Long getStorageLong(Player p, String key) {
        return getStorage(p, key);
    }

    Class<T> getConfigurationClass();

    void registerConfiguration(Class<T> type);

    boolean isEnabled();

    T getConfig();

    AdaptAdvancement buildAdvancements();

    void addStats(int level, Element v);

    int getBaseCost();

    String getDescription();

    Material getIcon();

    Skill<?> getSkill();

    void setSkill(Skill<?> skill);

    String getName();

    int getInitialCost();

    double getCostFactor();

    List<AdaptRecipe> getRecipes();

    void onRegisterAdvancements(List<AdaptAdvancement> advancements);

    default boolean hasAdaptation(Player p) {
        if (p.getClass().getSimpleName().equals("PlayerNPC")) {
            return false;
        }

        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return false;
        }
        return getLevel(p) > 0;
    }

    default int getLevel(Player p) {
        if (p.getClass().getSimpleName().equals("PlayerNPC")) {
            return 0;
        }
        return getPlayer(p).getData().getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
    }

    default double getLevelPercent(Player p) {
        if (p.getClass().getSimpleName().equals("PlayerNPC")) {
            return 0.0;
        }
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), getLevel(p))), 1);
    }

    default double getLevelPercent(int p) {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), p)), 1);
    }

    default int getCostFor(int level) {
        return (int) (Math.max(1, getBaseCost() + (getBaseCost() * (level * getCostFactor())))) + (level == 1 ? getInitialCost() : 0);
    }

    default int getPowerCostFor(int level, int myLevel) {
        return level - myLevel;
    }

    default int getCostFor(int level, int myLevel) {
        if (myLevel >= level) {
            return 0;
        }


        int c = 0;

        for (int i = myLevel + 1; i <= level; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default int getRefundCostFor(int level, int myLevel) {
        if (myLevel <= level) {
            return 0;
        }

        int c = 0;

        for (int i = level + 1; i <= myLevel; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default String getDisplayName() {
        return C.RESET + "" + C.BOLD + getSkill().getColor().toString() + Form.capitalizeWords(getName().replaceAll("\\Q" + getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
    }

    default String getDisplayName(int level) {
        if (level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
        }

        return getDisplayName();
    }

    default String getDisplayNameNoRoman(int level) {
        if (level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
        }

        return getDisplayName();
    }

    default BlockFace getBlockFace(Player player, int maxrange) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, maxrange);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    default void openGui(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        Window w = new UIWindow(player);
        w.setDecorator((window, position, row) -> new UIElement("bg").setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE)));
        w.setResolution(WindowResolution.W9_H6);
        int o = 0;

        if (getMaxLevel() == 1 || getMaxLevel() == 2) {
            o = 4;
        }

        if (getMaxLevel() == 3 || getMaxLevel() == 4) {
            o = 3;
        }

        if (getMaxLevel() == 5 || getMaxLevel() == 6) {
            o = 2;
        }

        if (getMaxLevel() == 7 || getMaxLevel() == 8) {
            o = 1;
        }

        int mylevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());

        long k = getPlayer(player).getData().getSkillLine(getSkill().getName()).getKnowledge();
        for (int i = 1; i <= getMaxLevel(); i++) {
            int pos = w.getPosition(i - 1 + o);
            int row = 1;
            int c = getCostFor(i, mylevel);
            int rc = getRefundCostFor(i - 1, mylevel);
            int pc = getPowerCostFor(i, mylevel);
            int lvl = i;
            Element de = new UIElement("lp-" + i + "g")
                    .setMaterial(new MaterialBlock(getIcon()))
                    .setName(getDisplayName(i))
                    .setEnchanted(mylevel >= lvl)
                    .setProgress(1D)
                    .addLore(Form.wrapWordsPrefixed(getDescription(), "" + C.GRAY, 40))
                    .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "KnowledgeCost")))
                    .addLore(mylevel >= lvl ? (C.GREEN + Adapt.dLocalize("Snippets", "AdaptMenu", "AlreadyLearned") + " " + C.GRAY + Adapt.dLocalize("Snippets", "AdaptMenu", "UnlearnRefund") + " " + C.GREEN + rc + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "KnowledgeCost")) : (k >= c ? (C.BLUE + Adapt.dLocalize("Snippets", "AdaptMenu", "ClickLearn") + " " + getDisplayName(i)) : (k == 0 ? (C.RED + Adapt.dLocalize("Snippets", "AdaptMenu", "NoKnowledge")) : (C.RED + "(" + Adapt.dLocalize("Snippets", "AdaptMenu", "YouOnlyHave") + " " + C.WHITE + k + C.RED + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "KnowledgeCost") + ")"))))
                    .addLore(mylevel < lvl && getPlayer(player).getData().hasPowerAvailable(pc) ? C.GREEN + "" + lvl + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "Power") : mylevel >= lvl ? C.GREEN + "" + lvl + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "Power") : C.RED + Adapt.dLocalize("Snippets", "AdaptMenu", "NotEnoughPower") + " \n" + C.RED + Adapt.dLocalize("Snippets", "AdaptMenu", "HowToLevelUp"))
                    .onLeftClick((e) -> {
                        if (mylevel >= lvl) {
                            getPlayer(player).getData().getSkillLine(getSkill().getName()).giveKnowledge(rc);
                            getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl - 1);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                            w.close();
                            player.sendTitle(" ", C.GRAY + Adapt.dLocalize("Snippets", "AdaptMenu", "Unlearned") + " " + getDisplayName(mylevel), 1, 5, 11);
                            J.s(() -> openGui(player), 14);
                            return;
                        }

                        if (k >= c && getPlayer(player).getData().hasPowerAvailable(pc)) {
                            if (getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c)) {
                                getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                                w.close();
                                player.sendTitle(" ", C.GRAY + Adapt.dLocalize("Snippets", "AdaptMenu", "Learned") + " " + getDisplayName(lvl), 1, 5, 11);
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
        w.setTitle(getDisplayName() + " " + C.DARK_GRAY + " " + Form.f(a.getSkillLine(getSkill().getName()).getKnowledge()) + " " + Adapt.dLocalize("Snippets", "AdaptMenu", "Knowledge"));
        w.onClosed((vv) -> J.s(() -> {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
            getSkill().openGui(player);
        }));
        w.open();
    }

    default boolean isAdaptationRecipe(Recipe recipe) {
        for (AdaptRecipe i : getRecipes()) {
            if (i.is(recipe)) {
                return true;
            }
        }

        return false;
    }
}
