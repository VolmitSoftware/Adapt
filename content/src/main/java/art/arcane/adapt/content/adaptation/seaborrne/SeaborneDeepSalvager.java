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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SeaborneDeepSalvager extends SimpleAdaptation<SeaborneDeepSalvager.Config> {
  public static final Id ID = Id.adapt("seaborne/deep_salvager");
  public static final PlayerPreference<CommonPreferences.Toggle> OUTLINES = CommonPreferences.toggle("outlines", Text.key("adapt.seabornedeepsalvager.preferences.preference_outlines"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<ContainerTypes> CONTAINER_TYPES = PlayerPreference.of("container-types", ContainerTypes.class, ContainerTypes.ALL, List.of(
          new PlayerPreference.Choice<>(ContainerTypes.ALL, Text.key("adapt.seabornedeepsalvager.preferences.preference_container_types_all"), Id.minecraft("chest"), 1),
          new PlayerPreference.Choice<>(ContainerTypes.CHESTS, Text.key("adapt.seabornedeepsalvager.preferences.preference_container_types_chests"), Id.minecraft("chest"), 1),
          new PlayerPreference.Choice<>(ContainerTypes.BARRELS, Text.key("adapt.seabornedeepsalvager.preferences.preference_container_types_barrels"), Id.minecraft("barrel"), 1))).labelled(Text.key("adapt.seabornedeepsalvager.preferences.preference_container_types"));
  public static final PlayerPreference<OutlineColor> COLOR = PlayerPreference.of("color", OutlineColor.class, OutlineColor.AQUA, List.of(
          new PlayerPreference.Choice<>(OutlineColor.AQUA, Text.key("adapt.seabornedeepsalvager.preferences.preference_color_aqua"), Id.minecraft("cyan_stained_glass_pane"), 1),
          new PlayerPreference.Choice<>(OutlineColor.GOLD, Text.key("adapt.seabornedeepsalvager.preferences.preference_color_gold"), Id.minecraft("yellow_stained_glass_pane"), 1),
          new PlayerPreference.Choice<>(OutlineColor.PURPLE, Text.key("adapt.seabornedeepsalvager.preferences.preference_color_purple"), Id.minecraft("purple_stained_glass_pane"), 1))).labelled(Text.key("adapt.seabornedeepsalvager.preferences.preference_color"));

  private static final Id WATER = Id.minecraft("water");
  private static final Id HEART_OF_THE_SEA = Id.minecraft("heart_of_the_sea");
  private static final BlockFace[] WATER_FACES = {
      BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN
  };
  private static final Id[] TREASURE = {
      Id.minecraft("nautilus_shell"), Id.minecraft("nautilus_shell"),
      Id.minecraft("prismarine_shard"), Id.minecraft("prismarine_shard"), Id.minecraft("prismarine_shard"),
      Id.minecraft("prismarine_crystals"), Id.minecraft("prismarine_crystals"),
      Id.minecraft("gold_ingot"), Id.minecraft("iron_ingot"), Id.minecraft("lapis_lazuli"),
      Id.minecraft("emerald"), Id.minecraft("ink_sac"), Id.minecraft("glow_ink_sac"), Id.minecraft("tropical_fish"),
      HEART_OF_THE_SEA
  };
  static final DataKey<Byte> SALVAGED_KEY = DataKey.adapt("seaborne_salvaged", DataCodecs.BYTE);

  private final Cooldowns shimmerCooldowns = cooldowns();
  private final SeaborneShimmer shimmer = new SeaborneShimmer(this,
      (PlayerRef player) -> player.isInWater() && isPlayerEnabled(player) && preferenceEnabled(player, OUTLINES),
      (PlayerRef player) -> SeaborneShimmer.color(preference(player, COLOR)),
      (PlayerRef player) -> fx(player, FxPriority.AMBIENT).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F));
  public SeaborneDeepSalvager() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.deep_salvager");
    setIcon(Id.minecraft("chest"));
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_seaborne_salvage_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_seaborne_salvage_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_salvage_100", "seaborne.deep-salvager.containers-salvaged", 100, 400);
    registerMilestone("challenge_seaborne_salvage_1k", "seaborne.deep-salvager.containers-salvaged", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, OUTLINES, CONTAINER_TYPES, COLOR);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    shimmer.forget(player.player().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getDetectionRange(level), 1), 1);
    statLore(v, getBonusRolls(level), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    if (!getConfig().enableShimmer) {
      return;
    }

    long now = System.currentTimeMillis();
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> {
        if (!player.isOnline() || !player.isInWater() || !preferenceEnabled(player, OUTLINES)) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        if (!shimmerCooldowns.isReady(player.uuid(), getConfig().shimmerScanCooldownMillis)) {
          return;
        }
        if (shimmer.scanning(player.uuid())) {
          return;
        }

        shimmerCooldowns.mark(player.uuid());
        scanShimmer(player, level);
      });
    }
  }

  private void scanShimmer(PlayerRef player, int level) {
    int durationTicks = Math.max(20, (int) Math.min(200L, (getConfig().shimmerScanCooldownMillis / 50L) + 20L));
    shimmer.scan(player, (int) Math.round(getDetectionRange(level)), preference(player, CONTAINER_TYPES), durationTicks);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    shimmerCooldowns.clear(playerId);
    shimmer.forget(playerId);
  }

  @Override
  public void unregister() {
    shimmer.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryOpenEvent e) {
    PlayerRef p = e.player();
    BlockRef block = e.holderBlock();
    if (block == null || block.container() == null) {
      return;
    }

    InventoryRef inventory = e.inventory();
    withAdaptedPlayer(p, e, () -> trySalvage(p, inventory, block));
  }

  private void trySalvage(PlayerRef p, InventoryRef inventory, BlockRef block) {
    int level = getActiveLevel(p);
    if (level <= 0 || !p.isInWater() || !isSubmerged(block)) {
      return;
    }

    DataHolder data = block.blockEntityData();
    if (data == null) {
      return;
    }

    if (data.has(SALVAGED_KEY)) {
      return;
    }

    int rolls = getBonusRolls(level);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int added = 0;
    for (int i = 0; i < rolls; i++) {
      ItemRef loot = rollTreasure(random, 0);
      if (inventory.addItem(loot).isEmpty()) {
        added++;
      }
    }

    if (added <= 0) {
      return;
    }

    data.set(SALVAGED_KEY, (byte) 1);
    addStat(p, "seaborne.deep-salvager.containers-salvaged", 1);
    xp(p, getConfig().salvageXp * added);
    playSalvageEffects(block);
  }

  void playSalvageEffects(BlockRef block) {
    fx(block.location().add(0.5D, 1.0D, 0.5D), FxPriority.GAMEPLAY)
        .ring(Particles.GLOW, 0.6D, 12, 0.3D)
        .particle(Particles.END_ROD, 6, 0D, 0.3D, 0D, 0.35D, 0.02D)
        .chord(Sounds.BLOCK_CONDUIT_ACTIVATE, 0.5F, 1.3F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.6F);
  }

  private static boolean isSubmerged(BlockRef block) {
    for (BlockFace face : WATER_FACES) {
      if (block.relative(face).type().equals(WATER)) {
        return true;
      }
    }

    return false;
  }

  /** One treasure stack; each point of {@code luck} is one more draw at the heart of the sea. */
  public static ItemRef rollTreasure(Random random, int luck) {
    Id material = TREASURE[random.nextInt(TREASURE.length)];
    for (int draw = 0; draw < luck && !material.equals(HEART_OF_THE_SEA); draw++) {
      if (TREASURE[random.nextInt(TREASURE.length)].equals(HEART_OF_THE_SEA)) {
        material = HEART_OF_THE_SEA;
      }
    }
    int amount = material.equals(HEART_OF_THE_SEA) ? 1 : 1 + random.nextInt(3);
    return AdaptApi.platform().host().items().create(material, amount);
  }

  private double getDetectionRange(int level) {
    return detectionRange(getConfig().detectionRangeBase, getConfig().detectionRangeFactor, getLevelPercent(level));
  }

  private int getBonusRolls(int level) {
    return bonusRolls(getConfig().bonusRollsBase, getConfig().bonusRollsFactor, getLevelPercent(level));
  }

  static double detectionRange(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static int bonusRolls(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  @ConfigDescription("Underwater containers privately glow within range and reward bonus treasure the first time you open them submerged.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base block range that underwater containers shimmer within.", impact = "Higher values reveal containers from farther away at low levels.")
    double detectionRangeBase = 4;
    @ConfigDoc(value = "Additional shimmer detection range gained across levels.", impact = "Higher values greatly extend detection range at higher levels.")
    double detectionRangeFactor = 5;
    @ConfigDoc(value = "Base number of bonus treasure rolls added when salvaging a container.", impact = "Higher values grant more bonus loot at low levels.")
    int bonusRollsBase = 1;
    @ConfigDoc(value = "Additional bonus treasure rolls gained across levels.", impact = "Higher values grant far more bonus loot at higher levels.")
    double bonusRollsFactor = 3;
    @ConfigDoc(value = "Bonus XP granted per bonus treasure item salvaged.", impact = "Higher values reward salvaging with more skill XP.")
    double salvageXp = 12;
    @ConfigDoc(value = "Cooldown between underwater container shimmer scans per player.", impact = "Higher values reduce scan frequency and scanning cost.")
    long shimmerScanCooldownMillis = 3000;
    @ConfigDoc(value = "Enables the underwater container shimmer locator scan.", impact = "Set to false to disable shimmer scanning entirely while keeping bonus loot.")
    boolean enableShimmer = true;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }

  public enum ContainerTypes { ALL, CHESTS, BARRELS }

  public enum OutlineColor { AQUA, GOLD, PURPLE }
}
