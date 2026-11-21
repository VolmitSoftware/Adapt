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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SeaborneFishersFantasy extends SimpleAdaptation<SeaborneFishersFantasy.Config> {
  private static final List<Id> FISHING_DROPS = fishingDrops();

  private final Cooldowns rewardCooldown = cooldowns();

  public SeaborneFishersFantasy() {
    super("seaborne-fishers-fantasy");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.fishers_fantasy");
    setIcon(Id.minecraft("fishing_rod"));
    setInterval(8080);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fishing_rod"))
        .key("challenge_seaborne_fish_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tropical_fish"))
            .key("challenge_seaborne_fish_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_fish_500", "seaborne.fishers-fantasy.fish-caught", 500, 300);
    registerMilestone("challenge_seaborne_fish_5k", "seaborne.fishers-fantasy.fish-caught", 5000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(SeabornMessages.FISHERS_FANTASY_LORE1));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerFishEvent e) {
    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> {
      if (e.state() == PlayerFishEvent.State.CAUGHT_FISH) {
        addStat(p, "seaborne.fishers-fantasy.fish-caught", 1);
        int level = getActiveLevel(p);
        Config config = getConfig();
        if (!rewardCooldown.isReady(p.uuid(), config.cooldownMillis)) {
          return;
        }

        double chance = bonusChance(level, config.maxLevel, config.bonusChanceAtLevelOne, config.bonusChanceAtMaxLevel);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
          return;
        }

        rewardCooldown.mark(p.uuid(), config.cooldownMillis);
        Id drop = FISHING_DROPS.get(ThreadLocalRandom.current().nextInt(FISHING_DROPS.size()));
        ItemRef item = AdaptCore.platform().host().items().create(drop, 1);
        p.world().dropItemNaturally(p.location().position(), item);
        int vanillaXp = rewardXp(level, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumVanillaXpPerCatch);
        if (vanillaXp > 0) {
          p.world().spawnExperience(p.location().position(), vanillaXp);
        }
        if (config.skillXpOnSuccess > 0D) {
          xp(p, config.skillXpOnSuccess);
        }

        float pitch = (float) Math.min(2.0D, 1.2D + (0.08D * level));
        float chimePitch = (float) Math.min(2.0D, 1.5D + (0.05D * level));
        fx(p.location().add(0D, 0.5D, 0D), FxPriority.TRANSITION)
            .particle(Particles.SPLASH, 6, 0D, 0.3D, 0D, 0.25D, 0.05D)
            .particle(Particles.GLOW, 4, 0D, 0.4D, 0D, 0.3D, 0D)
            .dustBurst(3, 0.3D, 0.9F)
            .chord(Sounds.BLOCK_CONDUIT_ACTIVATE, 0.4F, pitch, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, chimePitch);
      }
    });
  }

  static double bonusChance(int level, int maxLevel, double atLevelOne, double atMaxLevel) {
    if (maxLevel <= 1) {
      return Math.max(0D, Math.min(1D, atMaxLevel));
    }
    double progress = (double) Math.max(0, Math.min(level, maxLevel) - 1) / (double) (maxLevel - 1);
    double chance = atLevelOne + ((atMaxLevel - atLevelOne) * progress);
    return Math.max(0D, Math.min(1D, chance));
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  private static List<Id> fishingDrops() {
    List<Id> drops = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      drops.addAll(Collections.nCopies(6, Id.minecraft("cod")));
      drops.addAll(Collections.nCopies(4, Id.minecraft("salmon")));
      drops.add(Id.minecraft("pufferfish"));
      drops.add(Id.minecraft("tropical_fish"));
    }
    for (String path : List.of("bow", "fishing_rod", "name_tag", "nautilus_shell", "saddle",
        "lily_pad", "bowl", "leather", "stick", "rotten_flesh", "string", "glass_bottle", "bone", "ink_sac", "tripwire_hook")) {
      drops.add(Id.minecraft(path));
    }
    return List.copyOf(drops);
  }

  @ConfigDescription("Fishing can grant one bounded bonus item, vanilla XP, and Seaborne XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance of one bonus reward bundle at adaptation level one.", impact = "Higher values make low-level bonus catches more common.")
    double bonusChanceAtLevelOne = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance of one bonus reward bundle at maximum adaptation level.", impact = "Higher values make max-level bonus catches more common.")
    double bonusChanceAtMaxLevel = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points granted by a level-one successful bonus catch.", impact = "Higher values increase the base fishing reward.")
    int vanillaXpAtLevelOne = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the fishing reward scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum vanilla XP points granted by one catch.", impact = "Caps oversized or heavily scaled fishing rewards.")
    int maximumVanillaXpPerCatch = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Seaborne skill XP granted after a successful bonus catch.", impact = "Higher values accelerate Seaborne progression through fishing bonuses.")
    double skillXpOnSuccess = 8D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between successful bonus reward bundles for the same player.", impact = "Higher values limit unusually rapid fishing reward sources.")
    long cooldownMillis = 5000L;

    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      maxLevel = 7;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      bonusChanceAtLevelOne = clampFinite(bonusChanceAtLevelOne, 0D, 1D);
      bonusChanceAtMaxLevel = Math.max(bonusChanceAtLevelOne, clampFinite(bonusChanceAtMaxLevel, 0D, 1D));
      vanillaXpAtLevelOne = Math.max(0, Math.min(vanillaXpAtLevelOne, 100000));
      vanillaXpPerAdditionalLevel = Math.max(0, Math.min(vanillaXpPerAdditionalLevel, 100000));
      maximumVanillaXpPerCatch = Math.max(0, Math.min(maximumVanillaXpPerCatch, 100000));
      skillXpOnSuccess = clampFinite(skillXpOnSuccess, 0D, 100000D);
      cooldownMillis = Math.max(0L, Math.min(cooldownMillis, 3600000L));
    }

    private static double clampFinite(double value, double minimum, double maximum) {
      if (!Double.isFinite(value)) {
        return minimum;
      }
      return Math.max(minimum, Math.min(value, maximum));
    }
  }
}
