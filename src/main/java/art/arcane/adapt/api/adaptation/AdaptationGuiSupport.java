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
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class AdaptationGuiSupport {
  private static final Map<String, Long> PERMANENT_LEARN_CONFIRMATIONS = new ConcurrentHashMap<>();
  private static final long PERMANENT_LEARN_CONFIRM_WINDOW_MS = 6_000L;
  private static final long CLOSE_SUPPRESS_MS = 1200L;
  private static final int CLOSE_SUPPRESS_CLEAR_TICKS = 4;
  private static final Map<UUID, Long> CLOSE_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

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
    if (!adaptation.isEnabled()) {
      return;
    }
    if (!adaptation.getSkill().isEnabled()) {
      return;
    }
    if (!J.isPrimaryThread()) {
      int targetPage = page;
      J.s(() -> openGui(adaptation, player, targetPage));
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

    int mylevel = adaptation.getPlayer(player).getSkillLine(adaptation.getSkill().getName()).getAdaptationLevel(adaptation.getName());

    long k = adaptation.getPlayer(player).getData().getSkillLine(adaptation.getSkill().getName()).getKnowledge();

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
        Element de = new UIElement("lp-" + lvl + "g")
            .setMaterial(new MaterialBlock(adaptation.getIcon()))
            .setBaseItemStack(adaptation.getModel(lvl).toItemStack())
            .setName(adaptation.getDisplayName(lvl))
            .setEnchanted(mylevel >= lvl)
            .setProgress(1D)
            .addLore(Form.wrapWordsPrefixed(adaptation.getDescription(), "" + C.GRAY, 40))
            .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_cost") + " " + (AdaptConfig.get().isHardcoreNoRefunds() ? C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.no_refunds") : "")))
            .addLore(mylevel >= lvl ? AdaptConfig.get().isHardcoreNoRefunds() ? (C.GREEN + Localizer.dLocalize("snippets.adapt_menu.already_learned") + " " + C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.no_refunds")) : (adaptation.isPermanent() ? "" : (C.GREEN + Localizer.dLocalize("snippets.adapt_menu.already_learned") + " " + C.GRAY + Localizer.dLocalize("snippets.adapt_menu.unlearn_refund") + " " + C.GREEN + rc + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_cost"))) : (k >= c ? (C.BLUE + Localizer.dLocalize("snippets.adapt_menu.click_learn") + " " + adaptation.getDisplayName(lvl)) : (k == 0 ? (C.RED + Localizer.dLocalize("snippets.adapt_menu.no_knowledge")) : (C.RED + "(" + Localizer.dLocalize("snippets.adapt_menu.you_only_have") + " " + C.WHITE + k + C.RED + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_available") + ")"))))
            .addLore(mylevel < lvl && adaptation.getPlayer(player).getData().hasPowerAvailable(pc) ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets.adapt_menu.power_drain") : mylevel >= lvl ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets.adapt_menu.power_drain") : C.RED + Localizer.dLocalize("snippets.adapt_menu.not_enough_power") + "\n" + C.RED + Localizer.dLocalize("snippets.adapt_menu.how_to_level_up"))
            .addLore((adaptation.isPermanent() ? C.RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.may_not_unlearn") : ""))
            .addLore(adaptation.isPermanent() && mylevel < lvl
                ? (pendingPermanentConfirm
                ? C.GOLD + "" + C.BOLD + "Click again now to confirm permanent learn."
                : C.YELLOW + "Double-click required to confirm permanent learn.")
                : "")
            .onLeftClick((e) -> {
              AdaptPlayer adaptPlayer = adaptation.getPlayer(player);
              PlayerSkillLine skillLine = adaptPlayer.getSkillLine(adaptation.getSkill().getName());
              if (skillLine == null) {
                spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                return;
              }

              int delayTicks = AdaptConfig.get().getLearnUnlearnButtonDelayTicks();
              int currentLevel = skillLine.getAdaptationLevel(adaptation.getName());
              if (currentLevel >= lvl) {
                adaptation.unlearn(player, lvl, false);
                int updatedLevel = skillLine.getAdaptationLevel(adaptation.getName());
                if (updatedLevel < currentLevel) {
                  spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                  if (delayTicks != 0) {
                    player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets.adapt_menu.unlearned") + " " + adaptation.getDisplayName(currentLevel), 1, 10, 11);
                  }
                  closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
                  return;
                }

                spw.play(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 0.5f, 1.355f);
                if (delayTicks != 0) {
                  player.sendTitle(" ", C.RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.may_not_unlearn") + " " + adaptation.getDisplayName(currentLevel), 1, 10, 11);
                }
                J.s(() -> openAdaptationPage(adaptation, player, currentPage), delayTicks);
                return;
              }

              long currentKnowledge = skillLine.getKnowledge();
              if (currentKnowledge >= c && adaptPlayer.getData().hasPowerAvailable(pc)) {
                if (adaptation.isPermanent() && !consumePermanentLearnConfirmation(player, adaptation, lvl)) {
                  spw.play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.85f);
                  player.sendTitle(" ", C.GOLD + "" + C.BOLD + "Click again to confirm permanent learn", 1, 16, 8);
                  J.s(() -> openAdaptationPage(adaptation, player, currentPage), 1);
                  return;
                }

                if (skillLine.spendKnowledge(c)) {
                  skillLine.setAdaptation(adaptation, lvl);
                  spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                  spw.play(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                  if (adaptation.isPermanent()) {
                    spw.play(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.355f);
                    spw.play(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.7f, 1.355f);
                  }
                  if (delayTicks != 0) {
                    player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets.adapt_menu.learned") + " " + adaptation.getDisplayName(lvl), 1, 5, 11);
                  }
                  closeAndReopenAfterLevelChange(adaptation, player, currentPage, delayTicks);
                } else {
                  spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
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
      if (currentPage > 0) {
        w.setElement(-4, navRow, new UIElement("adapt-prev")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + "Previous")
            .addLore(C.GRAY + "Right click: jump -" + jumpPages + " pages")
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, currentPage - 1))
            .onRightClick((e) -> openAdaptationPage(adaptation, player, jumpBack)));
        w.setElement(-3, navRow, new UIElement("adapt-first")
            .setMaterial(new MaterialBlock(Material.LECTERN))
            .setName(C.GRAY + "First")
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, 0)));
      }
      if (currentPage < plan.pageCount() - 1) {
        w.setElement(4, navRow, new UIElement("adapt-next")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName(C.WHITE + "Next")
            .addLore(C.GRAY + "Right click: jump +" + jumpPages + " pages")
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, currentPage + 1))
            .onRightClick((e) -> openAdaptationPage(adaptation, player, jumpForward)));
        w.setElement(3, navRow, new UIElement("adapt-last")
            .setMaterial(new MaterialBlock(Material.LECTERN))
            .setName(C.GRAY + "Last")
            .onLeftClick((e) -> openAdaptationPage(adaptation, player, plan.pageCount() - 1)));
      }

      int from = adaptation.getMaxLevel() <= 0 ? 0 : (start + 1);
      int to = adaptation.getMaxLevel() <= 0 ? 0 : end;
      w.setElement(-1, navRow, new UIElement("adapt-page-info")
          .setMaterial(new MaterialBlock(Material.PAPER))
          .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + plan.pageCount())
          .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + adaptation.getMaxLevel())
          .setProgress(1D));

      if (AdaptConfig.get().isGuiBackButton()) {
        w.setElement(0, navRow, new UIElement("back")
            .setMaterial(new MaterialBlock(Material.ARROW))
            .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets.gui.back"))
            .onLeftClick((e) -> navigateBack(adaptation, player)));
      }

    }

    w.setTitle(adaptation.getDisplayName());
    w.onClosed((vv) -> J.s(() -> onGuiClosed(adaptation, player, !AdaptConfig.get().isEscClosesAllGuis())));
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
    if (adaptation.isPermanent() && !force) {
      return;
    }
    int myLevel = adaptation.getPlayer(player).getSkillLine(adaptation.getSkill().getName()).getAdaptationLevel(adaptation.getName());
    int rc = adaptation.getRefundCostFor(lvl - 1, myLevel);
    if (!AdaptConfig.get().isHardcoreNoRefunds()) {
      adaptation.getPlayer(player).getData().getSkillLine(adaptation.getSkill().getName()).giveKnowledge(rc);
    }
    adaptation.getPlayer(player).getData().getSkillLine(adaptation.getSkill().getName()).setAdaptation(adaptation, lvl - 1);
  }

  static void learn(Adaptation<?> adaptation, Player player, int lvl, boolean force) {
    int myLevel = adaptation.getPlayer(player).getSkillLine(adaptation.getSkill().getName()).getAdaptationLevel(adaptation.getName());
    int c = adaptation.getCostFor(lvl, myLevel);
    if (adaptation.getPlayer(player).getData().hasPowerAvailable(c) || force) {
      if (adaptation.getPlayer(player).getData().getSkillLine(adaptation.getSkill().getName()).spendKnowledge(c) || force) {
        adaptation.getPlayer(player).getData().getSkillLine(adaptation.getSkill().getName()).setAdaptation(adaptation, lvl);
      }
    }
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

  private static void openAdaptationPage(Adaptation<?> adaptation, Player player, int page) {
    suppressClose(player);
    openGui(adaptation, player, page);
  }

  private static void closeAndReopenAfterLevelChange(Adaptation<?> adaptation, Player player, int page, int delayTicks) {
    closeCurrentAdaptationGui(player);
    int reopenDelay = Math.max(0, delayTicks);
    J.s(() -> reopenAdaptationPageIfReady(adaptation, player, page), reopenDelay);
  }

  private static void closeCurrentAdaptationGui(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }

    suppressClose(player);
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
    suppressClose(player);
    adaptation.getSkill().openGui(player);
  }

  private static void onGuiClosed(Adaptation<?> adaptation, Player player, boolean openPrevGui) {
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
          adaptation.getSkill().openGui(player);
        }
      }, 1);
    } else {
      Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
    }
  }

  private static void playCloseSound(Player player) {
    SoundPlayer spw = SoundPlayer.of(player.getWorld());
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
    spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
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

  private static String permanentConfirmPrefix(Player player, Adaptation<?> adaptation) {
    return player.getUniqueId() + "|" + adaptation.getName() + "|";
  }

  private static String permanentConfirmKey(Player player, Adaptation<?> adaptation, int level) {
    return permanentConfirmPrefix(player, adaptation) + level;
  }
}
