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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiGroupLayout;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class SkillGuiSupport {
  private static final Id PAPER = Id.minecraft("paper");
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Id LECTERN = Id.minecraft("lectern");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");

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

  static void openGui(Skill<?> skill, PlayerRef player, int page) {
    if (skill == null || !skill.isEnabled() || !SkillRuntimeGuards.isRuntimePlayer(player)) {
      return;
    }

    if (!Tasks.isPrimaryThread()) {
      int targetPage = page;
      Tasks.runEntity(player, () -> openGui(skill, player, targetPage));
      return;
    }

    AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }

    SoundPlayer spw = SoundPlayer.of(player.world());
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);

    List<List<Adaptation<?>>> groups = SkillMenus.adaptationRows(skill, player);
    int visibleCount = 0;
    for (List<Adaptation<?>> group : groups) {
      visibleCount += group.size();
    }

    boolean reserveNavigation = AdaptConfig.get().isGuiBackButton();
    boolean hasPreferences = AdaptConfig.get().isPlayerSettingsEnabled();
    GuiGroupLayout.Plan<Adaptation<?>> plan = GuiGroupLayout.plan(groups, reserveNavigation, hasPreferences);
    int currentPage = GuiLayout.clampPage(page, plan.pageCount());
    GuiGroupLayout.Page<Adaptation<?>> currentLayout = plan.pages().get(currentPage);
    int start = currentLayout.offset();
    int end = start + currentLayout.size();

    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "skill/" + skill.getName());
    window.setViewportHeight(plan.rows());
    window.setLayout(MenuLayout.GRID).setPaging(new MenuPaging(currentPage, plan.pageCount(), visibleCount));

    if (visibleCount == 0) {
      window.setElement(0, 0, new GuiElement("ada-empty")
          .setMaterial(PAPER)
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.NO_ADAPTATIONS_AVAILABLE))
          .setRole(MenuRole.HEADER));
    } else {
      List<GuiEffects.Placement> reveal = new ArrayList<>();
      for (int row = 0; row < currentLayout.rows().size(); row++) {
        List<Adaptation<?>> rowAdaptations = currentLayout.rows().get(row);
        int rowCount = rowAdaptations.size();
        for (int i = 0; i < rowCount; i++) {
          Adaptation<?> adaptation = rowAdaptations.get(i);
          int level = adaptPlayer.getData().getSkillLine(skill.getName()).getAdaptationLevel(adaptation.getName());
          int pos = GuiLayout.centeredPosition(i, rowCount);
          CustomModel model = GuiConfig.adaptationModel(
              adaptation.getName(),
              adaptation.getIcon(),
              adaptation.getModel()
          );
          GuiElement element = new GuiElement("ada-" + adaptation.getName())
              .setMaterial(model.material())
              .setModel(model)
              .setName(adaptation.getDisplayName(level))
              .addLore(C.GRAY + adaptation.getDescription())
              .addLore(level == 0
                  ? C.DARK_GRAY + AdaptLanguage.text(SnippetsMessages.GUI_NOT_LEARNED)
                  : C.GRAY + AdaptLanguage.text(
                      SnippetsMessages.GUI_LEVEL_VALUE,
                      trusted("level", C.WHITE + Form.toRoman(level))
                  ))
              .setRole(MenuRole.NAVIGATION)
              .onLeftClick((MenuClick e) -> openAdaptation(adaptation, player));
          reveal.add(new GuiEffects.Placement(pos, row, element));
        }
      }
      GuiEffects.applyReveal(window, reveal);
    }

    if (plan.hasNavigationRow()) {
      int navRow = plan.rows() - (hasPreferences ? 2 : 1);
      int jumpPages = 5;
      int jumpBack = Math.max(0, currentPage - jumpPages);
      int jumpForward = Math.min(plan.pageCount() - 1, currentPage + jumpPages);
      if (plan.pageCount() > 1 && currentPage > 0) {
        window.setElement(-4, navRow, GuiTheme.element("skill-first", LECTERN, "gui", "navigation", "first")
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
            .onLeftClick((MenuClick e) -> openSkillPage(skill, player, 0)));
        window.setElement(-3, navRow, GuiTheme.element("skill-prev", ARROW, "gui", "navigation", "previous")
            .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
            .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_BACK, trusted("pages", jumpPages)))
            .onLeftClick((MenuClick e) -> openSkillPage(skill, player, currentPage - 1))
            .onRightClick((MenuClick e) -> openSkillPage(skill, player, jumpBack)));
      } else if (plan.pageCount() > 1) {
        window.setElement(-4, navRow, boundaryElement("skill-first-disabled", GuiMessages.FIRST_PAGE));
        window.setElement(-3, navRow, boundaryElement("skill-prev-disabled", GuiMessages.NO_PREVIOUS_PAGE));
      }
      if (plan.pageCount() > 1 && currentPage < plan.pageCount() - 1) {
        window.setElement(3, navRow, GuiTheme.element("skill-next", ARROW, "gui", "navigation", "next")
            .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
            .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_FORWARD, trusted("pages", jumpPages)))
            .onLeftClick((MenuClick e) -> openSkillPage(skill, player, currentPage + 1))
            .onRightClick((MenuClick e) -> openSkillPage(skill, player, jumpForward)));
        window.setElement(4, navRow, GuiTheme.element("skill-last", LECTERN, "gui", "navigation", "last")
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
            .onLeftClick((MenuClick e) -> openSkillPage(skill, player, plan.pageCount() - 1)));
      } else if (plan.pageCount() > 1) {
        window.setElement(3, navRow, boundaryElement("skill-next-disabled", GuiMessages.NO_NEXT_PAGE));
        window.setElement(4, navRow, boundaryElement("skill-last-disabled", GuiMessages.LAST_PAGE));
      }

      int from = visibleCount == 0 ? 0 : (start + 1);
      int to = visibleCount == 0 ? 0 : end;
      GuiElement center;
      if (AdaptConfig.get().isGuiBackButton()) {
        center = GuiTheme.element("back", ARROW, "gui", "navigation", "back")
            .setName("" + C.RESET + C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
            .onLeftClick((MenuClick e) -> navigateBack(player));
      } else {
        center = GuiTheme.element("skill-page-info", PAPER, "gui", "navigation", "page")
            .setName(C.AQUA + AdaptLanguage.text(GuiMessages.ADAPTATIONS))
            .setHint(MenuHint.navigation("page-info"));
      }
      center.addLore(C.DARK_GRAY + AdaptLanguage.text(
          GuiMessages.PAGE_SHOWING_RANGE,
          trusted("page", currentPage + 1),
          trusted("pages", plan.pageCount()),
          trusted("from", from),
          trusted("to", to),
          trusted("total", visibleCount)
      ));
      window.setElement(0, navRow, center);
    }

    if (hasPreferences) {
      SkillPreferenceGuiSupport.populate(skill, window, plan.rows() - 1);
    }
    window.setTitle(AdaptLanguage.text(
        GuiMessages.SKILL_TITLE,
        trusted("skill", skill.getDisplayName(adaptPlayer.getSkillLine(skill.getName()).getLevel())),
        trusted("progress", Form.pc(XP.getLevelProgress(adaptPlayer.getSkillLine(skill.getName()).getXp()))),
        trusted("xp", Form.f((int) XP.getXpUntilLevelUp(adaptPlayer.getSkillLine(skill.getName()).getXp()))),
        trusted("nextLevel", adaptPlayer.getSkillLine(skill.getName()).getLevel() + 1)
    ));
    window.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onGuiClosed(player, !AdaptConfig.get().isEscClosesAllGuis())));
    window.open();
  }

  private static void openSkillPage(Skill<?> skill, PlayerRef player, int page) {
    openGui(skill, player, page);
  }

  private static void openAdaptation(Adaptation<?> adaptation, PlayerRef player) {
    adaptation.openGui(player);
  }

  private static void navigateBack(PlayerRef player) {
    playCloseSound(player);
    SkillsGui.open(player);
  }

  private static void onGuiClosed(PlayerRef player, boolean openPrevGui) {
    if (player == null) {
      return;
    }

    if (GuiCloseSuppression.consume(player)) {
      return;
    }

    playCloseSound(player);
    if (!openPrevGui) {
      return;
    }

    Tasks.runEntity(player, () -> {
      if (GuiCloseSuppression.windowFree(player)) {
        SkillsGui.open(player);
      }
    }, 1);
  }

  private static void playCloseSound(PlayerRef player) {
    SoundPlayer spw = SoundPlayer.of(player.world());
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
    spw.play(player.location(), Sounds.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
  }

  private static GuiElement boundaryElement(String id, TextKey name) {
    return GuiTheme.element(id, GRAY_PANE, "gui", "navigation", "disabled")
        .setName(C.DARK_GRAY + AdaptLanguage.text(name))
        .setRole(MenuRole.DECORATION);
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
        AdaptLog.verbose("Failed reading boolean field '" + fieldName + "' from " + source.getClass().getName()
            + ": " + ex.getClass().getSimpleName()
            + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        return null;
      }
    }

    return null;
  }
}
