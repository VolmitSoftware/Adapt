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
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeChisel;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDeepCore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeGemPolish;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeObsidianRush;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeQuarrySense;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeRepairRhythm;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeSilkSpawner;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeStoneSkin;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeTunnelBore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeUnbreakablePact;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVeinminer;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesBoon;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesFightStrike;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesFuel;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesState;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesTrigger;
import art.arcane.adapt.content.adaptation.pickaxe.blocks.PickaxesWorkStrike;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Earthshaker;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.EternalPick;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Motherlode;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import java.util.List;
import java.util.Set;

public class SkillPickaxes extends SimpleSkill<SkillPickaxes.Config> {
  public static final Id ID = Id.adapt("pickaxe");
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.pickaxe.combat"));
  private final XpSource mine = xpSource("mine", Text.key("adapt.xp_guide.pickaxe.mine"));
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private final Cooldowns cooldowns = cooldowns();

  public SkillPickaxes() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.pickaxe.silk"));
    xpNote(Text.key("adapt.xp_guide.farmed_mobs"));
    xpNote(Text.key("adapt.xp_guide.placed_blocks"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFAA00));
    setInterval(2750);
    setIcon(Id.minecraft("netherite_pickaxe"));
    registerAdaptation(PickaxeChisel.ID, PickaxeChisel::new);
    registerAdaptation(PickaxeVeinminer.ID, PickaxeVeinminer::new);
    registerAdaptation(PickaxeAutosmelt.ID, PickaxeAutosmelt::new);
    registerAdaptation(PickaxeDropToInventory.ID, PickaxeDropToInventory::new);
    registerAdaptation(PickaxeSilkSpawner.ID, PickaxeSilkSpawner::new);
    registerAdaptation(PickaxeQuarrySense.ID, PickaxeQuarrySense::new);
    registerAdaptation(PickaxeTunnelBore.ID, PickaxeTunnelBore::new);
    registerAdaptation(PickaxeDeepCore.ID, PickaxeDeepCore::new);
    registerAdaptation(PickaxeObsidianRush.ID, PickaxeObsidianRush::new);
    registerAdaptation(PickaxeUnbreakablePact.ID, PickaxeUnbreakablePact::new);
    registerAdaptation(PickaxeRepairRhythm.ID, PickaxeRepairRhythm::new);
    registerAdaptation(PickaxeGemPolish.ID, PickaxeGemPolish::new);
    registerAdaptation(PickaxeStoneSkin.ID, PickaxeStoneSkin::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.pickaxe"), Text.key("adapt.mutated.adjective.pickaxe"))
        .trigger(TriggerKind.WORK, PickaxesTrigger::new)
        .strikes(PickaxesFightStrike::new, PickaxesWorkStrike::new)
        .boon(PickaxesBoon::new)
        .fuel(PickaxesFuel::new)
        .state(PickaxesState::new)
        .upgrade(Motherlode.ID, Motherlode::new, List.of(PickaxeVeinminer.ID, PickaxeAutosmelt.ID, PickaxeChisel.ID), Set.of())
        .toolUpgrade(Earthshaker.ID, Earthshaker::new,
            List.of(PickaxeTunnelBore.ID, PickaxeDeepCore.ID, PickaxeObsidianRush.ID), Set.of())
        .upgrade(EternalPick.ID, EternalPick::new,
            List.of(PickaxeUnbreakablePact.ID, PickaxeRepairRhythm.ID, PickaxeStoneSkin.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("block.stone.break"), Id.minecraft("block.ancient_debris.break"),
            Id.minecraft("block.lodestone.hit"), Id.minecraft("block.stone.hit")))
        .tint(Rgb.of(0xb8c2cc))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wooden_pickaxe"))
        .key("challenge_pickaxe_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stone_pickaxe"))
            .key("challenge_pickaxe_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_pickaxe"))
                .key("challenge_pickaxe_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());

    registerMilestone("challenge_pickaxe_1k", "pickaxe.blocks.broken", 1000, () -> getConfig().emeraldBonus * 2);
    registerMilestone("challenge_pickaxe_5k", "pickaxe.blocks.broken", 5000, () -> getConfig().emeraldBonus * 5);
    registerMilestone("challenge_pickaxe_50k", "pickaxe.blocks.broken", 50000, () -> getConfig().emeraldBonus * 10);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_pickaxe")).key("challenge_pick_damage_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_pickaxe"))
            .key("challenge_pick_damage_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_damage_1k", "pickaxe.damage", 1000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_damage_10k", "pickaxe.damage", 10000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("raw_iron")).key("challenge_pick_value_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("raw_gold"))
            .key("challenge_pick_value_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_value_5k", "pickaxe.blocks.value", 5000, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_value_50k", "pickaxe.blocks.value", 50000, () -> getConfig().emeraldBonus * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ore")).key("challenge_pick_ores_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_ore"))
            .key("challenge_pick_ores_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pick_ores_500", "pickaxe.ores", 500, () -> getConfig().emeraldBonus);
    registerMilestone("challenge_pick_ores_5k", "pickaxe.ores", 5000, () -> getConfig().emeraldBonus * 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().getXpForAttackingWithTools
        || !(e.damager() instanceof PlayerRef p)
        || !checkValidEntity(e.entity())
        || !ItemKinds.isPickaxe(p.inventory().mainHand())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      cooldowns.mark(p.uuid());
      PlayerProfile adaptPlayer = getPlayer(p);
      adaptPlayer.addStat("pickaxe.damage", creditedDamage);
      combat.award(p, e.entity().location(), getConfig().damageXPMultiplier * creditedDamage);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    ItemRef mainHand = p.inventory().mainHand();
    if (!ItemKinds.isPickaxe(mainHand)) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      Id blockType = e.block().type();
      double blockValue = blockValue(blockType);
      PlayerProfile adaptPlayer = getPlayer(p);

      adaptPlayer.addStat("pickaxe.blocks.broken", 1);
      adaptPlayer.addStat("pickaxe.blocks.value", blockValue);
      if (blockType.path().contains("_ore")) {
        adaptPlayer.addStat("pickaxe.ores", 1);
      }

      Loc blockLocation = e.block().center();
      if (mainHand.enchantmentLevel(SILK_TOUCH) > 0) {
        mine.award(p, blockLocation, 5);
      } else {
        mine.award(p, blockLocation, blockXP(e.block(), blockValue));
      }
    });
  }

  /** The XP value of mining one block of {@code type}, before tool and level scaling. */
  public double blockValue(Id type) {
    Config c = getConfig();
    ItemOps items = AdaptApi.platform().host().items();
    double value = getValue(type) * c.blockValueMultiplier;
    value += Math.min(c.maxHardnessBonus, items.blockHardness(type));
    value += Math.min(c.maxBlastResistanceBonus, items.blastResistance(type));

    value += switch (type.isMinecraft() ? type.path() : "") {
      case "coal_ore" -> c.coalBonus;
      case "copper_ore" -> c.copperBonus;
      case "iron_ore" -> c.ironBonus;
      case "gold_ore" -> c.goldBonus;
      case "lapis_ore" -> c.lapisBonus;
      case "diamond_ore" -> c.diamondBonus;
      case "emerald_ore" -> c.emeraldBonus;
      case "nether_gold_ore" -> c.netherGoldBonus;
      case "nether_quartz_ore" -> c.netherQuartzBonus;
      case "redstone_ore" -> c.redstoneBonus;
      case "ancient_debris" -> c.debrisBonus;
      case "deepslate_coal_ore" -> c.coalBonus * c.deepslateMultiplier;
      case "deepslate_copper_ore" -> c.copperBonus * c.deepslateMultiplier;
      case "deepslate_iron_ore" -> c.ironBonus * c.deepslateMultiplier;
      case "deepslate_gold_ore" -> c.goldBonus * c.deepslateMultiplier;
      case "deepslate_lapis_ore" -> c.lapisBonus * c.deepslateMultiplier;
      case "deepslate_diamond_ore" -> c.diamondBonus * c.deepslateMultiplier;
      case "deepslate_emerald_ore" -> c.emeraldBonus * c.deepslateMultiplier;
      case "deepslate_redstone_ore" -> c.redstoneBonus * c.deepslateMultiplier;
      default -> 0;
    };

    return value * 0.48;
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Debris Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double debrisBonus = 210;
    @ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Pickaxes skill.", impact = "True enables this behavior and false disables it.")
    boolean getXpForAttackingWithTools = true;
    @ConfigDoc(value = "Controls Damage XPMultiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageXPMultiplier = 6.0;
    @ConfigDoc(value = "Controls Block Value Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockValueMultiplier = 0.125;
    @ConfigDoc(value = "Controls Max Hardness Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHardnessBonus = 9;
    @ConfigDoc(value = "Controls Max Blast Resistance Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBlastResistanceBonus = 10;
    @ConfigDoc(value = "Controls Coal Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double coalBonus = 18;
    @ConfigDoc(value = "Controls Iron Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ironBonus = 30;
    @ConfigDoc(value = "Controls Redstone Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redstoneBonus = 55;
    @ConfigDoc(value = "Controls Copper Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double copperBonus = 22;
    @ConfigDoc(value = "Controls Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double goldBonus = 38;
    @ConfigDoc(value = "Controls Lapis Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisBonus = 75;
    @ConfigDoc(value = "Milliseconds between Pickaxes combat XP awards.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long cooldownDelay = 1250;
    @ConfigDoc(value = "Controls Diamond Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double diamondBonus = 175;
    @ConfigDoc(value = "Controls Emerald Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double emeraldBonus = 210;
    @ConfigDoc(value = "Controls Nether Gold Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherGoldBonus = 105;
    @ConfigDoc(value = "Controls Nether Quartz Bonus for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double netherQuartzBonus = 125;
    @ConfigDoc(value = "Controls Deepslate Multiplier for the Pickaxes skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double deepslateMultiplier = 1.35;

    public Config() {
    }
  }
}
