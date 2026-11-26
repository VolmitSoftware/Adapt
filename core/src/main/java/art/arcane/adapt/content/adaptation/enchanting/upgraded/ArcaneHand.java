package art.arcane.adapt.content.adaptation.enchanting.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBooks;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingCurseCleansing;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstones;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Quick-Click Enchant, Infusion Transfer and Tome Rebinding fused, all done in the inventory: left-click a book onto
 * gear to apply it, right-click enchanted gear onto other gear to move its best enchantment, and drop a multi-enchant
 * book while sneaking to split it. Applying and moving both respect vanilla conflicts and the power limit. At a
 * grindstone it can also pull one enchantment of the ground item onto a plain book from the inventory.
 */
public class ArcaneHand extends SkillUpgrade<ArcaneHand.Config> {
  private static final Id BOOK = Id.minecraft("book");
  private final EnchantingGrindstones grindstones = new EnchantingGrindstones(this, playerState());

  public ArcaneHand() {
    super("upgraded-enchanting-arcane-hand");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("enchanted_book"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getPowerLimit(level), 1);
    statLore(v, C.YELLOW, "* ", getMoveCost(level), 2);
    statLore(v, C.YELLOW, "* ", getSplitCost(level), 3);
    statLore(v, Form.pc(getLibraryChance(level), 0), 4);
    statLore(v, C.YELLOW, "* ", getLibraryCost(level), 5);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.viewKind() == InventoryKind.GRINDSTONE && e.rawSlot() == EnchantingGrindstones.RESULT_SLOT) {
      library(e);
      return;
    }
    if (e.action() != InventoryAction.SWAP_WITH_CURSOR
        || (e.slotKind() != SlotKind.CONTAINER && e.slotKind() != SlotKind.ARMOR && e.slotKind() != SlotKind.QUICKBAR)) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef current = e.currentItem();
    ItemRef cursor = e.cursor();
    if (e.click() == ClickKind.LEFT && EnchantingBooks.isBookApplication(current, cursor)) {
      apply(e, p, level, current, cursor);
    } else if (e.click() == ClickKind.RIGHT && isMove(current, cursor)) {
      move(e, p, level, current, cursor);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareResultEvent e) {
    ItemRef result = e.result();
    if (e.kind() != InventoryKind.GRINDSTONE || result == null || result.isEmpty()) {
      return;
    }
    ItemRef cleared = result.copy();
    if (EnchantingGrindstones.clearLibraryMarks(cleared)) {
      e.setResult(cleared);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    ItemEntityRef dropped = e.itemDrop();
    ItemRef book = dropped.item();
    if (!p.isSneaking() || !EnchantingBooks.isMultiEnchantBook(book)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int cost = getSplitCost(level);
    if (p.level() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    List<ItemRef> books = EnchantingBooks.splitBook(book, 0D);
    if (!payExperienceCost(p, "experience-levels", cost, () -> EnchantingBooks.spendLevels(p, cost))) {
      return;
    }

    EnchantingBooks.dropSplit(p, dropped, books);
    addStat(p, "enchanting.arcane-hand.books-split", 1);
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(
        EnchantingMessages.TOME_REBINDING_MESSAGE,
        trusted("count", books.size())
    ));
    worked(p);
  }

  private void apply(InventoryClickEvent e, PlayerRef p, int level, ItemRef current, ItemRef cursor) {
    e.setCancelled(true);
    ItemRef item = current.copy();
    ItemRef book = cursor.copy();
    if (book.storedEnchantments().isEmpty()) {
      return;
    }

    EnchantingBooks.Merge merge = EnchantingBooks.merge(item, book.storedEnchantments());
    if (merge.power() > getPowerLimit(level)) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
          EnchantingMessages.QUICK_ENCHANT_LIMIT,
          trusted("power", getPowerLimit(level))
      ));
      FxPresets.failFizzle(this, p);
      return;
    }

    if (!merge.changed()) {
      return;
    }

