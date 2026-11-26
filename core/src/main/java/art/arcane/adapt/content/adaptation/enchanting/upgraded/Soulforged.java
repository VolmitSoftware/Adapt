package art.arcane.adapt.content.adaptation.enchanting.upgraded;

import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingAscension;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBooks;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingCurseCleansing;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingEchoes;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingSoulVault;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Soul Link, Echo of Knowledge and Curse Cleansing fused: gear linked at an anvil survives death, collected XP charges
 * it until one of its enchantments rises a level, and it loses its curses when linked and as it grows. An enchantment
 * it grew to its vanilla maximum can ascend one level past it at an enchanting table; that level holds only while the
 * player carrying the item has this upgrade.
 */
public class Soulforged extends SkillUpgrade<Soulforged.Config> {
  private final EnchantingSoulVault vault = new EnchantingSoulVault(this, new EnchantingSoulVault.Feedback() {
    @Override
    public void refused(Loc at) {
      FxPresets.failFizzle(Soulforged.this, at);
    }

    @Override
    public void linked(PlayerRef p) {
      cue(p, MutationCue.WORK);
      glow(p);
    }

    @Override
    public void saved(PlayerRef p) {
      addStat(p, "enchanting.soulforged.items-saved", 1);
    }

    @Override
    public void returned(PlayerRef p) {
      cue(p, MutationCue.BOON);
      glow(p);
    }
  });

  private static final Id ENCHANTING_TABLE = Id.minecraft("enchanting_table");
  private final AdaptationOwnerPulse.Registration settling;

