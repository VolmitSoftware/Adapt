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

import com.google.common.collect.ImmutableSet;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.Component;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.protection.Protector;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.event.AdaptAdaptationUseEvent;
import com.volmit.adapt.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

import java.util.*;

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

    default boolean canUse(AdaptPlayer player) {
        Adapt.verbose("Checking if " + player.getPlayer().getName() + " can use " + getName() + "...");
        AdaptAdaptationUseEvent e = new AdaptAdaptationUseEvent(!Bukkit.isPrimaryThread(), player, this);
        Bukkit.getServer().getPluginManager().callEvent(e);
        return (!e.isCancelled());
    }

    default boolean canUse(Player player) {
        return canUse(getPlayer(player));
    }

    default boolean hasBlacklistPermission(Player p, Adaptation a) {
        if (p.isOp()) { // If the player is an operator, bypass the permission check
            return false;
        }
        String blacklistPermission = "adapt.blacklist." + a.getName().replaceAll("-", "");
        Adapt.verbose("Checking if player " + p.getName() + " has blacklist permission " + blacklistPermission);

        return p.hasPermission(blacklistPermission);
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

    boolean isPermanent();

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

    List<BrewingRecipe> getBrewingRecipes();

    void onRegisterAdvancements(List<AdaptAdvancement> advancements);

    default Set<Protector> getProtectors() {
        Set<Protector> protectors = new HashSet<>(Adapt.instance.getProtectorRegistry().getDefaultProtectors());
        Map<String, Boolean> overrides = AdaptConfig.get().getProtectionOverrides().getOrDefault(this.getName(), Collections.emptyMap());
        overrides.forEach((protector, enabled) -> {
            if (enabled) {
                Protector p = Adapt.instance.getProtectorRegistry().getAllProtectors()
                        .stream()
                        .filter(pr -> pr.getName().equals(protector))
                        .findFirst()
                        .orElse(null);
                if (p == null) {
                    Adapt.error("Could not find protector " + protector + " for adaptation " + this.getName() + ". Skipping...");
                } else {
                    protectors.add(p);
                }
            } else {
                protectors.removeIf(pr -> pr.getName().equals(protector));
            }
        });
        return ImmutableSet.copyOf(protectors);
    }

    default boolean canBlockBreak(Player player, Location blockLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canBlockBreak(player, blockLocation, this));
    }

    default boolean canBlockPlace(Player player, Location blockLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canBlockPlace(player, blockLocation, this));
    }

    default boolean canPVP(Player player, Location victimLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canPVP(player, victimLocation, this));
    }

    default boolean canPVE(Player player, Location victimLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canPVE(player, victimLocation, this));
    }

    default boolean canInteract(Player player, Location targetLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canInteract(player, targetLocation, this));
    }

    default boolean canAccessChest(Player player, Location chestLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canAccessChest(player, chestLocation, this));
    }

    default boolean checkRegion(Player player) {
        return getProtectors().stream().allMatch(protector -> protector.checkRegion(player, player.getLocation(), this));
    }

    default boolean hasAdaptation(Player p) {
        try {
            if (p == null || p.isDead()) { // Check if player is not invalid
                return false;
            }
            if (!this.getSkill().isEnabled()) {
                Adapt.verbose("Skill " + this.getSkill().getName() + " is disabled. Skipping adaptation " + this.getName());
                this.unregister();
            }
            if (getLevel(p) > 0) {
                if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                    Adapt.verbose("Player " + p.getName() + " is in a blacklisted world. Skipping adaptation " + this.getName());
                    return false;
                }
                if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
                    Adapt.verbose("Player " + p.getName() + " is in creative or spectator mode. Skipping adaptation " + this.getName());
                    return false;
                }
                if (!checkRegion(p)) {
                    Adapt.verbose("Player " + p.getName() + " don't have adaptation - " + this.getName() + " permission.");
                    return false;
                }

                if (hasBlacklistPermission(p, this)) {
                    Adapt.verbose("Player " + p.getName() + " has blacklist permission for adaptation " + this.getName());
                    return false;
                }
                if (!canUse(p)) {
                    Adapt.verbose("Player " + p.getName() + " can't use adaptation, This is an API restriction" + this.getName());
                    return false;
                }
                Adapt.verbose("Player " + p.getName() + " used adaptation " + this.getName());
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            if (e instanceof IndexOutOfBoundsException) { // This is that fucking bug with Citizens Spoofing Players. I hate it.
                Adapt.verbose("Citizens/PacketSpoofing is Messing stuff up again. I hate it.");
                Adapt.verbose(e.getMessage());
            } else {
                e.printStackTrace();
            }
            return false;
        }
    }

    default int getLevel(Player p) {
        if (p == null) {
            return 0;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            Adapt.verbose("Simple name: " + p.getClass().getSimpleName());
            return 0;
        }
        if (!this.getSkill().isEnabled()) {
            this.unregister();
            return 0;
        } else {
            PlayerSkillLine line = getPlayer(p).getData().getSkillLine(getSkill().getName());
            if (line == null)
                return 0;
            return getPlayer(p).getData().getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
        }
    }

    default double getLevelPercent(Player p) {
        if (!this.getSkill().isEnabled()) {
            this.unregister();
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
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
        if (!this.getSkill().isEnabled()) {
            this.unregister();
        }
        return C.RESET + "" + C.BOLD + getSkill().getColor().toString() + Form.capitalizeWords(getName().replaceAll("\\Q" + getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
    }

    default String getDisplayName(int level) {
        if (!this.getSkill().isEnabled()) {
            this.unregister();
        }
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

    default boolean openGui(Player player, boolean checkPermissions) {
        if (hasBlacklistPermission(player, this)) {
            return false;
        } else {
            openGui(player);
            return true;
        }
    }

    default void openGui(Player player) {
        for (Player players : player.getWorld().getPlayers()) {
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        }
        Window w = new UIWindow(player);
        w.setTag("skill/" + getSkill().getName() + "/" + getName());
        w.setDecorator((window, position, row) -> new UIElement("bg").setName(" ").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE)));
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
                    .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " " + Localizer.dLocalize("snippets", "adaptmenu", "knowledgecost") + " " + (AdaptConfig.get().isHardcoreNoRefunds() ? C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets", "adaptmenu", "norefunds") : "")))
                    .addLore(mylevel >= lvl ? AdaptConfig.get().isHardcoreNoRefunds() ? (C.GREEN + Localizer.dLocalize("snippets", "adaptmenu", "alreadylearned") + " " + C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets", "adaptmenu", "norefunds")) : (isPermanent() ? "" : (C.GREEN + Localizer.dLocalize("snippets", "adaptmenu", "alreadylearned") + " " + C.GRAY + Localizer.dLocalize("snippets", "adaptmenu", "unlearnrefund") + " " + C.GREEN + rc + " " + Localizer.dLocalize("snippets", "adaptmenu", "knowledgecost"))) : (k >= c ? (C.BLUE + Localizer.dLocalize("snippets", "adaptmenu", "clicklearn") + " " + getDisplayName(i)) : (k == 0 ? (C.RED + Localizer.dLocalize("snippets", "adaptmenu", "noknowledge")) : (C.RED + "(" + Localizer.dLocalize("snippets", "adaptmenu", "youonlyhave") + " " + C.WHITE + k + C.RED + " " + Localizer.dLocalize("snippets", "adaptmenu", "knowledgeavailable") + ")"))))
                    .addLore(mylevel < lvl && getPlayer(player).getData().hasPowerAvailable(pc) ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets", "adaptmenu", "powerdrain") : mylevel >= lvl ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets", "adaptmenu", "powerdrain") : C.RED + Localizer.dLocalize("snippets", "adaptmenu", "notenoughpower") + "\n" + C.RED + Localizer.dLocalize("snippets", "adaptmenu", "howtolevelup"))
                    .addLore((isPermanent() ? C.RED + "" + C.BOLD + Localizer.dLocalize("snippets", "adaptmenu", "maynotunlearn") : ""))
                    .onLeftClick((e) -> {
                        if (mylevel >= lvl) {
                            unlearn(player, lvl, false);

                            for (Player players : player.getWorld().getPlayers()) {
                                players.playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                                players.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                            }
                            w.close();
                            if (AdaptConfig.get().getLearnUnlearnButtonDelayTicks() != 0) {
                                if (isPermanent()) {
                                    for (Player players : player.getWorld().getPlayers()) {
                                        players.playSound(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 0.5f, 1.355f);
                                    }
                                    player.sendTitle(" ", C.RED + "" + C.BOLD + Localizer.dLocalize("snippets", "adaptmenu", "maynotunlearn") + " " + getDisplayName(mylevel), 1, 10, 11);
                                } else {
                                    player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets", "adaptmenu", "unlearned") + " " + getDisplayName(mylevel), 1, 10, 11);
                                }
                            }
                            J.s(() -> openGui(player), AdaptConfig.get().getLearnUnlearnButtonDelayTicks());
                            return;
                        }

                        if (k >= c && getPlayer(player).getData().hasPowerAvailable(pc)) {
                            if (getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c)) {
                                getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
                                for (Player players : player.getWorld().getPlayers()) {
                                    players.playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                                    players.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                                    players.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                                    players.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                                    if (isPermanent()) {
                                        players.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.355f);
                                        players.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.7f, 1.355f);
                                    }
                                }
                                w.close();
                                if (AdaptConfig.get().getLearnUnlearnButtonDelayTicks() != 0) {
                                    player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets", "adaptmenu", "learned") + " " + getDisplayName(lvl), 1, 5, 11);
                                }
                                J.s(() -> openGui(player), AdaptConfig.get().getLearnUnlearnButtonDelayTicks());
                            } else {
                                for (Player players : player.getWorld().getPlayers()) {
                                    players.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                                }
                            }
                        } else {
                            for (Player players : player.getWorld().getPlayers()) {
                                players.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                            }
                        }
                    });
            de.addLore(" ");
            addStats(lvl, de);
            w.setElement(pos, row, de);
        }

        if (AdaptConfig.get().isGuiBackButton()) {
            int backPos = w.getResolution().getWidth() - 1;
            int backRow = w.getViewportHeight() - 1;
            w.setElement(backPos, backRow, new UIElement("back")
                    .setMaterial(new MaterialBlock(Material.RED_BED))
                    .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets", "gui", "back"))
                    .onLeftClick((e) -> {
                        w.close();
                        onGuiClose(player, true);
                    }));
        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        w.setTitle(getDisplayName() + " " + C.DARK_GRAY + " " + Form.f(a.getSkillLine(getSkill().getName()).getKnowledge()) + " " + Localizer.dLocalize("snippets", "adaptmenu", "knowledge"));
        w.onClosed((vv) -> J.s(() -> onGuiClose(player, !AdaptConfig.get().isEscClosesAllGuis())));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private void onGuiClose(Player player, boolean openPrevGui) {
        for (Player players : player.getWorld().getPlayers()) {
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        }
        if (openPrevGui) {
            getSkill().openGui(player);
        }
    }

    default void unlearn(Player player, int lvl, boolean force) {
        if (isPermanent() && !force) {
            return;
        }
        int myLevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
        int rc = getRefundCostFor(lvl - 1, myLevel);
        if (!AdaptConfig.get().isHardcoreNoRefunds()) {
            getPlayer(player).getData().getSkillLine(getSkill().getName()).giveKnowledge(rc);
        }
        getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl - 1);
    }

    default void learn(Player player, int lvl, boolean force) {
        int myLevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
        int c = getCostFor(lvl, myLevel);
        if (getPlayer(player).getData().hasPowerAvailable(c) || force) {
            if (getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c) || force) {
                getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
            }
        }
    }

    default boolean isAdaptationRecipe(Recipe recipe) {
        if (!this.getSkill().isEnabled()) {
            this.unregister();
        }
        for (AdaptRecipe i : getRecipes()) {
            if (i.is(recipe)) {
                return true;
            }
        }
        return false;
    }
}
