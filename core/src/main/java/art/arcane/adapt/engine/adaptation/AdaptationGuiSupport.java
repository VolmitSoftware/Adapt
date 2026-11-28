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

package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.engine.gui.LoreLists;
import art.arcane.adapt.engine.gui.SettingsMenus;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.misc.MenuSounds;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

final class AdaptationGuiSupport {
  private static final Map<String, Long> PERMANENT_LEARN_CONFIRMATIONS = new ConcurrentHashMap<>();
  private static final long PERMANENT_LEARN_CONFIRM_WINDOW_MS = 6_000L;
  private static final Id PAPER = Id.minecraft("paper");
  private static final Id RECIPE_BOOK = Id.minecraft("crafting_table");

  private AdaptationGuiSupport() {
  }

  static boolean areParticlesEnabled(Adaptation<?> adaptation, boolean componentEnabled) {
    if (!componentEnabled) {
      return false;
    }

    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    if (effects != null && effects.getAdaptationParticleOverrides() != null && !effects.getAdaptationParticleOverrides().isEmpty()) {
      Boolean override = AdaptConfig.contentEntry(effects.getAdaptationParticleOverrides(), adaptation.getId().toString());
      if (override != null && !override) {
        return false;
      }
    }

    Object config = adaptation.getConfig();
    if (config == null) {
      return true;
    }

    if (config instanceof AdaptationConfig shared) {
      return shared.showParticles;
    }

    Boolean directToggle = readBooleanField(config, "showParticles");
    if (directToggle != null) {
      return directToggle;
    }

    Boolean genericToggle = readBooleanField(config, "showParticleEffects");
    if (genericToggle != null) {
      return genericToggle;
    }

    return true;
  }

  static boolean areSoundsEnabled(Adaptation<?> adaptation, boolean componentEnabled) {
    if (!componentEnabled) {
      return false;
    }

    Object config = adaptation.getConfig();
    if (config == null) {
      return true;
    }

    if (config instanceof AdaptationConfig shared) {
      return shared.showSounds;
    }

    Boolean directToggle = readBooleanField(config, "showSounds");
    if (directToggle != null) {
      return directToggle;
    }

    return true;
  }

  static String getDisplayName(Adaptation<?> adaptation) {
    if (!adaptation.isEnabled()) {
      return C.DARK_GRAY + adaptation.getId().toString();
    }
    if (!adaptation.getSkill().isEnabled()) {
      return C.DARK_GRAY + adaptation.getId().toString();
    }
    return C.RESET + "" + C.BOLD + SkillHost.legacyColor(adaptation.getSkill().getColor()) + adaptation.getId();
  }

  static String getDisplayName(Adaptation<?> adaptation, int level) {
    return leveled(adaptation, adaptation.getDisplayName(), level);
  }

  static String getShortName(Adaptation<?> adaptation, int level) {
    return leveled(adaptation, adaptation.getShortName(), level);
  }

  private static String leveled(Adaptation<?> adaptation, String name, int level) {
    if (level < 1 || !adaptation.isEnabled() || !adaptation.getSkill().isEnabled()) {
      return name;
    }
    return name + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
  }