    EnchantingBooks.writeEnchantments(item, merge.merged());
    EnchantingGrindstones.carryLibraryMarks(item, merge.added().keySet().stream()
        .filter(EnchantingGrindstones.libraryEnchants(book)::contains).collect(Collectors.toSet()));
    e.setCurrentItem(item);
    EnchantingBooks.settleCursor(e, book, merge.leftover());
    EnchantingBooks.showAdded(p, merge.added());
    addStat(p, "enchanting.arcane-hand.books-applied", 1);
    worked(p);
  }

  private void move(InventoryClickEvent e, PlayerRef p, int level, ItemRef current, ItemRef cursor) {
    Id transfer = EnchantingBooks.pickTransferEnchant(current, cursor);
    if (transfer == null) {
      return;
    }

    e.setCancelled(true);
    int enchantLevel = EnchantingBooks.transferLevel(cursor, transfer);
    int power = enchantLevel + current.enchantments().values().stream().mapToInt((Integer i) -> i).sum();
    if (power > getPowerLimit(level)) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
          EnchantingMessages.QUICK_ENCHANT_LIMIT,
          trusted("power", getPowerLimit(level))
      ));
      FxPresets.failFizzle(this, p);
      return;
    }

    int cost = getMoveCost(level);
    if (p.level() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    if (!payExperienceCost(p, "experience-levels", cost, () -> EnchantingBooks.spendLevels(p, cost))) {
      return;
    }

    ItemRef target = current.copy();
    target.setEnchantment(transfer, enchantLevel);
    if (EnchantingGrindstones.libraryEnchants(cursor).contains(transfer)) {
      EnchantingGrindstones.carryLibraryMarks(target, Set.of(transfer));
    }
    e.setCurrentItem(target);
    e.setCursor(EnchantingBooks.reduceSource(cursor, transfer));
    EnchantingBooks.showAdded(p, Map.of(transfer, enchantLevel));
    addStat(p, "enchanting.arcane-hand.enchantments-moved", 1);
    worked(p);
  }

  private void library(InventoryClickEvent e) {
    PlayerRef p = e.player();
    EnchantingGrindstones.Pull pull = EnchantingGrindstones.pull(e, getActiveLevel(p));
    if (pull == null) {
      return;
    }
    Id pulled = libraryPick(pull.source());
    int cost = getLibraryCost(pull.level());
    if (pulled == null) {
      return;
    }
    if (!p.inventory().contains(BOOK, 1) || p.level() < cost) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(EnchantingMessages.ARCANE_HAND_LIBRARY_NEEDS, trusted("levels", cost)));
      return;
    }
    if (ThreadLocalRandom.current().nextDouble() >= getLibraryChance(pull.level())) {
      return;
    }
    int enchantLevel = pull.source().enchantmentLevel(pulled);
    grindstones.commitNextTick(p, pull, (EnchantingGrindstones.Pull done) -> pullToBook(p, done, pulled, enchantLevel));
  }

  private void pullToBook(PlayerRef p, EnchantingGrindstones.Pull pull, Id pulled, int enchantLevel) {
    int cost = getLibraryCost(pull.level());
    ItemRef book = EnchantingGrindstones.book(pulled, enchantLevel);
    if (book == null || !p.inventory().contains(BOOK, 1) || p.level() < cost) {
      return;
    }
    if (!payItemCost(p, "book", AdaptCore.platform().host().items().create(BOOK), 1, () -> p.inventory().removeItem(BOOK, 1) == 1)) {
      return;
    }
    if (!payExperienceCost(p, "experience-levels", cost, () -> EnchantingBooks.spendLevels(p, cost))) {
      EnchantingGrindstones.deliver(p, AdaptCore.platform().host().items().create(BOOK));
      return;
    }
    EnchantingGrindstones.markLibraryBook(book, pulled);
    EnchantingGrindstones.deliver(p, book);
    p.setCooldown(EnchantingGrindstones.GRINDSTONE, Math.max(0, getConfig().libraryCooldownTicks));
    EnchantingBooks.showAdded(p, book.storedEnchantments());
    addStat(p, "enchanting.arcane-hand.books-pulled", 1);
    worked(p);
  }

  /** The ground item's highest enchantment that is not a curse and was not itself taken from a library book. */
  static @Nullable Id libraryPick(ItemRef source) {
    Set<Id> taken = EnchantingGrindstones.libraryEnchants(source);
    Id best = null;
    int bestLevel = 0;
    for (Map.Entry<Id, Integer> entry : new ArrayList<>(source.enchantments().entrySet())) {
      Id enchant = entry.getKey();
      if (taken.contains(enchant) || EnchantingCurseCleansing.isCurse(enchant)) {
        continue;
      }
      if (entry.getValue() > bestLevel) {
        best = enchant;
        bestLevel = entry.getValue();
      }
    }
    return best;
  }

  private void worked(PlayerRef p) {
    cue(p, MutationCue.WORK);
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.2D, 0, 0.4D, 0.05D)
        .particle(Particles.CRIT_MAGIC, 3, 0, 0.2D, 0, 0.2D, 0.03D);
  }

  private static boolean isMove(ItemRef current, ItemRef cursor) {
    return EnchantingBooks.isTransferBase(current)
        && EnchantingBooks.isTransferBase(cursor)
        && current.amount() == 1
        && cursor.amount() == 1
        && !cursor.enchantments().isEmpty();
  }

  int getPowerLimit(int level) {
    return (int) Math.round(getConfig().powerLimitBase + (level * getConfig().powerLimitPerLevel));
  }

  int getMoveCost(int level) {
    return Math.max(getConfig().minimumLevelCost, (int) Math.round(getConfig().moveCostBase - (level * getConfig().costReductionPerLevel)));
  }

  double getLibraryChance(int level) {
    return Math.max(0D, Math.min(1D, getConfig().libraryChanceBase + (level * getConfig().libraryChancePerLevel)));
  }

  int getLibraryCost(int level) {
    return Math.max(getConfig().minimumLevelCost, (int) Math.round(getConfig().libraryCostBase - (level * getConfig().costReductionPerLevel)));
  }

  int getSplitCost(int level) {
    return Math.max(getConfig().minimumLevelCost, (int) Math.round(getConfig().splitCostBase - (level * getConfig().costReductionPerLevel)));
  }

  @ConfigDescription("Apply books, move enchantments between items and split books from your inventory, with no anvil.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Total enchantment levels an item may hold after a book is applied or an enchantment is moved, before level scaling.", impact = "Higher values let more books stack onto one item.")
    double powerLimitBase = 6;
    @ConfigDoc(value = "Enchantment level limit added per upgrade level.", impact = "Higher values raise the limit faster.")
    double powerLimitPerLevel = 3;
    @ConfigDoc(value = "Experience levels a move costs before level scaling.", impact = "Higher values make moving enchantments dearer.")
    double moveCostBase = 4;
    @ConfigDoc(value = "Experience levels a split costs before level scaling.", impact = "Higher values make splitting books dearer.")
    double splitCostBase = 3;
    @ConfigDoc(value = "Experience levels removed from both costs per upgrade level.", impact = "Higher values make higher levels cheaper.")
    double costReductionPerLevel = 1;
    @ConfigDoc(value = "Lowest experience-level cost of a move or split.", impact = "Zero makes top-level moves and splits free.")
    int minimumLevelCost = 1;
    @ConfigDoc(value = "Chance a grind pulls one enchantment onto a book, before level scaling.", impact = "Higher values make the library more reliable.")
    double libraryChanceBase = 0.2;
    @ConfigDoc(value = "Library chance added per upgrade level.", impact = "Higher values raise the chance faster.")
    double libraryChancePerLevel = 0.2;
    @ConfigDoc(value = "Experience levels a library book costs before level scaling.", impact = "Higher values make pulling enchantments dearer.")
    double libraryCostBase = 4;
    @ConfigDoc(value = "Grindstone cooldown after a library book, in ticks.", impact = "Higher values space out library pulls.")
    int libraryCooldownTicks = 100;
  }
}
