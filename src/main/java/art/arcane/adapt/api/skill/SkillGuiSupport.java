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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.gui.SkillsGui;
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
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class SkillGuiSupport {
    private SkillGuiSupport() {
    }

    static boolean areParticlesEnabled(Skill<?> skill, boolean componentEnabled) {
        if (!componentEnabled) {
            return false;
        }

        AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
        if (effects != null && effects.getSkillParticleOverrides() != null && !effects.getSkillParticleOverrides().isEmpty()) {
            String key = skill.getName();
            Boolean override = effects.getSkillParticleOverrides().get(key);
            if (override == null && key != null) {
                override = effects.getSkillParticleOverrides().get(key.toLowerCase(Locale.ROOT));
            }
            if (override != null && !override) {
                return false;
            }
        }

        Object config = skill.getConfig();
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

    static boolean areSoundsEnabled(Skill<?> skill, boolean componentEnabled) {
        if (!componentEnabled) {
            return false;
        }

        Object config = skill.getConfig();
        if (config != null) {
            Boolean directToggle = readBooleanField(config, "showSounds");
            if (directToggle != null && !directToggle) {
                return false;
            }
        }

        return true;
    }

    static void openGui(Skill<?> skill, Player player, int page) {
        if (skill == null || !skill.isEnabled() || !SkillRuntimeGuards.isRuntimePlayer(player)) {
            return;
        }

        if (!J.isPrimaryThread()) {
            int targetPage = page;
            J.s(() -> openGui(skill, player, targetPage));
            return;
        }

        AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
        if (adaptPlayer == null) {
            return;
        }

        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);

        List<Adaptation<?>> visibleAdaptations = new ArrayList<>();
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
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
        visibleAdaptations.sort(
                Comparator.comparing((Adaptation<?> adaptation) -> normalizeSortKey(adaptation.getDisplayName()))
                        .thenComparing(Adaptation::getName, String.CASE_INSENSITIVE_ORDER)
        );

        boolean reserveNavigation = AdaptConfig.get().isGuiBackButton();
        GuiLayout.PagePlan plan = GuiLayout.plan(visibleAdaptations.size(), reserveNavigation);
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(visibleAdaptations.size(), start + plan.itemsPerPage());

        Window window = new UIWindow(player);
        GuiTheme.apply(window, "skill/" + skill.getName());
        window.setViewportHeight(plan.rows());

        if (visibleAdaptations.isEmpty()) {
            window.setElement(0, 0, new UIElement("ada-empty")
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
                    int level = adaptPlayer.getData().getSkillLine(skill.getName()).getAdaptationLevel(adaptation.getName());
                    int pos = GuiLayout.centeredPosition(i, rowCount);
                    Element element = new UIElement("ada-" + adaptation.getName())
                            .setMaterial(new MaterialBlock(adaptation.getIcon()))
                            .setModel(adaptation.getModel())
                            .setName(adaptation.getDisplayName(level))
                            .addLore(Form.wrapWordsPrefixed(adaptation.getDescription(), "" + C.GRAY, 45))
                            .addLore(level == 0 ? (C.DARK_GRAY + Localizer.dLocalize("snippets.gui.not_learned")) : (C.GRAY + Localizer.dLocalize("snippets.gui.level") + " " + C.WHITE + Form.toRoman(level)))
                            .setProgress(1D)
                            .onLeftClick((e) -> adaptation.openGui(player));
                    reveal.add(new GuiEffects.Placement(pos, row, element));
                }
            }
            GuiEffects.applyReveal(window, reveal);
        }

        if (plan.hasNavigationRow()) {
            int navRow = plan.rows() - 1;
            int jumpPages = 5;
            int jumpBack = Math.max(0, currentPage - jumpPages);
            int jumpForward = Math.min(plan.pageCount() - 1, currentPage + jumpPages);
            if (currentPage > 0) {
                window.setElement(-4, navRow, new UIElement("skill-prev")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Previous")
                        .addLore(C.GRAY + "Right click: jump -" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(skill, player, currentPage - 1))
                        .onRightClick((e) -> openGui(skill, player, jumpBack)));
                window.setElement(-3, navRow, new UIElement("skill-first")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "First")
                        .onLeftClick((e) -> openGui(skill, player, 0)));
            }
            if (currentPage < plan.pageCount() - 1) {
                window.setElement(4, navRow, new UIElement("skill-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .addLore(C.GRAY + "Right click: jump +" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(skill, player, currentPage + 1))
                        .onRightClick((e) -> openGui(skill, player, jumpForward)));
                window.setElement(3, navRow, new UIElement("skill-last")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "Last")
                        .onLeftClick((e) -> openGui(skill, player, plan.pageCount() - 1)));
            }

            int from = visibleAdaptations.isEmpty() ? 0 : (start + 1);
            int to = visibleAdaptations.isEmpty() ? 0 : end;
            window.setElement(-1, navRow, new UIElement("skill-page-info")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + plan.pageCount())
                    .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + visibleAdaptations.size())
                    .setProgress(1D));

            if (AdaptConfig.get().isGuiBackButton()) {
                window.setElement(0, navRow, new UIElement("back")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets.gui.back"))
                        .onLeftClick((e) -> onGuiClose(player, true)));
            }

        }

        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        window.setTitle(skill.getDisplayName(adaptPlayer.getSkillLine(skill.getName()).getLevel()) + " " + Form.pc(XP.getLevelProgress(adaptPlayer.getSkillLine(skill.getName()).getXp())) + " (" + Form.f((int) XP.getXpUntilLevelUp(adaptPlayer.getSkillLine(skill.getName()).getXp())) + Localizer.dLocalize("snippets.gui.xp") + " " + (adaptPlayer.getSkillLine(skill.getName()).getLevel() + 1) + ")" + pageSuffix);
        window.onClosed((vv) -> J.s(() -> onGuiClose(player, !AdaptConfig.get().isEscClosesAllGuis())));
        window.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
    }

    private static void onGuiClose(Player player, boolean openPrevGui) {
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
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            } catch (Throwable ex) {
                Adapt.verbose("Failed reading boolean field '" + fieldName + "' from " + source.getClass().getName()
                        + ": " + ex.getClass().getSimpleName()
                        + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
                return null;
            }
        }

        return null;
    }
}