  static String getDisplayNameNoRoman(Adaptation<?> adaptation, int level) {
    if (level >= 1) {
      return adaptation.getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    return adaptation.getDisplayName();
  }

  static int paidLevel(PlayerSkillLine skillLine, String adaptationName) {
    PlayerAdaptation stored = skillLine == null ? null : skillLine.getAdaptation(adaptationName);
    if (stored == null || stored.isRegionGranted()) {
      return 0;
    }

    return Math.max(0, stored.getLevel());
  }

  static boolean ownsLevel(int paidLevel, int tileLevel) {
    return paidLevel >= tileLevel;
  }

  static CustomModel getModel(Adaptation<?> adaptation) {
    return CustomModel.get(adaptation.getIcon().item(), "adaptation", adaptation.getId().toString(), "icon");
  }

  static CustomModel getModel(Adaptation<?> adaptation, int level) {
    CustomModel model = CustomModel.get(adaptation.getIcon().item(), "adaptation", adaptation.getId().toString(), "level-" + level);
    if (model.material().equals(adaptation.getIcon().item()) && model.model() == 0)
      model = CustomModel.get(PAPER, "snippets", "gui", "level", String.valueOf(level));
    if (model.material().equals(PAPER) && model.model() == 0)
      model = ComponentHost.of(adaptation).getModel();
    return model;
  }

  static boolean openGui(Adaptation<?> adaptation, PlayerRef player, boolean checkPermissions) {
    if (adaptation == null || player == null || AdaptCore.get().getAdaptServer().getPlayer(player) == null) {
      return false;
    }
    if (checkPermissions && !adaptation.hasUsePermission(player)) {
      return false;
    } else {
      openGui(adaptation, player);
      return true;
    }
  }

  static void openGui(Adaptation<?> adaptation, PlayerRef player) {
    openGui(adaptation, player, 0);
  }

  static void openGui(Adaptation<?> adaptation, PlayerRef player, int page) {
    if (adaptation == null || player == null || !adaptation.isEnabled()) {
      return;
    }
    if (!adaptation.getSkill().isEnabled()) {
      return;
    }
    if (!Tasks.isPrimaryThread()) {
      int targetPage = page;
      Tasks.runEntity(player, () -> openGui(adaptation, player, targetPage));
      return;
    }

    AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }
    PlayerSkillLine line = adaptPlayer.getSkillLine(SkillHost.lineKey(adaptation.getSkill()));
    if (line == null) {
      return;
    }
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    SoundPlayer spw = SoundPlayer.of(player.world());

    int keptFloor = SkillUpgrades.keptFloor(adaptation, adaptPlayer.getData());
    int mylevel = Math.max(keptFloor, paidLevel(line, adaptation.getId().toString()));
    boolean debugLearning = AdaptDebugMode.isActive(player);
    int heldLevel = SkillUpgrades.holdable(adaptation, adaptPlayer.getData()) ? 0
        : keptFloor > 0 ? keptFloor : PlayerPreferences.editLevel(adaptation, adaptPlayer);
    String heldState = heldLevel > 0 ? heldState(adaptation, player, adaptPlayer, heldLevel) : null;
    boolean hasPreferences = AdaptConfig.get().isPlayerSettingsEnabled() && !adaptation.getPlayerPreferences().isEmpty();

    List<Integer> levels = new ArrayList<>(adaptation.getMaxLevel());
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      levels.add(level);
    }
    GuiFrame.Layout<Integer> layout = GuiFrame.grid(levels, GuiFrame.COLUMNS);
    int currentPage = layout.clampedPage(page);

