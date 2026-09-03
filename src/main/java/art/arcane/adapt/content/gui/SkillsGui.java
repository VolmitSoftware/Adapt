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
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class SkillsGui {
  private static final int PAGE_JUMP = 5;
  private static final int SKILLS_PER_ROW = GuiLayout.FIVE_WIDTH;

  public static void open(Player player) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUniqueId())) {
      open(player, 0);
    }
  }

  public static void open(Player player, int page) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUniqueId())) {
      if (player == null || !player.isOnline()) {
        return;
      }
      boolean owned = (!J.isFoliaThreading() && J.isPrimaryThread())
          || (J.isFoliaThreading() && J.isOwnedByCurrentRegion(player));
      if (!owned) {
        int targetPage = page;
        J.runEntity(player, () -> open(player, targetPage));
        return;
      }

      AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
      if (adaptPlayer == null) {
        Adapt.error("Failed to open skills gui for " + player.getName() + " because they are not Online, Were Kicked, Or are a fake player.");
        return;
      }

      boolean debugMode = AdaptDebugMode.isActive(player);
      boolean showAll = debugMode || AdaptConfig.get().isGuiShowAllSkills();
      List<SkillPageEntry> entries = new ArrayList<>();
      List<String> knownSkills = new ArrayList<>();
      for (Skill<?> skill : adaptPlayer.getServer().getSkillRegistry().getSkills()) {
        if (skill == null) {
          continue;
        }
        knownSkills.add(skill.getName());
        if (!skill.isEnabled()) {
          continue;
        }
        PlayerSkillLine line = resolveSkillLine(adaptPlayer.getData(), skill.getName(), debugMode, showAll);
        if (line == null) {
          continue;
        }

        int adaptationLevel = sumAdaptationLevels(line);
        boolean permitted = skill.hasUsePermission(adaptPlayer.getPlayer(), skill);
        if (!isSkillEntryVisible(permitted, debugMode, showAll, line, adaptationLevel)) {
          continue;
        }

        entries.add(new SkillPageEntry(skill, line, adaptationLevel));
      }

      Map<String, Integer> configuredOrder = GuiConfig.skillOrder(knownSkills);
      entries.sort(
          Comparator.comparingInt((SkillPageEntry entry) -> GuiConfig.rankOf(configuredOrder, entry.skill().getName()))
              .thenComparing((SkillPageEntry entry) -> normalizeSortKey(entry.skill().getDisplayName()))
              .thenComparing(entry -> entry.skill().getName(), String.CASE_INSENSITIVE_ORDER)
      );

      MutationSVC mutationService = MutationSVC.get();
      boolean showMutations = (player.hasPermission("adapt.mutations") || debugMode)
          && mutationService != null
          && mutationService.getManager() != null
          && mutationService.getManager().getConfig().isEnabled();
      int configuredRows = GuiConfig.skillsGuiRows();
      GuiLayout.PagePlan plan = GuiLayout.planFiveWide(entries.size(), configuredRows);
      int currentPage = GuiLayout.clampPage(page, plan.pageCount());
      int start = currentPage * plan.itemsPerPage();
      int end = Math.min(entries.size(), start + plan.itemsPerPage());
      int viewportRows = GuiLayout.fiveWideRowsForPage(end - start, configuredRows);
      int contentRows = viewportRows - 1;
      int navRow = viewportRows - 1;

      UIWindow w = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(w, "/");
      w.setViewportHeight(viewportRows);

      if (entries.isEmpty()) {
        w.setElement(0, GuiLayout.centeredFiveRow(0, 1, contentRows), new UIElement("skills-empty")
            .setMaterial(new MaterialBlock(Material.PAPER))
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.NO_SKILLS_AVAILABLE))
            .addLore(C.DARK_GRAY + AdaptLanguage.text(GuiMessages.NO_ELIGIBLE_SKILLS)));
      } else {
        List<GuiEffects.Placement> reveal = new ArrayList<>();
        int pageItems = end - start;
        int usedRows = Math.max(1, (int) Math.ceil(pageItems / (double) SKILLS_PER_ROW));
        for (int logicalRow = 0; logicalRow < usedRows; logicalRow++) {
          int row = GuiLayout.centeredFiveRow(logicalRow, usedRows, contentRows);
          int rowStart = start + (logicalRow * SKILLS_PER_ROW);
          if (rowStart >= end) {
            break;
          }

          int rowCount = Math.min(SKILLS_PER_ROW, end - rowStart);
          for (int i = 0; i < rowCount; i++) {
            SkillPageEntry entry = entries.get(rowStart + i);
            int pos = GuiLayout.spacedFivePosition(i, rowCount);
            CustomModel model = GuiConfig.skillModel(
                entry.skill().getName(),
                entry.skill().getIcon(),
                entry.skill().getModel()
            );
            Element element = new UIElement("skill-" + entry.skill().getName())
                .setMaterial(new MaterialBlock(model.material()))
                .setBaseItemStack(model.toItemStack())
                .setName(entry.skill().getDisplayName(entry.line().getLevel()))
                .setProgress(1D)
                .addLore(C.ITALIC + "" + C.GRAY + entry.skill().getDescription())
                .addLore(C.GRAY + AdaptLanguage.text(
                    SnippetsMessages.GUI_KNOWLEDGE_VALUE,
                    trusted("knowledge", C.UNDERLINE + "" + C.WHITE + entry.line().getKnowledge() + C.RESET + " " + C.GRAY)
                ))
                .addLore(C.ITALIC + "" + C.GRAY + AdaptLanguage.text(
                    SnippetsMessages.GUI_POWER_USED_VALUE,
                    trusted("power", C.DARK_GREEN + String.valueOf(entry.adaptationLevel()))
                ))
                .onLeftClick((e) -> entry.skill().openGui(player));
            reveal.add(new GuiEffects.Placement(pos, row, element));
          }
        }
        GuiEffects.applyReveal(w, reveal);
      }

      applyPageControls(w, player, navRow, currentPage, plan.pageCount(), entries.size(), start, end, showMutations);

      w.setTitle(AdaptLanguage.text(
          GuiMessages.SKILLS_TITLE,
          trusted("level", (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp())),
          trusted("used", adaptPlayer.getData().getUsedPower()),
          trusted("maximum", adaptPlayer.getData().getMaxPower())
      ));
      w.onClosed((e) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), w));
      w.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }
  }

  static PlayerSkillLine resolveSkillLine(PlayerData data, String skillName, boolean debugMode, boolean showAll) {
    if (debugMode) {
      return data.getSkillLine(skillName);
    }

    PlayerSkillLine stored = data.getSkillLineNullable(skillName);
    if (stored != null || !showAll) {
      return stored;
    }

    PlayerSkillLine preview = new PlayerSkillLine();
    preview.setLine(skillName);
    return preview;
  }

  static boolean isSkillEntryVisible(
      boolean hasUsePermission,
      boolean debugMode,
      boolean showAll,
      PlayerSkillLine line,
      int adaptationLevel
  ) {
    if (line == null || !hasUsePermission) {
      return false;
    }
    if (!debugMode && line.getLevel() < 0) {
      return false;
    }
    return showAll || hasVisibleProgress(line, adaptationLevel);
  }

  private static int sumAdaptationLevels(PlayerSkillLine line) {
    int total = 0;
    for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
      if (adaptation == null) {
        continue;
      }
      total += Math.max(0, adaptation.getLevel());
    }
    return total;
  }

  private static boolean hasVisibleProgress(PlayerSkillLine line, int adaptationLevel) {
    return line.getXp() > 0D || line.getKnowledge() > 0L || adaptationLevel > 0;
  }

  private static String normalizeSortKey(String value) {
    if (value == null) {
      return "";
    }

    String normalized = ColorFormatter.stripColor(value).toLowerCase(Locale.ROOT).trim();
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
      int end,
      boolean showMutations
  ) {
    int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
    int jumpForward = Math.min(pageCount - 1, currentPage + PAGE_JUMP);

    if (pageCount > 1 && currentPage > 0) {
      window.setElement(-4, navRow, new UIElement("skills-first")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
          .onLeftClick((e) -> open(player, 0)));
      window.setElement(-3, navRow, new UIElement("skills-prev")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_BACK, trusted("pages", PAGE_JUMP)))
          .onLeftClick((e) -> open(player, currentPage - 1))
          .onRightClick((e) -> open(player, jumpBack)));
    } else if (pageCount > 1) {
      window.setElement(-4, navRow, boundaryElement("skills-first-disabled", GuiMessages.FIRST_PAGE));
      window.setElement(-3, navRow, boundaryElement("skills-prev-disabled", GuiMessages.NO_PREVIOUS_PAGE));
    }

    if (pageCount > 1 && currentPage < pageCount - 1) {
      window.setElement(3, navRow, new UIElement("skills-next")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_FORWARD, trusted("pages", PAGE_JUMP)))
          .onLeftClick((e) -> open(player, currentPage + 1))
          .onRightClick((e) -> open(player, jumpForward)));
      window.setElement(4, navRow, new UIElement("skills-last")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
          .onLeftClick((e) -> open(player, pageCount - 1)));
    } else if (pageCount > 1) {
      window.setElement(3, navRow, boundaryElement("skills-next-disabled", GuiMessages.NO_NEXT_PAGE));
      window.setElement(4, navRow, boundaryElement("skills-last-disabled", GuiMessages.LAST_PAGE));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    Element center;
    if (showMutations) {
      center = new UIElement("mutations")
          .setMaterial(new MaterialBlock(Material.AMETHYST_CLUSTER))
          .setName(C.LIGHT_PURPLE + AdaptLanguage.text(GuiMessages.EXPERIMENTAL_MUTATIONS))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.MUTATIONS_MENU_SUMMARY))
          .addLore(C.WHITE + AdaptLanguage.text(GuiMessages.MUTATIONS_MENU_OPEN))
          .onLeftClick(event -> MutationGui.open(player));
    } else {
      center = new UIElement("skills-page-info")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.AQUA + AdaptLanguage.text(GuiMessages.SKILLS));
    }
    center.addLore(C.DARK_GRAY + AdaptLanguage.text(
        GuiMessages.PAGE_SHOWING_RANGE,
        trusted("page", currentPage + 1),
        trusted("pages", pageCount),
        trusted("from", from),
        trusted("to", to),
        trusted("total", totalEntries)
    ));
    window.setElement(0, navRow, center.setProgress(1D));
  }

  private static Element boundaryElement(String id, TextKey name) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE))
        .setName(C.DARK_GRAY + AdaptLanguage.text(name));
  }

  private record SkillPageEntry(Skill<?> skill, PlayerSkillLine line,
                                int adaptationLevel) {
  }
}
