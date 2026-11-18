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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.brewing.BrewingAbsorption;
import art.arcane.adapt.content.adaptation.brewing.BrewingBlindness;
import art.arcane.adapt.content.adaptation.brewing.BrewingDarkness;
import art.arcane.adapt.content.adaptation.brewing.BrewingDecay;
import art.arcane.adapt.content.adaptation.brewing.BrewingFatigue;
import art.arcane.adapt.content.adaptation.brewing.BrewingHaste;
import art.arcane.adapt.content.adaptation.brewing.BrewingHealthBoost;
import art.arcane.adapt.content.adaptation.brewing.BrewingHunger;
import art.arcane.adapt.content.adaptation.brewing.BrewingLingering;
import art.arcane.adapt.content.adaptation.brewing.BrewingNausea;
import art.arcane.adapt.content.adaptation.brewing.BrewingResistance;
import art.arcane.adapt.content.adaptation.brewing.BrewingSaturation;
import art.arcane.adapt.content.adaptation.brewing.BrewingSuperHeated;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.Optional;
import java.util.Set;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final Set<Id> EXCLUDED_BASE_POTIONS = Set.of(Id.minecraft("water"), Id.minecraft("mundane"),
      Id.minecraft("thick"), Id.minecraft("awkward"));
  private final Cooldowns cooldowns = cooldowns();

  public SkillBrewing() {
    super("brewing", SkillPresentation.of(SkillMessages.BREWING_NAME, SkillMessages.BREWING_ICON, SkillMessages.BREWING_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.LIGHT_PURPLE);
    setInterval(5851);
    setIcon(Id.minecraft("lingering_potion"));
    registerAdaptation(new BrewingLingering()); // Features
    registerAdaptation(new BrewingSuperHeated());
    registerAdaptation(new BrewingAbsorption()); // Brews
    registerAdaptation(new BrewingBlindness());
    registerAdaptation(new BrewingDarkness());
    registerAdaptation(new BrewingDecay());
    registerAdaptation(new BrewingFatigue());
    registerAdaptation(new BrewingHaste());
    registerAdaptation(new BrewingHealthBoost());
    registerAdaptation(new BrewingHunger());
    registerAdaptation(new BrewingNausea());
    registerAdaptation(new BrewingResistance());
    registerAdaptation(new BrewingSaturation());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("potion")).key("challenge_brew_1k")
        .model(CustomModel.get(Id.minecraft("potion"), "advancement", "brewing", "challenge_brew_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("potion"))
            .key("challenge_brew_5k")
            .model(CustomModel.get(Id.minecraft("potion"), "advancement", "brewing", "challenge_brew_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_1k", "brewing.consumed", 1000, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_5k", "brewing.consumed", 5000, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("splash_potion")).key("challenge_brewsplash_1k")
        .model(CustomModel.get(Id.minecraft("splash_potion"), "advancement", "brewing", "brewsplash_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("splash_potion"))
            .key("challenge_brewsplash_5k")
            .model(CustomModel.get(Id.minecraft("splash_potion"), "advancement", "brewing", "brewsplash_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewsplash_1k", "brewing.splashes", 1000, () -> getConfig().challengeBrewSplash1k);
    registerMilestone("challenge_brewsplash_5k", "brewing.splashes", 5000, () -> getConfig().challengeBrewSplash1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brewing_stand")).key("challenge_brew_stands_10")
        .model(CustomModel.get(Id.minecraft("brewing_stand"), "advancement", "brewing", "challenge_brew_stands_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blaze_rod"))
            .key("challenge_brew_stands_50")
            .model(CustomModel.get(Id.minecraft("blaze_rod"), "advancement", "brewing", "challenge_brew_stands_50"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_stands_10", "brewing.stands.placed", 10, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_stands_50", "brewing.stands.placed", 50, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glowstone_dust")).key("challenge_brew_strong_25")
        .model(CustomModel.get(Id.minecraft("glowstone_dust"), "advancement", "brewing", "challenge_brew_strong_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_breath"))
            .key("challenge_brew_strong_250")
            .model(CustomModel.get(Id.minecraft("dragon_breath"), "advancement", "brewing", "challenge_brew_strong_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_strong_25", "brewing.strong", 25, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_strong_250", "brewing.strong", 250, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("splash_potion")).key("challenge_brew_splash_hits_50")
        .model(CustomModel.get(Id.minecraft("splash_potion"), "advancement", "brewing", "challenge_brew_splash_hits_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lingering_potion"))
            .key("challenge_brew_splash_hits_500")
            .model(CustomModel.get(Id.minecraft("lingering_potion"), "advancement", "brewing", "challenge_brew_splash_hits_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_splash_hits_50", "brewing.splash.hits", 50, () -> getConfig().challengeBrewSplash1k);
    registerMilestone("challenge_brew_splash_hits_500", "brewing.splash.hits", 500, () -> getConfig().challengeBrewSplash1k * 2);
  }

  private void handleCooldown(PlayerRef p, Runnable runnable) {
    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.uuid());
    runnable.run();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      Optional<PotionContents> found = e.item().potionContents();
      if (found.isEmpty()) {
        return;
      }
      PotionContents potion = found.get();

      Id baseType = potion.potion();
      if (isBasePotionExcluded(baseType)) {
        return;
      }

      boolean upgraded = isUpgraded(baseType);
      addStat(p, "brewing.consumed", 1);
      if (upgraded) {
        addStat(p, "brewing.strong", 1);
      }

      double customEffectPower = sumPotionEffects(potion.customEffects());
      double upgradeBonus = upgraded ? 50 : 25;
      handleCooldown(p, () -> {
        xp(p, p.location(),
            getConfig().splashXP
                + (getConfig().splashMultiplier * customEffectPower)
                + (getConfig().splashMultiplier * upgradeBonus));
        Rgb tint = potion.color();
        fx(p.location().add(0.0D, 1.0D, 0.0D), FxPriority.AMBIENT)
            .dustBurst(tint, 5, 0.3D, 1.0F)
            .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.4F);
      });
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (e.potion().shooter() instanceof PlayerRef p) {
      shouldReturnForPlayer(p, e, () -> {
        Optional<PotionContents> found = e.item().potionContents();
        if (found.isEmpty()) {
          return;
        }
        PotionContents potion = found.get();
        if (isBasePotionExcluded(potion.potion()) && potion.customEffects().isEmpty()) {
          return;
        }
        AdaptPlayer a = getPlayer(p);
        addStat(p, "brewing.splashes", 1);
        addStat(p, "brewing.splash.hits", e.affected().size());
        double effectPower = sumPotionEffects(e.effects());
        handleCooldown(p, () -> {
          Loc loc = e.potion().location();
          xp(a.getPlayer(), loc, getConfig().splashXP + (getConfig().splashMultiplier * effectPower));
          Rgb tint = potion.color();
          timeline(loc)
              .duration(4)
              .priority(FxPriority.TRANSITION)
              .cullRadius(24.0D)
              .frame((f, tick, progress) -> {
                f.dustRing(tint, 0.2D + (2.0D * progress), 16, 1.1F);
                if (tick == 0) {
                  f.sound(Sounds.BLOCK_GLASS_BREAK, 0.4F, 1.5F);
                }
              })
              .start();
        });
      });
    }
  }

  private boolean isBasePotionExcluded(Id baseType) {
    return baseType == null || EXCLUDED_BASE_POTIONS.contains(baseType);
  }

  private static boolean isUpgraded(Id baseType) {
    return baseType != null && baseType.path().startsWith("strong_");
  }

  private double sumPotionEffects(Iterable<EffectInstance> effects) {
    double sum = 0D;
    for (EffectInstance effect : effects) {
      sum += (effect.amplifier() + 1) * (effect.durationTicks() / 20D);
    }
    return sum;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> {
      if (e.block().type().equals(BREWING_STAND)) {
        worldData().setBlockOwner(e.block(), e.player().uuid());
        getPlayer(e.player()).getData().addStat("brewing.stands.placed", 1);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryOpenEvent e) {
    if (e.kind() != InventoryKind.BREWING) {
      return;
    }

    BlockRef block = e.holderBlock();
    if (block == null) return;
    if (!block.type().equals(BREWING_STAND)) return;

    PlayerRef player = e.player();
    shouldReturnForPlayer(player, e, () -> {
      WorldDataPort data = worldData();
      if (data.blockOwner(block) != null) return;
      data.setBlockOwner(block, player.uuid());
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> {
      if (!e.block().type().equals(BREWING_STAND)) {
        return;
      }
      worldData().setBlockOwner(e.block(), null);
    });
  }

  private static WorldDataPort worldData() {
    return AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&d";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Brew1k for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBrew1k = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Brew Splash1k for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBrewSplash1k = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Splash XP for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double splashXP = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 2500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Splash Multiplier for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double splashMultiplier = 0.4;
  }
}