    GuiWindow w = new GuiWindow(player);
    GuiFrame.apply(w, "skill/" + SkillHost.lineKey(adaptation.getSkill()) + "|" + adaptation.getId(), layout.bodyRows());
    w.setLayout(MenuLayout.GRID);
    GuiFrame.subject(w, header(adaptation, adaptPlayer, line, mylevel, heldState));
    GuiFrame.body(w, layout.page(currentPage), (GuiFrame.Cell<Integer> cell) -> {
      int lvl = cell.item();
      CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(),
          AdaptationHost.of(adaptation).getModel(lvl));
      GuiElement element = new GuiElement("lp-" + lvl + "g")
          .setMaterial(model.material())
          .setModel(model);
      if (heldState != null && (keptFloor <= 0 || lvl <= keptFloor)) {
        element.setName(adaptation.getShortName(lvl)).setEnchanted(lvl <= heldLevel);
        if (lvl <= heldLevel) {
          element.addLore(C.GREEN + heldState);
        }
      } else {
        int c = adaptation.getCostFor(lvl, mylevel);
        int pc = debugLearning ? 0 : adaptation.getPowerCostFor(lvl - keptFloor, mylevel - keptFloor);
        LevelLore.State state = LevelLore.state(adaptation, adaptPlayer, mylevel, lvl, c, pc, debugLearning, keptFloor);
        element.setName(state.reachable() ? adaptation.getShortName(lvl) : C.DARK_GRAY + C.stripColor(adaptation.getShortName(lvl)))
            .setEnchanted(ownsLevel(mylevel, lvl))
            .addLore(state.lines());
        if (adaptation.isPermanent() && mylevel < lvl && isPermanentLearnConfirmationPending(player, adaptation, lvl)) {
          element.addLore(C.GOLD + "" + C.BOLD + AdaptLanguage.text(GuiMessages.PACK_CONFIRM_LEARN));
        }
        element.onLeftClick((MenuClick e) -> clickLevel(adaptation, player, spw, lvl, c, pc, currentPage));
      }
      StatLines stats = new StatLines();
      adaptation.addLevelStats(lvl, stats);
      if (!stats.lines().isEmpty()) {
        element.addLore(" ").addLore(stats.lines());
      }
      List<String> recipes = recipeNames(adaptation, lvl);
      if (!recipes.isEmpty()) {
        element.addLore(C.AQUA + AdaptLanguage.text(GuiMessages.RECIPES, trusted("recipes", LoreLists.joined(recipes))));
      }
      return element;
    });

    GuiFrame.back(w, () -> ComponentHost.of(pageSkill(adaptation, player)).openGui(player));
    GuiFrame.pager(w, currentPage, layout.pageCount(), adaptation.getMaxLevel(), (int target) -> openGui(adaptation, player, target));
    GuiElement tools = LoreLists.tools(List.of(adaptation));
    if (tools != null) {
      GuiFrame.footer(w, GuiFrame.Slot.TOOLS, tools);
    }
    GuiElement recipes = recipesElement(adaptation);
    if (recipes != null) {
      GuiFrame.footer(w, GuiFrame.Slot.LIST, recipes);
    }
    if (hasPreferences) {
      GuiFrame.footer(w, GuiFrame.Slot.SETTINGS, SettingsMenus.opener("adaptation-settings", () -> openSettings(adaptation, player, currentPage)));
    }

    w.setTitle(adaptation.getShortName());
    w.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onGuiClosed(player,
        () -> ComponentHost.of(pageSkill(adaptation, player)).openGui(player))));
    w.open();
  }

  /** The adaptation's settings under its header; Back returns to its levels at {@code adaptationPage}. */
  static void openSettings(Adaptation<?> adaptation, PlayerRef player, int adaptationPage) {
    if (!Tasks.isPrimaryThread()) {
      Tasks.runEntity(player, () -> openSettings(adaptation, player, adaptationPage));
      return;
    }
    AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    PlayerSkillLine line = adaptPlayer == null ? null : adaptPlayer.getSkillLine(SkillHost.lineKey(adaptation.getSkill()));
    if (line == null || !adaptation.isEnabled() || !adaptation.getSkill().isEnabled()) {
      return;
    }
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    int mylevel = Math.max(SkillUpgrades.keptFloor(adaptation, adaptPlayer.getData()), paidLevel(line, adaptation.getId().toString()));
    GuiWindow w = new GuiWindow(player);
    GuiFrame.apply(w, "skill/" + SkillHost.lineKey(adaptation.getSkill()) + "|" + adaptation.getId() + "|settings", 1);
    w.setLayout(MenuLayout.LIST);
    GuiFrame.subject(w, header(adaptation, adaptPlayer, line, mylevel, null));
    PreferenceGuiSupport.populate(adaptation, w, 1, adaptationPage);
    GuiFrame.back(w, () -> openGui(adaptation, player, adaptationPage));
    w.setTitle(AdaptLanguage.text(GuiMessages.PACK_SETTINGS));
    w.onClosed((GuiWindow closed) -> Tasks.runEntity(player, () -> onGuiClosed(player, () -> openGui(adaptation, player, adaptationPage))));
    w.open();
  }

  /** The header: the adaptation at the player's level, its state, description and requirements. */
  private static GuiElement header(Adaptation<?> adaptation, AdaptPlayer adaptPlayer, PlayerSkillLine line, int level, String heldState) {
    CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(),
        AdaptationHost.of(adaptation).getModel(Math.max(1, level)));
    GuiElement element = new GuiElement("adaptation-header")
        .setMaterial(model.material())
        .setModel(model)
        .setName(adaptation.getDisplayName())
        .setEnchanted(level > 0)
        .addLore(level > 0
            ? C.GREEN + AdaptLanguage.text(GuiMessages.PACK_LEVEL_OF, trusted("level", Form.toRoman(level)),
                trusted("maximum", Form.toRoman(adaptation.getMaxLevel())))
            : C.DARK_GRAY + AdaptLanguage.text(GuiMessages.PACK_NOT_LEARNED))
        .setRole(MenuRole.HEADER);
    if (heldState != null) {
      element.addLore(C.GREEN + heldState);
    }
    if (adaptation.isPermanent()) {
      element.addLore(C.RED + AdaptLanguage.text(GuiMessages.PACK_PERMANENT));
    }
    element.addLore(C.AQUA + AdaptLanguage.text(GuiMessages.PACK_KNOWLEDGE, trusted("knowledge", line.getKnowledge())))
        .addLore(" ")
        .addLore(C.GRAY + adaptation.getDescription());
    List<String> requirements = requirementLore(adaptation, adaptPlayer.getData());
    if (!requirements.isEmpty()) {
      element.addLore(" ").addLore(requirements);
    }
    return element;
  }

  /** The footer's Recipes item: what each level's recipes make, by level; null when there are none. */
  private static GuiElement recipesElement(Adaptation<?> adaptation) {
    GuiElement element = null;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      List<String> names = recipeNames(adaptation, level);
      if (names.isEmpty()) {
        continue;
      }
      if (element == null) {
        element = GuiTheme.element("recipes", RECIPE_BOOK, "gui", "navigation", "recipes")
            .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PACK_RECIPES))
            .setRole(MenuRole.INFO);
      }
      element.addLore(C.WHITE + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", Form.toRoman(level))))
          .addLore(C.GRAY + LoreLists.joined(names));
    }
    return element;
  }

  private static void clickLevel(Adaptation<?> adaptation, PlayerRef player, SoundPlayer spw, int lvl, int c, int pc, int currentPage) {
    AdaptPlayer currentPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    if (currentPlayer == null) {
      return;
    }
    PlayerSkillLine skillLine = currentPlayer.getSkillLine(SkillHost.lineKey(adaptation.getSkill()));
    if (skillLine == null) {
      playRefused(player, spw);
      return;
    }

    int delayTicks = AdaptConfig.get().getLearnUnlearnButtonDelayTicks();
    int currentLevel = Math.max(SkillUpgrades.keptFloor(adaptation, currentPlayer.getData()),
        paidLevel(skillLine, adaptation.getId().toString()));
    if (ownsLevel(currentLevel, lvl)) {
      AdaptationLearningTransaction.Result unlearnResult = AdaptationHost.of(adaptation).unlearn(player, lvl, false);
      int updatedLevel = skillLine.getAdaptationLevel(adaptation.getId().toString());
      if (updatedLevel < currentLevel) {
        playUnlearned(adaptation, player, spw);
        if (delayTicks != 0) {
          AdaptHud.guiTitle(player, " ", C.GRAY + AdaptLanguage.text(
              SnippetsMessages.ADAPT_MENU_UNLEARNED_TITLE,
              trusted("adaptation", adaptation.getShortName(currentLevel))
          ));
        }
        closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
        return;
      }

      playUnlearnRefused(player, spw);
      notifyDependents(adaptation, player, currentPlayer, lvl - 1, unlearnResult);
      if (delayTicks != 0) {
        AdaptHud.guiTitle(player, " ", C.RED + "" + C.BOLD + AdaptLanguage.text(
            SnippetsMessages.ADAPT_MENU_MAY_NOT_UNLEARN_TITLE,
            trusted("adaptation", adaptation.getShortName(currentLevel))
        ));
      }
      Tasks.runEntity(player, () -> openAdaptationPage(adaptation, player, currentPage), delayTicks);
      return;
    }

    boolean debugLearningClick = AdaptDebugMode.isActive(player);
    long currentKnowledge = skillLine.getKnowledge();
    if (debugLearningClick || (currentKnowledge >= c && (pc <= 0 || currentPlayer.getData().hasPowerAvailable(pc)))) {
      LearnGate gate = learnGate(adaptation, player, currentPlayer, lvl, debugLearningClick);
      if (gate == LearnGate.REQUIREMENTS_MISSING) {
        playRefused(player, spw);
        notifyLearningFailure(adaptation, player, currentPlayer, AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);
        return;
      }
      if (gate == LearnGate.CONFIRM_PERMANENT) {
        spw.play(player.location(), Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.85f);
        AdaptHud.guiTitle(player, " ", C.GOLD + "" + C.BOLD + AdaptLanguage.text(GuiMessages.PERMANENT_LEARN_CONFIRM));
        Tasks.runEntity(player, () -> openAdaptationPage(adaptation, player, currentPage), 1);
        return;
      }

      AdaptationLearningTransaction.Result result =
          AdaptationLearningTransaction.learn(adaptation, player, lvl, debugLearningClick);
      if (result == AdaptationLearningTransaction.Result.LEARNED) {
        playLearned(adaptation, player, spw);
        if (delayTicks != 0) {
          AdaptHud.guiTitle(player, " ", C.GRAY + AdaptLanguage.text(
              SnippetsMessages.ADAPT_MENU_LEARNED_TITLE,
              trusted("adaptation", adaptation.getShortName(lvl))
          ));
        }
        closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
      } else {
        playRefused(player, spw);
        notifyLearningFailure(adaptation, player, currentPlayer, result);
      }
    } else {
      playRefused(player, spw);
    }
  }

  static void playLearned(Adaptation<?> adaptation, PlayerRef player, SoundPlayer spw) {
    spw.play(player.location(), Sounds.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
    spw.play(player.location(), Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
    spw.play(player.location(), Sounds.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
    spw.play(player.location(), Sounds.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
    if (adaptation.isPermanent()) {
      spw.play(player.location(), Sounds.ENTITY_PLAYER_LEVELUP, 0.7f, 1.355f);
      spw.play(player.location(), Sounds.ITEM_GOAT_HORN_SOUND_1, 0.7f, 1.355f);
    }
    FxPresets.learnCelebration(adaptation, player);
  }

  static void playUnlearned(Adaptation<?> adaptation, PlayerRef player, SoundPlayer spw) {
    MenuSounds.play(player, MenuSounds.Action.UNLEARN);
    FxPresets.failFizzle(adaptation, player);
  }

  static void playUnlearnRefused(PlayerRef player, SoundPlayer spw) {
    spw.play(player.location(), Sounds.ENTITY_BLAZE_DEATH, 0.5f, 1.355f);
  }

  static void playRefused(PlayerRef player, SoundPlayer spw) {
    spw.play(player.location(), Sounds.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
  }

  static boolean isPermanentLearnConfirmationPending(PlayerRef player, Adaptation<?> adaptation, int level) {
    if (player == null || adaptation == null) {
      return false;
    }

    Long until = PERMANENT_LEARN_CONFIRMATIONS.get(permanentConfirmKey(player, adaptation, level));
    return until != null && until >= M.ms();
  }

  static boolean consumePermanentLearnConfirmation(PlayerRef player, Adaptation<?> adaptation, int level) {
    if (player == null) {
      return false;
    }

    long now = M.ms();
    PERMANENT_LEARN_CONFIRMATIONS.entrySet().removeIf((Map.Entry<String, Long> e) -> e.getValue() < now);

    String key = permanentConfirmKey(player, adaptation, level);
    Long until = PERMANENT_LEARN_CONFIRMATIONS.get(key);
    if (until != null && until >= now) {
      PERMANENT_LEARN_CONFIRMATIONS.remove(key);
      return true;
    }

    String prefix = permanentConfirmPrefix(player, adaptation);
    PERMANENT_LEARN_CONFIRMATIONS.keySet().removeIf((String existing) -> existing.startsWith(prefix));
    PERMANENT_LEARN_CONFIRMATIONS.put(key, now + PERMANENT_LEARN_CONFIRM_WINDOW_MS);
    return false;
  }

  static LearnGate learnGate(
      Adaptation<?> adaptation,
      PlayerRef player,
      AdaptPlayer adaptPlayer,
      int level,
      boolean debugLearning
  ) {
    return learnGate(adaptation, player, adaptPlayer, level, debugLearning, false);
  }

  /** {@code confirmed} is true when the player already confirmed a permanent learn elsewhere, such as the client screen. */
  static LearnGate learnGate(
      Adaptation<?> adaptation,
      PlayerRef player,
      AdaptPlayer adaptPlayer,
      int level,
      boolean debugLearning,
      boolean confirmed
  ) {
    if (!AdaptationRequirements.missing(adaptation, adaptPlayer.getData()).isEmpty()) {
      return LearnGate.REQUIREMENTS_MISSING;
    }
    if (adaptation.isPermanent() && !debugLearning && !confirmed
        && !consumePermanentLearnConfirmation(player, adaptation, level)) {
      return LearnGate.CONFIRM_PERMANENT;
    }
    return LearnGate.READY;
  }

  static AdaptationLearningTransaction.Result unlearn(Adaptation<?> adaptation, PlayerRef player, int lvl, boolean force) {
    boolean debugLearning = AdaptDebugMode.isActive(player);
    return AdaptationLearningTransaction.unlearn(adaptation, player, lvl - 1, force || debugLearning);
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

  private static void openAdaptationPage(Adaptation<?> adaptation, PlayerRef player, int page) {
    openGui(adaptation, player, page);
  }

  private static void closeAndReopenAfterLevelChange(Adaptation<?> adaptation, PlayerRef player, int page, int delayTicks) {
    closeCurrentAdaptationGui(player);
    int reopenDelay = Math.max(0, delayTicks);
    Tasks.runEntity(player, () -> reopenAdaptationPageIfReady(adaptation, player, page), reopenDelay);
  }

  private static void closeCurrentAdaptationGui(PlayerRef player) {
    if (player == null || !player.isOnline()) {
      return;
    }

    GuiCloseSuppression.suppress(player);
    MenuSession open = AdaptCore.platform().menus().current(player);
    if (open != null) {
      open.close();
    }
  }

  private static void reopenAdaptationPageIfReady(Adaptation<?> adaptation, PlayerRef player, int page) {
    if (player == null || !GuiCloseSuppression.windowFree(player)) {
      return;
    }

    openAdaptationPage(adaptation, player, page);
  }

  /** What a page of an original the player works with without learning says it is: kept, or lent by an upgrade. */
  private static String heldState(Adaptation<?> adaptation, PlayerRef player, AdaptPlayer adaptPlayer, int level) {
    SkillPair mutation = adaptPlayer.getData().mutationOf(SkillHost.lineKey(adaptation.getSkill()));
    if (mutation != null) {
      for (Lending.Running running : Lending.running(player, adaptPlayer, AdaptCore.get().getAdaptServer().getSkillRegistry(), mutation)) {
        if (running.original() == adaptation) {
          return AdaptLanguage.text(MutatedMessages.LENT_STATE, trusted("level", Form.toRoman(level)),
              trusted("upgrade", C.stripColor(running.upgrade().getDisplayName())));
        }
      }
    }
    return AdaptLanguage.text(MutatedMessages.KEPT_STATE, trusted("level", Form.toRoman(level)));
  }

  /** The skill page an adaptation page returns to: the player's mutated skill for an upgrade, else its own skill. */
  static Skill<?> pageSkill(Adaptation<?> adaptation, PlayerRef player) {
    AdaptPlayer adaptPlayer = AdaptCore.get().getAdaptServer().getPlayer(player);
    SkillPair mutation = adaptPlayer == null ? null : adaptPlayer.getData().mutationOf(SkillHost.lineKey(adaptation.getSkill()));
    if (mutation == null) {
      return adaptation.getSkill();
    }
    Skill<?> mutated = AdaptCore.get().getAdaptServer().getSkillRegistry().getMutatedSkill(mutation);
    return mutated == null ? adaptation.getSkill() : mutated;
  }

  /** A menu closed by Escape: unless a click replaced it, play the close sound and reopen {@code parent}. */
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

  /** What the recipes {@code level} unlocks make, in recipe order; tools are left to the Tools item. */
  static List<String> recipeNames(Adaptation<?> adaptation, int level) {
    Set<Id> tools = new HashSet<>();
    for (MutationTool<?> tool : MutationTools.handedOut(adaptation)) {
      for (RecipeSpec recipe : tool.recipes()) {
        tools.add(recipe.key());
      }
    }
    Set<String> names = new LinkedHashSet<>();
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      if (recipe.requiredLevel() == level && !tools.contains(recipe.key())) {
        names.add(C.stripColor(recipe.result().displayName()).trim());
      }
    }
    if (level == 1) {
      for (BrewingRecipe brew : adaptation.getBrewingRecipes()) {
        ItemRef result = brew.result();
        String name = result.potionContents().map(PotionContents::name).orElse(null);
        names.add(C.stripColor(name == null ? result.displayName() : name).trim());
      }
    }
    names.remove("");
    return List.copyOf(names);
  }

  private static List<String> requirementLore(Adaptation<?> adaptation, PlayerData data) {
    List<AdaptationRequirement> requirements = adaptation.getRequirements();
    if (requirements.isEmpty()) {
      return List.of();
    }

    List<AdaptationRequirement> missing = AdaptationRequirements.missing(adaptation, data);
    List<String> lines = new ArrayList<>(requirements.size());
    for (AdaptationRequirement requirement : requirements) {
      lines.add((missing.contains(requirement) ? C.RED : C.GREEN) + AdaptLanguage.text(
          GuiMessages.REQUIRES,
          untrusted("requirement", AdaptationRequirements.describe(requirement))
      ));
    }
    return lines;
  }

  private static void notifyDependents(
      Adaptation<?> adaptation,
      PlayerRef player,
      AdaptPlayer adaptPlayer,
      int targetLevel,
      AdaptationLearningTransaction.Result result
  ) {
    if (result == AdaptationLearningTransaction.Result.REQUIRED_BY_DEPENDENTS) {
      messagePlayer(player, dependentsText(adaptation, adaptPlayer, targetLevel));
    }
  }

  static String dependentsText(Adaptation<?> adaptation, AdaptPlayer adaptPlayer, int targetLevel) {
    return AdaptLanguage.text(
        GuiMessages.REQUIRED_BY_DEPENDENTS,
        trusted("dependents", AdaptationRequirements.names(
            AdaptationRequirements.dependents(adaptation, adaptPlayer.getData(), targetLevel)
        )),
        trusted("adaptation", AdaptationRequirements.plainName(adaptation))
    );
  }

  private static void notifyLearningFailure(
      Adaptation<?> adaptation,
      PlayerRef player,
      AdaptPlayer adaptPlayer,
      AdaptationLearningTransaction.Result result
  ) {
    String text = learningFailureText(adaptation, adaptPlayer, result);
    if (!text.isEmpty()) {
      messagePlayer(player, text);
    }
  }

  /** The chat text the chest GUI shows for a failed learn, or an empty string when it shows none. */
  static String learningFailureText(
      Adaptation<?> adaptation,
      AdaptPlayer adaptPlayer,
      AdaptationLearningTransaction.Result result
  ) {
    return switch (result) {
      case REQUIREMENTS_MISSING -> AdaptLanguage.text(
          GuiMessages.REQUIREMENTS_MISSING,
          trusted("requirements", AdaptationRequirements.describe(
              AdaptationRequirements.missing(adaptation, adaptPlayer.getData())
          )),
          trusted("adaptation", AdaptationRequirements.plainName(adaptation))
      );
      case INSUFFICIENT_FUNDS -> AdaptLanguage.text(GuiMessages.VAULT_INSUFFICIENT_FUNDS);
      case ECONOMY_UNAVAILABLE, ECONOMY_FAILED -> AdaptLanguage.text(GuiMessages.VAULT_TRANSACTION_FAILED);
      default -> "";
    };
  }

  private static void messagePlayer(PlayerRef player, String message) {
    player.sendMessage(C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + message);
  }

  /**
   * The power line of a level's entry: what learning it takes when that starts holding the adaptation, or what
   * unlearning it frees when that drops the last paid level; nothing for levels that change no power.
   */
  static String powerLore(
      Adaptation<?> adaptation,
      AdaptPlayer adaptPlayer,
      int currentLevel,
      int targetLevel,
      int powerCost,
      int keptFloor
  ) {
    if (currentLevel >= targetLevel) {
      int freed = -adaptation.getPowerCostFor(targetLevel - 1 - keptFloor, currentLevel - keptFloor);
      if (freed <= 0 || adaptation.isPermanent() || AdaptConfig.get().isHardcoreNoRefunds()) {
        return "";
      }
      return C.GREEN + AdaptLanguage.text(GuiMessages.POWER_FREED, trusted("power", freed));
    }
    if (powerCost <= 0) {
      return "";
    }
    if (adaptPlayer.getData().hasPowerAvailable(powerCost)) {
      return C.GREEN + AdaptLanguage.text(GuiMessages.POWER_DRAIN, trusted("power", powerCost));
    }
    return C.RED + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_NOT_ENOUGH_POWER) + "\n"
        + C.RED + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_HOW_TO_LEVEL_UP);
  }

  private static String permanentConfirmPrefix(PlayerRef player, Adaptation<?> adaptation) {
    return player.uuid() + "|" + adaptation.getId().toString() + "|";
  }

  private static String permanentConfirmKey(PlayerRef player, Adaptation<?> adaptation, int level) {
    return permanentConfirmPrefix(player, adaptation) + level;
  }

  enum LearnGate {
    READY,
    REQUIREMENTS_MISSING,
    CONFIRM_PERMANENT
  }
}
