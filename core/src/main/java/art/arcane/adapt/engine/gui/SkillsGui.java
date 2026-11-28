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

package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillMenus;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.SpendViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SeatMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class SkillsGui {
  private static final int SKILLS_PER_ROW = 7;
  private static final Id PAPER = Id.minecraft("paper");
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
      boolean owned = (!AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().isTickThread())
          || (AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().owns(player));
      if (!owned) {
        int targetPage = page;
        AdaptApi.scheduler().runEntity(player, () -> open(player, targetPage));
        return;
      }

      AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
      if (adaptPlayer == null) {
        AdaptLog.error("Failed to open skills gui for " + player.name() + " because they are not Online, Were Kicked, Or are a fake player.");
        return;
      }

      SkillRegistry registry = adaptPlayer.getServer().getSkillRegistry();
      List<Seat> seats = seats(adaptPlayer, player);
      List<Seat> shown = new ArrayList<>(seats.size());
      for (Seat seat : seats) {
        shown.add(seat.empty() ? null : seat);
      }
      int configuredRows = GuiConfig.skillsGuiRows();
      GuiFrame.Layout<Seat> layout = configuredRows > 0
          ? GuiFrame.grid(shown, SKILLS_PER_ROW, configuredRows - 2)
          : GuiFrame.grid(shown, SKILLS_PER_ROW);
      int currentPage = layout.clampedPage(page);

      GuiWindow w = new GuiWindow(player);
      GuiFrame.apply(w, "/", configuredRows > 0 ? configuredRows - 2 : layout.bodyRows());
      w.setLayout(MenuLayout.LIST);
      GuiFrame.subject(w, summary(adaptPlayer, seats));
      if (seats.isEmpty()) {
        GuiFrame.body(w, 0, GuiFrame.SUBJECT_COLUMN, new GuiElement("skills-empty")
            .setMaterial(PAPER)
            .setName(TextFormat.GRAY + AdaptLanguage.text(GuiMessages.NO_SKILLS_AVAILABLE))
            .setRole(MenuRole.INFO));
      }
      GuiFrame.body(w, layout.page(currentPage), (GuiFrame.Cell<Seat> cell) -> seatElement(cell.item(), player, registry));
      GuiFrame.pager(w, currentPage, layout.pageCount(), seats.size(), (int target) -> open(player, target));
      GuiElement mutate = MutationGui.leadsEntry(player, adaptPlayer.getData());
      if (mutate != null) {
        GuiFrame.footer(w, GuiFrame.Slot.ACTION, mutate);
      }

      w.setTitle(AdaptLanguage.text(
          GuiMessages.SKILLS_TITLE,
          trusted("level", (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp())),
          trusted("used", adaptPlayer.getData().getUsedPower()),
          trusted("maximum", adaptPlayer.getData().getMaxPower())
      ));
      w.open();
    }
  }

  /** A seat's card; clicking it opens the skill. */
  private static GuiElement seatElement(Seat seat, PlayerRef player, SkillRegistry registry) {
    Skill<?> skill = seat.skill();
    return SkillCards.card("skill-" + SkillHost.lineKey(skill), skill, seat.line(), seat.started(), registry)
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick e) -> ComponentHost.of(skill).openGui(player));
  }

  /** The header: the player's level and power, knowledge earned, spent and unspent, then each seat's knowledge. */
  private static GuiElement summary(AdaptPlayer adaptPlayer, List<Seat> seats) {
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
      int skillSpent = spent.getOrDefault(SkillHost.lineKey(seat.skill()), 0);
      long available = seat.line() == null ? 0L : seat.line().getKnowledge();
      totalSpent += skillSpent;
      unspent += available;
      if (skillSpent > 0 || available > 0) {
        lines.add(TextFormat.GRAY + AdaptLanguage.text(SeatMessages.SKILL, trusted("skill", TextFormat.strip(seat.skill().getDisplayName())),
            trusted("spent", TextFormat.WHITE + String.valueOf(skillSpent) + TextFormat.GRAY), trusted("available", String.valueOf(available))));
      }
    }
    PlayerData data = adaptPlayer.getData();
    GuiElement element = GuiTheme.element("skills-knowledge", KNOWLEDGE, "gui", "navigation", "knowledge")
        .setName(TextFormat.WHITE + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", (int) XP.getLevelForXp(data.getMasterXp()))))
        .setRole(MenuRole.HEADER)
        .addLore(TextFormat.GRAY + AdaptLanguage.text(GuiMessages.PACK_POWER, trusted("used", data.getUsedPower()),
            trusted("maximum", data.getMaxPower())))
        .addLore(" ")
        .addLore(TextFormat.AQUA + AdaptLanguage.text(SeatMessages.KNOWLEDGE))
        .addLore(TextFormat.GRAY + AdaptLanguage.text(SeatMessages.EARNED, trusted("value", totalSpent + unspent)))
        .addLore(TextFormat.GRAY + AdaptLanguage.text(SeatMessages.SPENT, trusted("value", totalSpent)))
        .addLore(TextFormat.GRAY + AdaptLanguage.text(SeatMessages.UNSPENT, trusted("value", unspent)));
    if (!lines.isEmpty()) {
      element.addLore(" ");
      for (String line : lines) {
        element.addLore(line);
      }
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
      if (registered != null && registered.isEnabled() && registered.hasUsePermission(player)) {
        roster.add(registered);
        knownSkills.add(SkillHost.lineKey(registered));
      }
    }
    Map<String, Integer> configuredOrder = GuiConfig.skillOrder(knownSkills);
    roster.sort(Comparator.comparingInt((Skill<?> skill) -> GuiConfig.rankOf(configuredOrder, SkillHost.lineKey(skill)))
        .thenComparing((Skill<?> skill) -> SkillMenus.sortKey(skill.getDisplayName()))
        .thenComparing(Skill::getId));
    List<Seat> seats = new ArrayList<>();
    for (Skill<?> registered : roster) {
      Skill<?> listed = SkillMutations.listedSkill(registry, data, registered);
      if (listed == null) {
        if (inactive) {
          seats.add(Seat.EMPTY);
        }
        continue;
      }
      PlayerSkillLine line = resolveSkillLine(data, SkillHost.lineKey(listed), debugMode, true);
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
      knownSkills.add(SkillHost.lineKey(registered));
      Skill<?> skill = SkillMutations.listedSkill(adaptPlayer.getServer().getSkillRegistry(), adaptPlayer.getData(), registered);
      if (skill == null) {
        continue;
      }
      if (!skill.isEnabled()) {
        continue;
      }
      PlayerSkillLine line = resolveSkillLine(adaptPlayer.getData(), SkillHost.lineKey(skill), debugMode, showAll);
      if (line == null) {
        continue;
      }

      int adaptationLevel = sumAdaptationLevels(line);
      boolean permitted = skill.hasUsePermission(player);
      if (!isSkillEntryVisible(permitted, debugMode, showAll, line, adaptationLevel)) {
        continue;
      }

      entries.add(new SkillPageEntry(skill, line, adaptationLevel));
    }

    Map<String, Integer> configuredOrder = GuiConfig.skillOrder(knownSkills);
    entries.sort(
        Comparator.comparingInt((SkillPageEntry entry) -> GuiConfig.rankOf(configuredOrder, SkillHost.lineKey(entry.skill())))
            .thenComparing((SkillPageEntry entry) -> SkillMenus.sortKey(entry.skill().getDisplayName()))
            .thenComparing((SkillPageEntry entry) -> SkillHost.lineKey(entry.skill()), String.CASE_INSENSITIVE_ORDER)
    );
    return entries;
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
