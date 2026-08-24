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

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiCloseSuppression;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class AdaptationGuiSupport {
  private static final Map<String, Long> PERMANENT_LEARN_CONFIRMATIONS = new ConcurrentHashMap<>();
  private static final long PERMANENT_LEARN_CONFIRM_WINDOW_MS = 6_000L;

  private AdaptationGuiSupport() {
  }

  static boolean areParticlesEnabled(Adaptation<?> adaptation, boolean componentEnabled) {
    if (!componentEnabled) {
      return false;
    }

    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    if (effects != null && effects.getAdaptationParticleOverrides() != null && !effects.getAdaptationParticleOverrides().isEmpty()) {
      String key = adaptation.getName();
      Boolean override = effects.getAdaptationParticleOverrides().get(key);
      if (override == null && key != null) {
        override = effects.getAdaptationParticleOverrides().get(key.toLowerCase(Locale.ROOT));
      }
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
      return C.DARK_GRAY + Form.capitalizeWords(adaptation.getName().replaceAll("\\Q" + adaptation.getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
    }
    if (!adaptation.getSkill().isEnabled()) {
      return C.DARK_GRAY + Form.capitalizeWords(adaptation.getName().replaceAll("\\Q" + adaptation.getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
    }
    return C.RESET + "" + C.BOLD + adaptation.getSkill().getColor().toString() + Form.capitalizeWords(adaptation.getName().replaceAll("\\Q" + adaptation.getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
  }

  static String getDisplayName(Adaptation<?> adaptation, int level) {
    if (!adaptation.isEnabled()) {
      return adaptation.getDisplayName();
    }
    if (!adaptation.getSkill().isEnabled()) {
      return adaptation.getDisplayName();
    }
    if (level >= 1) {
      return adaptation.getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
    }

    return adaptation.getDisplayName();
  }

  static String getDisplayNameNoRoman(Adaptation<?> adaptation, int level) {
    if (level >= 1) {
      return adaptation.getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
    }

    return adaptation.getDisplayName();
  }

  static BlockFace getBlockFace(Player player, int maxrange) {
    List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, maxrange);
    if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding())
      return null;
    Block targetBlock = lastTwoTargetBlocks.get(1);
    Block adjacentBlock = lastTwoTargetBlocks.get(0);
    return targetBlock.getFace(adjacentBlock);
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
    return CustomModel.get(adaptation.getIcon(), "adaptation", adaptation.getName(), "icon");
  }

  static CustomModel getModel(Adaptation<?> adaptation, int level) {
    CustomModel model = CustomModel.get(adaptation.getIcon(), "adaptation", adaptation.getName(), "level-" + level);
    if (model.material() == adaptation.getIcon() && model.model() == 0)
      model = CustomModel.get(Material.PAPER, "snippets", "gui", "level", String.valueOf(level));
    if (model.material() == Material.PAPER && model.model() == 0)
      model = adaptation.getModel();
    return model;
  }

  static boolean openGui(Adaptation<?> adaptation, Player player, boolean checkPermissions) {
    if (adaptation == null || player == null || adaptation.getPlayer(player) == null) {
      return false;
    }
    if (checkPermissions && !adaptation.hasUsePermission(player, adaptation)) {
      return false;
    } else {
      openGui(adaptation, player);
      return true;
    }
  }

  static void openGui(Adaptation<?> adaptation, Player player) {
    openGui(adaptation, player, 0);
  }

  static void openGui(Adaptation<?> adaptation, Player player, int page) {
    if (adaptation == null || player == null || !adaptation.isEnabled()) {
      return;
    }
    if (!adaptation.getSkill().isEnabled()) {
      return;
    }
    if (!J.isPrimaryThread()) {
      int targetPage = page;
      J.runEntity(player, () -> openGui(adaptation, player, targetPage));
      return;
    }

    AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }

    SoundPlayer spw = SoundPlayer.of(player.getWorld());
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);

    boolean reserveNavigation = AdaptConfig.get().isGuiBackButton();
    GuiLayout.PagePlan plan = GuiLayout.plan(adaptation.getMaxLevel(), reserveNavigation);
    int currentPage = GuiLayout.clampPage(page, plan.pageCount());
    int start = currentPage * plan.itemsPerPage();
    int end = Math.min(adaptation.getMaxLevel(), start + plan.itemsPerPage());

    PlayerSkillLine line = adaptPlayer.getSkillLine(adaptation.getSkill().getName());
    if (line == null) {
      return;
    }
    int mylevel = paidLevel(line, adaptation.getName());

    long k = line.getKnowledge();

    boolean debugLearning = AdaptDebugMode.isActive(player);

    UIWindow w = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(w, "skill/" + adaptation.getSkill().getName() + "/" + adaptation.getName());
    w.setViewportHeight(plan.rows());

    List<GuiEffects.Placement> reveal = new ArrayList<>();
    for (int row = 0; row < plan.contentRows(); row++) {
      int rowStart = start + (row * GuiLayout.WIDTH);
      if (rowStart >= end) {
        break;
      }

      int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
      for (int i = 0; i < rowCount; i++) {
        int lvl = rowStart + i + 1;
        int pos = GuiLayout.centeredPosition(i, rowCount);
        int c = adaptation.getCostFor(lvl, mylevel);
        int rc = adaptation.getRefundCostFor(lvl - 1, mylevel);
        int pc = adaptation.getPowerCostFor(lvl, mylevel);
        boolean pendingPermanentConfirm = isPermanentLearnConfirmationPending(player, adaptation, lvl);
        CustomModel model = GuiConfig.adaptationModel(
            adaptation.getName(),
            adaptation.getIcon(),
            adaptation.getModel(lvl)
        );
        Element de = new UIElement("lp-" + lvl + "g")
            .setMaterial(new MaterialBlock(model.material()))
            .setBaseItemStack(model.toItemStack())
            .setName(adaptation.getDisplayName(lvl))
            .setEnchanted(ownsLevel(mylevel, lvl))
            .setProgress(1D)
            .addLore(C.GRAY + adaptation.getDescription())
            .addLore(knowledgeCostLore(mylevel, lvl, c))
            .addLore(vaultCostLore(adaptation, player, mylevel, lvl, c))
            .addLore(learningActionLore(adaptation, mylevel, lvl, rc, k, c, debugLearning))
            .addLore(powerLore(adaptPlayer, mylevel, lvl, pc))
            .addLore((adaptation.isPermanent() ? C.RED + "" + C.BOLD + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_MAY_NOT_UNLEARN) : ""))
            .addLore(adaptation.isPermanent() && mylevel < lvl
                ? (pendingPermanentConfirm
                ? C.GOLD + "" + C.BOLD + AdaptLanguage.text(GuiMessages.PERMANENT_LEARN_CONFIRM_NOW)
                : C.YELLOW + AdaptLanguage.text(GuiMessages.PERMANENT_LEARN_DOUBLE_CLICK))
                : "")
            .onLeftClick((e) -> {
              AdaptPlayer currentPlayer = adaptation.getPlayer(player);
              if (currentPlayer == null) {
                return;
              }
              PlayerSkillLine skillLine = currentPlayer.getSkillLine(adaptation.getSkill().getName());
              if (skillLine == null) {
                spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                return;
              }

              int delayTicks = AdaptConfig.get().getLearnUnlearnButtonDelayTicks();
              int currentLevel = paidLevel(skillLine, adaptation.getName());
              if (ownsLevel(currentLevel, lvl)) {
                adaptation.unlearn(player, lvl, false);
                int updatedLevel = skillLine.getAdaptationLevel(adaptation.getName());
                if (updatedLevel < currentLevel) {
                  spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                  FxPresets.failFizzle(adaptation, player);
                  if (delayTicks != 0) {
                    AdaptHud.guiTitle(player, " ", C.GRAY + AdaptLanguage.text(
                        SnippetsMessages.ADAPT_MENU_UNLEARNED_TITLE,
                        trusted("adaptation", adaptation.getDisplayName(currentLevel))
                    ));
                  }
                  closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
                  return;
                }

                spw.play(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 0.5f, 1.355f);
                if (delayTicks != 0) {
                  AdaptHud.guiTitle(player, " ", C.RED + "" + C.BOLD + AdaptLanguage.text(
                      SnippetsMessages.ADAPT_MENU_MAY_NOT_UNLEARN_TITLE,
                      trusted("adaptation", adaptation.getDisplayName(currentLevel))
                  ));
                }
                J.runEntity(player, () -> openAdaptationPage(adaptation, player, currentPage), delayTicks);
                return;
              }

              boolean debugLearningClick = AdaptDebugMode.isActive(player);
              long currentKnowledge = skillLine.getKnowledge();
              if (debugLearningClick || (currentKnowledge >= c && currentPlayer.getData().hasPowerAvailable(pc))) {
                if (adaptation.isPermanent() && !debugLearningClick && !consumePermanentLearnConfirmation(player, adaptation, lvl)) {
                  spw.play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.85f);
                  AdaptHud.guiTitle(player, " ", C.GOLD + "" + C.BOLD + AdaptLanguage.text(GuiMessages.PERMANENT_LEARN_CONFIRM));
                  J.runEntity(player, () -> openAdaptationPage(adaptation, player, currentPage), 1);
                  return;
                }

                AdaptationLearningTransaction.Result result =
                    AdaptationLearningTransaction.learn(adaptation, player, lvl, debugLearningClick);
                if (result == AdaptationLearningTransaction.Result.LEARNED) {
                  spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                  if (adaptation.isPermanent()) {
                    spw.play(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.355f);
                    spw.play(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.7f, 1.355f);
                  }
                  FxPresets.learnCelebration(adaptation, player);
                  if (delayTicks != 0) {
                    AdaptHud.guiTitle(player, " ", C.GRAY + AdaptLanguage.text(
                        SnippetsMessages.ADAPT_MENU_LEARNED_TITLE,
                        trusted("adaptation", adaptation.getDisplayName(lvl))
                    ));
                  }
                  closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
                } else {
                  spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                  notifyEconomyFailure(player, result);
                }
              } else {
                spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
              }
            });
        de.addLore(" ");
        adaptation.addStats(lvl, de);
        reveal.add(new GuiEffects.Placement(pos, row, de));
      }
    }
    GuiEffects.applyReveal(w, reveal);

    if (plan.hasNavigationRow()) {
      int navRow = plan.rows() - 1;
      int jumpPages = 5;
      int jumpBack = Math.max(0, currentPage - jumpPages);
      int jumpForward = Math.min(plan.pageCount() - 1, currentPage + jumpPages);
      if (plan.pageCount() > 1 && currentPage > 0) {
        w.setElement(-4, navRow, new UIElement("adapt-first")
            .setMaterial(new MaterialBlock(Material.LECTERN))
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, 0)));
        w.setElement(-3, navRow, new UIElement("adapt-prev")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
            .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_BACK, trusted("pages", jumpPages)))
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, currentPage - 1))
            .onRightClick((e) -> openAdaptationPage(adaptation, player, jumpBack)));
      } else if (plan.pageCount() > 1) {
        w.setElement(-4, navRow, boundaryElement("adapt-first-disabled", GuiMessages.FIRST_PAGE));
        w.setElement(-3, navRow, boundaryElement("adapt-prev-disabled", GuiMessages.NO_PREVIOUS_PAGE));
      }
      if (plan.pageCount() > 1 && currentPage < plan.pageCount() - 1) {
        w.setElement(3, navRow, new UIElement("adapt-next")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
            .addLore(C.GRAY + AdaptLanguage.text(GuiMessages.RIGHT_CLICK_JUMP_FORWARD, trusted("pages", jumpPages)))
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, currentPage + 1))
            .onRightClick((e) -> openAdaptationPage(adaptation, player, jumpForward)));
        w.setElement(4, navRow, new UIElement("adapt-last")
            .setMaterial(new MaterialBlock(Material.LECTERN))
            .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, plan.pageCount() - 1)));
      } else if (plan.pageCount() > 1) {
        w.setElement(3, navRow, boundaryElement("adapt-next-disabled", GuiMessages.NO_NEXT_PAGE));
        w.setElement(4, navRow, boundaryElement("adapt-last-disabled", GuiMessages.LAST_PAGE));
      }

      int from = adaptation.getMaxLevel() <= 0 ? 0 : (start + 1);
      int to = adaptation.getMaxLevel() <= 0 ? 0 : end;
      Element center;
      if (AdaptConfig.get().isGuiBackButton()) {
        center = new UIElement("back")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName("" + C.RESET + C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
            .onLeftClick((e) -> navigateBack(adaptation, player));
      } else {
        center = new UIElement("adapt-page-info")
            .setMaterial(new MaterialBlock(Material.PAPER))
            .setName(C.AQUA + AdaptLanguage.text(GuiMessages.LEVELS));
      }
      center.addLore(C.DARK_GRAY + AdaptLanguage.text(
          GuiMessages.PAGE_SHOWING_RANGE,
          trusted("page", currentPage + 1),
          trusted("pages", plan.pageCount()),
          trusted("from", from),
          trusted("to", to),
          trusted("total", adaptation.getMaxLevel())
      ));
      w.setElement(0, navRow, center.setProgress(1D));
    }

    w.setTitle(adaptation.getDisplayName());
    w.onClosed((vv) -> J.runEntity(player, () -> onGuiClosed(adaptation, player, w, !AdaptConfig.get().isEscClosesAllGuis())));
    w.open();
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
  }

  static boolean isPermanentLearnConfirmationPending(Player player, Adaptation<?> adaptation, int level) {
    if (player == null || adaptation == null) {
      return false;
    }

    Long until = PERMANENT_LEARN_CONFIRMATIONS.get(permanentConfirmKey(player, adaptation, level));
    return until != null && until >= M.ms();
  }

  static boolean consumePermanentLearnConfirmation(Player player, Adaptation<?> adaptation, int level) {
    if (player == null) {
      return false;
    }

    long now = M.ms();
    PERMANENT_LEARN_CONFIRMATIONS.entrySet().removeIf(e -> e.getValue() < now);

    String key = permanentConfirmKey(player, adaptation, level);
    Long until = PERMANENT_LEARN_CONFIRMATIONS.get(key);
    if (until != null && until >= now) {
      PERMANENT_LEARN_CONFIRMATIONS.remove(key);
      return true;
    }

    String prefix = permanentConfirmPrefix(player, adaptation);
    PERMANENT_LEARN_CONFIRMATIONS.keySet().removeIf(existing -> existing.startsWith(prefix));
    PERMANENT_LEARN_CONFIRMATIONS.put(key, now + PERMANENT_LEARN_CONFIRM_WINDOW_MS);
    return false;
  }

  static void unlearn(Adaptation<?> adaptation, Player player, int lvl, boolean force) {
    boolean debugLearning = AdaptDebugMode.isActive(player);
    AdaptationLearningTransaction.unlearn(adaptation, player, lvl - 1, force || debugLearning);
  }

  static void learn(Adaptation<?> adaptation, Player player, int lvl, boolean force) {
    AdaptationLearningTransaction.learn(adaptation, player, lvl, force);
  }

  static boolean isAdaptationRecipe(Adaptation<?> adaptation, Recipe recipe) {
    if (!adaptation.isEnabled()) {
      return false;
    }
    if (!adaptation.getSkill().isEnabled()) {
      return false;
    }
    for (AdaptRecipe i : adaptation.getRecipes()) {
      if (i.is(recipe)) {
        return true;
      }
    }
    return false;
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

  private static Element boundaryElement(String id, TextKey name) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE))
        .setName(C.DARK_GRAY + AdaptLanguage.text(name));
  }

  private static void openAdaptationPage(Adaptation<?> adaptation, Player player, int page) {
    openGui(adaptation, player, page);
  }

  private static void closeAndReopenAfterLevelChange(Adaptation<?> adaptation, Player player, int page, int delayTicks) {
    closeCurrentAdaptationGui(player);
    int reopenDelay = Math.max(0, delayTicks);
    J.runEntity(player, () -> reopenAdaptationPageIfReady(adaptation, player, page), reopenDelay);
  }

  private static void closeCurrentAdaptationGui(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }

    GuiCloseSuppression.suppress(player);
    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
    if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
      player.closeInventory();
    }
  }

  private static void reopenAdaptationPageIfReady(Adaptation<?> adaptation, Player player, int page) {
    if (player == null || !player.isOnline()) {
      return;
    }

    if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) {
      return;
    }

    openAdaptationPage(adaptation, player, page);
  }

  private static void navigateBack(Adaptation<?> adaptation, Player player) {
    playCloseSound(player);
    adaptation.getSkill().openGui(player);
  }

  private static void onGuiClosed(Adaptation<?> adaptation, Player player, UIWindow window, boolean openPrevGui) {
    if (player == null) {
      return;
    }

    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window);
    if (GuiCloseSuppression.consume(player)) {
      return;
    }

    playCloseSound(player);
    if (!openPrevGui) {
      return;
    }

    J.runEntity(player, () -> {
      if (player.isOnline() && player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
        adaptation.getSkill().openGui(player);
      }
    }, 1);
  }

  private static void playCloseSound(Player player) {
    SoundPlayer spw = SoundPlayer.of(player.getWorld());
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
  }

  private static String knowledgeCostLore(int currentLevel, int targetLevel, int cost) {
    if (currentLevel >= targetLevel) {
      return "";
    }

    return C.GRAY + AdaptLanguage.text(
        AdaptConfig.get().isHardcoreNoRefunds()
            ? GuiMessages.KNOWLEDGE_COST_NO_REFUNDS
            : GuiMessages.KNOWLEDGE_COST,
        trusted("cost", C.WHITE + String.valueOf(cost) + C.GRAY)
    );
  }

  private static String vaultCostLore(
      Adaptation<?> adaptation,
      Player player,
      int currentLevel,
      int targetLevel,
      int knowledgeCost
  ) {
    if (!AdaptationLearningTransaction.isConfigured()) {
      return "";
    }
    if (currentLevel >= targetLevel) {
      String refund = AdaptationLearningTransaction.formattedRefund(
          adaptation,
          player,
          targetLevel - 1,
          currentLevel
      );
      return refund.isBlank() ? "" : C.GREEN + AdaptLanguage.text(
          GuiMessages.VAULT_REFUND,
          trusted("amount", refund)
      );
    }
    String cost = AdaptationLearningTransaction.formattedLearnCost(knowledgeCost);
    if (cost.isBlank()) {
      return C.YELLOW + AdaptLanguage.text(GuiMessages.VAULT_UNAVAILABLE_FALLBACK);
    }
    return C.GRAY + AdaptLanguage.text(
        GuiMessages.VAULT_LEARN_COST,
        trusted("amount", cost)
    );
  }

  private static void notifyEconomyFailure(Player player, AdaptationLearningTransaction.Result result) {
    if (result == AdaptationLearningTransaction.Result.INSUFFICIENT_FUNDS) {
      Adapt.messagePlayer(player, AdaptLanguage.text(GuiMessages.VAULT_INSUFFICIENT_FUNDS));
      return;
    }
    if (result == AdaptationLearningTransaction.Result.ECONOMY_UNAVAILABLE
        || result == AdaptationLearningTransaction.Result.ECONOMY_FAILED) {
      Adapt.messagePlayer(player, AdaptLanguage.text(GuiMessages.VAULT_TRANSACTION_FAILED));
    }
  }

  private static String learningActionLore(
      Adaptation<?> adaptation,
      int currentLevel,
      int targetLevel,
      int refund,
      long knowledge,
      int cost,
      boolean debugLearning
  ) {
    if (currentLevel >= targetLevel) {
      if (AdaptConfig.get().isHardcoreNoRefunds()) {
        return C.GREEN + AdaptLanguage.text(GuiMessages.ALREADY_LEARNED_NO_REFUNDS);
      }
      if (adaptation.isPermanent()) {
        return "";
      }
      return C.GREEN + AdaptLanguage.text(
          GuiMessages.ALREADY_LEARNED_REFUND,
          trusted("refund", refund)
      );
    }

    if (debugLearning || knowledge >= cost) {
      return C.BLUE + AdaptLanguage.text(
          GuiMessages.CLICK_TO_LEARN,
          trusted("adaptation", adaptation.getDisplayName(targetLevel))
      );
    }
    if (knowledge == 0) {
      return C.RED + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_NO_KNOWLEDGE);
    }
    return C.RED + AdaptLanguage.text(
        GuiMessages.KNOWLEDGE_SHORTAGE,
        trusted("knowledge", C.WHITE + String.valueOf(knowledge) + C.RED)
    );
  }

  private static String powerLore(
      AdaptPlayer adaptPlayer,
      int currentLevel,
      int targetLevel,
      int powerCost
  ) {
    if (currentLevel >= targetLevel || adaptPlayer.getData().hasPowerAvailable(powerCost)) {
      return C.GREEN + AdaptLanguage.text(
          GuiMessages.POWER_DRAIN,
          trusted("power", targetLevel)
      );
    }
    return C.RED + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_NOT_ENOUGH_POWER) + "\n"
        + C.RED + AdaptLanguage.text(SnippetsMessages.ADAPT_MENU_HOW_TO_LEVEL_UP);
  }

  private static String permanentConfirmPrefix(Player player, Adaptation<?> adaptation) {
    return player.getUniqueId() + "|" + adaptation.getName() + "|";
  }

  private static String permanentConfirmKey(Player player, Adaptation<?> adaptation, int level) {
    return permanentConfirmPrefix(player, adaptation) + level;
  }
}
