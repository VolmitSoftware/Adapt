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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.adaptation.enchanting.upgraded.ArcaneHand;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingGrindstoneRecovery extends SimpleAdaptation<EnchantingGrindstoneRecovery.Config> {
  public static final Id ID = Id.adapt("enchanting/grindstone_recovery");
  public static final PlayerPreference<CommonPreferences.Toggle> BOOKS = CommonPreferences.toggle("books", Text.key("adapt.enchanting.preferences.enchantinggrindstonerecovery.books"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BONUS_XP = CommonPreferences.toggle("bonus-xp", Text.key("adapt.enchanting.preferences.enchantinggrindstonerecovery.bonus-xp"), CommonPreferences.Toggle.ON);
  private final EnchantingGrindstones grindstones = new EnchantingGrindstones(this, playerState());

  public EnchantingGrindstoneRecovery() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
    setInterval(1700);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("grindstone"))
        .key("challenge_enchanting_grindstone_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("grindstone"))
            .key("challenge_enchanting_grindstone_500")
            .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, Format.pc(getRecoverChance(level), 0), 1);
    statLore(v, Format.f(getBonusXp(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (level <= 0 || !preferenceEnabled(p, BOOKS) && !preferenceEnabled(p, BONUS_XP) || libraryRuns(p)) {
      return;
    }

    EnchantingGrindstones.Pull pull = EnchantingGrindstones.pull(e, level);
    if (pull == null || ThreadLocalRandom.current().nextDouble() >= getRecoverChance(level)) {
      return;
    }

    Map<Id, Integer> recoverable = new LinkedHashMap<>(pull.source().enchantments());
    recoverable.keySet().removeAll(EnchantingGrindstones.libraryEnchants(pull.source()));
    recoverable.keySet().removeIf(EnchantingCurseCleansing::isCurse);
    ItemRef recovered = makeBook(recoverable);
    if (recovered != null) {
      grindstones.commitNextTick(p, pull, (EnchantingGrindstones.Pull done) -> commitRecovery(p, done, recovered));
    }
  }

  /** Arcane Hand's grindstone library takes over the grind for its holders. */
  private boolean libraryRuns(PlayerRef p) {
    if (getSkill() == null) {
      return false;
    }
    for (Adaptation<?> upgrade : getSkill().getUpgrades()) {
      if (upgrade instanceof ArcaneHand && upgrade.getActiveLevel(p) > 0) {
        return true;
      }
    }
    return false;
  }

  private void commitRecovery(PlayerRef p, EnchantingGrindstones.Pull pull, ItemRef book) {
    if (preferenceEnabled(p, BOOKS)) {
      EnchantingGrindstones.deliver(p, book);
    }
    int xp = Math.max(0, (int) Math.round(getBonusXp(pull.level())));
    if (xp > 0 && preferenceEnabled(p, BONUS_XP)) {
      giveExp(p, xp);
    }

    int cooldownTicks = getCooldownTicks(pull.level());
    p.setCooldown(EnchantingGrindstones.GRINDSTONE, cooldownTicks);
    HudStatus.cooldown(p, this, cooldownTicks * 50L);
    recoverySuccessFx(p, pull.level());
    xp(p, getConfig().skillXpOnRecovery);
    addStat(p, "enchanting.grindstone-recovery.enchants-recovered", 1);
  }

  private void recoverySuccessFx(PlayerRef p, int level) {
    int rods = 2 + (int) Math.round(getLevelPercent(level) * 3.0D);
    boolean bright = getLevelPercent(level) > 0.66D;
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANT, (0.5D * (1.0D - progress)) + 0.2D, 1.6D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT, 3, 0, 1.0D, 0, 0.3D, 0.05D)
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

  private ItemRef makeBook(Map<Id, Integer> source) {
    if (source.isEmpty()) {
      return null;
    }

    List<Map.Entry<Id, Integer>> entries = new ArrayList<>(source.entrySet());
    Map.Entry<Id, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    return EnchantingGrindstones.book(picked.getKey(), picked.getValue());
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


  @ConfigDescription("Using a grindstone can recover one removed enchant on a book with bonus XP.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Recover Chance Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceBase = 0.10;
    @ConfigDoc(value = "Controls Recover Chance Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double recoverChanceFactor = 0.25;
    @ConfigDoc(value = "Controls Max Recover Chance for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRecoverChance = 0.35;
    @ConfigDoc(value = "Controls Bonus Xp Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpBase = 1;
    @ConfigDoc(value = "Controls Bonus Xp Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusXpFactor = 4;
    @ConfigDoc(value = "Maximum vanilla XP points granted by one successful recovery.", impact = "Caps oversized or heavily scaled recovery rewards.")
    double maximumBonusXp = 8;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 200;
    @ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 80;
    @ConfigDoc(value = "Minimum grindstone cooldown in ticks after a successful recovery.", impact = "Higher values place a firmer limit on repeated recoveries.")
    int minimumCooldownTicks = 40;
    @ConfigDoc(value = "Controls Skill Xp On Recovery for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnRecovery = 8;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
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
