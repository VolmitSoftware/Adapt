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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscoveryRelicAppraiser extends SimpleAdaptation<DiscoveryRelicAppraiser.Config> {
  public static final Id ID = Id.adapt("discovery/relic_appraiser");
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", Text.key("adapt.discovery.preferences.discoveryrelicappraiser.confirmation"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> DISCS = CommonPreferences.toggle("discs", Text.key("adapt.discovery.preferences.discoveryrelicappraiser.discs"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HEADS = CommonPreferences.toggle("heads", Text.key("adapt.discovery.preferences.discoveryrelicappraiser.heads"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> TRIMS = CommonPreferences.toggle("trims", Text.key("adapt.discovery.preferences.discoveryrelicappraiser.trims"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SHERDS = CommonPreferences.toggle("sherds", Text.key("adapt.discovery.preferences.discoveryrelicappraiser.sherds"), CommonPreferences.Toggle.ON);

  private static final long COOLDOWN_MILLIS = 400L;
  static final double MAX_RANDOM_SKILL_XP = 10_000D;

  private final Cooldowns cooldowns = cooldowns();

  public DiscoveryRelicAppraiser() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    setInterval(3300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_discovery_appraiser_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("decorated_pot"))
            .key("challenge_discovery_appraiser_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_appraiser_50", "discovery.relic-appraiser.appraised", 50, 300);
    registerMilestone("challenge_discovery_appraiser_500", "discovery.relic-appraiser.appraised", 500, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, DISCS, HEADS, TRIMS, SHERDS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor), 0), 1);
    statLore(v, Format.f(normalizedMinimum(getConfig().randomSkillXpMin, getConfig().randomSkillXpMax), 0)
        + " - " + Format.f(normalizedMaximum(getConfig().randomSkillXpMin, getConfig().randomSkillXpMax), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    RelicAppraisal.rememberPlaced(e);
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockDropItemEvent e) {
    RelicAppraisal.restorePlaced(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!RelicAppraisal.isAppraisalClick(e)) {
      return;
    }

    PlayerRef p = e.player();

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isAppraisable(hand)) {
      return;
    }

    UUID id = p.uuid();
    if (!cooldowns.isReady(id, COOLDOWN_MILLIS)) {
      return;
    }

    if (hand.isEmpty()) {
      return;
    }

    if (hand.data().has(RelicAppraisal.APPRAISED)) {
      fx(p.eyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.7F);
      return;
    }

    String materialName = DiscoveryNames.enumName(hand.type());
    if (!preferenceEnabled(p, materialName.startsWith("MUSIC_DISC_") ? DISCS
        : materialName.endsWith("_HEAD") || materialName.endsWith("_SKULL") ? HEADS
        : materialName.endsWith("_SMITHING_TEMPLATE") ? TRIMS : SHERDS)) {
      return;
    }
    if (preferenceEnabled(p, CONFIRM) && !confirm(p, "appraise", hand)) {
      e.setCancelled(true);
      return;
    }
    cooldowns.mark(id);
    if (e.action() == InteractAction.RIGHT_CLICK_BLOCK) {
      e.setCancelled(true);
    }

    double reward = appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor)
        * rarityWeight(hand.type(), getConfig().discRarityWeight, getConfig().headRarityWeight,
        getConfig().trimRarityWeight, getConfig().sherdRarityWeight);
    RelicAppraisal.stamp(p, hand);

    xp(p, reward);
    RelicAppraisal.grantRandomSkillXp(p, getSkill(), getConfig().randomSkillXpMin, getConfig().randomSkillXpMax);
    addStat(p, "discovery.relic-appraiser.appraised", 1);

    timeline(p)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((f, tick, progress) -> {
          f.helix(Particles.WAX_ON, 0.4D, 1.5D, 5, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F, Sounds.ITEM_LODESTONE_COMPASS_LOCK, 0.4F, 1.6F);
          }
        })
        .onComplete(() -> fx(p.eyeLocation(), FxPriority.TRANSITION).particle(Particles.ENCHANT, 8, 0, 0.4, 0, 0.3, 0.02))
        .start();
  }

  public static boolean isAppraisable(ItemRef item) {
    String n = DiscoveryNames.enumName(item.type());
    return (n.startsWith("MUSIC_DISC_")
        || n.endsWith("_POTTERY_SHERD")
        || n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")
        || n.endsWith("_HEAD")
        || n.endsWith("_SKULL"))
        && !GolemItems.isGolemItem(item);
  }

  public static boolean isAppraisableBlock(Id type) {
    String name = DiscoveryNames.enumName(type);
    return name.endsWith("_HEAD") || name.endsWith("_SKULL");
  }

  static boolean isAppraised(ItemRef item, DataKey<Byte> appraisedKey) {
    if (item == null || item.type().isAir()) {
      return false;
    }
    return item.data().has(appraisedKey);
  }

  static boolean restoreAppraisedDrop(List<ItemEntityRef> drops, ItemRef snapshot) {
    for (ItemEntityRef drop : drops) {
      if (!drop.item().type().equals(snapshot.type())) {
        continue;
      }
      snapshot.setAmount(drop.item().amount());
      drop.setItem(snapshot);
      return true;
    }
    return false;
  }

  static List<Skill<?>> eligibleRandomSkills(List<Skill<?>> candidates, PlayerRef player, Skill<?> sourceSkill) {
    List<Skill<?>> eligible = new ArrayList<>(candidates.size());
    for (Skill<?> candidate : candidates) {
      if (candidate == null || candidate == sourceSkill || !candidate.isEnabled()) {
        continue;
      }
      if (candidate.hasUsePermission(player)) {
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

  public static double rarityWeight(Id type, double disc, double head, double trim, double sherd) {
    String n = DiscoveryNames.enumName(type);
    if (n.startsWith("MUSIC_DISC_")) {
      return disc;
    }
    if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) {
      return head;
    }
    if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
      return trim;
    }
    return sherd;
  }

  public static double appraiseXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }


  @ConfigDescription("Sneak-right-click rare drops for Discovery XP and a random eligible skill XP payout; appraised block items retain their stamp after placement.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Appraise Xp Base for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpBase = 60;
    @ConfigDoc(value = "Controls Appraise Xp Factor for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpFactor = 180;
    @ConfigDoc(value = "Minimum XP granted to one random enabled and permitted non-Discovery skill after an appraisal.", impact = "Values are clamped to 0-10,000; set both random skill XP bounds to zero to disable the additional payout.")
    double randomSkillXpMin = 20;
    @ConfigDoc(value = "Maximum XP granted to one random enabled and permitted non-Discovery skill after an appraisal.", impact = "Values are clamped to 0-10,000 and the two bounds are reordered when necessary.")
    double randomSkillXpMax = 60;
    @ConfigDoc(value = "Rarity multiplier applied to music disc appraisals.", impact = "Higher values pay more Discovery XP for discs.")
    double discRarityWeight = 1.5;
    @ConfigDoc(value = "Rarity multiplier applied to head and skull appraisals.", impact = "Higher values pay more Discovery XP for mob heads.")
    double headRarityWeight = 1.4;
    @ConfigDoc(value = "Rarity multiplier applied to armor trim template appraisals.", impact = "Higher values pay more Discovery XP for trims.")
    double trimRarityWeight = 1.25;
    @ConfigDoc(value = "Rarity multiplier applied to pottery sherd appraisals.", impact = "Higher values pay more Discovery XP for sherds.")
    double sherdRarityWeight = 1.0;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
