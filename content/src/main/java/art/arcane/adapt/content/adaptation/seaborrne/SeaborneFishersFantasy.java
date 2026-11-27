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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.concurrent.ThreadLocalRandom;

public class SeaborneFishersFantasy extends SimpleAdaptation<SeaborneFishersFantasy.Config> {
  public static final Id ID = Id.adapt("seaborne/fishers_fantasy");
  private final Cooldowns rewardCooldown = cooldowns();

  public SeaborneFishersFantasy() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.fishers_fantasy");
    setIcon(Id.minecraft("fishing_rod"));
    setInterval(8080);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fishing_rod"))
        .key("challenge_seaborne_fish_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tropical_fish"))
            .key("challenge_seaborne_fish_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_fish_500", "seaborne.fishers-fantasy.fish-caught", 500, 300);
    registerMilestone("challenge_seaborne_fish_5k", "seaborne.fishers-fantasy.fish-caught", 5000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + Text.key("adapt.seaborn.fishers_fantasy.lore1").resolve());
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
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

        double chance = SeaborneCatches.bonusChance(level, config.maxLevel, config.bonusChanceAtLevelOne, config.bonusChanceAtMaxLevel);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
          return;
        }

        rewardCooldown.mark(p.uuid(), config.cooldownMillis);
        SeaborneCatches.award(p, SeaborneCatches.rewardXp(level, config.vanillaXpAtLevelOne,
            config.vanillaXpPerAdditionalLevel, config.maximumVanillaXpPerCatch));
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

  @ConfigDescription("Fishing can grant one bounded bonus item, vanilla XP, and Seaborne XP.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Chance of one bonus reward bundle at adaptation level one.", impact = "Higher values make low-level bonus catches more common.")
    double bonusChanceAtLevelOne = 0.10;
    @ConfigDoc(value = "Chance of one bonus reward bundle at maximum adaptation level.", impact = "Higher values make max-level bonus catches more common.")
    double bonusChanceAtMaxLevel = 0.35;
    @ConfigDoc(value = "Vanilla XP points granted by a level-one successful bonus catch.", impact = "Higher values increase the base fishing reward.")
    int vanillaXpAtLevelOne = 2;
    @ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the fishing reward scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 1;
    @ConfigDoc(value = "Maximum vanilla XP points granted by one catch.", impact = "Caps oversized or heavily scaled fishing rewards.")
    int maximumVanillaXpPerCatch = 16;
    @ConfigDoc(value = "Seaborne skill XP granted after a successful bonus catch.", impact = "Higher values accelerate Seaborne progression through fishing bonuses.")
    double skillXpOnSuccess = 8D;
    @ConfigDoc(value = "Minimum milliseconds between successful bonus reward bundles for the same player.", impact = "Higher values limit unusually rapid fishing reward sources.")
    long cooldownMillis = 5000L;

    public Config() {
      initialCost = 2;
      baseCost = 3;
      costFactor = 0.6;
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
