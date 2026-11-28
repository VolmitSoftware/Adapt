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

package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.engine.gui.KeptGui;
import art.arcane.adapt.engine.gui.LoreLists;
import art.arcane.adapt.engine.gui.SettingsMenus;
import art.arcane.adapt.engine.gui.SkillCards;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.misc.MenuSounds;
import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.engine.gui.SkillsGui;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class SkillGuiSupport {
  private static final Id PAPER = Id.minecraft("paper");
  private static final Id MUTATION = Id.minecraft("nether_star");
  private static final Id GROUP = Id.minecraft("name_tag");

  private SkillGuiSupport() {
  }

  static boolean areParticlesEnabled(Skill<?> skill, boolean componentEnabled) {
    if (!componentEnabled) {
      return false;
    }

    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    if (effects != null && effects.getSkillParticleOverrides() != null && !effects.getSkillParticleOverrides().isEmpty()) {
      Boolean override = AdaptConfig.contentEntry(effects.getSkillParticleOverrides(), SkillHost.lineKey(skill));
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
    MenuSounds.play(player, MenuSounds.Action.OPEN);

    List<List<Adaptation<?>>> groups = SkillMenus.adaptationRows(skill, player);
    List<Adaptation<?>> shown = new ArrayList<>();
    for (List<Adaptation<?>> group : groups) {
      shown.addAll(group);
    }
    boolean labelled = skill instanceof MutatedSkill || !skill.getAdaptationGroups().isEmpty();
    GuiFrame.Layout<Adaptation<?>> layout = GuiFrame.groups(groups, labelled);
    int currentPage = layout.clampedPage(page);
    PlayerSkillLine line = adaptPlayer.getData().getSkillLine(SkillHost.lineKey(skill));

    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, "skill/" + SkillHost.lineKey(skill), layout.bodyRows());
    window.setLayout(MenuLayout.GRID);
    GuiFrame.subject(window, SkillCards.header(skill, line, shown, adaptPlayer.getServer().getSkillRegistry()).setRole(MenuRole.HEADER));
    if (shown.isEmpty()) {
      GuiFrame.body(window, 0, GuiFrame.SUBJECT_COLUMN, new GuiElement("ada-empty")
          .setMaterial(PAPER)
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.NO_ADAPTATIONS_AVAILABLE))
          .setRole(MenuRole.INFO));
    }
    GuiFrame.body(window, layout.page(currentPage), (GuiFrame.Cell<Adaptation<?>> cell) -> cell.heading()
        ? heading(skill, groups.get(cell.group()))
        : adaptationElement(cell.item(), line, player));

    GuiFrame.back(window, () -> SkillsGui.open(player));
    GuiFrame.pager(window, currentPage, layout.pageCount(), shown.size(), (int target) -> openGui(skill, player, target));
    GuiElement tools = LoreLists.tools(shown);
    if (tools != null) {
      GuiFrame.footer(window, GuiFrame.Slot.TOOLS, tools);
    }
    GuiElement kept = KeptGui.entry(skill, player);
    if (kept != null) {
      GuiFrame.footer(window, GuiFrame.Slot.LIST, kept);
    }
    if (AdaptConfig.get().isPlayerSettingsEnabled()) {
      GuiFrame.footer(window, GuiFrame.Slot.SETTINGS, SettingsMenus.opener("skill-settings", () -> openSettings(skill, player)));
    }
    GuiElement mutate = MutationGui.entry(skill, player, adaptPlayer.getData());
    if (mutate != null) {
      GuiFrame.footer(window, GuiFrame.Slot.ACTION, mutate);
    }
    MutationGui.placeUnmutate(window, skill, player);

    window.setTitle(skill.getDisplayName(line.getLevel()));
    window.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onGuiClosed(player, () -> SkillsGui.open(player))));
    window.open();
  }

  /** The skill's settings: its on/off switch and reset under the skill's header; Back returns to the skill. */
  static void openSettings(Skill<?> skill, PlayerRef player) {
    AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, "skill/" + SkillHost.lineKey(skill), 1);
    window.setLayout(MenuLayout.LIST);
    List<Adaptation<?>> shown = new ArrayList<>();
    for (List<Adaptation<?>> group : SkillMenus.adaptationRows(skill, player)) {
      shown.addAll(group);
    }
    GuiFrame.subject(window, SkillCards.header(skill, adaptPlayer.getData().getSkillLine(SkillHost.lineKey(skill)), shown,
        adaptPlayer.getServer().getSkillRegistry()).setRole(MenuRole.HEADER));
    SkillPreferenceGuiSupport.populate(skill, window, 1);
    GuiFrame.back(window, () -> openGui(skill, player, 0));
    window.setTitle(AdaptLanguage.text(GuiMessages.PACK_SETTINGS));
    window.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onGuiClosed(player, () -> openGui(skill, player, 0))));
    window.open();
  }

  /** A group's heading: the skill an upgrade group comes from, the mutation for its own, else the group's name. */
  private static GuiElement heading(Skill<?> skill, List<Adaptation<?>> group) {
    if (group.isEmpty()) {
      return null;
    }
    Adaptation<?> first = group.getFirst();
    if (skill instanceof MutatedSkill mutated) {
      Skill<?> owner = first.getSkill();
      if (owner == null || owner == mutated) {
        return new GuiElement("group-own").setMaterial(MUTATION)
            .setName(SkillHost.legacyColor(mutated.getColor()) + C.stripColor(mutated.getDisplayName()))
            .setRole(MenuRole.INFO);
      }
      return SkillCards.icon("group-" + SkillHost.lineKey(owner), owner).setRole(MenuRole.INFO);
    }
    for (AdaptationGroup declared : skill.getAdaptationGroups()) {
      if (declared.adaptations().contains(first.getId())) {
        return GuiTheme.element("group-heading", GROUP, "gui", "navigation", "group")
            .setName(SkillHost.legacyColor(skill.getColor()) + declared.label().resolve())
            .setRole(MenuRole.INFO);
      }
    }
    return null;
  }

  private static GuiElement adaptationElement(Adaptation<?> adaptation, PlayerSkillLine line, PlayerRef player) {
    int level = line.getAdaptationLevel(adaptation.getId().toString());
    CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(),
        ComponentHost.of(adaptation).getModel());
    return new GuiElement("ada-" + adaptation.getId().toString())
        .setMaterial(model.material())
        .setModel(model)
        .setName(adaptation.getShortName(level))
        .setEnchanted(level > 0)
        .addLore(level == 0
            ? C.DARK_GRAY + AdaptLanguage.text(GuiMessages.PACK_NOT_LEARNED)
            : C.GREEN + AdaptLanguage.text(GuiMessages.PACK_LEVEL_OF, trusted("level", Form.toRoman(level)),
                trusted("maximum", Form.toRoman(adaptation.getMaxLevel()))))
        .addLore(C.GRAY + adaptation.getDescription())
        .setRole(MenuRole.NAVIGATION)
        .onLeftClick((MenuClick e) -> ComponentHost.of(adaptation).openGui(player));
  }

  /** A skill menu closed by Escape: unless a click replaced it, play the close sound and reopen {@code parent}. */
  private static void onGuiClosed(PlayerRef player, Runnable parent) {
    if (player == null || GuiCloseSuppression.consume(player)) {
      return;
    }
    MenuSounds.play(player, MenuSounds.Action.BACK);
    if (AdaptConfig.get().isEscClosesAllGuis()) {
      return;
    }
    Tasks.runEntity(player, () -> {
      if (GuiCloseSuppression.windowFree(player)) {
        parent.run();
      }
    }, 1);
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
