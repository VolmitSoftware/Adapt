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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CraftingXpTransaction;
import art.arcane.adapt.content.adaptation.crafting.CraftingBackpacks;
import art.arcane.adapt.content.adaptation.crafting.CraftingBulkArtisan;
import art.arcane.adapt.content.adaptation.crafting.CraftingCompactor;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingLeather;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.content.adaptation.crafting.CraftingProvisioner;
import art.arcane.adapt.content.adaptation.crafting.CraftingReconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingSkulls;
import art.arcane.adapt.content.adaptation.crafting.CraftingStations;
import art.arcane.adapt.content.adaptation.crafting.CraftingThriftyHands;
import art.arcane.adapt.content.adaptation.crafting.CraftingTinkerer;
import art.arcane.adapt.content.adaptation.crafting.CraftingXP;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingBoon;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingFightStrike;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingFuel;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingState;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingTrigger;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingWorkStrike;
import art.arcane.adapt.content.adaptation.crafting.upgraded.FieldWorkshop;
import art.arcane.adapt.content.adaptation.crafting.upgraded.GrandArtisan;
import art.arcane.adapt.content.adaptation.crafting.upgraded.Unmaker;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillCrafting extends SimpleSkill<SkillCrafting.Config> {
  public static final Id ID = Id.adapt("crafting");
  private final XpSource recipe = xpSource("recipe", Text.key("adapt.xp_guide.crafting.craft"));
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, CraftingXpTransaction> pendingCrafts = playerState();
  private final Map<String, Long> furnaceXpMarks = new ConcurrentHashMap<>();

  public SkillCrafting() {
    super(ID);
    xpSource("smelt", Text.key("adapt.xp_guide.crafting.smelt"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0xFFFF55));
    setInterval(3789);
    setIcon(Id.minecraft("crafting_table"));
    registerAdaptation(CraftingDeconstruction.ID, CraftingDeconstruction::new);
    registerAdaptation(CraftingXP.ID, CraftingXP::new);
    registerAdaptation(CraftingLeather.ID, CraftingLeather::new);
    registerAdaptation(CraftingSkulls.ID, CraftingSkulls::new);
    registerAdaptation(CraftingBackpacks.ID, CraftingBackpacks::new);
    registerAdaptation(CraftingStations.ID, CraftingStations::new);
    registerAdaptation(CraftingReconstruction.ID, CraftingReconstruction::new);
    registerAdaptation(CraftingBulkArtisan.ID, CraftingBulkArtisan::new);
    registerAdaptation(CraftingThriftyHands.ID, CraftingThriftyHands::new);
    registerAdaptation(CraftingMasterwork.ID, CraftingMasterwork::new);
    registerAdaptation(CraftingCompactor.ID, CraftingCompactor::new);
    registerAdaptation(CraftingTinkerer.ID, CraftingTinkerer::new);
    registerAdaptation(CraftingProvisioner.ID, CraftingProvisioner::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.crafting"), Text.key("adapt.mutated.adjective.crafting"))
        .trigger(TriggerKind.WORK, CraftingTrigger::new)
        .strikes(CraftingFightStrike::new, CraftingWorkStrike::new)
        .boon(CraftingBoon::new)
        .fuel(CraftingFuel::new)
        .state(CraftingState::new)
        .upgrade(GrandArtisan.ID, GrandArtisan::new,
            List.of(CraftingMasterwork.ID, CraftingTinkerer.ID, CraftingThriftyHands.ID), Set.of(CraftingMasterwork.ID, CraftingTinkerer.ID, CraftingThriftyHands.ID))
        .upgrade(Unmaker.ID, Unmaker::new,
            List.of(CraftingDeconstruction.ID, CraftingReconstruction.ID, CraftingCompactor.ID), Set.of(CraftingDeconstruction.ID, CraftingReconstruction.ID))
        .toolUpgrade(FieldWorkshop.ID, FieldWorkshop::new,
            List.of(CraftingStations.ID, CraftingBackpacks.ID, CraftingBulkArtisan.ID), Set.of(CraftingStations.ID, CraftingBackpacks.ID, CraftingBulkArtisan.ID))
        .sounds(SoundPalette.of(Id.minecraft("ui.stonecutter.take_result"), Id.minecraft("block.anvil.hit"),
            Id.minecraft("ui.loom.select_pattern"), Id.minecraft("ui.stonecutter.select_recipe")))
        .tint(Rgb.of(0xc08450))
        // Compactor and Unmaker.
        .reserve(BarterGesture.SNEAK_SWAP)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("crafting_table")).key("challenge_craft_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crafting_table"))
            .key("challenge_craft_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("crafting_table"))
                .key("challenge_craft_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_craft_1k", "crafted.items", 1000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_5k", "crafted.items", 5000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_50k", "crafted.items", 50000, () -> getConfig().challengeCraft1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gold_ingot")).key("challenge_craft_value_10k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_craft_value_100k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_value_10k", "crafted.value", 10000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_value_100k", "crafted.value", 100000, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe")).key("challenge_craft_tools_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_pickaxe"))
            .key("challenge_craft_tools_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_tools_25", "crafting.tools", 25, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_tools_250", "crafting.tools", 250, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate")).key("challenge_craft_armor_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_chestplate"))
            .key("challenge_craft_armor_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_armor_25", "crafting.armor", 25, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_armor_250", "crafting.armor", 250, () -> getConfig().challengeCraft1kReward * 2);

  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    pendingCrafts.remove(p.uuid());
    shouldReturnForPlayer(p, e, () -> {
      CraftingXpTransaction transaction = CraftingXpTransaction.open(e);
      if (transaction == null) {
        return;
      }
      pendingCrafts.put(p.uuid(), transaction);
      AdaptApi.scheduler().runEntity(p, () -> completeCraft(p, transaction), 1);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    CraftingXpTransaction transaction = pendingCrafts.get(e.player().uuid());
    if (transaction != null) {
      transaction.accept(e.crafted());
    }
  }

  private void completeCraft(PlayerRef player, CraftingXpTransaction transaction) {
    pendingCrafts.remove(player.uuid(), transaction);
    shouldReturnForPlayer(player, () -> rewardCraft(player, transaction));
  }

  private void rewardCraft(PlayerRef player, CraftingXpTransaction transaction) {
    int amount = transaction.committedItems();
    if (amount <= 0 || !cooldowns.isReady(player.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(player.uuid());
    double value = amount * getValue(transaction.material()) * getConfig().craftingValueXPMultiplier;
    addStat(player, "crafted.items", amount);
    addStat(player, "crafted.value", value);
    String type = transaction.material().path().toUpperCase(java.util.Locale.ROOT);
    if (type.endsWith("_PICKAXE") || type.endsWith("_AXE") || type.endsWith("_SHOVEL")
        || type.endsWith("_HOE") || type.endsWith("_SWORD")) {
      addStat(player, "crafting.tools", amount);
    }
    if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE")
        || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")) {
      addStat(player, "crafting.armor", amount);
    }
    recipe.award(player, value + getConfig().baseCraftingXP, transaction.recipe());
    fx(player.location().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.ENCHANT, 3, 0, 0.2D, 0, 0.3D, 0.3D);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(FurnaceSmeltEvent e) {
    BlockRef block = e.block();
    if (e.isCancelled() || shouldReturnForWorld(block.world())) {
      return;
    }
    String furnaceKey = block.world().uid() + ":" + block.x() + ":" + block.y() + ":" + block.z();
    if (!markFurnaceXp(furnaceXpMarks, furnaceKey, System.currentTimeMillis(), getConfig().furnaceXpCooldown)) {
      return;
    }
    xp(block.location(), getConfig().furnaceBaseXP + (getValue(e.result()) * getConfig().furnaceValueXPMultiplier), getConfig().furnaceXPRadius, getConfig().furnaceXPDuration);
    Loc furnace = block.location().add(0.5D, 0.9D, 0.5D);
    fx(furnace, FxPriority.AMBIENT)
        .particle(Particles.SMOKE, 1, 0, 0, 0, 0.04D, 0.02D)
        .particle(Particles.FLAME, 1, 0, 0, 0, 0.03D, 0.01D)
        .sound(Sounds.BLOCK_FURNACE_FIRE_CRACKLE, 0.3F, 1.0F);
  }

  static boolean markFurnaceXp(Map<String, Long> marks, String key, long now, long cooldownMs) {
    if (cooldownMs <= 0) {
      return true;
    }
    if (marks.size() > 2048) {
      marks.values().removeIf((Long last) -> now - last >= cooldownMs);
    }
    Long previous = marks.putIfAbsent(key, now);
    if (previous == null) {
      return true;
    }
    if (now - previous < cooldownMs) {
      return false;
    }
    return marks.replace(key, previous, now);
  }

  @Override
  public void unregister() {
    furnaceXpMarks.clear();
    super.unregister();
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Furnace Base XP for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceBaseXP = 30;
    @ConfigDoc(value = "Controls Furnace Value XPMultiplier for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceValueXPMultiplier = 4;
    @ConfigDoc(value = "Controls Furnace XPRadius for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int furnaceXPRadius = 32;
    @ConfigDoc(value = "Controls Cooldown Delay for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 3000;
    @ConfigDoc(value = "Controls Furnace XPDuration for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long furnaceXPDuration = 10000;
    @ConfigDoc(value = "Cooldown in milliseconds between spatial XP pulses per furnace.", impact = "Higher values reduce AFK furnace XP farming; lower values reward smelting more often.")
    long furnaceXpCooldown = 10000;
    @ConfigDoc(value = "Controls Crafting Value XPMultiplier for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double craftingValueXPMultiplier = 2.0;
    @ConfigDoc(value = "Controls Base Crafting XP for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCraftingXP = 1.5;
    @ConfigDoc(value = "Controls Challenge Craft1k Reward for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeCraft1kReward = 1200;

    public Config() {
    }
  }
}
