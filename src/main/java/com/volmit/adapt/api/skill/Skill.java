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

package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.Component;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public interface Skill<T> extends Ticked, Component {
    AdaptAdvancement buildAdvancements();

    Class<T> getConfigurationClass();

    void registerConfiguration(Class<T> type);

    boolean isEnabled();

    T getConfig();

    String getName();

    String getEmojiName();

    Material getIcon();

    String getDescription();

    List<AdaptRecipe> getRecipes();

    void registerAdaptation(Adaptation<?> a);

    void registerStatTracker(AdaptStatTracker tracker);

    List<AdaptStatTracker> getStatTrackers();

    default void checkStatTrackers(AdaptPlayer player) {
        if (!this.isEnabled()) {
            this.unregister();
        }
        if (!player.getPlayer().getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        if (!player.getAdvancementHandler().isReady()) {
            return;
        }
        if (!AdaptConfig.get().isAdvancements()) {
            return;
        }
        PlayerData d = player.getData();

        for (AdaptStatTracker i : getStatTrackers()) {
            if (!d.isGranted(i.getAdvancement()) && d.getStat(i.getStat()) >= i.getGoal()) {
                player.getAdvancementHandler().grant(i.getAdvancement());
                xp(player.getPlayer(), i.getReward());
            }
        }
    }

    List<Adaptation<?>> getAdaptations();

    C getColor();

    double getMinXp();

    void onRegisterAdvancements(List<AdaptAdvancement> advancements);

    default boolean hasBlacklistPermission(Player p, Skill s) {
        if (p.isOp()) { // If the player is an operator, bypass the permission check
            return false;
        }
        String blacklistPermission = "adapt.blacklist." + s.getName().replaceAll("-", "");
        Adapt.verbose("Checking if player " + p.getName() + " has blacklist permission " + blacklistPermission);
        return p.hasPermission(blacklistPermission);
    }

    default String getDisplayName() {
        if (!this.isEnabled()) {
            this.unregister();
        }
        return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName() + " " + Form.capitalize(getName());
    }

    default String getShortName() {
        if (!this.isEnabled()) {
            this.unregister();
        }
        return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName();
    }

    default String getDisplayName(int level) {
        if (!this.isEnabled()) {
            this.unregister();
        }
        return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    default void xp(Player p, double xp) {
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        xp(p, p.getLocation(), xp);

    }

    default void xp(Player p, Location at, double xp) {
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xp(p, this, xp);
            if (xp > 50) {
                vfxXP(p, at, (int) xp);
            }
            Adapt.verbose("Gave " + p.getName() + " " + xp + " xp in " + getName() + " " + this.getClass());
        } catch (Exception e) {
            Adapt.verbose("Failed to give xp to " + p.getName() + " for " + getName() + " (" + xp + ")");
        }
    }

    default void xpS(Player p, Location at, double xp) {
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xpSilent(p, this, xp);
            if (xp > 50) {
                vfxXP(p, at, (int) xp);
            }
            Adapt.verbose("Gave " + p.getName() + " " + xp + " xp in " + getName() + " " + this.getClass());
        } catch (Exception e) {
            Adapt.verbose("Failed to give xp to " + p.getName() + " for " + getName() + " (" + xp + ")");
        }
    }

    default void xpSilent(Player p, double xp) {
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xpSilent(p, this, xp);
        } catch (
                Exception ignored) { // Player was Given XP (Likely Teleportation) before i can see it because some plugin has higher priority than me and moves a player. so im not going to throw an error, as i know why it's happening.
            Adapt.verbose("Player was Given XP (Likely Teleportation) before i can see it because some plugin has higher priority than me and moves a player. so im not going to throw an error, as i know why it's happening.");
        }
    }


    default void xp(Location at, double xp, int rad, long duration) {
        XP.spatialXP(at, this, xp, rad, duration);
        vfxXP(at);
    }

    default void knowledge(Player p, long k) {
        XP.knowledge(p, this, k);
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
        if (!this.isEnabled()) {
            this.unregister();
        }
        if (!player.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        for (Player players : player.getWorld().getPlayers()) {
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        }
        Window w = new UIWindow(player);
        w.setTag("skill/" + getName());
        w.setDecorator((window, position, row) -> new UIElement("bg").setName(" ").setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE)));

        int ind = 0;

        for (Adaptation i : getAdaptations()) {
            if (i.hasBlacklistPermission(player, i)) {
                continue;
            }
            int pos = w.getPosition(ind);
            int row = w.getRow(ind);
            int lvl = getPlayer(player).getData().getSkillLine(getName()).getAdaptationLevel(i.getName());
            w.setElement(pos, row, new UIElement("ada-" + i.getName())
                    .setMaterial(new MaterialBlock(i.getIcon()))
                    .setName(i.getDisplayName(lvl))
                    .addLore(Form.wrapWordsPrefixed(i.getDescription(), "" + C.GRAY, 45)) // Set to the actual Description
                    .addLore(lvl == 0 ? (C.DARK_GRAY + Localizer.dLocalize("snippets", "gui", "notlearned")) : (C.GRAY + Localizer.dLocalize("snippets", "gui", "level") + " " + C.WHITE + Form.toRoman(lvl)))
                    .setProgress(1D)
                    .onLeftClick((e) -> {
                        w.close();
                        i.openGui(player);
                    }));
            ind++;
        }

        if (AdaptConfig.get().isGuiBackButton()) {
            int backPos = w.getResolution().getWidth() - 1;
            int backRow = w.getViewportHeight() - 1;
            if (w.getElement(backPos, backRow) != null) backRow++;
            w.setElement(backPos, backRow, new UIElement("back")
                    .setMaterial(new MaterialBlock(Material.RED_BED))
                    .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets", "gui", "back"))
                    .onLeftClick((e) -> {
                        w.close();
                        onGuiClose(player, true);
                    }));
        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        w.setTitle(getDisplayName(a.getSkillLine(getName()).getLevel()) + " " + Form.pc(XP.getLevelProgress(a.getSkillLine(getName()).getXp())) + " (" + Form.f((int) XP.getXpUntilLevelUp(a.getSkillLine(getName()).getXp())) + Localizer.dLocalize("snippets", "gui", "xp") + " " + (a.getSkillLine(getName()).getLevel() + 1) + ")");
        w.onClosed((vv) -> J.s(() -> onGuiClose(player, !AdaptConfig.get().isEscClosesAllGuis())));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private void onGuiClose(Player player, boolean openPrevGui) {
        for (Player players : player.getWorld().getPlayers()) {
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
            players.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        }
        if (openPrevGui) {
            SkillsGui.open(player);
        }
    }
}
