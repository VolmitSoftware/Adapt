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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingGrindstoneRecovery extends SimpleAdaptation<EnchantingGrindstoneRecovery.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BOOKS = CommonPreferences.toggle("books", EnchantingMessages.PREFERENCE_ENCHANTINGGRINDSTONERECOVERY_BOOKS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BONUS_XP = CommonPreferences.toggle("bonus-xp", EnchantingMessages.PREFERENCE_ENCHANTINGGRINDSTONERECOVERY_BONUS_XP, CommonPreferences.Toggle.ON);
  private static final Id GRINDSTONE = Id.minecraft("grindstone");
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");
  private final Map<UUID, PendingRecovery> pendingRecoveries = playerState();

  public EnchantingGrindstoneRecovery() {
    super("enchanting-grindstone-recovery");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
    setInterval(1700);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("grindstone"))
        .key("challenge_enchanting_grindstone_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("grindstone"))
            .key("challenge_enchanting_grindstone_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_grindstone_50", "enchanting.grindstone-recovery.enchants-recovered", 50, 300);
    registerMilestone("challenge_enchanting_grindstone_500", "enchanting.grindstone-recovery.enchants-recovered", 500, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BOOKS, BONUS_XP);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRecoverChance(level), 0), 1);
    statLore(v, Form.f(getBonusXp(level), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    if (e.rawSlot() != 2 || e.viewKind() != InventoryKind.GRINDSTONE) {
      return;
    }

    if (!isItem(e.currentItem()) || e.action() == InventoryAction.NOTHING
        || e.action() == InventoryAction.CLONE_STACK) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || p.hasCooldown(GRINDSTONE)
        || !preferenceEnabled(p, BOOKS) && !preferenceEnabled(p, BONUS_XP)) {
      return;
    }

    InventoryRef top = p.openMenuInventory();
    if (top == null) {
      return;
    }
    ItemRef source = getEnchantedSource(top.item(0), top.item(1));
    if (source == null) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() >= getRecoverChance(level)) {
      return;
    }

    ItemRef recovered = makeBook(source.enchantments());
    if (recovered == null) {
      return;
    }

    PendingRecovery pending = new PendingRecovery(e, top, p.openMenuLocation(), recovered, level);
    UUID playerId = p.uuid();
    pendingRecoveries.put(playerId, pending);
    Tasks.runEntity(p, () -> commitRecovery(p, pending), 1);
  }

  private void commitRecovery(PlayerRef p, PendingRecovery pending) {
    if (!pendingRecoveries.remove(p.uuid(), pending) || !p.isOnline()
        || getActiveLevel(p) <= 0 || p.hasCooldown(GRINDSTONE)
        || !pending.inventory().equals(p.openMenuInventory())
        || (Tasks.isFoliaThreading() && pending.location() != null
        && !Tasks.isOwnedByCurrentRegion(pending.location()))
        || !committedRemoval(pending.event(), pending.inventory())) {
      return;
    }

    if (preferenceEnabled(p, BOOKS)) {
      List<ItemRef> overflow = p.inventory().addItem(pending.book());
      overflow.forEach(item -> p.world().dropItemNaturally(p.location().position(), item));
    }
    int xp = Math.max(0, (int) Math.round(getBonusXp(pending.level())));
    if (xp > 0 && preferenceEnabled(p, BONUS_XP)) {
      giveExp(p, xp);
    }

    p.setCooldown(GRINDSTONE, getCooldownTicks(pending.level()));
    recoverySuccessFx(p, pending.level());
    xp(p, getConfig().skillXpOnRecovery);
    addStat(p, "enchanting.grindstone-recovery.enchants-recovered", 1);
  }

  static boolean committedRemoval(InventoryClickEvent event, InventoryRef inventory) {
    return !event.isCancelled() && isEmpty(inventory.item(0))
        && isEmpty(inventory.item(1)) && isEmpty(inventory.item(2));
  }

  private static boolean isEmpty(ItemRef item) {
    if (item == null || item.amount() <= 0) {
      return true;
    }
    return item.type().isAir();
  }

  private void recoverySuccessFx(PlayerRef p, int level) {
    int rods = 2 + (int) Math.round(getLevelPercent(level) * 3.0D);
    boolean bright = getLevelPercent(level) > 0.66D;
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.5D * (1.0D - progress)) + 0.2D, 1.6D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT_MAGIC, 3, 0, 1.0D, 0, 0.3D, 0.05D)
                .sound(Sounds.BLOCK_GRINDSTONE_USE, 0.95F, 1.15F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, rods, 1.4D)
                .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.35F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.45F);
            if (bright) {
              f.particle(Particles.FLASH, 1, 0, 1.0D, 0, 0, 0);
            }
          }
        })
        .start();
  }

  private ItemRef getEnchantedSource(ItemRef a, ItemRef b) {
    if (isEnchanted(a)) {
      return a;
    }

    if (isEnchanted(b)) {
      return b;
    }

    return null;
  }

  private boolean isEnchanted(ItemRef item) {
    return isItem(item) && !item.enchantments().isEmpty();
  }

  private ItemRef makeBook(Map<Id, Integer> source) {
    if (source.isEmpty()) {
      return null;
    }

    List<Map.Entry<Id, Integer>> entries = new ArrayList<>(source.entrySet());
    Map.Entry<Id, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    ItemRef book = AdaptCore.platform().host().items().create(ENCHANTED_BOOK);
    if (book.isEmpty()) {
      return null;
    }

    int safeLevel = Math.max(1, Math.min(picked.getValue(), AdaptCore.platform().host().registries().enchantmentMaxLevel(picked.getKey())));
    book.setStoredEnchantment(picked.getKey(), safeLevel);
    return book;
  }

  private double getRecoverChance(int level) {
    return recoverChance(getLevelPercent(level), getConfig().recoverChanceBase, getConfig().recoverChanceFactor, getConfig().maxRecoverChance);
  }

  private double getBonusXp(int level) {
    return bonusXp(getLevelPercent(level), getConfig().bonusXpBase, getConfig().bonusXpFactor, getConfig().maximumBonusXp);
  }

  private int getCooldownTicks(int level) {
    return cooldownTicks(getLevelPercent(level), getConfig().cooldownTicksBase, getConfig().cooldownTicksFactor, getConfig().minimumCooldownTicks);
  }

  static double recoverChance(double levelPercent, double base, double factor, double maximum) {
    return Math.max(0D, Math.min(Math.max(0D, maximum), Math.max(0D, base) + (Math.max(0D, levelPercent) * Math.max(0D, factor))));
  }

  static double bonusXp(double levelPercent, double base, double factor, double maximum) {
    return Math.max(0D, Math.min(Math.max(0D, maximum), Math.max(0D, base) + (Math.max(0D, levelPercent) * Math.max(0D, factor))));
  }

  static int cooldownTicks(double levelPercent, double base, double factor, int minimum) {
    double scaled = Math.max(0D, base) - (Math.max(0D, levelPercent) * Math.max(0D, factor));
    return Math.max(Math.max(0, minimum), (int) Math.round(Math.max(0D, Math.min(Integer.MAX_VALUE, scaled))));
  }


  private record PendingRecovery(InventoryClickEvent event, InventoryRef inventory, Loc location, ItemRef book, int level) {
  }

  @ConfigDescription("Using a grindstone can recover one removed enchant on a book with bonus XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Recover Chance for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRecoverChance = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum vanilla XP points granted by one successful recovery.", impact = "Caps oversized or heavily scaled recovery rewards.")
    double maximumBonusXp = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum grindstone cooldown in ticks after a successful recovery.", impact = "Higher values place a firmer limit on repeated recoveries.")
    int minimumCooldownTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Recovery for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnRecovery = 8;

    public Config() {
      costFactor = 0.74;
      initialCost = 4;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      recoverChanceBase = clampFinite(recoverChanceBase, 0D, 1D);
      recoverChanceFactor = clampFinite(recoverChanceFactor, 0D, 1D);
      maxRecoverChance = clampFinite(maxRecoverChance, 0D, 1D);
      bonusXpBase = clampFinite(bonusXpBase, 0D, 100000D);
      bonusXpFactor = clampFinite(bonusXpFactor, 0D, 100000D);
      maximumBonusXp = clampFinite(maximumBonusXp, 0D, 100000D);
      cooldownTicksBase = clampFinite(cooldownTicksBase, 0D, 72000D);
      cooldownTicksFactor = clampFinite(cooldownTicksFactor, 0D, 72000D);
      minimumCooldownTicks = Math.max(0, Math.min(minimumCooldownTicks, 72000));
      skillXpOnRecovery = clampFinite(skillXpOnRecovery, 0D, 100000D);
    }

    private static double clampFinite(double value, double minimum, double maximum) {
      if (!Double.isFinite(value)) {
        return minimum;
      }
      return Math.max(minimum, Math.min(value, maximum));
    }
  }
}
