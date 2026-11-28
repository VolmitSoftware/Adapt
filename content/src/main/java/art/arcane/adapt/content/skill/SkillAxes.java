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
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxeBarkHide;
import art.arcane.adapt.content.adaptation.axe.AxeChop;
import art.arcane.adapt.content.adaptation.axe.AxeCleave;
import art.arcane.adapt.content.adaptation.axe.AxeCraftLogSwap;
import art.arcane.adapt.content.adaptation.axe.AxeDropToInventory;
import art.arcane.adapt.content.adaptation.axe.AxeGroundSmash;
import art.arcane.adapt.content.adaptation.axe.AxeIrisFeller;
import art.arcane.adapt.content.adaptation.axe.AxeLeafVeinminer;
import art.arcane.adapt.content.adaptation.axe.AxeShieldSplitter;
import art.arcane.adapt.content.adaptation.axe.AxeSunder;
import art.arcane.adapt.content.adaptation.axe.AxeThrowingAxe;
import art.arcane.adapt.content.adaptation.axe.AxeWoodVeinminer;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesBoon;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesFightStrike;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesFuel;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesState;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesTrigger;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesWorkStrike;
import art.arcane.adapt.content.adaptation.axe.upgraded.SunderingCleave;
import art.arcane.adapt.content.adaptation.axe.upgraded.Timberfall;
import art.arcane.adapt.content.adaptation.axe.upgraded.Worldsplitter;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import art.arcane.adapt.platform.world.BlockStateRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public class SkillAxes extends SimpleSkill<SkillAxes.Config> {
  public static final Id ID = Id.adapt("axes");
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.axes.combat"));
  private final XpSource chop = xpSource("chop", Text.key("adapt.xp_guide.axes.chop"));
  private final Cooldowns cooldowns = cooldowns();
  private final ToolSkillRules toolRules = new ToolSkillRules(ToolKind.AXE, ID);

  public SkillAxes() {
    this(Integrations.treeFellingAvailable());
  }

  SkillAxes(boolean irisTreeFellerAvailable) {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFFF55));
    setInterval(5251);
    setIcon(Id.minecraft("golden_axe"));
    registerAdaptation(AxeGroundSmash.ID, AxeGroundSmash::new);
    registerAdaptation(AxeChop.ID, AxeChop::new);
    registerAdaptation(AxeDropToInventory.ID, AxeDropToInventory::new);
    registerAdaptation(AxeLeafVeinminer.ID, AxeLeafVeinminer::new);
    if (irisTreeFellerAvailable) {
      registerAdaptation(AxeIrisFeller.ID, AxeIrisFeller::new);
    }
    registerAdaptation(AxeWoodVeinminer.ID, AxeWoodVeinminer::new);
    registerAdaptation(AxeCraftLogSwap.ID, AxeCraftLogSwap::new);
    registerAdaptation(AxeThrowingAxe.ID, AxeThrowingAxe::new);
    registerAdaptation(AxeSunder.ID, AxeSunder::new);
    registerAdaptation(AxeCleave.ID, AxeCleave::new);
    registerAdaptation(AxeBarkHide.ID, AxeBarkHide::new);
    registerAdaptation(AxeShieldSplitter.ID, AxeShieldSplitter::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.axes"), Text.key("adapt.mutated.adjective.axes"))
        .trigger(TriggerKind.FIGHT, AxesTrigger::new)
        .strikes(AxesFightStrike::new, AxesWorkStrike::new)
        .boon(AxesBoon::new)
        .fuel(AxesFuel::new)
        .state(AxesState::new)
        .upgrade(Timberfall.ID, Timberfall::new, List.of(AxeChop.ID, AxeWoodVeinminer.ID, AxeLeafVeinminer.ID), Set.of())
        .upgrade(SunderingCleave.ID, SunderingCleave::new, List.of(AxeSunder.ID, AxeCleave.ID), Set.of())
        .upgrade(Worldsplitter.ID, Worldsplitter::new, List.of(AxeGroundSmash.ID, AxeThrowingAxe.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("item.axe.strip"), Id.minecraft("block.wood.break"),
            Id.minecraft("item.armor.equip_leather"), Id.minecraft("block.wood.hit")))
        .tint(Rgb.of(0xe8946a))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_axe")).key("challenge_chop_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stone_axe"))
            .key("challenge_chop_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_axe"))
                .key("challenge_chop_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_chop_1k", "axes.blocks.broken", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_5k", "axes.blocks.broken", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_chop_50k", "axes.blocks.broken", 50000, () -> getConfig().challengeChopReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_axe")).key("challenge_axe_damage_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_axe"))
            .key("challenge_axe_damage_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_damage_1k", "axes.damage", 1000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_damage_10k", "axes.damage", 10000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_log")).key("challenge_axe_value_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dark_oak_log"))
            .key("challenge_axe_value_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_value_5k", "axes.blocks.value", 5000, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_axe_value_50k", "axes.blocks.value", 50000, () -> getConfig().challengeChopReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_leaves")).key("challenge_leaves_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("azalea_leaves"))
            .key("challenge_leaves_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_leaves_500", "axes.leaves", 500, () -> getConfig().challengeChopReward);
    registerMilestone("challenge_leaves_5k", "axes.leaves", 5000, () -> getConfig().challengeChopReward * 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !ItemKinds.isAxe(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (p.isDead() || p.isInvulnerable() || !cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }

      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      cooldowns.mark(p.uuid());
      PlayerProfile adaptPlayer = getPlayer(p);
      adaptPlayer.addStat("axes.damage", creditedDamage);
      combat.award(p, e.entity().location(), combatXp(getConfig().axeHitXp, getConfig().axeDamageXPMultiplier, creditedDamage));
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    BlockStateRef state = e.block().state();
    if (!Tools.serves(p.inventory().mainHand(), state, ToolKind.AXE)) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      Id blockType = e.block().type();
      OptionalDouble listed = Tools.rules(ToolKind.AXE).blockXp(blockType);
      if (listed.isPresent() || ItemKinds.isLog(blockType)) {
        double value = listed.isPresent() ? listed.getAsDouble() : blockValue(blockType);
        PlayerProfile adaptPlayer = getPlayer(p);
        adaptPlayer.addStat("axes.blocks.broken", 1);
        adaptPlayer.addStat("axes.blocks.value", getValue(e.block().state()));
        chop.award(p, e.block().center(), blockXP(e.block(), value));
      }
      if (blockType.path().endsWith("_leaves")) {
        addStat(p, "axes.leaves", 1);
      }
    });
  }

  static double combatXp(double hitXp, double damageMultiplier, double creditedDamage) {
    if (!Double.isFinite(creditedDamage) || creditedDamage <= 0D) {
      return 0D;
    }
    return Math.max(0D, hitXp) + Math.max(0D, damageMultiplier) * creditedDamage;
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
    float hardness = items.blockHardness(type);
    String typeName = type.path();
    value += Math.min(getConfig().maxHardnessBonus, hardness);
    value += Math.min(getConfig().maxBlastResistanceBonus, items.blastResistance(type));

    if (typeName.endsWith("_log") || typeName.endsWith("_wood") || !type.isMinecraft()) {
      value += getConfig().logOrWoodXPMultiplier;
    }
    if (typeName.endsWith("_leaves")) {
      value += getConfig().leavesMultiplier;
    }

    if (hardness == 0) {
      value = 0;
    }

    return value;
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Axes skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;

    @ConfigDoc(value = "Controls Max Hardness Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @ConfigDoc(value = "Controls Challenge Chop Reward for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeChopReward = 1750;
    @ConfigDoc(value = "Controls Log Or Wood XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double logOrWoodXPMultiplier = 2.0;
    @ConfigDoc(value = "Controls Leaves Multiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leavesMultiplier = 0.75;
    @ConfigDoc(value = "Milliseconds between Axes combat XP awards.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long cooldownDelay = 1500;
    @ConfigDoc(value = "Controls Value XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valueXPMultiplier = 0.175;
    @ConfigDoc(value = "Controls Axe Damage XPMultiplier for the Axes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeDamageXPMultiplier = 7.0;
    @ConfigDoc(value = "Flat XP paid for each credited axe hit, before the damage XP.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double axeHitXp = 5.0;

    @ConfigDoc(value = "Item ids or #tags that also count as an axe for this skill and all its adaptations, such as mekanismtools:osmium_paxel or #forge:tools/hammers.", impact = "Listed tools earn Axes XP and trigger its adaptations; a tag covers every item in it.")
    public List<String> extraTools = new ArrayList<>();
    @ConfigDoc(value = "Item ids or #tags that never count as an axe for this skill, whatever a tag or other rule says.", impact = "Wins over every other rule, the extra tools list included.")
    public List<String> excludedTools = new ArrayList<>();
    @ConfigDoc(value = "Block ids or #tags that count as this skill's work, each with the XP one block gives before placed-block scaling.", impact = "Listed blocks use this XP instead of the derived value, and any tool of this skill earns it.")
    public Map<String, Double> extraBlocks = new LinkedHashMap<>();
    @ConfigDoc(value = "Block ids or #tags that never give Axes XP or count as its work.", impact = "Use it to stop XP from a modded block the automatic rules pick up.")
    public List<String> excludedBlocks = new ArrayList<>();
    @ConfigDoc(value = "Counts items in the axe tags minecraft:axes, forge:tools/axes, c:axes and fabric:axes as an axe.", impact = "Set to false to recognise tools only by the other rules.")
    public boolean toolTagRule = true;
    @ConfigDoc(value = "Counts items the mod loader says dig as an axe (Forge tool actions, NeoForge 1.21.1 item abilities) as an axe.", impact = "Set to false to ignore loader tool actions; Fabric and newer loaders have none.")
    public boolean toolCapabilityRule = true;
    @ConfigDoc(value = "Counts items that are the correct tool for an oak log as an axe, and pays Axes XP for a mineable/axe block broken with its correct tool.", impact = "Set to false to recognise tools only by lists, tags and loader actions.")
    public boolean toolBehaviourRule = true;

    public Config() {
    }
  }
}
