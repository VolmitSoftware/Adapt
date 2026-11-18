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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.xp.CraftingXpTransaction;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Map;
import java.util.UUID;


public class CraftingXP extends SimpleAdaptation<CraftingXP.Config> {
  private final Cooldowns xpCooldown = cooldowns();
  private final Map<UUID, CraftingXpTransaction> pendingCrafts = playerState();


  public CraftingXP() {
    super("crafting-xp");
    registerConfiguration(CraftingXP.Config.class);
    setIcon(Id.minecraft("enchanted_book"));
    setInterval(5580);
    AdvancementSpec xp25k = AdvancementSpec.challenge(
        "challenge_crafting_xp_25k",
        Id.minecraft("experience_bottle"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_XP_25K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_XP_25K_DESCRIPTION)
    );
    AdvancementSpec xp1k = AdvancementSpec.challenge(
        "challenge_crafting_xp_1k",
        Id.minecraft("crafting_table"),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_XP_1K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_XP_1K_DESCRIPTION)
    ).withChild(xp25k);
    registerAdvancementSpec(xp1k);
    registerStatTracker(xp1k.statTracker("crafting.xp.items-crafted", 1000, 300));
    registerStatTracker(xp25k.statTracker("crafting.xp.items-crafted", 25000, 1500));
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.XP_LORE1));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef player = e.player();
    pendingCrafts.remove(player.uuid());
    if (!hasActiveAdaptation(player)) {
      return;
    }
    CraftingXpTransaction transaction = CraftingXpTransaction.open(e);
    if (transaction == null) {
      return;
    }
    pendingCrafts.put(player.uuid(), transaction);
    Tasks.runEntity(player, () -> rewardCommittedCraft(player, transaction), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    CraftingXpTransaction transaction = pendingCrafts.get(e.player().uuid());
    if (transaction != null) {
      transaction.accept(e.crafted());
    }
  }

  private void rewardCommittedCraft(PlayerRef player, CraftingXpTransaction transaction) {
    pendingCrafts.remove(player.uuid(), transaction);
    if (transaction.committedItems() <= 0 || !player.isOnline()) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0 || !xpCooldown.isReady(player.uuid(), getConfig().cooldownMillis)) {
      return;
    }
    int reward = rewardXp(level, getConfig().vanillaXpAtLevelOne,
        getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerCraft);
    if (reward <= 0) {
      return;
    }
    xpCooldown.mark(player.uuid());
    player.world().spawnExperience(player.location().position(), reward);
    addStat(player, "crafting.xp.items-crafted", transaction.committedItems());
    xpShimmer(player.location().add(0, 1, 0), level);
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  private void xpShimmer(Loc center, int level) {
    float pickupPitch = (float) Math.min(2.0D, 0.8D + (level * 0.05D));
    FxEmitter fx = fx(center, FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, Math.min(14, 6 + level), 0, 0.2D, 0, 0.4D, 0.4D)
        .chord(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, pickupPitch, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.3F);
    if (level >= 5) {
      fx.burst(Particles.GLOW, 3, 0.2D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.8F);
    }
  }



  @ConfigDescription("Committed crafts periodically grant a bounded vanilla XP orb.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points granted by a level-one activation.", impact = "Higher values increase the base crafting reward.")
    int vanillaXpAtLevelOne = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the reward scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum vanilla XP points granted by one craft activation.", impact = "Caps oversized or heavily scaled rewards.")
    int maximumXpPerCraft = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between vanilla XP rewards for the same player.", impact = "Higher values reduce rapid-crafting XP generation.")
    long cooldownMillis = 30000L;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 7;
      initialCost = 3;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      vanillaXpAtLevelOne = Math.max(0, Math.min(vanillaXpAtLevelOne, 100000));
      vanillaXpPerAdditionalLevel = Math.max(0, Math.min(vanillaXpPerAdditionalLevel, 100000));
      maximumXpPerCraft = Math.max(0, Math.min(maximumXpPerCraft, 100000));
      cooldownMillis = Math.max(0L, Math.min(cooldownMillis, 3600000L));
    }
  }
}
