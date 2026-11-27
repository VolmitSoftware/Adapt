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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
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
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismBoon;
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismFightStrike;
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismFuel;
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismState;
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismTrigger;
import art.arcane.adapt.content.adaptation.herbalism.blocks.HerbalismWorkStrike;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.IronStomach;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.Wildgrowth;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;
import java.util.Set;

public class SkillHerbalism extends SimpleSkill<SkillHerbalism.Config> {
  public static final Id ID = Id.adapt("herbalism");
  private final XpSource harvest = xpSource("harvest", Text.key("adapt.xp_guide.herbalism.harvest"));
  private final XpSource eat = xpSource("eat", Text.key("adapt.xp_guide.herbalism.eat"));
  private final XpSource shear = xpSource("shear", Text.key("adapt.xp_guide.herbalism.shear"));
  private final XpSource compost = xpSource("compost", Text.key("adapt.xp_guide.herbalism.compost"));
  private static final Id COMPOSTER = Id.minecraft("composter");
  private static final Set<Id> POTION_ITEMS = Set.of(Id.minecraft("potion"), Id.minecraft("splash_potion"),
      Id.minecraft("lingering_potion"), Id.minecraft("tipped_arrow"));
  private final Cooldowns cooldown = cooldowns();


  public SkillHerbalism() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.herbalism.bonemeal"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FF55));
    setInterval(3990);
    setIcon(Id.minecraft("wheat"));
    registerAdaptation(HerbalismGrowthAura.ID, HerbalismGrowthAura::new);
    registerAdaptation(HerbalismReplant.ID, HerbalismReplant::new);
    registerAdaptation(HerbalismHungryShield.ID, HerbalismHungryShield::new);
    registerAdaptation(HerbalismHungryHippo.ID, HerbalismHungryHippo::new);
    registerAdaptation(HerbalismDropToInventory.ID, HerbalismDropToInventory::new);
    registerAdaptation(HerbalismLuck.ID, HerbalismLuck::new);
    registerAdaptation(HerbalismMyconid.ID, HerbalismMyconid::new);
    registerAdaptation(HerbalismTerralid.ID, HerbalismTerralid::new);
    registerAdaptation(HerbalismCraftableMushroomBlocks.ID, HerbalismCraftableMushroomBlocks::new);
    registerAdaptation(HerbalismCraftableCobweb.ID, HerbalismCraftableCobweb::new);
    registerAdaptation(HerbalismSeedSower.ID, HerbalismSeedSower::new);
    registerAdaptation(HerbalismCompostCascade.ID, HerbalismCompostCascade::new);
    registerAdaptation(HerbalismRootedFooting.ID, HerbalismRootedFooting::new);
    registerAdaptation(HerbalismBeeShepherd.ID, HerbalismBeeShepherd::new);
    registerAdaptation(HerbalismSporeBloom.ID, HerbalismSporeBloom::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.herbalism"), Text.key("adapt.mutated.adjective.herbalism"))
        .trigger(TriggerKind.WORK, HerbalismTrigger::new)
        .strikes(HerbalismFightStrike::new, HerbalismWorkStrike::new)
        .boon(HerbalismBoon::new)
        .fuel(HerbalismFuel::new)
        .state(HerbalismState::new)
        .upgrade(Wildgrowth.ID, Wildgrowth::new,
            List.of(HerbalismGrowthAura.ID, HerbalismBeeShepherd.ID, HerbalismSeedSower.ID), Set.of())
        .toolUpgrade(HarvestMoon.ID, HarvestMoon::new,
            List.of(HerbalismReplant.ID, HerbalismCompostCascade.ID, HerbalismLuck.ID), Set.of(HerbalismCompostCascade.ID))
        .upgrade(IronStomach.ID, IronStomach::new, List.of(HerbalismHungryShield.ID, HerbalismHungryHippo.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("block.vine.step"), Id.minecraft("item.hoe.till"),
            Id.minecraft("block.moss.break"), Id.minecraft("block.wet_grass.step")))
        .tint(Rgb.of(0x5ee89a))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cooked_beef"))
        .key("challenge_eat_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cooked_beef"))
            .key("challenge_eat_1000")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("cooked_beef"))
                .key("challenge_eat_10000")
                .frame(AdvancementFrame.CHALLENGE)
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
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cooked_beef"))
            .key("challenge_harvest_1000")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_harvest_100", "harvest.blocks", 100, () -> getConfig().challengeHarvest100Reward);
    registerMilestone("challenge_harvest_1000", "harvest.blocks", 1000, () -> getConfig().challengeHarvest1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat_seeds"))
        .key("challenge_plant_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("beetroot_seeds"))
            .key("challenge_plant_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("golden_carrot"))
                .key("challenge_plant_5k")
                .frame(AdvancementFrame.CHALLENGE)
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
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone_meal"))
            .key("challenge_compost_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_compost_50", "harvest.composted", 50, () -> getConfig().challengeCompost50Reward);
    registerMilestone("challenge_compost_500", "harvest.composted", 500, () -> getConfig().challengeCompost500Reward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shears"))
        .key("challenge_shear_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("white_wool"))
            .key("challenge_shear_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_shear_50", "herbalism.sheared", 50, () -> getConfig().challengeShear50Reward);
    registerMilestone("challenge_shear_250", "herbalism.sheared", 250, () -> getConfig().challengeShear250Reward);
    registerGroup(Text.key("adapt.skill.herbalism.group.farming"), List.of(Id.parseAdapt("adapt:herbalism/replant"), Id.parseAdapt("adapt:herbalism/seed_sower"), Id.parseAdapt("adapt:herbalism/compost_cascade"), Id.parseAdapt("adapt:herbalism/bee_shepherd"), Id.parseAdapt("adapt:herbalism/growth_aura"), Id.parseAdapt("adapt:herbalism/spore_bloom"), Id.parseAdapt("adapt:herbalism/drop_to_inventory")));
    registerGroup(Text.key("adapt.skill.herbalism.group.survival"), List.of(Id.parseAdapt("adapt:herbalism/hippo"), Id.parseAdapt("adapt:herbalism/luck"), Id.parseAdapt("adapt:herbalism/rooted_footing"), Id.parseAdapt("adapt:herbalism/hungry_shield")));
    registerGroup(Text.key("adapt.skill.herbalism.group.recipes"), List.of(Id.parseAdapt("adapt:herbalism/myconid"), Id.parseAdapt("adapt:herbalism/terralid"), Id.parseAdapt("adapt:herbalism/mushroom_blocks"), Id.parseAdapt("adapt:herbalism/cobweb")));
  }



  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (e.item() != null && POTION_ITEMS.contains(e.item().type())) {
        return;
      }

      handleHerbCooldown(p, () -> {
        eat.award(p, getConfig().foodConsumeXP);
        addStat(p, "food.eaten", 1);
      });


    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerShearEntityEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      addStat(p, "herbalism.sheared", 1);
      shear.award(p, e.entity().location(), getConfig().shearXP);
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
      double integrity = XpProvenance.harvestXpMultiplier(block) * XpProvenance.fieldCycleMultiplier(p, block);
      if (integrity <= 0D) {
        return;
      }
      harvest.award(p, block.center(), getConfig().harvestPerAgeXP * state.age() * integrity);
      addStat(p, stat, 1);
    }
  }

  private void handleComposterInteraction(BlockRef b, PlayerRef p) {
    int ol = composterLevel(b.state());
    if (ol < 0)
      return;
    AdaptApi.scheduler().runAt(b.location(), () -> {
      int nl = composterLevel(b.state());
      if (nl < 0)
        return;
      if (nl > ol || (ol > 0 && nl == 0)) {
        compost.award(p, b.center(), getConfig().composterBaseXP + (nl * getConfig().composterLevelXPMultiplier) + (nl == 0 ? getConfig().composterEmptyBonus : getConfig().composterNonZeroLevelBonus));
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



  public static class Config extends SkillConfig {
    @ConfigDoc(value = "Milliseconds between Herbalism XP awards for eating.", impact = "Higher values reduce how often eating pays; harvesting crops has no cooldown.")
    public double harvestXpCooldown = 3500;
    @ConfigDoc(value = "Controls Food Consume XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double foodConsumeXP = 35;
    @ConfigDoc(value = "Controls Shear XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double shearXP = 35;
    @ConfigDoc(value = "Controls Harvest Per Age XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double harvestPerAgeXP = 5.0;
    @ConfigDoc(value = "Controls Plant Crop Seeds XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double plantCropSeedsXP = 4.0;
    @ConfigDoc(value = "Controls Composter Base XP for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterBaseXP = 2.5;
    @ConfigDoc(value = "Controls Composter Level XPMultiplier for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterLevelXPMultiplier = 1.25;
    @ConfigDoc(value = "Controls Composter Non Zero Level Bonus for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterNonZeroLevelBonus = 25;
    @ConfigDoc(value = "Bonus XP when a composter empties after collection.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double composterEmptyBonus = 5;
    @ConfigDoc(value = "Controls Challenge Eat100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeEat100Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Eat1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeEat1kReward = 4000;
    @ConfigDoc(value = "Controls Challenge Harvest100Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeHarvest100Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Harvest1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeHarvest1kReward = 4000;
    @ConfigDoc(value = "Controls Challenge Plant100 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant100Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Plant1k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant1kReward = 4000;
    @ConfigDoc(value = "Controls Challenge Plant5k Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengePlant5kReward = 8000;
    @ConfigDoc(value = "Controls Challenge Compost50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeCompost50Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Compost500 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeCompost500Reward = 4000;
    @ConfigDoc(value = "Controls Challenge Shear50 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeShear50Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Shear250 Reward for the Herbalism skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double challengeShear250Reward = 4000;

    public Config() {
    }
  }
}
