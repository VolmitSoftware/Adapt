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
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBurrow;
import art.arcane.adapt.content.adaptation.excavation.ExcavationClodHurler;
import art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.adaptation.excavation.ExcavationHaste;
import art.arcane.adapt.content.adaptation.excavation.ExcavationMudlark;
import art.arcane.adapt.content.adaptation.excavation.ExcavationOmniTool;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSeismicPing;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSoftFall;
import art.arcane.adapt.content.adaptation.excavation.ExcavationSpelunker;
import art.arcane.adapt.content.adaptation.excavation.ExcavationTreasureHunter;
import art.arcane.adapt.content.adaptation.excavation.ExcavationTunneler;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationBoon;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationFightStrike;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationFuel;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationState;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationTrigger;
import art.arcane.adapt.content.adaptation.excavation.blocks.ExcavationWorkStrike;
import art.arcane.adapt.content.adaptation.excavation.upgraded.DeepSight;
import art.arcane.adapt.content.adaptation.excavation.upgraded.Gravekeeper;
import art.arcane.adapt.content.adaptation.excavation.upgraded.Landshaper;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public class SkillExcavation extends SimpleSkill<SkillExcavation.Config> {
  public static final Id ID = Id.adapt("excavation");
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.excavation.combat"));
  private final XpSource dig = xpSource("dig", Text.key("adapt.xp_guide.excavation.dig"));
  private static final Set<String> LOOSE_GROUND = Set.of("gravel", "sand", "red_sand", "clay", "soul_sand", "soul_soil");
  private final Cooldowns cooldowns = cooldowns();
  private final ToolSkillRules toolRules = new ToolSkillRules(ToolKind.SHOVEL, ID);

  public SkillExcavation() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.excavation.throw"));
    xpNote(Text.key("adapt.xp_guide.farmed_mobs"));
    xpNote(Text.key("adapt.xp_guide.placed_blocks"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFFF55));
    setInterval(5953);
    setIcon(Id.minecraft("diamond_shovel"));
    registerAdaptation(ExcavationHaste.ID, ExcavationHaste::new);
    registerAdaptation(ExcavationSpelunker.ID, ExcavationSpelunker::new);
    registerAdaptation(ExcavationOmniTool.ID, ExcavationOmniTool::new);
    registerAdaptation(ExcavationDropToInventory.ID, ExcavationDropToInventory::new);
    registerAdaptation(ExcavationSeismicPing.ID, ExcavationSeismicPing::new);
    registerAdaptation(ExcavationTunneler.ID, ExcavationTunneler::new);
    registerAdaptation(ExcavationTreasureHunter.ID, ExcavationTreasureHunter::new);
    registerAdaptation(ExcavationSoftFall.ID, ExcavationSoftFall::new);
    registerAdaptation(ExcavationEarthMover.ID, ExcavationEarthMover::new);
    registerAdaptation(ExcavationBurrow.ID, ExcavationBurrow::new);
    registerAdaptation(ExcavationGraveDigger.ID, ExcavationGraveDigger::new);
    registerAdaptation(ExcavationMudlark.ID, ExcavationMudlark::new);
    registerAdaptation(ExcavationClodHurler.ID, ExcavationClodHurler::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.excavation"), Text.key("adapt.mutated.adjective.excavation"))
        .trigger(TriggerKind.WORK, ExcavationTrigger::new)
        .strikes(ExcavationFightStrike::new, ExcavationWorkStrike::new)
        .boon(ExcavationBoon::new)
        .fuel(ExcavationFuel::new)
        .state(ExcavationState::new)
        .toolUpgrade(Landshaper.ID, Landshaper::new,
            List.of(ExcavationTunneler.ID, ExcavationBurrow.ID, ExcavationHaste.ID), Set.of())
        .upgrade(DeepSight.ID, DeepSight::new, List.of(ExcavationSpelunker.ID, ExcavationSeismicPing.ID), Set.of())
        .upgrade(Gravekeeper.ID, Gravekeeper::new,
            List.of(ExcavationTreasureHunter.ID, ExcavationGraveDigger.ID, ExcavationMudlark.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("item.shovel.flatten"), Id.minecraft("block.gravel.break"),
            Id.minecraft("block.sand.fall"), Id.minecraft("block.mud.step")))
        .tint(Rgb.of(0xb07048))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_shovel")).key("challenge_excavate_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("knowledge_book"))
            .key("challenge_excavate_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("stone_shovel"))
                .key("challenge_excavate_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_excavate_1k", "excavation.blocks.broken", 1000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_excavate_5k", "excavation.blocks.broken", 5000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_excavate_50k", "excavation.blocks.broken", 50000, () -> getConfig().challengeExcavationReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_shovel")).key("challenge_dig_damage_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_shovel"))
            .key("challenge_dig_damage_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_damage_1k", "excavation.damage", 1000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_damage_10k", "excavation.damage", 10000, () -> getConfig().challengeExcavationReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clay_ball")).key("challenge_dig_value_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("brick"))
            .key("challenge_dig_value_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_value_5k", "excavation.blocks.value", 5000, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_value_50k", "excavation.blocks.value", 50000, () -> getConfig().challengeExcavationReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gravel")).key("challenge_dig_gravel_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("flint"))
            .key("challenge_dig_gravel_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_dig_gravel_500", "excavation.gravel", 500, () -> getConfig().challengeExcavationReward);
    registerMilestone("challenge_dig_gravel_5k", "excavation.gravel", 5000, () -> getConfig().challengeExcavationReward * 2);
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !ItemKinds.isShovel(p.inventory().mainHand())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleEntityDamageByPlayer(p, e));
  }

  private void handleEntityDamageByPlayer(PlayerRef p, EntityDamageByEntityEvent e) {
    PlayerProfile a = getPlayer(p);
    ItemRef hand = a.player().inventory().mainHand();
    if (ItemKinds.isShovel(hand)) {
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      cooldowns.mark(p.uuid());
      addStat(p, "excavation.damage", creditedDamage);
      combat.award(a.player(), e.entity().location(), getConfig().axeDamageXPMultiplier * creditedDamage);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    if (!Tools.serves(p.inventory().mainHand(), e.block().state(), ToolKind.SHOVEL)) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> handleBlockBreakWithShovel(p, e));

  }

  private void handleBlockBreakWithShovel(PlayerRef p, BlockBreakEvent e) {
    if (XpProvenance.breakXpMultiplier(e.block()) <= 0) {
      return;
    }

    addStat(p, "excavation.blocks.broken", 1);
    addStat(p, "excavation.blocks.value", getValue(e.block().state()));
    Id blockType = e.block().type();
    if (blockType.isMinecraft() && LOOSE_GROUND.contains(blockType.path())) {
      addStat(p, "excavation.gravel", 1);
    }
    OptionalDouble listed = Tools.rules(ToolKind.SHOVEL).blockXp(blockType);
    double v = listed.isPresent() ? listed.getAsDouble() : blockValue(blockType);
    dig.award(p, e.block().center(), blockXP(e.block(), v));
  }

  @Override
  protected void onRuntimeActivated() {
    applyToolRules(getConfig());
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    applyToolRules(newConfig);
  }

  @Handle
  public void on(ResourcesReloadedEvent e) {
    Tools.invalidate();
  }

  private void applyToolRules(Config c) {
    toolRules.apply(c, c.extraTools, c.excludedTools, c.extraBlocks, c.excludedBlocks, c.toolTagRule,
        c.toolCapabilityRule, c.toolBehaviourRule);
  }

  private double blockValue(Id type) {
    ItemOps items = AdaptApi.platform().host().items();
    double value = getValue(type) * getConfig().valueXPMultiplier;
    value += Math.min(getConfig().maxHardnessBonus, items.blockHardness(type));
    value += Math.min(getConfig().maxBlastResistanceBonus, items.blastResistance(type));
    return value;
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Excavation skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @ConfigDoc(value = "Controls Max Hardness Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @ConfigDoc(value = "Controls Challenge Excavation Reward for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeExcavationReward = 1200;
    @ConfigDoc(value = "Controls Value XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valueXPMultiplier = 0.6;
    @ConfigDoc(value = "Milliseconds between Excavation combat XP awards.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long cooldownDelay = 1250;
    @ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Excavation skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeDamageXPMultiplier = 4.0;

    @ConfigDoc(value = "Item ids or #tags that also count as a shovel for this skill and all its adaptations, such as mekanismtools:osmium_paxel or #forge:tools/hammers.", impact = "Listed tools earn Excavation XP and trigger its adaptations; a tag covers every item in it.")
    public List<String> extraTools = new ArrayList<>();
    @ConfigDoc(value = "Item ids or #tags that never count as a shovel for this skill, whatever a tag or other rule says.", impact = "Wins over every other rule, the extra tools list included.")
    public List<String> excludedTools = new ArrayList<>();
    @ConfigDoc(value = "Block ids or #tags that count as this skill's work, each with the XP one block gives before placed-block scaling.", impact = "Listed blocks use this XP instead of the derived value, and any tool of this skill earns it.")
    public Map<String, Double> extraBlocks = new LinkedHashMap<>();
    @ConfigDoc(value = "Block ids or #tags that never give Excavation XP or count as its work.", impact = "Use it to stop XP from a modded block the automatic rules pick up.")
    public List<String> excludedBlocks = new ArrayList<>();
    @ConfigDoc(value = "Counts items in the shovel tags minecraft:shovels, forge:tools/shovels, c:shovels and fabric:shovels as a shovel.", impact = "Set to false to recognise tools only by the other rules.")
    public boolean toolTagRule = true;
    @ConfigDoc(value = "Counts items the mod loader says dig as a shovel (Forge tool actions, NeoForge 1.21.1 item abilities) as a shovel.", impact = "Set to false to ignore loader tool actions; Fabric and newer loaders have none.")
    public boolean toolCapabilityRule = true;
    @ConfigDoc(value = "Counts items that are the correct tool for dirt as a shovel, and pays Excavation XP for a mineable/shovel block broken with its correct tool.", impact = "Set to false to recognise tools only by lists, tags and loader actions.")
    public boolean toolBehaviourRule = true;

    public Config() {
    }
  }
}
