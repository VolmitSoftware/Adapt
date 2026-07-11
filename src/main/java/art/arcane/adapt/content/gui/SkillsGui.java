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
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.format.ColorFormatter;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SkillsGui {
  private static final int PAGE_JUMP = 5;
  private static final int SKILLS_PER_ROW = 5;
  private static final int SKILL_ROWS = 5;
  private static final int SKILLS_PER_PAGE = SKILLS_PER_ROW * SKILL_ROWS;

  public static void open(Player player) {
    open(player, 0);
  }

  public static void open(Player player, int page) {
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

    List<SkillPageEntry> entries = new ArrayList<>();
    for (Skill<?> skill : adaptPlayer.getServer().getSkillRegistry().getSkills()) {
      if (skill == null) {
        continue;
      }
      if (!skill.isEnabled()) {
        continue;
      }
      PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(skill.getName());
      if (line == null) {
        continue;
      }
      if (!skill.hasUsePermission(adaptPlayer.getPlayer(), skill) || line.getLevel() < 0) {
        continue;
      }

      int adaptationLevel = sumAdaptationLevels(line);
      if (!hasVisibleProgress(line, adaptationLevel)) {
        continue;
      }

      entries.add(new SkillPageEntry(skill, line, adaptationLevel));
    }

    entries.sort(
        Comparator.comparing((SkillPageEntry entry) -> normalizeSortKey(entry.skill().getDisplayName()))
            .thenComparing(entry -> entry.skill().getName(), String.CASE_INSENSITIVE_ORDER)
    );

    MutationSVC mutationService = MutationSVC.get();
    boolean showMutations = player.hasPermission("adapt.mutations")
        && mutationService != null
        && mutationService.getManager() != null
        && mutationService.getManager().getConfig().isEnabled();
    int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) SKILLS_PER_PAGE));
    int currentPage = GuiLayout.clampPage(page, pageCount);
    int start = currentPage * SKILLS_PER_PAGE;
    int end = Math.min(entries.size(), start + SKILLS_PER_PAGE);

    UIWindow w = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(w, "/");
    w.setViewportHeight(6);

    if (entries.isEmpty()) {
      w.setElement(0, 2, new UIElement("skills-empty")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.GRAY + "No skills available")
          .addLore(C.DARK_GRAY + "No eligible skills were found for this player."));
    } else {
      List<GuiEffects.Placement> reveal = new ArrayList<>();
      int pageItems = end - start;
      int usedRows = Math.max(1, (int) Math.ceil(pageItems / (double) SKILLS_PER_ROW));
      for (int logicalRow = 0; logicalRow < usedRows; logicalRow++) {
        int row = GuiLayout.centeredFiveRow(logicalRow, usedRows);
        int rowStart = start + (logicalRow * SKILLS_PER_ROW);
        if (rowStart >= end) {
          break;
        }

        int rowCount = Math.min(SKILLS_PER_ROW, end - rowStart);
        for (int i = 0; i < rowCount; i++) {
          SkillPageEntry entry = entries.get(rowStart + i);
          int pos = GuiLayout.spacedFivePosition(i, rowCount);
          Element element = new UIElement("skill-" + entry.skill().getName())
              .setMaterial(new MaterialBlock(entry.skill().getIcon()))
              .setBaseItemStack(entry.skill().getModel().toItemStack())
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

    applyPageControls(w, player, 5, currentPage, pageCount, entries.size(), start, end, showMutations);

    w.setTitle(Localizer.dLocalize("snippets.gui.level") + " " + (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp()) + " (" + adaptPlayer.getData().getUsedPower() + "/" + adaptPlayer.getData().getMaxPower() + " " + Localizer.dLocalize("snippets.gui.power_used") + ")");
    w.open();
    w.onClosed((e) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString()));
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
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
          .setName(C.GRAY + "First")
          .onLeftClick((e) -> open(player, 0)));
      window.setElement(-3, navRow, new UIElement("skills-prev")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + "Previous")
          .addLore(C.GRAY + "Right click: jump -" + PAGE_JUMP + " pages")
          .onLeftClick((e) -> open(player, currentPage - 1))
          .onRightClick((e) -> open(player, jumpBack)));
    } else if (pageCount > 1) {
      window.setElement(-4, navRow, boundaryElement("skills-first-disabled", "First page"));
      window.setElement(-3, navRow, boundaryElement("skills-prev-disabled", "No previous page"));
    }

    if (pageCount > 1 && currentPage < pageCount - 1) {
      window.setElement(3, navRow, new UIElement("skills-next")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + "Next")
          .addLore(C.GRAY + "Right click: jump +" + PAGE_JUMP + " pages")
          .onLeftClick((e) -> open(player, currentPage + 1))
          .onRightClick((e) -> open(player, jumpForward)));
      window.setElement(4, navRow, new UIElement("skills-last")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + "Last")
          .onLeftClick((e) -> open(player, pageCount - 1)));
    } else if (pageCount > 1) {
      window.setElement(3, navRow, boundaryElement("skills-next-disabled", "No next page"));
      window.setElement(4, navRow, boundaryElement("skills-last-disabled", "Last page"));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    Element center;
    if (showMutations) {
      center = new UIElement("mutations")
          .setMaterial(new MaterialBlock(Material.AMETHYST_CLUSTER))
          .setName(C.LIGHT_PURPLE + "Experimental Mutations")
          .addLore(C.GRAY + "Optional build choices unlocked by your Adapt level.")
          .addLore(C.WHITE + "Click to view your slots and requirements.")
          .onLeftClick(event -> MutationGui.open(player));
    } else {
      center = new UIElement("skills-page-info")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.AQUA + "Skills");
    }
    center.addLore(C.DARK_GRAY + "Page " + (currentPage + 1) + "/" + pageCount
        + " • Showing " + from + "-" + to + " of " + totalEntries);
    window.setElement(0, navRow, center.setProgress(1D));
  }

  private static Element boundaryElement(String id, String name) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE))
        .setName(C.DARK_GRAY + name);
  }

  private record SkillPageEntry(Skill<?> skill, PlayerSkillLine line,
                                int adaptationLevel) {
  }
}
