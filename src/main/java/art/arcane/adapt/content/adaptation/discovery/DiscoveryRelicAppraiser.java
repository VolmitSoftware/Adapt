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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryRelicAppraiser extends SimpleAdaptation<DiscoveryRelicAppraiser.Config> {
  private static final long COOLDOWN_MILLIS = 400L;
  static final double MAX_RANDOM_SKILL_XP = 10_000D;

  private final Cooldowns cooldowns = cooldowns();
  private final NamespacedKey appraisedKey = new NamespacedKey(Adapt.instance, "relic_appraised");
  private final NamespacedKey placedAppraisedItemKey = new NamespacedKey(Adapt.instance, "relic_appraised_item");

  public DiscoveryRelicAppraiser() {
    super("discovery-relic-appraiser");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(3300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_discovery_appraiser_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DECORATED_POT)
            .key("challenge_discovery_appraiser_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_appraiser_50", "discovery.relic-appraiser.appraised", 50, 300);
    registerMilestone("challenge_discovery_appraiser_500", "discovery.relic-appraiser.appraised", 500, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor), 0), 1);
    statLore(v, Form.f(normalizedMinimum(getConfig().randomSkillXpMin, getConfig().randomSkillXpMax), 0)
        + " - " + Form.f(normalizedMaximum(getConfig().randomSkillXpMin, getConfig().randomSkillXpMax), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    ItemStack placed = e.getItemInHand();
    if (!isAppraisableBlock(placed.getType()) || !isAppraised(placed, appraisedKey)) {
      return;
    }

    BlockState state = e.getBlockPlaced().getState();
    if (!(state instanceof TileState tile)) {
      return;
    }

    ItemStack snapshot = placed.clone();
    snapshot.setAmount(1);
    tile.getPersistentDataContainer().set(
        placedAppraisedItemKey,
        PersistentDataType.BYTE_ARRAY,
        snapshot.serializeAsBytes()
    );
    tile.update(false, false);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockDropItemEvent e) {
    BlockState state = e.getBlockState();
    if (!isAppraisableBlock(state.getType()) || !(state instanceof TileState tile)) {
      return;
    }

    byte[] encoded = tile.getPersistentDataContainer().get(placedAppraisedItemKey, PersistentDataType.BYTE_ARRAY);
    if (encoded == null || encoded.length == 0) {
      return;
    }

    ItemStack snapshot;
    try {
      snapshot = ItemStack.deserializeBytes(encoded);
    } catch (IllegalArgumentException ex) {
      Adapt.error("Could not restore an appraised relic broken at " + state.getLocation() + ".");
      Adapt.error(ex);
      return;
    }

    if (!isAppraisableBlock(snapshot.getType()) || !isAppraised(snapshot, appraisedKey)) {
      return;
    }
    restoreAppraisedDrop(e.getItems(), snapshot);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isAppraisable(hand.getType())) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, COOLDOWN_MILLIS)) {
      return;
    }

    ItemMeta meta = hand.getItemMeta();
    if (meta == null) {
      return;
    }

    if (meta.getPersistentDataContainer().has(appraisedKey, PersistentDataType.BYTE)) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.7F);
      return;
    }

    cooldowns.mark(id);
    if (action == Action.RIGHT_CLICK_BLOCK) {
      e.setCancelled(true);
    }

    double reward = appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor)
        * rarityWeight(hand.getType());
    meta.getPersistentDataContainer().set(appraisedKey, PersistentDataType.BYTE, (byte) 1);
    List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    lore.add(C.LIGHT_PURPLE + "" + C.ITALIC + AdaptLanguage.text(DiscoveryMessages.RELIC_APPRAISER_LORE_TAG));
    meta.setLore(lore);
    hand.setItemMeta(meta);
    p.getInventory().setItemInMainHand(hand);

    xp(p, reward);
    grantRandomSkillXp(p);
    getPlayer(p).getData().addStat("discovery.relic-appraiser.appraised", 1);

    timeline(p)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((f, tick, progress) -> {
          f.helix(Particle.WAX_ON, 0.4D, 1.5D, 5, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F, Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.4F, 1.6F);
          }
        })
        .onComplete(() -> fx(p.getEyeLocation(), FxPriority.TRANSITION).particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.4, 0, 0.3, 0.02))
        .start();
  }

  static boolean isAppraisable(Material type) {
    String n = type.name();
    return n.startsWith("MUSIC_DISC_")
        || n.endsWith("_POTTERY_SHERD")
        || n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")
        || n.endsWith("_HEAD")
        || n.endsWith("_SKULL");
  }

  static boolean isAppraisableBlock(Material type) {
    String name = type.name();
    return name.endsWith("_HEAD") || name.endsWith("_SKULL");
  }

  static boolean isAppraised(ItemStack item, NamespacedKey appraisedKey) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    return meta != null && meta.getPersistentDataContainer().has(appraisedKey, PersistentDataType.BYTE);
  }

  static boolean restoreAppraisedDrop(List<Item> drops, ItemStack snapshot) {
    for (Item drop : drops) {
      if (drop.getItemStack().getType() != snapshot.getType()) {
        continue;
      }
      snapshot.setAmount(drop.getItemStack().getAmount());
      drop.setItemStack(snapshot);
      return true;
    }
    return false;
  }

  static List<Skill<?>> eligibleRandomSkills(List<Skill<?>> candidates, Player player, Skill<?> sourceSkill) {
    List<Skill<?>> eligible = new ArrayList<>(candidates.size());
    for (Skill<?> candidate : candidates) {
      if (candidate == null || candidate == sourceSkill || !candidate.isEnabled()) {
        continue;
      }
      if (candidate.hasUsePermission(player, candidate)) {
        eligible.add(candidate);
      }
    }
    return eligible;
  }

  static double randomSkillXp(double configuredMin, double configuredMax, double unitRoll) {
    double minimum = normalizedMinimum(configuredMin, configuredMax);
    double maximum = normalizedMaximum(configuredMin, configuredMax);
    double roll = Double.isFinite(unitRoll) ? Math.max(0D, Math.min(1D, unitRoll)) : 0D;
    return minimum + ((maximum - minimum) * roll);
  }

  private static double normalizedMinimum(double configuredMin, double configuredMax) {
    double min = finiteNonNegative(configuredMin);
    double max = finiteNonNegative(configuredMax);
    return Math.min(min, max);
  }

  private static double normalizedMaximum(double configuredMin, double configuredMax) {
    double min = finiteNonNegative(configuredMin);
    double max = finiteNonNegative(configuredMax);
    return Math.max(min, max);
  }

  private static double finiteNonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, Math.min(MAX_RANDOM_SKILL_XP, value)) : 0D;
  }

  private void grantRandomSkillXp(Player player) {
    List<Skill<?>> eligible = eligibleRandomSkills(
        Adapt.instance.getAdaptServer().getSkillRegistry().getSkills(),
        player,
        getSkill()
    );
    if (eligible.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    double reward = randomSkillXp(getConfig().randomSkillXpMin, getConfig().randomSkillXpMax, random.nextDouble());
    if (reward <= 0D) {
      return;
    }
    Skill<?> selected = eligible.get(random.nextInt(eligible.size()));
    selected.xp(player, reward, "relic-appraiser");
  }

  private double rarityWeight(Material type) {
    String n = type.name();
    if (n.startsWith("MUSIC_DISC_")) {
      return getConfig().discRarityWeight;
    }

    if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) {
      return getConfig().headRarityWeight;
    }

    if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
      return getConfig().trimRarityWeight;
    }

    return getConfig().sherdRarityWeight;
  }

  static double appraiseXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }


  @ConfigDescription("Sneak-right-click rare drops for Discovery XP and a random eligible skill XP payout; appraised block items retain their stamp after placement.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Appraise Xp Base for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Appraise Xp Factor for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpFactor = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum XP granted to one random enabled and permitted non-Discovery skill after an appraisal.", impact = "Values are clamped to 0-10,000; set both random skill XP bounds to zero to disable the additional payout.")
    double randomSkillXpMin = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum XP granted to one random enabled and permitted non-Discovery skill after an appraisal.", impact = "Values are clamped to 0-10,000 and the two bounds are reordered when necessary.")
    double randomSkillXpMax = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to music disc appraisals.", impact = "Higher values pay more Discovery XP for discs.")
    double discRarityWeight = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to head and skull appraisals.", impact = "Higher values pay more Discovery XP for mob heads.")
    double headRarityWeight = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to armor trim template appraisals.", impact = "Higher values pay more Discovery XP for trims.")
    double trimRarityWeight = 1.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to pottery sherd appraisals.", impact = "Higher values pay more Discovery XP for sherds.")
    double sherdRarityWeight = 1.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
