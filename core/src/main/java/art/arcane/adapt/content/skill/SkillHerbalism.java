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

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.XpNovelty;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBeeShepherd;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismCompostCascade;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismCraftableCobweb;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismCraftableMushroomBlocks;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismDropToInventory;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthAura;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHungryHippo;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHungryShield;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismLuck;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismMyconid;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismReplant;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismRootedFooting;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSeedSower;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSporeBloom;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismTerralid;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerShearEntityEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import lombok.NoArgsConstructor;

import java.util.Set;

public class SkillHerbalism extends SimpleSkill<SkillHerbalism.Config> {
  private static final Id COMPOSTER = Id.minecraft("composter");
  private static final Set<Id> POTION_ITEMS = Set.of(Id.minecraft("potion"), Id.minecraft("splash_potion"),
      Id.minecraft("lingering_potion"), Id.minecraft("tipped_arrow"));
  private final Cooldowns cooldown = cooldowns();

  public SkillHerbalism() {
    super("herbalism", SkillPresentation.of(SkillMessages.HERBALISM_NAME, SkillMessages.HERBALISM_ICON, SkillMessages.HERBALISM_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GREEN);
    setInterval(3990);
    setIcon(Id.minecraft("wheat"));
    registerAdaptation(new HerbalismGrowthAura());
    registerAdaptation(new HerbalismReplant());
    registerAdaptation(new HerbalismHungryShield());
    registerAdaptation(new HerbalismHungryHippo());
    registerAdaptation(new HerbalismDropToInventory());
    registerAdaptation(new HerbalismLuck());
    registerAdaptation(new HerbalismMyconid());
    registerAdaptation(new HerbalismTerralid());
    registerAdaptation(new HerbalismCraftableMushroomBlocks());
    registerAdaptation(new HerbalismCraftableCobweb());
    registerAdaptation(new HerbalismSeedSower());
    registerAdaptation(new HerbalismCompostCascade());
    registerAdaptation(new HerbalismRootedFooting());
    registerAdaptation(new HerbalismBeeShepherd());
    registerAdaptation(new HerbalismSporeBloom());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cooked_beef"))
        .key("challenge_eat_100")
        .model(CustomModel.get(Id.minecraft("cooked_beef"), "advancement", "herbalism", "challenge_eat_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cooked_beef"))
            .key("challenge_eat_1000")
            .model(CustomModel.get(Id.minecraft("cooked_beef"), "advancement", "herbalism", "challenge_eat_1000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("cooked_beef"))
                .key("challenge_eat_10000")
                .model(CustomModel.get(Id.minecraft("cooked_beef"), "advancement", "herbalism", "challenge_eat_10000"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_eat_100", "food.eaten", 100, () -> getConfig().challengeEat100Reward);
    registerMilestone("challenge_eat_1000", "food.eaten", 1000, () -> getConfig().challengeEat1kReward);
    registerMilestone("challenge_eat_10000", "food.eaten", 10000, () -> getConfig().challengeEat1kReward * 2);


    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cooked_beef"))
        .key("challenge_harvest_100")
        .model(CustomModel.get(Id.minecraft("cooked_beef"), "advancement", "herbalism", "harvest_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cooked_beef"))
            .key("challenge_harvest_1000")
            .model(CustomModel.get(Id.minecraft("cooked_beef"), "advancement", "herbalism", "harvest_1000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_harvest_100", "harvest.blocks", 100, () -> getConfig().challengeHarvest100Reward);
    registerMilestone("challenge_harvest_1000", "harvest.blocks", 1000, () -> getConfig().challengeHarvest1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat_seeds"))
        .key("challenge_plant_100")
        .model(CustomModel.get(Id.minecraft("wheat_seeds"), "advancement", "herbalism", "challenge_plant_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("beetroot_seeds"))
            .key("challenge_plant_1k")
            .model(CustomModel.get(Id.minecraft("beetroot_seeds"), "advancement", "herbalism", "challenge_plant_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("golden_carrot"))
                .key("challenge_plant_5k")
                .model(CustomModel.get(Id.minecraft("golden_carrot"), "advancement", "herbalism", "challenge_plant_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_plant_100", "harvest.planted", 100, () -> getConfig().challengePlant100Reward);
    registerMilestone("challenge_plant_1k", "harvest.planted", 1000, () -> getConfig().challengePlant1kReward);
    registerMilestone("challenge_plant_5k", "harvest.planted", 5000, () -> getConfig().challengePlant5kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("composter"))
        .key("challenge_compost_50")
        .model(CustomModel.get(Id.minecraft("composter"), "advancement", "herbalism", "challenge_compost_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone_meal"))
            .key("challenge_compost_500")
            .model(CustomModel.get(Id.minecraft("bone_meal"), "advancement", "herbalism", "challenge_compost_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_compost_50", "harvest.composted", 50, () -> getConfig().challengeCompost50Reward);
    registerMilestone("challenge_compost_500", "harvest.composted", 500, () -> getConfig().challengeCompost500Reward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shears"))
        .key("challenge_shear_50")
        .model(CustomModel.get(Id.minecraft("shears"), "advancement", "herbalism", "challenge_shear_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("white_wool"))
            .key("challenge_shear_250")
            .model(CustomModel.get(Id.minecraft("white_wool"), "advancement", "herbalism", "challenge_shear_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_shear_50", "herbalism.sheared", 50, () -> getConfig().challengeShear50Reward);
    registerMilestone("challenge_shear_250", "herbalism.sheared", 250, () -> getConfig().challengeShear250Reward);
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (e.item() != null && POTION_ITEMS.contains(e.item().type())) {
        return;
      }

      handleHerbCooldown(p, () -> {
        xp(p, getConfig().foodConsumeXP);
        addStat(p, "food.eaten", 1);
      });


    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerShearEntityEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      addStat(p, "herbalism.sheared", 1);
      xp(p, e.entity().location(), getConfig().shearXP);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerHarvestBlockEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> handleEvent(e.player(), e.harvestedBlock(), "harvest.blocks"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> handleEvent(e.player(), e.block(), "harvest.planted"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (e.useItem() == UseResult.DENY) {
        return;
      }
      if (e.clickedBlock() == null) {
        return;
      }
      if (e.clickedBlock().type().equals(COMPOSTER)) {
        handleComposterInteraction(e.clickedBlock(), p);
      }
    });

  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> handleEvent(e.player(), e.block(), "harvest.blocks"));
  }

  private void handleHerbCooldown(PlayerRef p, Runnable action) {
    if (!cooldown.isReady(p.uuid(), (long) getConfig().harvestXpCooldown)) {
      return;
    }

    cooldown.mark(p.uuid());
    action.run();
  }

  private void handleEvent(PlayerRef p, BlockRef block, String stat) {
    BlockStateRef state = block.state();
    if (state.age() >= 0) {
      double integrity = XpProvenance.harvestXpMultiplier(block) * XpNovelty.fieldCycleMultiplier(p, block);
      if (integrity <= 0D) {
        return;
      }
      xp(p, block.center(), getConfig().harvestPerAgeXP * state.age() * integrity, "herbalism:harvest");
      addStat(p, stat, 1);
    }
  }

  private void handleComposterInteraction(BlockRef b, PlayerRef p) {
    int ol = composterLevel(b.state());
    if (ol < 0)
      return;
    Tasks.runAt(b.location(), () -> {
      int nl = composterLevel(b.state());
      if (nl < 0)
        return;
      if (nl > ol || (ol > 0 && nl == 0)) {
        xp(p, b.center(), getConfig().composterBaseXP + (nl * getConfig().composterLevelXPMultiplier) + (nl == 0 ? getConfig().composterEmptyBonus : getConfig().composterNonZeroLevelBonus));
        addStat(p, "harvest.composted", 1);
      }
    });
  }

  private static int composterLevel(BlockStateRef state) {
    String level = state.property("level");
    if (level == null) {
      return -1;
    }
    try {
      return Integer.parseInt(level);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  public static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    public boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between Herbalism XP awards for eating.", impact = "Higher values reduce how often eating pays; harvesting crops has no cooldown.")
    public double harvestXpCooldown = 3500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Consume XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double foodConsumeXP = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Shear XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double shearXP = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Harvest Per Age XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double harvestPerAgeXP = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Plant Crop Seeds XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double plantCropSeedsXP = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Composter Base XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterBaseXP = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Composter Level XPMultiplier for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterLevelXPMultiplier = 1.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Composter Non Zero Level Bonus for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterNonZeroLevelBonus = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP when a composter empties after collection.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterEmptyBonus = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Eat100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeEat100Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Eat1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeEat1kReward = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Harvest100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeHarvest100Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Harvest1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeHarvest1kReward = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant100 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant100Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant1kReward = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Plant5k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant5kReward = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Compost50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeCompost50Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Compost500 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeCompost500Reward = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Shear50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeShear50Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Shear250 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeShear250Reward = 4000;
    String skillColor = "&a";
  }
}
