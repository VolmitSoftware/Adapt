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

package art.arcane.adapt.content.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.UIElement;
import art.arcane.adapt.util.common.inventorygui.UIWindow;
import art.arcane.adapt.util.common.inventorygui.Window;
import art.arcane.adapt.util.common.math.MaterialBlock;
import art.arcane.adapt.util.common.scheduling.J;

public class SkillsGui {
    private static final int PAGE_JUMP = 5;

    public static void open(Player player) {
        open(player, 0);
    }

    public static void open(Player player, int page) {
        if (!Bukkit.isPrimaryThread()) {
            int targetPage = page;
            J.s(() -> open(player, targetPage));
            return;
        }

        AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
        if (adaptPlayer == null) {
            Adapt.error("Failed to open skills gui for " + player.getName() + " because they are not Online, Were Kicked, Or are a fake player.");
            return;
        }

        List<SkillPageEntry> entries = new ArrayList<>();
        for (PlayerSkillLine line : adaptPlayer.getData().getSkillLines().sortV()) {
            Skill<?> skill = line.getRawSkill(adaptPlayer);
            if (skill == null) {
                continue;
            }
            if (!skill.isEnabled()) {
                continue;
            }
            if (skill.hasBlacklistPermission(adaptPlayer.getPlayer(), skill) || line.getLevel() < 0) {
                continue;
            }

            int adaptationLevel = 0;
            for (PlayerAdaptation adaptation : line.getAdaptations().sortV()) {
                adaptationLevel += adaptation.getLevel();
            }

            entries.add(new SkillPageEntry(skill, line, adaptationLevel));
        }

        entries.sort(Comparator.comparing(entry -> normalizeSortKey(entry.skill().getDisplayName())));

        boolean reserveNavigation = false;
        GuiLayout.PagePlan plan = GuiLayout.plan(entries.size(), reserveNavigation);
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(entries.size(), start + plan.itemsPerPage());

        Window w = new UIWindow(player);
        GuiTheme.apply(w, "/");
        w.setViewportHeight(plan.rows());

        if (entries.isEmpty()) {
            w.setElement(0, 0, new UIElement("skills-empty")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.GRAY + "No skills available")
                    .addLore(C.DARK_GRAY + "No eligible skills were found for this player."));
        } else {
            List<GuiEffects.Placement> reveal = new ArrayList<>();
            for (int row = 0; row < plan.contentRows(); row++) {
                int rowStart = start + (row * GuiLayout.WIDTH);
                if (rowStart >= end) {
                    break;
                }

                int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
                for (int i = 0; i < rowCount; i++) {
                    SkillPageEntry entry = entries.get(rowStart + i);
                    int pos = GuiLayout.centeredPosition(i, rowCount);
                    Element element = new UIElement("skill-" + entry.skill().getName())
                            .setMaterial(new MaterialBlock(entry.skill().getIcon()))
                            .setModel(entry.skill().getModel())
                            .setName(entry.skill().getDisplayName(entry.line().getLevel()))
                            .setProgress(1D)
                            .addLore(C.ITALIC + "" + C.GRAY + entry.skill().getDescription())
                            .addLore(C.UNDERLINE + "" + C.WHITE + entry.line().getKnowledge() + C.RESET + " " + C.GRAY + Localizer.dLocalize("snippets.gui.knowledge"))
                            .addLore(C.ITALIC + "" + C.GRAY + Localizer.dLocalize("snippets.gui.power_used") + " " + C.DARK_GREEN + entry.adaptationLevel())
                            .onLeftClick((e) -> entry.skill().openGui(player));
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(w, reveal);
        }

        if (plan.hasNavigationRow()) {
            int navRow = plan.rows() - 1;
            applyPageControls(w, player, navRow, currentPage, plan.pageCount(), entries.size(), start, end);

        }

        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(Localizer.dLocalize("snippets.gui.level") + " " + (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp()) + " (" + adaptPlayer.getData().getUsedPower() + "/" + adaptPlayer.getData().getMaxPower() + " " + Localizer.dLocalize("snippets.gui.power_used") + ")" + pageSuffix);
        w.open();
        w.onClosed((e) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString()));
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private record SkillPageEntry(Skill<?> skill, PlayerSkillLine line, int adaptationLevel) {
    }

    private static String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = C.stripColor(value).toLowerCase(Locale.ROOT).trim();
        return normalized.replaceFirst("^[^\\p{L}\\p{N}]+", "");
    }

    private static void applyPageControls(
            Window window,
            Player player,
            int navRow,
            int currentPage,
            int pageCount,
            int totalEntries,
            int start,
            int end
    ) {
        if (pageCount <= 1) {
            return;
        }

        int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
        int jumpForward = Math.min(pageCount - 1, currentPage + PAGE_JUMP);

        if (currentPage > 0) {
            window.setElement(-4, navRow, new UIElement("skills-prev")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.WHITE + "Previous")
                    .addLore(C.GRAY + "Right click: jump -" + PAGE_JUMP + " pages")
                    .onLeftClick((e) -> open(player, currentPage - 1))
                    .onRightClick((e) -> open(player, jumpBack)));
            window.setElement(-3, navRow, new UIElement("skills-first")
                    .setMaterial(new MaterialBlock(Material.LECTERN))
                    .setName(C.GRAY + "First")
                    .onLeftClick((e) -> open(player, 0)));
        }

        if (currentPage < pageCount - 1) {
            window.setElement(4, navRow, new UIElement("skills-next")
                    .setMaterial(new MaterialBlock(Material.ARROW))
                    .setName(C.WHITE + "Next")
                    .addLore(C.GRAY + "Right click: jump +" + PAGE_JUMP + " pages")
                    .onLeftClick((e) -> open(player, currentPage + 1))
                    .onRightClick((e) -> open(player, jumpForward)));
            window.setElement(3, navRow, new UIElement("skills-last")
                    .setMaterial(new MaterialBlock(Material.LECTERN))
                    .setName(C.GRAY + "Last")
                    .onLeftClick((e) -> open(player, pageCount - 1)));
        }

        int from = totalEntries <= 0 ? 0 : (start + 1);
        int to = totalEntries <= 0 ? 0 : end;
        window.setElement(-1, navRow, new UIElement("skills-page-info")
                .setMaterial(new MaterialBlock(Material.PAPER))
                .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + pageCount)
                .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + totalEntries)
                .setProgress(1D));
    }
}
