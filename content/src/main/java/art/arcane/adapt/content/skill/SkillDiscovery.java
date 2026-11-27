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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.DiscoveryKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryArchaeologist;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryArmor;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryBetterMending;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryCartographerPulse;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryFieldNotes;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryFindEvent;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryInsight;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryKeenEye;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryNames;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryPolymath;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryRelicAppraiser;
import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryTrailblazer;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryUnity;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryVillagerAtt;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryXpResist;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryBoon;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryFightStrike;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryFuel;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryState;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryTrigger;
import art.arcane.adapt.content.adaptation.discovery.blocks.DiscoveryWorkStrike;
import art.arcane.adapt.content.adaptation.discovery.upgraded.ExplorersLuck;
import art.arcane.adapt.content.adaptation.discovery.upgraded.Omniscience;
import art.arcane.adapt.content.adaptation.discovery.upgraded.UnityPrime;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.PlayerAdvancementDoneEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SkillDiscovery extends SimpleSkill<SkillDiscovery.Config> {
  public static final Id ID = Id.adapt("discovery");
  private final XpSource discovered = xpSource("new", Text.key("adapt.xp_guide.discovery.new"));
  private final XpSource chunk = xpSource("chunk", Text.key("adapt.xp_guide.discovery.chunk"));
  private final XpSource lootContainer = xpSource("loot-container", Text.key("adapt.xp_guide.discovery.loot"));
  private final XpSource advancement = xpSource("advancement", Text.key("adapt.xp_guide.discovery.advancement"));
  private final XpSource worlds = xpSource("world", Text.key("adapt.xp_guide.discovery.world"));
  private final XpSource orbs = xpSource("orbs", Text.key("adapt.xp_guide.discovery.orbs"));
  static final String UNKNOWN_ENTITY_TYPE = "UNKNOWN";
  private static final double DISCOVERY_RARE_VALUE = 24.0D;
  private static final long TARGET_CHECK_INTERVAL_MILLIS = 500L;

  private final Cooldowns targetCheckThrottle = cooldowns();
  private final Map<UUID, TargetObservation> lastTargetObservations = playerState();
  private int targetCursor;



  public SkillDiscovery() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.discovery.once"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FFFF));
    setInterval(50);
    setIcon(Id.minecraft("filled_map"));
    registerAdaptation(DiscoveryUnity.ID, DiscoveryUnity::new);
    registerAdaptation(DiscoveryArmor.ID, DiscoveryArmor::new);
    registerAdaptation(DiscoveryXpResist.ID, DiscoveryXpResist::new);
    registerAdaptation(DiscoveryVillagerAtt.ID, DiscoveryVillagerAtt::new);
    registerAdaptation(DiscoveryBetterMending.ID, DiscoveryBetterMending::new);
    registerAdaptation(DiscoveryArchaeologist.ID, DiscoveryArchaeologist::new);
    registerAdaptation(DiscoveryCartographerPulse.ID, DiscoveryCartographerPulse::new);
    registerAdaptation(DiscoveryInsight.ID, DiscoveryInsight::new);
    registerAdaptation(DiscoveryTrailblazer.ID, DiscoveryTrailblazer::new);
    registerAdaptation(DiscoveryFieldNotes.ID, DiscoveryFieldNotes::new);
    registerAdaptation(DiscoveryPolymath.ID, DiscoveryPolymath::new);
    registerAdaptation(DiscoveryRelicAppraiser.ID, DiscoveryRelicAppraiser::new);
    registerAdaptation(DiscoverySixthSense.ID, DiscoverySixthSense::new);
    registerAdaptation(DiscoveryKeenEye.ID, DiscoveryKeenEye::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.discovery"), Text.key("adapt.mutated.adjective.discovery"))
        .trigger(TriggerKind.WORK, DiscoveryTrigger::new)
        .strikes(DiscoveryFightStrike::new, DiscoveryWorkStrike::new)
        .boon(DiscoveryBoon::new)
        .fuel(DiscoveryFuel::new)
        .state(DiscoveryState::new)
        .toolUpgrade(Omniscience.ID, Omniscience::new,
            List.of(DiscoveryInsight.ID, DiscoveryKeenEye.ID, DiscoverySixthSense.ID), Set.of())
        .upgrade(UnityPrime.ID, UnityPrime::new, List.of(DiscoveryUnity.ID, DiscoveryPolymath.ID), Set.of())
        .upgrade(ExplorersLuck.ID, ExplorersLuck::new,
            List.of(DiscoveryTrailblazer.ID, DiscoveryFieldNotes.ID, DiscoveryRelicAppraiser.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("item.spyglass.use"), Id.minecraft("block.note_block.pling"),
            Id.minecraft("block.note_block.iron_xylophone"), Id.minecraft("block.note_block.harp")))
        .tint(Rgb.of(0xd8f4ff))
        // Better Mending, also run by Unity Prime.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("item_frame")).key("challenge_discover_items_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_discover_items_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discover_items_50", "discovery.items", 50, 500);
    registerMilestone("challenge_discover_items_250", "discovery.items", 250, 1500);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("grass_block")).key("challenge_discover_blocks_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stone_bricks"))
            .key("challenge_discover_blocks_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discover_blocks_50", "discovery.blocks", 50, 500);
    registerMilestone("challenge_discover_blocks_250", "discovery.blocks", 250, 1500);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("egg")).key("challenge_discover_mobs_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spawner"))
            .key("challenge_discover_mobs_75")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discover_mobs_25", "discovery.mobs", 25, 500);
    registerMilestone("challenge_discover_mobs_75", "discovery.mobs", 75, 1500);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("map")).key("challenge_discover_biomes_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("filled_map"))
            .key("challenge_discover_biomes_40")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discover_biomes_10", "discovery.biomes", 10, 500);
    registerMilestone("challenge_discover_biomes_40", "discovery.biomes", 40, 1500);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("apple")).key("challenge_discover_foods_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_apple"))
            .key("challenge_discover_foods_30")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discover_foods_10", "discovery.foods", 10, 500);
    registerMilestone("challenge_discover_foods_30", "discovery.foods", 30, 1500);
    registerGroup(Text.key("adapt.skill.discovery.group.exploring"), List.of(Id.parseAdapt("adapt:discovery/insight"), Id.parseAdapt("adapt:discovery/keen_eye"), Id.parseAdapt("adapt:discovery/sixth_sense"), Id.parseAdapt("adapt:discovery/cartographer_pulse"), Id.parseAdapt("adapt:discovery/trailblazer"), Id.parseAdapt("adapt:discovery/archaeologist"), Id.parseAdapt("adapt:discovery/relic_appraiser"), Id.parseAdapt("adapt:discovery/world_armor")));
    registerGroup(Text.key("adapt.skill.discovery.group.experience"), List.of(Id.parseAdapt("adapt:discovery/field_notes"), Id.parseAdapt("adapt:discovery/villager_att"), Id.parseAdapt("adapt:discovery/better_mending"), Id.parseAdapt("adapt:discovery/xp_resist"), Id.parseAdapt("adapt:discovery/polymath"), Id.parseAdapt("adapt:discovery/unity")));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    shouldReturnForPlayer(e.player(), () -> scheduleSeeWorld(e.player()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    if (e.clickedPosition() == null) {
      return;
    }
    shouldReturnForPlayer(e.player(), e, () -> seeEntity(e.player(), e.clicked()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPickupItemEvent e) {
    if (e.entity() instanceof PlayerRef p) {
      shouldReturnForPlayer(p, e, () -> seeItem(p, e.item().item()));
    }

  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (e.recipeKey() != null) {
        seeCraftedRecipe(p, e.recipeKey().toString());
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> {
      seeItem(e.player(), e.item());
      seeFood(e.player(), e.item().type());
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    shouldReturnForPlayer(e.player(), e, () -> {
      if (e.clickedBlock() != null) {
        seeBlock(e.player(), e.clickedBlock().state(), e.clickedBlock().location());
      }
    });

  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    shouldReturnForPlayer(e.player(), () -> {
      if (e.amount() > 0 && e.player().level() > 0) {
        orbs.award(e.player(), e.amount());
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    if (to == null || to.world() == null || !from.sameWorld(to)
        || (from.chunkX() == to.chunkX() && from.chunkZ() == to.chunkZ())) {
      return;
    }
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      PlayerProfile profile = getPlayer(p);
      if (profile != null && profile.discoverChunk(to.world().uid(), to.chunkX(), to.chunkZ())) {
        chunk.awardSilent(p, getConfig().discoverChunkXP);
        found(p, to, true);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerLootGenerateEvent e) {
    shouldReturnForPlayer(e.player(), () -> lootContainer.award(e.player(), e.location(), getConfig().discoverLootContainerXP));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerAdvancementDoneEvent e) {
    double amount = advancementXp(e.advancement(), e.frame(), getConfig().advancementTaskXP,
        getConfig().advancementGoalXP, getConfig().advancementChallengeXP);
    if (amount > 0D) {
      shouldReturnForPlayer(e.player(), () -> advancement.award(e.player(), amount));
    }
  }

  static double advancementXp(Id advancement, AdvancementFrame frame, double taskXp, double goalXp, double challengeXp) {
    if (frame == null || Id.ADAPT.equals(advancement.namespace()) || advancement.path().startsWith("recipes/")) {
      return 0D;
    }
    double amount = switch (frame) {
      case TASK -> taskXp;
      case GOAL -> goalXp;
      case CHALLENGE -> challengeXp;
    };
    return Math.max(0D, amount);
  }

  private void scheduleSeeWorld(PlayerRef p) {
    try {
      AdaptApi.scheduler().runEntity(p, () -> seeWorld(p, p.world()), 15);
    } catch (Exception e) {
      AdaptApi.log().error("Failed to discover world " + p.world().name(), null);
    }
  }

  public void seeBlock(PlayerRef p, BlockStateRef bd, Loc l) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.BLOCK, bd.asString())) {
      double value = getValue(bd);
      discovered.award(p, getConfig().discoverBlockBaseXP + (value * getConfig().discoverBlockValueXPMultiplier));
      addStat(p, "discovery.blocks", 1);
      celebrateDiscovery(l.add(0.5, 0.5, 0.5), value);
      found(p, l, false);
    }

    seeItem(p, bd.type());
  }

  private void celebrateDiscovery(Loc loc, double value) {
    boolean force = getConfig().forceDiscoveryParticles;
    FxPriority priority = force ? FxPriority.GAMEPLAY : FxPriority.AMBIENT;
    if (value >= DISCOVERY_RARE_VALUE) {
      timeline(loc)
          .duration(8)
          .priority(priority)
          .cullRadius(20)
          .frame((f, tick, progress) -> {
            if (force) {
              f.forced();
            }
            f.dustHelix(0.5D, 1.6D, 6, progress * Math.PI * 2.0D, 0.9F);
            if (tick == 0) {
              f.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.0F, Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.3F, 1.0F);
            }
            if (tick == 4) {
              f.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
            }
          })
          .start();
    } else {
      FxEmitter emitter = fx(loc, priority);
      if (force) {
        emitter.forced();
      }
      emitter.particle(Particles.END_ROD, 3, 0, 0, 0, 0.1, 0.02);
    }
  }

  public void seeItem(PlayerRef p, Id bd) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.ITEM, DiscoveryNames.enumName(bd))) {
      discovered.award(p, getConfig().discoverItemBaseXP + (getValue(bd) * getConfig().discoverItemValueXPMultiplier));
      addStat(p, "discovery.items", 1);
      found(p, p.location(), false);
    }
  }

  public void seeItem(PlayerRef p, ItemRef bd) {
    seeItem(p, bd.type());
    Map<Id, Integer> m = bd.enchantments();

    for (Id i : m.keySet()) {
      seeEnchant(p, i, m.get(i));
    }
  }

  public void seeCraftedRecipe(PlayerRef p, String key) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.RECIPE, key)) {
      discovered.award(p, getConfig().discoverRecipeBaseXP);
      found(p, p.location(), false);
    }
  }

  public void seeFood(PlayerRef p, Id bd) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.FOOD, DiscoveryNames.enumName(bd))) {
      discovered.award(p, getConfig().discoverFoodTypeXP);
      addStat(p, "discovery.foods", 1);
      found(p, p.location(), false);
    }
  }

  public void seeEntity(PlayerRef p, EntityRef bd) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.MOB, bd.type() == null ? UNKNOWN_ENTITY_TYPE : DiscoveryNames.enumName(bd.type()))) {
      discovered.award(p, getConfig().discoverEntityTypeXP);
      addStat(p, "discovery.mobs", 1);
      found(p, p.location(), false);
    }

    if (bd instanceof PlayerRef seen) {
      seePlayer(p, seen);
    }

    if (bd instanceof LivingRef living) {
      for (EffectInstance i : living.effects()) {
        seePotionEffect(p, i);
      }
    }
  }

  public void seePlayer(PlayerRef p, PlayerRef bd) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.PERSON, bd.uuid().toString())) {
      discovered.award(p, getConfig().discoverPlayerXP);
      found(p, p.location(), false);
    }
  }

  public void seeEnchant(PlayerRef p, Id bd, int level) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.ENCHANTMENT, DiscoveryNames.enchantment(bd) + " " + Format.toRoman(level))) {
      discovered.award(p, getConfig().discoverEnchantBaseXP + Math.min(getConfig().discoverEnchantMaxXP, level * getConfig().discoverEnchantLevelXPMultiplier));
      found(p, p.location(), false);
    }
  }

  public void seeWorld(PlayerRef p, WorldRef world) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.WORLD, world.key() + "-" + world.seed())) {
      worlds.award(p, getConfig().discoverWorldXP);
      found(p, p.location(), false);
    }

    seeEnvironment(p, world.environment());
  }

  public void seeEnvironment(PlayerRef p, Environment world) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.ENVIRONMENT, world.name())) {
      worlds.award(p, getConfig().discoverEnvironmentXP);
      found(p, p.location(), false);
    }
  }

  public void seePotionEffect(PlayerRef p, EffectInstance e) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null) {
      return;
    }
    if (playerData.discover(DiscoveryKind.POTION_EFFECT, DiscoveryNames.effect(e.type()) + " " + Format.toRoman(e.amplifier()).trim())) {
      discovered.award(p, getConfig().discoverPotionXP);
      found(p, p.location(), false);
    }
  }

  public boolean seeBiome(PlayerRef p, Id e) {
    PlayerProfile playerData = getPlayer(p);
    if (playerData == null || e == null) {
      return false;
    }
    if (playerData.discover(DiscoveryKind.BIOME, e.toString())) {
      discovered.award(p, getConfig().discoverBiomeXP);
      addStat(p, "discovery.biomes", 1);
      found(p, p.location(), false);
      return true;
    }

    return false;
  }

  private static void found(PlayerRef p, Loc at, boolean chunk) {
    if (at != null) {
      AdaptApi.events().post(new DiscoveryFindEvent(p, at, chunk));
    }
  }

  @Override
  public void onTick() {
    if (!isEnabled()) {
      return;
    }

    List<PlayerProfile> online = AdaptApi.players().online();
    int size = online.size();
    int limit = targetCheckBatchSize(size, getConfig().maxTargetChecksPerPass);
    if (limit == 0) {
      targetCursor = 0;
      return;
    }

    int start = Math.floorMod(targetCursor, size);
    for (int offset = 0; offset < limit; offset++) {
      queueTargetCheck(online.get((start + offset) % size));
    }
    targetCursor = (start + limit) % size;
  }

  static int targetCheckBatchSize(int playerCount, int configuredLimit) {
    if (playerCount <= 0) {
      return 0;
    }
    return Math.max(1, Math.min(playerCount, configuredLimit));
  }

  private void queueTargetCheck(PlayerProfile adaptPlayer) {
    PlayerRef player = adaptPlayer.player();
    if (player == null || !player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    if (!targetCheckThrottle.isReady(playerId, TARGET_CHECK_INTERVAL_MILLIS)) {
      return;
    }

    targetCheckThrottle.mark(playerId);
    shouldReturnForPlayer(player, () -> seeTargetBlock(player));
  }

  private void seeTargetBlock(PlayerRef player) {
    try {
      BlockRef block = player.targetBlock(5);
      UUID playerId = player.uuid();
      if (block == null) {
        lastTargetObservations.remove(playerId);
        return;
      }

      BlockStateRef blockData = block.state();
      Id biome = block.biome();
      UUID worldId = block.world().uid();
      TargetObservation previous = lastTargetObservations.get(playerId);
      if (previous != null && previous.matches(worldId, block.x(), block.y(), block.z(), blockData, biome)) {
        return;
      }

      TargetObservation observation = new TargetObservation(
          worldId,
          block.x(),
          block.y(),
          block.z(),
          blockData,
          biome
      );
      seeBlock(player, blockData, block.location());
      boolean discoveredBiome = seeBiome(player, biome);
      lastTargetObservations.put(playerId, observation);
      if (discoveredBiome) {
        timeline(player.location())
            .duration(24)
            .priority(FxPriority.TRANSITION)
            .cullRadius(24)
            .frame((f, tick, progress) -> {
              f.dustRing(0.5D + (progress * 3.5D), 12, 1.0F);
              if (tick == 0) {
                f.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.2F, Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.3F, 1.4F);
              }
            })
            .start();
      }
    } catch (Throwable ex) {
      AdaptApi.log().verbose("Failed to get target block for " + player.name() + ": "
          + ex.getClass().getSimpleName()
          + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
    }
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Show Particles for the Discovery skill.", impact = "True enables this behavior and false disables it.")
    boolean showParticles = true;
    @ConfigDoc(value = "Sends new-discovery particles forced, so they show on reduced particle settings and are never thinned under load.", impact = "True forces them and false treats them as ambient effects.")
    boolean forceDiscoveryParticles = true;
    @ConfigDoc(value = "Controls Discover Biome XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverBiomeXP = 15;
    @ConfigDoc(value = "Controls Discover Potion XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverPotionXP = 36;
    @ConfigDoc(value = "Controls Discover Entity Type XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverEntityTypeXP = 125;
    @ConfigDoc(value = "Controls Discover Food Type XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverFoodTypeXP = 75;
    @ConfigDoc(value = "Controls Discover Player XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverPlayerXP = 125;
    @ConfigDoc(value = "Controls Discover Environment XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverEnvironmentXP = 750;
    @ConfigDoc(value = "XP for completing a non-Adapt task advancement.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double advancementTaskXP = 150;
    @ConfigDoc(value = "XP the first time you enter each chunk.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverChunkXP = 2;
    @ConfigDoc(value = "XP for opening a naturally generated loot container before anyone else.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverLootContainerXP = 40;
    @ConfigDoc(value = "XP for completing a non-Adapt goal advancement.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double advancementGoalXP = 400;
    @ConfigDoc(value = "XP for completing a non-Adapt challenge advancement.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double advancementChallengeXP = 1000;
    @ConfigDoc(value = "Controls Discover World XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverWorldXP = 750;
    @ConfigDoc(value = "Controls Discover Enchant Max XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverEnchantMaxXP = 250;
    @ConfigDoc(value = "Controls Discover Enchant Level XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverEnchantLevelXPMultiplier = 52;
    @ConfigDoc(value = "Controls Discover Enchant Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverEnchantBaseXP = 5;
    @ConfigDoc(value = "Controls Discover Item Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverItemBaseXP = 10;
    @ConfigDoc(value = "Controls Discover Recipe Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverRecipeBaseXP = 15;
    @ConfigDoc(value = "Controls Discover Item Value XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverItemValueXPMultiplier = 1;
    @ConfigDoc(value = "Controls Discover Block Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverBlockBaseXP = 3;
    @ConfigDoc(value = "Controls Discover Block Value XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double discoverBlockValueXPMultiplier = 0.333;
    @ConfigDoc(value = "Maximum target-block checks per scheduler pass.", impact = "Higher values refresh very large servers faster but increase per-tick ray tracing.")
    int maxTargetChecksPerPass = 64;

    public Config() {
    }
  }

  private record TargetObservation(UUID worldId, int x, int y, int z, BlockStateRef blockData, Id biome) {
    private boolean matches(UUID worldId, int x, int y, int z, BlockStateRef blockData, Id biome) {
      return this.x == x
          && this.y == y
          && this.z == z
          && this.worldId.equals(worldId)
          && this.blockData.equals(blockData)
          && Objects.equals(this.biome, biome);
    }
  }
}
