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

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
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
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.content.matter.BrewingStandOwnerMatter;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.spatial.matter.SpatialMatter;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public SkillBrewing() {
    super("brewing", SkillPresentation.of(SkillMessages.BREWING_NAME, SkillMessages.BREWING_ICON, SkillMessages.BREWING_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.LIGHT_PURPLE);
    setInterval(5851);
    setIcon(Material.LINGERING_POTION);
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
        .icon(Material.POTION).key("challenge_brew_1k")
        .model(CustomModel.get(Material.POTION, "advancement", "brewing", "challenge_brew_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
            .icon(Material.POTION)
            .key("challenge_brew_5k")
            .model(CustomModel.get(Material.POTION, "advancement", "brewing", "challenge_brew_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brew_1k", "brewing.consumed", 1000, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_5k", "brewing.consumed", 5000, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPLASH_POTION).key("challenge_brewsplash_1k")
        .model(CustomModel.get(Material.SPLASH_POTION, "advancement", "brewing", "brewsplash_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
            .icon(Material.SPLASH_POTION)
            .key("challenge_brewsplash_5k")
            .model(CustomModel.get(Material.SPLASH_POTION, "advancement", "brewing", "brewsplash_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brewsplash_1k", "brewing.splashes", 1000, () -> getConfig().challengeBrewSplash1k);
    registerMilestone("challenge_brewsplash_5k", "brewing.splashes", 5000, () -> getConfig().challengeBrewSplash1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREWING_STAND).key("challenge_brew_stands_10")
        .model(CustomModel.get(Material.BREWING_STAND, "advancement", "brewing", "challenge_brew_stands_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAZE_ROD)
            .key("challenge_brew_stands_50")
            .model(CustomModel.get(Material.BLAZE_ROD, "advancement", "brewing", "challenge_brew_stands_50"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brew_stands_10", "brewing.stands.placed", 10, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_stands_50", "brewing.stands.placed", 50, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLOWSTONE_DUST).key("challenge_brew_strong_25")
        .model(CustomModel.get(Material.GLOWSTONE_DUST, "advancement", "brewing", "challenge_brew_strong_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_BREATH)
            .key("challenge_brew_strong_250")
            .model(CustomModel.get(Material.DRAGON_BREATH, "advancement", "brewing", "challenge_brew_strong_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brew_strong_25", "brewing.strong", 25, () -> getConfig().challengeBrew1k);
    registerMilestone("challenge_brew_strong_250", "brewing.strong", 250, () -> getConfig().challengeBrew1k * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPLASH_POTION).key("challenge_brew_splash_hits_50")
        .model(CustomModel.get(Material.SPLASH_POTION, "advancement", "brewing", "challenge_brew_splash_hits_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LINGERING_POTION)
            .key("challenge_brew_splash_hits_500")
            .model(CustomModel.get(Material.LINGERING_POTION, "advancement", "brewing", "challenge_brew_splash_hits_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_brew_splash_hits_50", "brewing.splash.hits", 50, () -> getConfig().challengeBrewSplash1k);
    registerMilestone("challenge_brew_splash_hits_500", "brewing.splash.hits", 500, () -> getConfig().challengeBrewSplash1k * 2);

    SpatialMatter.registerSliceType(new BrewingStandOwnerMatter());
  }

  private void handleCooldown(Player p, Runnable runnable) {
    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.getUniqueId());
    runnable.run();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> {
      if (!(e.getItem().getItemMeta() instanceof PotionMeta potionMeta)) {
        return;
      }

      PotionType baseType = potionMeta.getBasePotionType();
      if (isBasePotionExcluded(baseType)) {
        return;
      }

      addStat(p, "brewing.consumed", 1);
      if (potionMeta.getBasePotionData().isUpgraded()) {
        addStat(p, "brewing.strong", 1);
      }

      double customEffectPower = sumPotionEffects(potionMeta.getCustomEffects());
      double upgradeBonus = potionMeta.getBasePotionData().isUpgraded() ? 50 : 25;
      handleCooldown(p, () -> {
        xp(p, p.getLocation(),
            getConfig().splashXP
                + (getConfig().splashMultiplier * customEffectPower)
                + (getConfig().splashMultiplier * upgradeBonus));
        Color tint = potionMeta.hasColor() ? potionMeta.getColor() : null;
        fx(p.getLocation().add(0.0D, 1.0D, 0.0D), FxPriority.AMBIENT)
            .dustBurst(tint, 5, 0.3D, 1.0F)
            .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.4F);
      });
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (e.getPotion().getShooter() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> {
        if (!(e.getPotion().getItem().getItemMeta() instanceof PotionMeta potionMeta)) {
          return;
        }
        if (isBasePotionExcluded(potionMeta.getBasePotionType()) && !potionMeta.hasCustomEffects()) {
          return;
        }
        AdaptPlayer a = getPlayer(p);
        addStat(p, "brewing.splashes", 1);
        addStat(p, "brewing.splash.hits", e.getAffectedEntities().size());
        double effectPower = sumPotionEffects(e.getPotion().getEffects());
        handleCooldown(p, () -> {
          xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().splashXP + (getConfig().splashMultiplier * effectPower));
          Color tint = potionMeta.hasColor() ? potionMeta.getColor() : null;
          Location loc = e.getEntity().getLocation();
          timeline(loc)
              .duration(4)
              .priority(FxPriority.TRANSITION)
              .cullRadius(24.0D)
              .frame((f, tick, progress) -> {
                f.dustRing(tint, 0.2D + (2.0D * progress), 16, 1.1F);
                if (tick == 0) {
                  f.sound(Sound.BLOCK_GLASS_BREAK, 0.4F, 1.5F);
                }
              })
              .start();
        });
      });
    }
  }

  private boolean isBasePotionExcluded(PotionType baseType) {
    if (baseType == null) {
      return true;
    }
    return baseType == PotionType.WATER
        || baseType == PotionType.MUNDANE
        || baseType == PotionType.THICK
        || baseType == PotionType.AWKWARD;
  }

  private double sumPotionEffects(Iterable<PotionEffect> effects) {
    double sum = 0D;
    for (PotionEffect effect : effects) {
      sum += (effect.getAmplifier() + 1) * (effect.getDuration() / 20D);
    }
    return sum;
  }


  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    shouldReturnForPlayer(e.getPlayer(), e, () -> {
      if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
        WorldData.of(e.getBlock().getWorld()).set(e.getBlock(), new BrewingStandOwner(e.getPlayer().getUniqueId()));
        getPlayer(e.getPlayer()).getData().addStat("brewing.stands.placed", 1);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryOpenEvent e) {
    if (!(e.getPlayer() instanceof Player player)
        || !(e.getInventory() instanceof BrewerInventory inv)) {
      return;
    }

    org.bukkit.block.BrewingStand holder = inv.getHolder();
    if (holder == null) return;
    org.bukkit.block.Block block = holder.getBlock();
    if (block.getType() != Material.BREWING_STAND) return;

    shouldReturnForPlayer(player, e, () -> {
      art.arcane.adapt.api.data.WorldData data = WorldData.of(block.getWorld());
      art.arcane.adapt.content.matter.BrewingStandOwner owner = data.get(block, BrewingStandOwner.class);
      if (owner != null) return;
      data.set(block, new BrewingStandOwner(player.getUniqueId()));
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    shouldReturnForPlayer(e.getPlayer(), e, () -> {
      if (!e.getBlock().getType().equals(Material.BREWING_STAND)) {
        return;
      }
      WorldData.of(e.getBlock().getWorld()).remove(e.getBlock(), BrewingStandOwner.class);
    });
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
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
