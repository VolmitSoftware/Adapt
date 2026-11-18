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
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingArcaneSiphon extends SimpleAdaptation<EnchantingArcaneSiphon.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BOOKS = CommonPreferences.toggle("books", EnchantingMessages.PREFERENCE_ENCHANTINGARCANESIPHON_BOOKS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BONUS_XP = CommonPreferences.toggle("bonus-xp", EnchantingMessages.PREFERENCE_ENCHANTINGARCANESIPHON_BONUS_XP, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PLAYER_VICTIMS = CommonPreferences.toggle("player-victims", EnchantingMessages.PREFERENCE_ENCHANTINGARCANESIPHON_PLAYER_VICTIMS, CommonPreferences.Toggle.ON);

  private static final Id AIR = Id.minecraft("air");
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  public EnchantingArcaneSiphon() {
    super("enchanting-arcane-siphon");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_lantern"));
    setInterval(2600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("soul_lantern"))
        .key("challenge_enchanting_siphon_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_enchanting_siphon_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_siphon_25", "enchanting.arcane-siphon.books-siphoned", 25, 400);
    registerMilestone("challenge_enchanting_siphon_250", "enchanting.arcane-siphon.books-siphoned", 250, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BOOKS, BONUS_XP, PLAYER_VICTIMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDropChance(level), 0), 1);
    statLore(v, getQualityBonus(level), 2);
  }

  static double siphonDropChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int siphonQualityBonus(double factor, double levelPercent) {
    return (int) Math.floor(levelPercent * factor);
  }

  private double getDropChance(int level) {
    return siphonDropChance(getConfig().dropChanceBase, getConfig().dropChanceFactor, getConfig().maxDropChance, getLevelPercent(level));
  }

  private int getQualityBonus(int level) {
    return siphonQualityBonus(getConfig().qualityFactor, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef victim = e.entity();
    PlayerRef killer = e.killer();
    if (killer == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }

    int level = getActiveDamageLevel(killer, victim);
    if (level <= 0) {
      return;
    }
    if (!isEligibleVictim(victim instanceof PlayerRef, level, getConfig().maxLevel)
        || victim instanceof PlayerRef && !preferenceEnabled(killer, PLAYER_VICTIMS)) {
      return;
    }

    Map<Id, Integer> gearEnchants = collectGearEnchants(victim);
    if (gearEnchants.isEmpty()) {
      return;
    }

    int enchantCount = gearEnchants.size();
    if (preferenceEnabled(killer, BONUS_XP)) {
      xp(killer, getConfig().bonusXpPerEnchant * enchantCount);
    }

    if (ThreadLocalRandom.current().nextDouble() > getDropChance(level) || !preferenceEnabled(killer, BOOKS)) {
      return;
    }

    ItemRef book = makeSiphonBook(gearEnchants, getQualityBonus(level));
    if (book == null) {
      return;
    }

    e.drops().add(book);
    addStat(killer, "enchanting.arcane-siphon.books-siphoned", 1);
    siphonFx(victim.location().add(0, 1.0D, 0));
  }

  static boolean isEligibleVictim(boolean playerVictim, int activeLevel, int maxLevel) {
    return activeLevel > 0 && (!playerVictim || activeLevel >= Math.max(1, maxLevel));
  }

  static Map<Id, Integer> collectGearEnchants(LivingRef entity) {
    Map<Id, Integer> enchants = new HashMap<>();
    if (entity == null) {
      return enchants;
    }

    accumulate(enchants, entity.equipment(EquipmentSlot.HEAD));
    accumulate(enchants, entity.equipment(EquipmentSlot.CHEST));
    accumulate(enchants, entity.equipment(EquipmentSlot.LEGS));
    accumulate(enchants, entity.equipment(EquipmentSlot.FEET));
    accumulate(enchants, entity.equipment(EquipmentSlot.MAIN_HAND));
    accumulate(enchants, entity.equipment(EquipmentSlot.OFF_HAND));
    return enchants;
  }

  private static void accumulate(Map<Id, Integer> enchants, ItemRef item) {
    if (item == null || item.amount() <= 0 || item.isEmpty() || item.type().equals(AIR)) {
      return;
    }

    mergeHighest(enchants, item.enchantments());
  }

  static <T> void mergeHighest(Map<T, Integer> destination, Map<T, Integer> source) {
    for (Map.Entry<T, Integer> entry : source.entrySet()) {
      destination.merge(entry.getKey(), entry.getValue(), Math::max);
    }
  }

  private ItemRef makeSiphonBook(Map<Id, Integer> pool, int qualityBonus) {
    if (pool.isEmpty()) {
      return null;
    }

    List<Map.Entry<Id, Integer>> entries = new ArrayList<>(pool.entrySet());
    Map.Entry<Id, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    ItemRef book = AdaptCore.platform().host().items().create(ENCHANTED_BOOK);
    int level = Math.max(1, Math.min(picked.getValue() + qualityBonus, AdaptCore.platform().host().registries().enchantmentMaxLevel(picked.getKey())));
    book.setStoredEnchantment(picked.getKey(), level);
    return book;
  }

  private void siphonFx(Loc location) {
    fx(location, FxPriority.COMBAT)
        .helix(Particles.ENCHANTMENT_TABLE, 0.6D, 1.4D, 4, 0)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.4D, 0, 0.3D, 0.05D)
        .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.3F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
  }


  @ConfigDescription("Killing entities in enchanted gear grants bonus XP and can siphon a book of their enchantments; player victims require max level.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Drop Chance for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDropChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Quality Factor for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double qualityFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Per Enchant for the Enchanting Arcane Siphon adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpPerEnchant = 12;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
