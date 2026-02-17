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
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SkillGuiSupport {
    private static final long CLOSE_SUPPRESS_MS = 1200L;
    private static final int CLOSE_SUPPRESS_CLEAR_TICKS = 4;
    private static final Map<UUID, Long> CLOSE_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

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

        UIWindow window = new UIWindow(Adapt.instance, player);
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
                            .setBaseItemStack(adaptation.getModel().toItemStack())
                            .setName(adaptation.getDisplayName(level))
                            .addLore(Form.wrapWordsPrefixed(adaptation.getDescription(), "" + C.GRAY, 45))
                            .addLore(level == 0 ? (C.DARK_GRAY + Localizer.dLocalize("snippets.gui.not_learned")) : (C.GRAY + Localizer.dLocalize("snippets.gui.level") + " " + C.WHITE + Form.toRoman(level)))
                            .setProgress(1D)
                            .onLeftClick((e) -> openAdaptation(adaptation, player));
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
                        .onLeftClick((e) -> openSkillPage(skill, player, currentPage - 1))
                        .onRightClick((e) -> openSkillPage(skill, player, jumpBack)));
                window.setElement(-3, navRow, new UIElement("skill-first")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "First")
                        .onLeftClick((e) -> openSkillPage(skill, player, 0)));
            }
            if (currentPage < plan.pageCount() - 1) {
                window.setElement(4, navRow, new UIElement("skill-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .addLore(C.GRAY + "Right click: jump +" + jumpPages + " pages")
                        .onLeftClick((e) -> openSkillPage(skill, player, currentPage + 1))
                        .onRightClick((e) -> openSkillPage(skill, player, jumpForward)));
                window.setElement(3, navRow, new UIElement("skill-last")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "Last")
                        .onLeftClick((e) -> openSkillPage(skill, player, plan.pageCount() - 1)));
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
                        .onLeftClick((e) -> navigateBack(player)));
            }

        }

        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        window.setTitle(skill.getDisplayName(adaptPlayer.getSkillLine(skill.getName()).getLevel()) + " " + Form.pc(XP.getLevelProgress(adaptPlayer.getSkillLine(skill.getName()).getXp())) + " (" + Form.f((int) XP.getXpUntilLevelUp(adaptPlayer.getSkillLine(skill.getName()).getXp())) + Localizer.dLocalize("snippets.gui.xp") + " " + (adaptPlayer.getSkillLine(skill.getName()).getLevel() + 1) + ")" + pageSuffix);
        window.onClosed((vv) -> J.s(() -> onGuiClosed(player, !AdaptConfig.get().isEscClosesAllGuis())));
        window.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
    }

    private static void openSkillPage(Skill<?> skill, Player player, int page) {
        suppressClose(player);
        openGui(skill, player, page);
    }

    private static void openAdaptation(Adaptation<?> adaptation, Player player) {
        suppressClose(player);
        adaptation.openGui(player);
    }

    private static void navigateBack(Player player) {
        playCloseSound(player);
        suppressClose(player);
        SkillsGui.open(player);
    }

    private static void onGuiClosed(Player player, boolean openPrevGui) {
        if (player == null) {
            return;
        }

        if (consumeCloseSuppression(player)) {
            return;
        }

        playCloseSound(player);
        if (openPrevGui) {
            J.s(() -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
                    SkillsGui.open(player);
                }
            }, 1);
        } else {
            Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
        }
    }

    private static void playCloseSound(Player player) {
        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
    }

    private static void suppressClose(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long suppressUntil = System.currentTimeMillis() + CLOSE_SUPPRESS_MS;
        CLOSE_SUPPRESS_UNTIL.put(playerId, suppressUntil);
        J.s(() -> {
            Long current = CLOSE_SUPPRESS_UNTIL.get(playerId);
            if (current != null && current.longValue() == suppressUntil) {
                CLOSE_SUPPRESS_UNTIL.remove(playerId);
            }
        }, CLOSE_SUPPRESS_CLEAR_TICKS);
    }

    private static boolean consumeCloseSuppression(Player player) {
        if (player == null) {
            return false;
        }

        Long until = CLOSE_SUPPRESS_UNTIL.get(player.getUniqueId());
        if (until == null) {
            return false;
        }

        if (until >= System.currentTimeMillis()) {
            CLOSE_SUPPRESS_UNTIL.remove(player.getUniqueId());
            return true;
        }

        CLOSE_SUPPRESS_UNTIL.remove(player.getUniqueId());
        return false;
    }

    private static String normalizeSortKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = ColorFormatter.stripColor(value).toLowerCase(Locale.ROOT).trim();
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
