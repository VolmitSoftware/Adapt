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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.world.BlockOwners;
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
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingBoon;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingFightStrike;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingFuel;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingState;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingTrigger;
import art.arcane.adapt.content.adaptation.brewing.blocks.BrewingWorkStrike;
import art.arcane.adapt.content.adaptation.brewing.upgraded.Apothecary;
import art.arcane.adapt.content.adaptation.brewing.upgraded.MasterDistiller;
import art.arcane.adapt.content.adaptation.brewing.upgraded.WitchsShelf;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
  public static final Id ID = Id.adapt("brewing");
  private final XpSource drink = xpSource("drink", Text.key("adapt.xp_guide.brewing.drink"));
  private final XpSource splash = xpSource("splash", Text.key("adapt.xp_guide.brewing.splash"));
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final Set<Id> EXCLUDED_BASE_POTIONS = Set.of(Id.minecraft("water"), Id.minecraft("mundane"),
      Id.minecraft("thick"), Id.minecraft("awkward"));
  private final Cooldowns cooldowns = cooldowns();

  public SkillBrewing() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.brewing.plain"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFF55FF));
    setInterval(5851);
    setIcon(Id.minecraft("lingering_potion"));
    registerAdaptation(BrewingLingering.ID, BrewingLingering::new); // Features
    registerAdaptation(BrewingSuperHeated.ID, BrewingSuperHeated::new);
    registerAdaptation(BrewingAbsorption.ID, BrewingAbsorption::new); // Brews
    registerAdaptation(BrewingBlindness.ID, BrewingBlindness::new);
    registerAdaptation(BrewingDarkness.ID, BrewingDarkness::new);
    registerAdaptation(BrewingDecay.ID, BrewingDecay::new);
    registerAdaptation(BrewingFatigue.ID, BrewingFatigue::new);
    registerAdaptation(BrewingHaste.ID, BrewingHaste::new);
    registerAdaptation(BrewingHealthBoost.ID, BrewingHealthBoost::new);
    registerAdaptation(BrewingHunger.ID, BrewingHunger::new);
    registerAdaptation(BrewingNausea.ID, BrewingNausea::new);
    registerAdaptation(BrewingResistance.ID, BrewingResistance::new);
    registerAdaptation(BrewingSaturation.ID, BrewingSaturation::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.brewing"), Text.key("adapt.mutated.adjective.brewing"))
        .trigger(TriggerKind.WORK, BrewingTrigger::new)
        .strikes(BrewingFightStrike::new, BrewingWorkStrike::new)
        .boon(BrewingBoon::new)
        .fuel(BrewingFuel::new)
        .state(BrewingState::new)
        .toolUpgrade(MasterDistiller.ID, MasterDistiller::new, List.of(BrewingLingering.ID, BrewingSuperHeated.ID), Set.of())
        .upgrade(WitchsShelf.ID, WitchsShelf::new,
            List.of(BrewingBlindness.ID, BrewingDarkness.ID, BrewingDecay.ID, BrewingFatigue.ID, BrewingHunger.ID, BrewingNausea.ID), Set.of())
        .upgrade(Apothecary.ID, Apothecary::new,
            List.of(BrewingAbsorption.ID, BrewingHealthBoost.ID, BrewingResistance.ID, BrewingSaturation.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.splash_potion.break"), Id.minecraft("item.bottle.fill"),
            Id.minecraft("entity.generic.drink"), Id.minecraft("block.bubble_column.bubble_pop")))
        .tint(Rgb.of(0xd8a8e8))
        // The Alchemist's Bandolier picks its potion.
        .reserve(BarterGesture.SNEAK_SCROLL)
        .build());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("potion")).key("challenge_brew_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("potion"))
            .key("challenge_brew_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_1k", "brewing.consumed", 1000, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_5k", "brewing.consumed", 5000, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("splash_potion")).key("challenge_brewsplash_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("splash_potion"))
            .key("challenge_brewsplash_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brewsplash_1k", "brewing.splashes", 1000, () -> getConfig().challengeBrewSplash1k);
    registerMilestone("challenge_brewsplash_5k", "brewing.splashes", 5000, () -> getConfig().challengeBrewSplash1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brewing_stand")).key("challenge_brew_stands_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blaze_rod"))
            .key("challenge_brew_stands_50")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_stands_10", "brewing.stands.placed", 10, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_stands_50", "brewing.stands.placed", 50, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glowstone_dust")).key("challenge_brew_strong_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_breath"))
            .key("challenge_brew_strong_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_brew_strong_25", "brewing.strong", 25, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_strong_250", "brewing.strong", 250, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("splash_potion")).key("challenge_brew_splash_hits_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lingering_potion"))
            .key("challenge_brew_splash_hits_500")
            .frame(AdvancementFrame.CHALLENGE)
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
        drink.award(p, p.location(),
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
        if (!isBrewed(potion)) {
          return;
        }
        PlayerProfile a = getPlayer(p);
        addStat(p, "brewing.splashes", 1);
        addStat(p, "brewing.splash.hits", e.affected().size());
        double effectPower = sumPotionEffects(e.effects());
        handleCooldown(p, () -> {
          Loc loc = e.potion().location();
          splash.award(a.player(), loc, getConfig().splashXP + (getConfig().splashMultiplier * effectPower));
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

  /** A potion with an effect: a brewed base type or custom effects, not water, mundane, thick or awkward. */
  public static boolean isBrewed(PotionContents potion) {
    return !isBasePotionExcluded(potion.potion()) || !potion.customEffects().isEmpty();
  }

  private static boolean isBasePotionExcluded(Id baseType) {
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
        BlockOwners.setOwner(e.block(), e.player().uuid());
        addStat(e.player(), "brewing.stands.placed", 1);
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
      if (BlockOwners.owner(block).isPresent()) return;
      BlockOwners.setOwner(block, player.uuid());
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> {
      if (!e.block().type().equals(BREWING_STAND)) {
        return;
      }
      BlockOwners.clearOwner(e.block());
    });
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Challenge Brew1k for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBrew1k = 1000;
    @ConfigDoc(value = "Controls Challenge Brew Splash1k for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBrewSplash1k = 1000;
    @ConfigDoc(value = "Controls Splash XP for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double splashXP = 100;
    @ConfigDoc(value = "Controls Cooldown Delay for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 2500;
    @ConfigDoc(value = "Controls Splash Multiplier for the Brewing skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double splashMultiplier = 0.4;

    public Config() {
    }
  }
}
