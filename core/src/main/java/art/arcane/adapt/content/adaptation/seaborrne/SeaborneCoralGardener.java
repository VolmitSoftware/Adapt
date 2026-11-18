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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.common.world.BlockTagSet;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SeaborneCoralGardener extends SimpleAdaptation<SeaborneCoralGardener.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> PRESERVATION = CommonPreferences.toggle("preservation", SeabornMessages.SEABORNECORALGARDENER_PREFERENCE_PRESERVATION, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GROWTH = CommonPreferences.toggle("growth", SeabornMessages.SEABORNECORALGARDENER_PREFERENCE_GROWTH, CommonPreferences.Toggle.ON);

  private static final int MAX_TRACKED_CORAL = 8192;
  private static final Id WATER = Id.minecraft("water");
  private static final Id BONE_MEAL = Id.minecraft("bone_meal");
  private static final Id[] CORAL_BLOCKS = {
      Id.minecraft("tube_coral_block"),
      Id.minecraft("brain_coral_block"),
      Id.minecraft("bubble_coral_block"),
      Id.minecraft("fire_coral_block"),
      Id.minecraft("horn_coral_block")
  };
  private static final BlockFace[] GROW_FACES = {
      BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN
  };
  private static final Set<Id> EXTRA_REEF = Set.of(
      Id.minecraft("prismarine"),
      Id.minecraft("prismarine_bricks"),
      Id.minecraft("dark_prismarine"),
      Id.minecraft("sea_lantern"),
      Id.minecraft("sponge"),
      Id.minecraft("wet_sponge")
  );

  private final Map<String, Long> placedCoral = new ConcurrentHashMap<>();
  private final BlockTagSet coralBlocks = new BlockTagSet(Id.minecraft("coral_blocks"));
  private final BlockTagSet corals = new BlockTagSet(Id.minecraft("corals"));
  private final BlockTagSet wallCorals = new BlockTagSet(Id.minecraft("wall_corals"));

  public SeaborneCoralGardener() {
    super("seaborne-coral-gardener");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.coral_gardener");
    setIcon(Id.minecraft("brain_coral_block"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tube_coral_block"))
        .key("challenge_seaborne_coral_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("brain_coral_block"))
            .key("challenge_seaborne_coral_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_coral_500", "seaborne.coral-gardener.coral-placed", 500, 300);
    registerMilestone("challenge_seaborne_coral_5k", "seaborne.coral-gardener.coral-placed", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PRESERVATION, GROWTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getSurvivalMillis(level), 0), 1);
    statLore(v, Form.pc(getGrowthChance(level), 0), 2);
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ResourcesReloadedEvent e) {
    coralBlocks.invalidate();
    corals.invalidate();
    wallCorals.invalidate();
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    BlockRef b = e.block();
    Id m = b.type();
    boolean coral = isFadeableCoral(m);
    boolean reef = coral || EXTRA_REEF.contains(m);
    if (!reef) {
      return;
    }

    Loc location = b.location();
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      xp(p, getConfig().reefPlaceXp);
      if (coral && preferenceEnabled(p, PRESERVATION)) {
        trackCoral(location, level);
        addStat(p, "seaborne.coral-gardener.coral-placed", 1);
        fx(location.add(0.5D, 0.6D, 0.5D), FxPriority.AMBIENT)
            .particle(Particles.HAPPY_VILLAGER, 4, 0D, 0.2D, 0D, 0.35D, 0D)
            .sound(Sounds.BLOCK_CORAL_BLOCK_PLACE, 0.5F, 1.3F);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFadeEvent e) {
    BlockRef b = e.block();
    if (!isFadeableCoral(b.type())) {
      return;
    }

    String key = key(b.location());
    Long expiry = placedCoral.get(key);
    if (expiry == null) {
      return;
    }

    if (System.currentTimeMillis() < expiry) {
      e.setCancelled(true);
      return;
    }

    placedCoral.remove(key);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND
        || e.action() != InteractAction.RIGHT_CLICK_BLOCK
        || e.useBlock() == UseResult.DENY) {
      return;
    }

    ItemRef item = e.item();
    if (item == null || item.isEmpty() || !item.type().equals(BONE_MEAL)) {
      return;
    }

    BlockRef clicked = e.clickedBlock();
    if (clicked == null) {
      return;
    }

    PlayerRef p = e.player();
    if (!preferenceEnabled(p, GROWTH) || !ownsCoralTarget(p, clicked) || !isFadeableCoral(clicked.type())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() >= getGrowthChance(level)) {
        return;
      }

      BlockRef target = null;
      for (BlockFace face : GROW_FACES) {
        BlockRef relative = clicked.relative(face);
        if (ownsCoralTarget(p, relative) && relative.type().equals(WATER)) {
          target = relative;
          break;
        }
      }

      if (target == null) {
        return;
      }

      Id coral = CORAL_BLOCKS[ThreadLocalRandom.current().nextInt(CORAL_BLOCKS.length)];
      if (!growCoral(p, target, coral, level)) {
        return;
      }
      fx(clicked.location().add(0.5D, 1.0D, 0.5D), FxPriority.GAMEPLAY)
          .particle(Particles.HAPPY_VILLAGER, 8, 0D, 0.4D, 0D, 0.5D, 0D)
          .particle(Particles.BUBBLE, 6, 0D, 0.3D, 0D, 0.4D, 0.02D)
          .chord(Sounds.BLOCK_CORAL_BLOCK_PLACE, 0.6F, 1.2F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    });
  }

  private boolean growCoral(PlayerRef p, BlockRef target, Id coral, int level) {
    if (!ownsCoralTarget(p, target)
        || !target.type().equals(WATER)
        || !canBlockPlace(p, target.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(p, target)) {
      return false;
    }
    if (!ownsCoralTarget(p, target) || !target.type().equals(WATER)) {
      return false;
    }

    target.setType(coral, false);
    boolean costPaid = false;
    try {
      costPaid = consumeBoneMeal(p);
      if (!costPaid) {
        return false;
      }
    } finally {
      if (!costPaid && ownsCoralTarget(p, target) && target.type().equals(coral)) {
        target.setType(WATER, false);
      }
    }

    trackCoral(target.location(), level);
    fx(target.location().add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION)
        .ring(Particles.HAPPY_VILLAGER, 0.5D, 8, 0.2D)
        .sound(Sounds.BLOCK_CORAL_BLOCK_BREAK, 0.4F, 1.8F);
    Tasks.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      xp(p, getConfig().growthXp);
      addStat(p, "seaborne.coral-gardener.coral-placed", 1);
    });
    return true;
  }

  private boolean ownsCoralTarget(PlayerRef player, BlockRef target) {
    return !Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(target.location()));
  }

  private boolean consumeBoneMeal(PlayerRef p) {
    if (p.gameMode() == GameMode.CREATIVE) {
      return true;
    }

    ItemRef inHand = p.inventory().mainHand();
    if (!inHand.type().equals(BONE_MEAL)) {
      return false;
    }

    return payItemCost(p, "bonemeal", AdaptCore.platform().host().items().create(BONE_MEAL), 1, () -> {
      inHand.setAmount(inHand.amount() - 1);
      p.inventory().setMainHand(inHand);
      return true;
    });
  }

  private void trackCoral(Loc location, int level) {
    if (placedCoral.size() >= MAX_TRACKED_CORAL) {
      purgeExpired();
    }

    if (placedCoral.size() >= MAX_TRACKED_CORAL) {
      return;
    }

    placedCoral.put(key(location), System.currentTimeMillis() + getSurvivalMillis(level));
  }

  private void purgeExpired() {
    long now = System.currentTimeMillis();
    placedCoral.values().removeIf(expiry -> expiry < now);
  }

  private static String key(Loc location) {
    return location.world().uid() + ":" + location.blockX() + ":" + location.blockY() + ":" + location.blockZ();
  }

  private boolean isFadeableCoral(Id m) {
    return coralBlocks.contains(m) || corals.contains(m) || wallCorals.contains(m);
  }

  private long getSurvivalMillis(int level) {
    return survivalMillis(getConfig().survivalSecondsBase, getConfig().survivalSecondsFactor, getLevelPercent(level));
  }

  private double getGrowthChance(int level) {
    return growthChance(getConfig().growthChanceBase, getConfig().growthChanceFactor, getLevelPercent(level));
  }

  static long survivalMillis(double baseSeconds, double factorSeconds, double levelPercent) {
    return (long) (Math.max(0D, baseSeconds + (levelPercent * factorSeconds)) * 1000D);
  }

  static double growthChance(double base, double factor, double levelPercent) {
    return Math.min(1D, Math.max(0D, base + (levelPercent * factor)));
  }

  @ConfigDescription("Coral you place survives out of water far longer, bonemeal grows coral, and reef blocks grant bonus XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds placed coral survives out of water before it may fade.", impact = "Higher values keep coral alive longer at low levels.")
    double survivalSecondsBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional survival seconds gained across levels.", impact = "Higher values make higher levels protect coral for much longer.")
    double survivalSecondsFactor = 240;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance bonemeal grows a new coral block from live coral.", impact = "Higher values make bonemeal growth succeed more often at low levels.")
    double growthChanceBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonemeal growth chance gained across levels.", impact = "Higher values make higher levels grow coral far more reliably.")
    double growthChanceFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when placing a reef block.", impact = "Higher values reward reef building with more skill XP.")
    double reefPlaceXp = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when bonemeal grows new coral.", impact = "Higher values reward coral gardening with more skill XP.")
    double growthXp = 14;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
