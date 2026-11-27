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
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingAnvilSavant;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingArcaneSiphon;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingBookshelfAttunement;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingCurseCleansing;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingEchoOfKnowledge;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstoneRecovery;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingInfusionTransfer;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingOfferReroll;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingRuneSight;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingSoulLink;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingTomeRebinding;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingBoon;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingFightStrike;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingFuel;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingState;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingTrigger;
import art.arcane.adapt.content.adaptation.enchanting.blocks.EnchantingWorkStrike;
import art.arcane.adapt.content.adaptation.enchanting.upgraded.ArcaneHand;
import art.arcane.adapt.content.adaptation.enchanting.upgraded.FateWeaver;
import art.arcane.adapt.content.adaptation.enchanting.upgraded.Soulforged;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;

public class SkillEnchanting extends SimpleSkill<SkillEnchanting.Config> {
  public static final Id ID = Id.adapt("enchanting");
  private final XpSource enchant = xpSource("enchant", Text.key("adapt.xp_guide.enchanting.enchant"));
  private final Cooldowns cooldowns = cooldowns();


  public SkillEnchanting() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFF55FF));
    setInterval(3909);
    setIcon(Id.minecraft("knowledge_book"));
    registerAdaptation(EnchantingQuickEnchant.ID, EnchantingQuickEnchant::new);
    registerAdaptation(EnchantingLapisReturn.ID, EnchantingLapisReturn::new);
    registerAdaptation(EnchantingXPReturn.ID, EnchantingXPReturn::new); //
    registerAdaptation(EnchantingAnvilSavant.ID, EnchantingAnvilSavant::new);
    registerAdaptation(EnchantingOfferReroll.ID, EnchantingOfferReroll::new);
    registerAdaptation(EnchantingBookshelfAttunement.ID, EnchantingBookshelfAttunement::new);
    registerAdaptation(EnchantingGrindstoneRecovery.ID, EnchantingGrindstoneRecovery::new);
    registerAdaptation(EnchantingCurseCleansing.ID, EnchantingCurseCleansing::new);
    registerAdaptation(EnchantingTomeRebinding.ID, EnchantingTomeRebinding::new);
    registerAdaptation(EnchantingSoulLink.ID, EnchantingSoulLink::new);
    registerAdaptation(EnchantingArcaneSiphon.ID, EnchantingArcaneSiphon::new);
    registerAdaptation(EnchantingRuneSight.ID, EnchantingRuneSight::new);
    registerAdaptation(EnchantingInfusionTransfer.ID, EnchantingInfusionTransfer::new);
    registerAdaptation(EnchantingEchoOfKnowledge.ID, EnchantingEchoOfKnowledge::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.enchanting"), Text.key("adapt.mutated.adjective.enchanting"))
        .trigger(TriggerKind.WORK, EnchantingTrigger::new)
        .strikes(EnchantingFightStrike::new, EnchantingWorkStrike::new)
        .boon(EnchantingBoon::new)
        .fuel(EnchantingFuel::new)
        .state(EnchantingState::new)
        .upgrade(ArcaneHand.ID, ArcaneHand::new,
            List.of(EnchantingQuickEnchant.ID, EnchantingInfusionTransfer.ID, EnchantingTomeRebinding.ID), Set.of())
        .upgrade(FateWeaver.ID, FateWeaver::new,
            List.of(EnchantingOfferReroll.ID, EnchantingRuneSight.ID, EnchantingBookshelfAttunement.ID), Set.of())
        .upgrade(Soulforged.ID, Soulforged::new,
            List.of(EnchantingSoulLink.ID, EnchantingEchoOfKnowledge.ID, EnchantingCurseCleansing.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("enchant.thorns.hit"), Id.minecraft("item.book.put"),
            Id.minecraft("block.amethyst_block.place"), Id.minecraft("block.candle.ambient")))
        .tint(Rgb.of(0xffa8d8))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("crafting_table")).key("challenge_enchant_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("knowledge_book"))
            .key("challenge_enchant_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchant_1k", "enchanted.items", 1000, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_5k", "enchanted.items", 5000, () -> getConfig().challengeEnchantReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("experience_bottle"))
        .key("challenge_enchant_power_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanting_table"))
            .key("challenge_enchant_power_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchant_power_100", "enchanted.power", 100, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_power_1k", "enchanted.power", 1000, () -> getConfig().challengeEnchantReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bookshelf"))
        .key("challenge_enchant_high_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_enchant_high_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchant_high_25", "enchanting.high.level", 25, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_high_250", "enchanting.high.level", 250, () -> getConfig().challengeEnchantReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("experience_bottle"))
        .key("challenge_enchant_total_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("dragon_breath"))
            .key("challenge_enchant_total_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchant_total_500", "enchanting.total.levels", 500, () -> getConfig().challengeEnchantReward);
    registerMilestone("challenge_enchant_total_5k", "enchanting.total.levels", 5000, () -> getConfig().challengeEnchantReward * 2);
    registerGroup(Text.key("adapt.skill.enchanting.group.table"), List.of(Id.parseAdapt("adapt:enchanting/lapis_return"), Id.parseAdapt("adapt:enchanting/rune_sight"), Id.parseAdapt("adapt:enchanting/bookshelf_attunement"), Id.parseAdapt("adapt:enchanting/offer_reroll"), Id.parseAdapt("adapt:enchanting/xp_return")));
    registerGroup(Text.key("adapt.skill.enchanting.group.anvil"), List.of(Id.parseAdapt("adapt:enchanting/anvil_savant"), Id.parseAdapt("adapt:enchanting/grindstone_recovery"), Id.parseAdapt("adapt:enchanting/curse_cleansing"), Id.parseAdapt("adapt:enchanting/tome_rebinding"), Id.parseAdapt("adapt:enchanting/infusion_transfer"), Id.parseAdapt("adapt:enchanting/soul_link")));
    registerGroup(Text.key("adapt.skill.enchanting.group.books"), List.of(Id.parseAdapt("adapt:enchanting/arcane_siphon"), Id.parseAdapt("adapt:enchanting/echo_of_knowledge"), Id.parseAdapt("adapt:enchanting/quick_enchant")));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      handleEnchantItemEvent(p, e);
    });

  }

  private void handleEnchantItemEvent(PlayerRef p, EnchantItemEvent e) {
    PlayerProfile adaptPlayer = getPlayer(p);
    int power = 0;
    for (int level : e.enchantsToAdd().values()) {
      power += level;
    }
    adaptPlayer.addStat("enchanted.items", 1);
    adaptPlayer.addStat("enchanted.power", power);
    Loc tableLocation = e.table().location().add(0.5D, 0.9D, 0.5D);
    if (e.expLevelCost() >= 30) {
      adaptPlayer.addStat("enchanting.high.level", 1);
      celebrateHighEnchant(tableLocation);
    }
    adaptPlayer.addStat("enchanting.total.levels", e.expLevelCost());

    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(p.uuid());
    enchant.award(p, getConfig().enchantPowerXPMultiplier * power);
    fx(tableLocation, FxPriority.AMBIENT)
        .particle(Particles.ENCHANT, Math.min(20, 6 + (power * 2)), 0, 0, 0, 0.5D, 0.05D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.2F);
  }

  private void celebrateHighEnchant(Loc location) {
    timeline(location)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20.0D)
        .frame((f, tick, progress) -> {
          f.particle(Particles.ENCHANT, 2, 0, 0, 0, 0.5D, 0.08D);
          if (tick == 0) {
            f.dome(Particles.END_ROD, 1.6D, 12)
                .chord(Sounds.BLOCK_BEACON_ACTIVATE, 0.6F, 1.0F, Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, 1.3F);
          }
        })
        .start();
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Enchant Power XPMultiplier for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double enchantPowerXPMultiplier = 45;
    @ConfigDoc(value = "Controls Cooldown Delay for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 5250;
    @ConfigDoc(value = "Controls Challenge Enchant Reward for the Enchanting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeEnchantReward = 2500;

    public Config() {
    }
  }
}
