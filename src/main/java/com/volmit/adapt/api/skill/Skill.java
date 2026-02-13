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
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.collection.KList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    KList<AdaptRecipe> getRecipes();

    void registerAdaptation(Adaptation<?> a);

    void registerStatTracker(AdaptStatTracker tracker);

    KList<AdaptStatTracker> getStatTrackers();

    @Override
    default boolean areParticlesEnabled() {
        if (!Component.super.areParticlesEnabled()) {
            return false;
        }

        AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
        if (effects != null && effects.getSkillParticleOverrides() != null && !effects.getSkillParticleOverrides().isEmpty()) {
            String key = getName();
            Boolean override = effects.getSkillParticleOverrides().get(key);
            if (override == null && key != null) {
                override = effects.getSkillParticleOverrides().get(key.toLowerCase(Locale.ROOT));
            }
            if (override != null && !override) {
                return false;
            }
        }

        Object config = getConfig();
        if (config != null) {
            Boolean directToggle = readBooleanField(config, "showParticles");
            if (directToggle != null && !directToggle) {
                return false;
            }

            Boolean genericToggle = readBooleanField(config, "showParticleEffects");
            if (genericToggle != null && !genericToggle) {
                return false;
            }
        }

        return true;
    }

    @Override
    default boolean areSoundsEnabled() {
        if (!Component.super.areSoundsEnabled()) {
            return false;
        }

        Object config = getConfig();
        if (config != null) {
            Boolean directToggle = readBooleanField(config, "showSounds");
            if (directToggle != null && !directToggle) {
                return false;
            }
        }

        return true;
    }

    private static Boolean readBooleanField(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(source);
                if (value instanceof Boolean bool) {
                    return bool;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }

        return null;
    }

    default void checkStatTrackers(AdaptPlayer player) {
        if (!this.isEnabled()) {
            return;
        }
        if (!player.getPlayer().getClass().getSimpleName().equals("CraftPlayer")) {
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

        for (Adaptation<?> adaptation : getAdaptations()) {
            if (!(adaptation instanceof SimpleAdaptation<?> sa)) continue;
            if (!adaptation.isEnabled()) continue;
            for (AdaptStatTracker tracker : sa.getStatTrackers()) {
                if (!d.isGranted(tracker.getAdvancement()) && d.getStat(tracker.getStat()) >= tracker.getGoal()) {
                    player.getAdvancementHandler().grant(tracker.getAdvancement());
                    xp(player.getPlayer(), tracker.getReward());
                }
            }
        }
    }

    KList<Adaptation<?>> getAdaptations();

    C getColor();

    double getMinXp();

    void onRegisterAdvancements(KList<AdaptAdvancement> advancements);

    default boolean hasBlacklistPermission(Player p, Skill<?> s) {
        if (p.isOp()) { // If the player is an operator, bypass the permission check
            return false;
        }
        String blacklistPermission = "adapt.blacklist." + s.getName().replaceAll("-", "");
        Adapt.verbose("Checking if player " + p.getName() + " has blacklist permission " + blacklistPermission);
        return p.hasPermission(blacklistPermission);
    }

    default String getDisplayName() {
        if (!this.isEnabled()) {
            return C.DARK_GRAY + Form.capitalize(getName());
        }
        return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName() + " " + Form.capitalize(getName());
    }

    default String getShortName() {
        if (!this.isEnabled()) {
            return C.DARK_GRAY + Form.capitalize(getName());
        }
        return C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName();
    }

    default String getDisplayName(int level) {
        if (!this.isEnabled()) {
            return C.DARK_GRAY + Form.capitalize(getName());
        }
        return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    default CustomModel getModel() {
        return CustomModel.get(getIcon(), "skill", getName());
    }

    default void xp(Player p, double xp) {
        xp(p, xp, null);
    }

    default void xp(Player p, double xp, String rewardKey) {
        if (!this.isEnabled()) {
            return;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        xp(p, p.getLocation(), xp, rewardKey);

    }

    default void xp(Player p, Location at, double xp) {
        xp(p, at, xp, null);
    }

    default void xp(Player p, Location at, double xp, String rewardKey) {
        if (!this.isEnabled()) {
            return;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xp(p, this, xp, rewardKey);
            if (xp > 50) {
                vfxXP(p, at, (int) xp);
            }
            Adapt.verbose("Gave " + p.getName() + " " + xp + " xp in " + getName() + " " + this.getClass());
        } catch (Exception e) {
            Adapt.verbose("Failed to give xp to " + p.getName() + " for " + getName() + " (" + xp + ")");
        }
    }

    default void xpS(Player p, Location at, double xp) {
        xpS(p, at, xp, null);
    }

    default void xpS(Player p, Location at, double xp, String rewardKey) {
        if (!this.isEnabled()) {
            return;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xpSilent(p, this, xp, rewardKey);
            if (xp > 50) {
                vfxXP(p, at, (int) xp);
            }
            Adapt.verbose("Gave " + p.getName() + " " + xp + " xp in " + getName() + " " + this.getClass());
        } catch (Exception e) {
            Adapt.verbose("Failed to give xp to " + p.getName() + " for " + getName() + " (" + xp + ")");
        }
    }

    default void xpSilent(Player p, double xp) {
        xpSilent(p, xp, null);
    }

    default void xpSilent(Player p, double xp, String rewardKey) {
        if (!this.isEnabled()) {
            return;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        try {
            XP.xpSilent(p, this, xp, rewardKey);
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
        if (!this.isEnabled()) {
            return;
        }
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
        openGui(player, 0);
    }

    default void openGui(Player player, int page) {
        if (!this.isEnabled()) {
            return;
        }
        if (!player.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            int targetPage = page;
            J.s(() -> openGui(player, targetPage));
            return;
        }

        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);

        List<Adaptation<?>> visibleAdaptations = new ArrayList<>();
        for (Adaptation<?> adaptation : getAdaptations()) {
            if (!adaptation.isEnabled()) {
                continue;
            }
            if (!adaptation.getSkill().isEnabled()) {
                continue;
            }
            if (adaptation.hasBlacklistPermission(player, adaptation)) {
                continue;
            }
            visibleAdaptations.add(adaptation);
        }
        visibleAdaptations.sort(Comparator.comparing(adaptation -> normalizeSortKey(adaptation.getDisplayName())));

        boolean reserveNavigation = AdaptConfig.get().isGuiBackButton();
        GuiLayout.PagePlan plan = GuiLayout.plan(visibleAdaptations.size(), reserveNavigation);
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(visibleAdaptations.size(), start + plan.itemsPerPage());

        Window w = new UIWindow(player);
        GuiTheme.apply(w, "skill/" + getName());
        w.setViewportHeight(plan.rows());

        if (visibleAdaptations.isEmpty()) {
            w.setElement(0, 0, new UIElement("ada-empty")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.GRAY + "No adaptations available"));
        } else {
            List<GuiEffects.Placement> reveal = new ArrayList<>();
            for (int row = 0; row < plan.contentRows(); row++) {
                int rowStart = start + (row * GuiLayout.WIDTH);
                if (rowStart >= end) {
                    break;
                }

                int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
                for (int i = 0; i < rowCount; i++) {
                    Adaptation<?> adaptation = visibleAdaptations.get(rowStart + i);
                    int lvl = getPlayer(player).getData().getSkillLine(getName()).getAdaptationLevel(adaptation.getName());
                    int pos = GuiLayout.centeredPosition(i, rowCount);
                    Element element = new UIElement("ada-" + adaptation.getName())
                            .setMaterial(new MaterialBlock(adaptation.getIcon()))
                            .setModel(adaptation.getModel())
                            .setName(adaptation.getDisplayName(lvl))
                            .addLore(Form.wrapWordsPrefixed(adaptation.getDescription(), "" + C.GRAY, 45))
                            .addLore(lvl == 0 ? (C.DARK_GRAY + Localizer.dLocalize("snippets.gui.not_learned")) : (C.GRAY + Localizer.dLocalize("snippets.gui.level") + " " + C.WHITE + Form.toRoman(lvl)))
                            .setProgress(1D)
                            .onLeftClick((e) -> adaptation.openGui(player));
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(w, reveal);
        }

        if (plan.hasNavigationRow()) {
            int navRow = plan.rows() - 1;
            int jumpPages = 5;
            int jumpBack = Math.max(0, currentPage - jumpPages);
            int jumpForward = Math.min(plan.pageCount() - 1, currentPage + jumpPages);
            if (currentPage > 0) {
                w.setElement(-4, navRow, new UIElement("skill-prev")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Previous")
                        .addLore(C.GRAY + "Right click: jump -" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(player, currentPage - 1))
                        .onRightClick((e) -> openGui(player, jumpBack)));
                w.setElement(-3, navRow, new UIElement("skill-first")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "First")
                        .onLeftClick((e) -> openGui(player, 0)));
            }
            if (currentPage < plan.pageCount() - 1) {
                w.setElement(4, navRow, new UIElement("skill-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .addLore(C.GRAY + "Right click: jump +" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(player, currentPage + 1))
                        .onRightClick((e) -> openGui(player, jumpForward)));
                w.setElement(3, navRow, new UIElement("skill-last")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "Last")
                        .onLeftClick((e) -> openGui(player, plan.pageCount() - 1)));
            }

            int from = visibleAdaptations.isEmpty() ? 0 : (start + 1);
            int to = visibleAdaptations.isEmpty() ? 0 : end;
            w.setElement(-1, navRow, new UIElement("skill-page-info")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + plan.pageCount())
                    .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + visibleAdaptations.size())
                    .setProgress(1D));

            if (AdaptConfig.get().isGuiBackButton()) {
                w.setElement(0, navRow, new UIElement("back")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets.gui.back"))
                        .onLeftClick((e) -> onGuiClose(player, true)));
            }

        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(getDisplayName(a.getSkillLine(getName()).getLevel()) + " " + Form.pc(XP.getLevelProgress(a.getSkillLine(getName()).getXp())) + " (" + Form.f((int) XP.getXpUntilLevelUp(a.getSkillLine(getName()).getXp())) + Localizer.dLocalize("snippets.gui.xp") + " " + (a.getSkillLine(getName()).getLevel() + 1) + ")" + pageSuffix);
        w.onClosed((vv) -> J.s(() -> onGuiClose(player, !AdaptConfig.get().isEscClosesAllGuis())));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private void onGuiClose(Player player, boolean openPrevGui) {
        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
        if (openPrevGui) {
            SkillsGui.open(player);
        } else {
            Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
        }
    }

    private static String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = C.stripColor(value).toLowerCase(Locale.ROOT).trim();
        return normalized.replaceFirst("^[^\\p{L}\\p{N}]+", "");
    }
}