  public Soulforged() {
    super("upgraded-enchanting-soulforged");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    settling = AdaptationOwnerPulse.register(this, () -> Math.max(250L, getConfig().settleIntervalMillis), () -> true,
        (UUID ignored) -> true, this::settleInventory);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "* ", getSaveCost(level), 1);
    statLore(v, Form.f(getChargeRate(level), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getRelinkCooldownMillis(level), 1), 3);
    if (getConfig().ascension) {
      statLore(v, getAscensions(level), 4);
      statLore(v, C.YELLOW, "* ", getAscensionCost(level), 5);
    }
  }

  @Override
  public void unregister() {
    settling.unregister();
    vault.clear();
    super.unregister();
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.action() == InteractAction.LEFT_CLICK_BLOCK && e.hand() == Hand.MAIN_HAND) {
      ascend(e);
      return;
    }
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    BlockRef block = e.clickedBlock();
    if (!p.isSneaking() || block == null || !block.type().equals(EnchantingSoulVault.ANVIL) || e.useBlock() == UseResult.DENY) {
      return;
    }

    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || !EnchantingSoulVault.isEnchanted(p.inventory().mainHand())) {
      return;
    }

    e.setCancelled(true);
    if (!vault.link(p, block.location().add(0.5D, 1.0D, 0.5D), getRelinkCooldownMillis(level), () -> true)) {
      return;
    }

    ItemRef linked = p.inventory().mainHand();
    if (EnchantingCurseCleansing.hasStrippableCurse(linked)) {
      int curses = EnchantingCurseCleansing.countCurses(linked);
      p.inventory().setMainHand(EnchantingCurseCleansing.stripCurses(linked));
      shed(p, curses);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveDeathLevel(p);
    if (level > 0) {
      vault.capture(e, p, getSaveCost(level), 0);
    }
    List<ItemRef> drops = e.drops();
    for (int index = 0; index < drops.size(); index++) {
      ItemRef drop = drops.get(index);
      if (EnchantingAscension.hasAscension(drop)) {
        ItemRef dormant = drop.copy();
        if (EnchantingAscension.settle(dormant, false)) {
          drops.set(index, dormant);
        }
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDropItemEvent e) {
    ItemEntityRef dropped = e.itemDrop();
    ItemRef item = dropped.item();
    if (EnchantingAscension.hasAscension(item)) {
      ItemRef dormant = item.copy();
      if (EnchantingAscension.settle(dormant, false)) {
        dropped.setItem(dormant);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    InventoryRef top = e.player().openMenuInventory();
    if (top != null) {
      settle(top, 0);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareResultEvent e) {
    ItemRef result = e.result();
    if (result == null || result.isEmpty()) {
      return;
    }
    ItemRef settled = result.copy();
    boolean changed = e.kind() == InventoryKind.GRINDSTONE
        ? EnchantingAscension.strip(settled)
        : EnchantingAscension.clampForeign(settled);
    if (changed) {
      e.setResult(settled);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    vault.restorePending(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    settleInventory(p);
    Tasks.runEntity(p, () -> vault.restorePending(p), 20);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    vault.forget(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef p = e.player();
    if (e.amount() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int slot = vault.linkedSlot(p);
    if (slot < 0) {
      return;
    }

    ItemRef linked = p.inventory().item(slot);
    int curses = EnchantingCurseCleansing.hasStrippableCurse(linked) ? EnchantingCurseCleansing.countCurses(linked) : 0;
    ItemRef grown = curses > 0 ? EnchantingCurseCleansing.stripCurses(linked) : linked.copy();
    int gain = Math.max(1, (int) Math.round(e.amount() * getChargeRate(level)));
    EnchantingEchoes.Charge charge = EnchantingEchoes.charge(grown, gain, Math.max(1, getConfig().chargeThreshold));
    if (!charge.changed() && curses <= 0) {
      return;
    }

    if (charge.upgraded() != null) {
      EnchantingAscension.markGrown(grown, charge.upgraded());
    }
    p.inventory().setItem(slot, grown);
    if (curses > 0) {
      shed(p, curses);
    }
    if (charge.upgraded() != null) {
      addStat(p, "enchanting.soulforged.levels-grown", 1);
      EnchantingEchoes.announce(p, charge.upgraded());
      cue(p, MutationCue.BOON);
      glow(p);
    }
  }

  private void ascend(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    BlockRef block = e.clickedBlock();
    if (!getConfig().ascension || !p.isSneaking() || block == null || !block.type().equals(ENCHANTING_TABLE)) {
      return;
    }
    int level = getActiveLevel(p);
    ItemRef held = p.inventory().mainHand();
    if (level <= 0 || !vault.isLinked(p, held)) {
      return;
    }
    Id enchant = EnchantingAscension.ascended(held).size() < getAscensions(level)
        ? EnchantingAscension.candidate(held, getConfig().ascendable) : null;
    if (enchant == null) {
      return;
    }
    e.setCancelled(true);
    int cost = getAscensionCost(level);
    if (p.level() < cost) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(EnchantingMessages.SOULFORGED_ASCEND_LEVELS, trusted("levels", cost)));
      FxPresets.failFizzle(this, block.location().add(0.5D, 1.0D, 0.5D));
      return;
    }
    if (!payExperienceCost(p, "experience-levels", cost, () -> EnchantingBooks.spendLevels(p, cost))) {
      return;
    }
    ItemRef ascended = held.copy();
    EnchantingAscension.ascend(ascended, enchant);
    p.inventory().setMainHand(ascended);
    addStat(p, "enchanting.soulforged.ascensions", 1);
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOULFORGED_ASCENDED) + " "
        + enchant.path().replace('_', ' '));
    cue(p, MutationCue.BOON);
    glow(p);
  }

  /**
   * Wakes ascended gear for a player holding this upgrade at a level that allows as many ascensions as the item has,
   * and puts it to sleep for everyone else.
   */
  void settleInventory(PlayerRef p) {
    int level = getConfig().ascension ? getActiveLevel(p) : 0;
    settle(p.inventory(), level > 0 ? getAscensions(level) : 0);
  }

  private static void settle(InventoryRef inventory, int ascensions) {
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef item = inventory.item(slot);
      if (EnchantingAscension.hasAscension(item)) {
        ItemRef settled = item.copy();
        boolean awake = EnchantingAscension.ascended(item).size() <= ascensions;
        if (EnchantingAscension.settle(settled, awake)) {
          inventory.setItem(slot, settled);
        }
      }
    }
  }

  private void shed(PlayerRef p, int curses) {
    addStat(p, "enchanting.soulforged.curses-shed", curses);
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 8, 0.35D)
        .particle(Particles.END_ROD, 4, 0, 0.3D, 0, 0.2D, 0.03D);
  }

  private void glow(PlayerRef p) {
    fx(p.location().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
        .ring(Particles.TOTEM, 0.8D, 12, 0.6D)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.6D, 0, 0.4D, 0.05D);
  }

  int getSaveCost(int level) {
    return Math.max(getConfig().minimumSaveCost, (int) Math.round(getConfig().saveCostBase - (level * getConfig().saveCostReductionPerLevel)));
  }

  double getChargeRate(int level) {
    return getConfig().chargeRateBase + (level * getConfig().chargeRatePerLevel);
  }

  int getAscensions(int level) {
    return Math.max(0, (int) Math.floor(getConfig().ascensionsBase + (level * getConfig().ascensionsPerLevel)));
  }

  int getAscensionCost(int level) {
    return Math.max(0, (int) Math.round(getConfig().ascensionCostBase - (level * getConfig().ascensionCostReductionPerLevel)));
  }

  long getRelinkCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumRelinkCooldownMillis,
        getConfig().relinkCooldownMillisBase - (level * getConfig().relinkCooldownMillisReductionPerLevel));
  }

  @ConfigDescription("Gear linked at an anvil survives death, grows with collected XP and sheds its curses.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Experience levels it costs to keep the linked item through a death, before level scaling.", impact = "Higher values make each save dearer.")
    double saveCostBase = 6;
    @ConfigDoc(value = "Experience levels removed from the save cost per level.", impact = "Higher values make higher levels cheaper.")
    double saveCostReductionPerLevel = 2;
    @ConfigDoc(value = "Lowest save cost in experience levels.", impact = "Zero lets the top level save for free.")
    int minimumSaveCost = 1;
    @ConfigDoc(value = "Charge gained per vanilla XP point before level scaling.", impact = "Higher values grow linked gear faster.")
    double chargeRateBase = 2;
    @ConfigDoc(value = "Charge rate added per level.", impact = "Higher values widen the gap between levels.")
    double chargeRatePerLevel = 1;
    @ConfigDoc(value = "Charge needed to raise one enchantment a level.", impact = "Higher values make growth slower.")
    int chargeThreshold = 120;
    @ConfigDoc(value = "Time before another item can be linked, before level scaling, in milliseconds.", impact = "Higher values make relinking rarer.")
    double relinkCooldownMillisBase = 30000;
    @ConfigDoc(value = "Relink cooldown removed per level, in milliseconds.", impact = "Higher values allow faster relinks at higher levels.")
    double relinkCooldownMillisReductionPerLevel = 8000;
    @ConfigDoc(value = "Shortest relink cooldown, in milliseconds.", impact = "Lower values allow more frequent relinks.")
    double minimumRelinkCooldownMillis = 5000;
    @ConfigDoc(value = "Whether linked gear can ascend an enchantment past its vanilla maximum.", impact = "False removes Ascension and puts every ascended item to sleep.")
    boolean ascension = true;
    @ConfigDoc(value = "Enchantments that may ascend.", impact = "Remove an id to keep it at its vanilla maximum. Single-level enchantments never ascend.")
    List<String> ascendable = List.of("minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
        "minecraft:efficiency", "minecraft:unbreaking", "minecraft:fortune", "minecraft:looting", "minecraft:power",
        "minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection",
        "minecraft:projectile_protection", "minecraft:feather_falling");
    @ConfigDoc(value = "Ascended enchantments one item may hold, before level scaling.", impact = "Higher values allow more ascensions per item.")
    double ascensionsBase = 0.5;
    @ConfigDoc(value = "Ascended enchantments added per level.", impact = "Higher values add ascensions sooner.")
    double ascensionsPerLevel = 0.5;
    @ConfigDoc(value = "Experience levels one ascension costs, before level scaling.", impact = "Higher values make ascending dearer.")
    double ascensionCostBase = 35;
    @ConfigDoc(value = "Experience levels removed from the ascension cost per level.", impact = "Higher values make higher levels cheaper.")
    double ascensionCostReductionPerLevel = 5;
    @ConfigDoc(value = "How often each online player's ascended gear is woken or put to sleep, in milliseconds.", impact = "Lower values react faster to gear changing hands.")
    long settleIntervalMillis = 1000;
  }
}
