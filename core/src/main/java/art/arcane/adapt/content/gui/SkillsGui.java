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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillMenus;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.SpendViews;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.XpGuideViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SeatMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class SkillsGui {
  private static final int PAGE_JUMP = 5;
  private static final int SKILLS_PER_ROW = GuiLayout.FIVE_WIDTH;
  private static final Id PAPER = Id.minecraft("paper");
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Id LECTERN = Id.minecraft("lectern");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");
  private static final Id KNOWLEDGE = Id.minecraft("knowledge_book");

  public static void open(PlayerRef player) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.uuid())) {
      open(player, 0);
    }
  }

  public static void open(PlayerRef player, int page) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.uuid())) {
      if (player == null || !player.isOnline()) {
        return;
      }
      boolean owned = (!Tasks.isFoliaThreading() && Tasks.isPrimaryThread())
          || (Tasks.isFoliaThreading() && Tasks.isOwnedByCurrentRegion(player));
      if (!owned) {
        int targetPage = page;
        Tasks.runEntity(player, () -> open(player, targetPage));
        return;
      }

      AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
      if (adaptPlayer == null) {
        AdaptLog.error("Failed to open skills gui for " + player.name() + " because they are not Online, Were Kicked, Or are a fake player.");
        return;
      }

      List<Seat> seats = seats(adaptPlayer, player);

      int configuredRows = GuiConfig.skillsGuiRows();
      GuiLayout.PagePlan plan = GuiLayout.planFiveWide(seats.size(), configuredRows);
      int currentPage = GuiLayout.clampPage(page, plan.pageCount());
      int start = currentPage * plan.itemsPerPage();
      int end = Math.min(seats.size(), start + plan.itemsPerPage());
      int viewportRows = GuiLayout.fiveWideRowsForPage(end - start, configuredRows);
      int contentRows = viewportRows - 1;
      int navRow = viewportRows - 1;

      GuiWindow w = new GuiWindow(player);
      GuiTheme.apply(w, "/");
      w.setViewportHeight(viewportRows);
      w.setLayout(MenuLayout.LIST).setPaging(new MenuPaging(currentPage, plan.pageCount(), seats.size()));

      if (seats.isEmpty()) {
        w.setElement(0, GuiLayout.centeredFiveRow(0, 1, contentRows), new GuiElement("skills-empty")
            .setMaterial(PAPER)
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.NO_SKILLS_AVAILABLE))
            .addLore(C.DARK_GRAY + AdaptLanguage.text(GuiMessages.NO_ELIGIBLE_SKILLS))
            .setRole(MenuRole.HEADER));
      } else {
        List<GuiEffects.Placement> reveal = new ArrayList<>();
        int usedRows = Math.max(1, (int) Math.ceil((end - start) / (double) SKILLS_PER_ROW));
        for (int index = start; index < end; index++) {
          Seat seat = seats.get(index);
          if (seat.empty()) {
            continue;
          }
          int logicalRow = (index - start) / SKILLS_PER_ROW;
          int row = GuiLayout.centeredFiveRow(logicalRow, usedRows, contentRows);
          int pos = GuiLayout.spacedFivePosition((index - start) % SKILLS_PER_ROW, SKILLS_PER_ROW);
          reveal.add(new GuiEffects.Placement(pos, row, seatElement(seat, player, adaptPlayer.getServer().getSkillRegistry())));
        }
        GuiEffects.applyReveal(w, reveal);
      }

      applyPageControls(w, player, navRow, currentPage, plan.pageCount(), seats.size(), start, end);
      w.setElement(1, navRow, knowledgeElement(adaptPlayer, seats));

      w.setTitle(AdaptLanguage.text(
          GuiMessages.SKILLS_TITLE,
          trusted("level", (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp())),
          trusted("used", adaptPlayer.getData().getUsedPower()),
          trusted("maximum", adaptPlayer.getData().getMaxPower())
      ));
      w.open();
    }
  }

  /** A seat's item: the skill as usual, or greyed out when the player has not started it. */
  private static GuiElement seatElement(Seat seat, PlayerRef player, SkillRegistry registry) {
    Skill<?> skill = seat.skill();
    CustomModel model = GuiConfig.skillModel(skill.getName(), skill.getIcon(), skill.getModel());
    GuiElement element = new GuiElement("skill-" + skill.getName())
        .setMaterial(model.material())
        .setModel(model)
        .setRole(MenuRole.NAVIGATION);
    if (!seat.started()) {
      element.setName(C.DARK_GRAY + C.stripColor(skill.getDisplayName()))
          .addLore(C.GRAY + AdaptLanguage.text(SeatMessages.NOT_STARTED))
          .addLore(C.ITALIC + "" + C.DARK_GRAY + skill.getDescription());
    } else {
      element.setName(skill.getDisplayName(seat.line().getLevel()))
          .setEnchanted(skill instanceof MutatedSkill)
          .addLore(C.ITALIC + "" + C.GRAY + skill.getDescription())
          .addLore(C.GRAY + AdaptLanguage.text(
              SnippetsMessages.GUI_KNOWLEDGE_VALUE,
              trusted("knowledge", C.UNDERLINE + "" + C.WHITE + seat.line().getKnowledge() + C.RESET + " " + C.GRAY)
          ))
          .addLore(C.ITALIC + "" + C.GRAY + AdaptLanguage.text(
              SnippetsMessages.GUI_POWER_USED_VALUE,
              trusted("power", C.DARK_GREEN + String.valueOf(seat.adaptationLevel()))
          ));
    }
    element.onLeftClick((MenuClick e) -> skill.openGui(player));
    addGuideLore(element, skill, registry);
    return element;
  }

  /** Knowledge earned, spent and unspent, then each seat's spent and unspent knowledge in seat order. */
  private static GuiElement knowledgeElement(AdaptPlayer adaptPlayer, List<Seat> seats) {
    Map<String, Integer> spent = new HashMap<>();
    for (SpendView spend : SpendViews.build(adaptPlayer.getServer().getSkillRegistry(), adaptPlayer)) {
      spent.merge(spend.skillId(), spend.knowledge(), Integer::sum);
    }
    long totalSpent = 0L;
    long unspent = 0L;
    List<String> lines = new ArrayList<>();
    for (Seat seat : seats) {
      if (seat.empty()) {
        continue;
      }
      int skillSpent = spent.getOrDefault(seat.skill().getName(), 0);
      long available = seat.line() == null ? 0L : seat.line().getKnowledge();
      totalSpent += skillSpent;
      unspent += available;
      if (skillSpent > 0 || available > 0) {
        lines.add(C.GRAY + AdaptLanguage.text(SeatMessages.SKILL, trusted("skill", C.stripColor(seat.skill().getDisplayName())),
            trusted("spent", C.WHITE + String.valueOf(skillSpent) + C.GRAY), trusted("available", String.valueOf(available))));
      }
    }
    GuiElement element = GuiTheme.element("skills-knowledge", KNOWLEDGE, "gui", "navigation", "knowledge")
        .setName(C.AQUA + AdaptLanguage.text(SeatMessages.KNOWLEDGE))
        .setRole(MenuRole.INFO)
        .addLore(C.GRAY + AdaptLanguage.text(SeatMessages.EARNED, trusted("value", totalSpent + unspent)))
        .addLore(C.GRAY + AdaptLanguage.text(SeatMessages.SPENT, trusted("value", totalSpent)))
        .addLore(C.GRAY + AdaptLanguage.text(SeatMessages.UNSPENT, trusted("value", unspent)))
        .addLore(" ");
    for (String line : lines) {
      element.addLore(line);
    }
    return element;
  }

  /**
   * Every skill on the server the player may use, one seat each in roster order: a mutation sits in its lead's seat and
   * its partner's seat stays as an empty slot; a skill the player has not started shows greyed out. With inactive skills
   * hidden by the server empty and greyed seats are left out and the rest close up in the same order.
   */
  static List<Seat> seats(AdaptPlayer adaptPlayer, PlayerRef player) {
    SkillRegistry registry = adaptPlayer.getServer().getSkillRegistry();
    PlayerData data = adaptPlayer.getData();
    boolean debugMode = AdaptDebugMode.isActive(player);
    boolean inactive = AdaptConfig.get().getGui().isShowInactiveSkills();
    List<Skill<?>> roster = new ArrayList<>();
    List<String> knownSkills = new ArrayList<>();
    for (Skill<?> registered : registry.getSkills()) {
      if (registered != null && registered.isEnabled() && registered.hasUsePermission(player, registered)) {
        roster.add(registered);
        knownSkills.add(registered.getName());
      }
    }
    Map<String, Integer> configuredOrder = GuiConfig.skillOrder(knownSkills);
    roster.sort(Comparator.comparingInt((Skill<?> skill) -> GuiConfig.rankOf(configuredOrder, skill.getName()))
        .thenComparing((Skill<?> skill) -> SkillMenus.sortKey(skill.getDisplayName()))
        .thenComparing(Skill::getName, String.CASE_INSENSITIVE_ORDER));
    List<Seat> seats = new ArrayList<>();
    for (Skill<?> registered : roster) {
      Skill<?> listed = SkillMutations.listedSkill(registry, data, registered);
      if (listed == null) {
        if (inactive) {
          seats.add(Seat.EMPTY);
        }
        continue;
      }
      PlayerSkillLine line = resolveSkillLine(data, listed.getName(), debugMode, true);
      int adaptationLevel = line == null ? 0 : sumAdaptationLevels(line);
      boolean started = line != null && (debugMode || hasVisibleProgress(line, adaptationLevel));
      if (started || inactive) {
        seats.add(new Seat(listed, line, adaptationLevel, started));
      }
    }
    return seats;
  }

  /** The skills the skills menu lists for {@code player}, in the menu's order. */
  public static List<Skill<?>> visibleSkills(AdaptPlayer adaptPlayer, PlayerRef player) {
    List<Skill<?>> skills = new ArrayList<>();
    for (SkillPageEntry entry : entries(adaptPlayer, player)) {
      skills.add(entry.skill());
    }
    return skills;
  }

  private static List<SkillPageEntry> entries(AdaptPlayer adaptPlayer, PlayerRef player) {
    boolean debugMode = AdaptDebugMode.isActive(player);
    boolean showAll = debugMode || AdaptConfig.get().isGuiShowAllSkills();
    List<SkillPageEntry> entries = new ArrayList<>();
    List<String> knownSkills = new ArrayList<>();
    for (Skill<?> registered : adaptPlayer.getServer().getSkillRegistry().getSkills()) {
      if (registered == null) {
        continue;
      }
      knownSkills.add(registered.getName());
      Skill<?> skill = SkillMutations.listedSkill(adaptPlayer.getServer().getSkillRegistry(), adaptPlayer.getData(), registered);
      if (skill == null) {
        continue;
      }
      if (!skill.isEnabled()) {
        continue;
      }
      PlayerSkillLine line = resolveSkillLine(adaptPlayer.getData(), skill.getName(), debugMode, showAll);
      if (line == null) {
        continue;
      }

      int adaptationLevel = sumAdaptationLevels(line);
      boolean permitted = skill.hasUsePermission(player, skill);
      if (!isSkillEntryVisible(permitted, debugMode, showAll, line, adaptationLevel)) {
        continue;
      }

      entries.add(new SkillPageEntry(skill, line, adaptationLevel));
    }

    Map<String, Integer> configuredOrder = GuiConfig.skillOrder(knownSkills);
    entries.sort(
        Comparator.comparingInt((SkillPageEntry entry) -> GuiConfig.rankOf(configuredOrder, entry.skill().getName()))
            .thenComparing((SkillPageEntry entry) -> SkillMenus.sortKey(entry.skill().getDisplayName()))
            .thenComparing((SkillPageEntry entry) -> entry.skill().getName(), String.CASE_INSENSITIVE_ORDER)
    );
    return entries;
  }

  /** How the skill earns XP, under the guide's heading: a mutated skill's parents each under their name, then its rules. */
  static void addGuideLore(GuiElement element, Skill<?> skill, SkillRegistry registry) {
    XpGuideView guide = XpGuideViews.guide(registry, skill);
    element.addLore(" ");
    element.addLore(C.GOLD + XpGuideViews.title());
    for (XpGuideView.Section section : guide.sections()) {
      if (guide.mutated()) {
        Skill<?> source = section.skillId().isEmpty() ? null : registry.getSkill(section.skillId());
        element.addLore((source == null ? C.LIGHT_PURPLE.toString() : source.getColor().toString()) + section.title());
      }
      for (String line : section.lines()) {
        element.addLore(C.GRAY + "- " + line);
      }
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

  private static void applyPageControls(
      GuiWindow window,
      PlayerRef player,
      int navRow,
      int currentPage,
      int pageCount,
      int totalEntries,
      int start,
      int end
  ) {
    int jumpBack = Math.max(0, currentPage - PAGE_JUMP);
    int jumpForward = Math.min(pageCount - 1, currentPage + PAGE_JUMP);

    if (pageCount > 1 && currentPage > 0) {
      window.setElement(-4, navRow, GuiTheme.element("skills-first", LECTERN, "gui", "navigation", "first")
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
          .onLeftClick((MenuClick e) -> open(player, 0)));
      window.setElement(-3, navRow, GuiTheme.element("skills-prev", ARROW, "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_BACK, trusted("pages", PAGE_JUMP)))
          .onLeftClick((MenuClick e) -> open(player, currentPage - 1))
          .onRightClick((MenuClick e) -> open(player, jumpBack)));
    } else if (pageCount > 1) {
      window.setElement(-4, navRow, boundaryElement("skills-first-disabled", GuiMessages.FIRST_PAGE));
      window.setElement(-3, navRow, boundaryElement("skills-prev-disabled", GuiMessages.NO_PREVIOUS_PAGE));
    }

    if (pageCount > 1 && currentPage < pageCount - 1) {
      window.setElement(3, navRow, GuiTheme.element("skills-next", ARROW, "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_FORWARD, trusted("pages", PAGE_JUMP)))
          .onLeftClick((MenuClick e) -> open(player, currentPage + 1))
          .onRightClick((MenuClick e) -> open(player, jumpForward)));
      window.setElement(4, navRow, GuiTheme.element("skills-last", LECTERN, "gui", "navigation", "last")
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
          .onLeftClick((MenuClick e) -> open(player, pageCount - 1)));
    } else if (pageCount > 1) {
      window.setElement(3, navRow, boundaryElement("skills-next-disabled", GuiMessages.NO_NEXT_PAGE));
      window.setElement(4, navRow, boundaryElement("skills-last-disabled", GuiMessages.LAST_PAGE));
    }

    int from = totalEntries <= 0 ? 0 : (start + 1);
    int to = totalEntries <= 0 ? 0 : end;
    GuiElement center = GuiTheme.element("skills-page-info", PAPER, "gui", "navigation", "page")
        .setName(C.AQUA + AdaptLanguage.text(GuiMessages.SKILLS))
        .setHint(MenuHint.navigation("page-info"));
    center.addLore(C.DARK_GRAY + AdaptLanguage.text(
        GuiMessages.PAGE_SHOWING_RANGE,
        trusted("page", currentPage + 1),
        trusted("pages", pageCount),
        trusted("from", from),
        trusted("to", to),
        trusted("total", totalEntries)
    ));
    window.setElement(0, navRow, center);
  }

  private static GuiElement boundaryElement(String id, TextKey name) {
    return GuiTheme.element(id, GRAY_PANE, "gui", "navigation", "disabled")
        .setName(C.DARK_GRAY + AdaptLanguage.text(name))
        .setRole(MenuRole.DECORATION);
  }

  private record SkillPageEntry(Skill<?> skill, PlayerSkillLine line,
                                int adaptationLevel) {
  }

  /** One seat of the skills menu: its skill and whether the player started it, or no skill for a partner's empty slot. */
  record Seat(Skill<?> skill, PlayerSkillLine line, int adaptationLevel, boolean started) {
    static final Seat EMPTY = new Seat(null, null, 0, false);

    boolean empty() {
      return skill == null;
    }
  }
}
