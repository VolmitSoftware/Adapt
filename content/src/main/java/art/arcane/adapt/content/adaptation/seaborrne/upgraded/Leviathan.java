package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneBreath;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneDeepSalvager;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneRainFed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneShimmer;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneShrines;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSwim;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTidalWard;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesMiningSpeed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWet;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockFadeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Organic Oxygen Tank, Dolphin's Grace, Tidal Ward, Brine Skin and Rain-Fed fused: more air, a faster swim and
 * regeneration while wet, and the lent Tidal Ward and Rain-Fed run on this upgrade's tick. Each part keeps its original's version requirements and does nothing on a
 * server that lacks them. Its Tide Shrine makes the water around it breathable, lit and calm for the owner: drowned
 * and guardians stop targeting them, they mine at full speed, and coral near it never dies.
 */
public class Leviathan extends SkillUpgrade<Leviathan.Config> {
  private static final Id WATER = Id.minecraft("water");
  private static final Id SEA_LANTERN = Id.minecraft("sea_lantern");
  private static final Id NAUTILUS_SHELL = Id.minecraft("nautilus_shell");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");
  private static final Id WATER_BREATHING = Id.minecraft("water_breathing");
  private static final Set<Id> CALMED = Set.of(Id.minecraft("drowned"), Id.minecraft("guardian"), Id.minecraft("elder_guardian"));
  private static final int OUTLINE_TICKS = 100;

  private final Map<UUID, LingeringSound> wet = playerState();
  private final Map<UUID, Boolean> inShrine = playerState();
  private final SeaborneShrines shrines = new SeaborneShrines();
  private final SeaborneShimmer outline = new SeaborneShimmer(this, (PlayerRef player) -> player.isOnline(),
      (PlayerRef player) -> SeaborneShimmer.color(SeaborneDeepSalvager.OutlineColor.AQUA), (PlayerRef player) -> {
      });

  public static final Id ID = Id.adapt("seaborne/upgrade/leviathan");

  public Leviathan() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("heart_of_the_sea"));
    setInterval(1000);
    registerTool(new TideShrine(this));
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      release(player.player());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getSavedAirFraction(level), 0), 1);
    statLore(v, Format.pc(getSwimEfficiency(level), 0), 2);
    statLore(v, getRegenAmplifier(level) + 1, 4);
    statLore(v, Format.f(getShrineRadius(level), 0), 5);
    statLore(v, getMaxShrines(level), 6);
    grantedLore(v, level);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (PlayerProfile adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> refresh(player));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    release(e.player());
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player) || !CALMED.contains(e.entity().type())
        || e.reason() == EntityTargetEvent.Reason.TARGET_ATTACKED_ENTITY) {
      return;
    }
    int level = getActiveLevel(player);
    if (level > 0 && coveringShrine(player, level) != null) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFadeEvent e) {
    BlockRef block = e.block();
    if (!SeaborneCoralGardener.isFadeableCoral(block.type())) {
      return;
    }
    Loc at = block.center();
    for (UUID owner : shrines.owners()) {
      PlayerRef player = AdaptApi.platform().host().player(owner);
      int level = player == null || !player.isOnline() ? 0 : getActiveLevel(player);
      if (level <= 0) {
        continue;
      }
      for (SeaborneShrines.Shrine shrine : shrines.of(owner)) {
        if (shrine.covers(at, getShrineRadius(level))) {
          e.setCancelled(true);
          return;
        }
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (shrineAt(e.block()) != null) {
      e.setDropItems(false);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBroken(BlockBreakEvent e) {
    SeaborneShrines.Shrine shrine = shrineAt(e.block());
    if (shrine != null) {
      shrines.remove(shrine);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(BlockExplodeEvent e) {
    e.blocks().removeIf((BlockRef block) -> shrineAt(block) != null);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityExplodeEvent e) {
    e.blocks().removeIf((BlockRef block) -> shrineAt(block) != null);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPistonEvent e) {
    for (BlockRef block : e.blocks()) {
      if (shrineAt(block) != null) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @Override
  public void unregister() {
    outline.clear();
    super.unregister();
  }

  /** Sets a Tide Shrine at {@code target} for {@code player}, paid with a nautilus shell through a build batch. */
  void placeShrine(PlayerRef player, BlockRef target, int level) {
    if (!WATER.equals(target.type()) || !WATER.equals(target.relative(BlockFace.UP).type())) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.seaborne.tide_shrine.water").resolve());
      return;
    }
    pruneVanished(player);
    if (shrines.of(player.uuid()).size() >= getMaxShrines(level)) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.seaborne.tide_shrine.full").resolve());
      return;
    }
    BlockStateRef lantern = AdaptApi.platform().host().items().blockState(SEA_LANTERN);
    if (lantern == null) {
      return;
    }
    ItemRef shell = AdaptApi.platform().host().items().create(NAUTILUS_SHELL, 1);
    BuildBatches.start(BuildBatch.of(this, player).limit(1).pace(1).place(target, lantern, shell)
        .onDone((BuildResult result) -> placed(player, target, level, result)).build());
  }

  /** Forgets the player's shrines in loaded chunks that are no longer sea lanterns, however they went. */
  private void pruneVanished(PlayerRef player) {
    for (SeaborneShrines.Shrine shrine : shrines.of(player.uuid())) {
      WorldRef world = AdaptApi.platform().host().world(shrine.world());
      if (world != null && world.isChunkLoaded(shrine.x() >> 4, shrine.z() >> 4)
          && AdaptApi.scheduler().owns(world, shrine.x() >> 4, shrine.z() >> 4)
          && !SEA_LANTERN.equals(world.blockAt(shrine.x(), shrine.y(), shrine.z()).type())) {
        shrines.remove(shrine);
      }
    }
  }

  private void placed(PlayerRef player, BlockRef target, int level, BuildResult result) {
    if (result.placed() > 0) {
      SeaborneShrines.Shrine shrine = new SeaborneShrines.Shrine(player.uuid(), target.world().uid(), target.x(),
          target.y(), target.z());
      shrines.add(shrine);
      showShrine(player, shrine, level);
      cue(player, MutationCue.WORK);
      return;
    }
    if (result.stop() == BuildResult.Stop.PROTECTED) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.seaborne.tide_shrine.protected").resolve());
    } else if (result.stop() == BuildResult.Stop.NO_ITEMS) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.seaborne.tide_shrine.shell").resolve());
    }
  }

  /** The player's own standing shrine whose radius at {@code level} reaches them, or null. */
  SeaborneShrines.Shrine coveringShrine(PlayerRef player, int level) {
    double radius = getShrineRadius(level);
    for (SeaborneShrines.Shrine shrine : shrines.of(player.uuid())) {
      if (!shrine.covers(player.location(), radius)) {
        continue;
      }
      if (SEA_LANTERN.equals(player.world().blockAt(shrine.x(), shrine.y(), shrine.z()).type())) {
        return shrine;
      }
      shrines.remove(shrine);
    }
    return null;
  }

  public List<SeaborneShrines.Shrine> shrinesOf(PlayerRef player) {
    return shrines.of(player.uuid());
  }

  private SeaborneShrines.Shrine shrineAt(BlockRef block) {
    return SEA_LANTERN.equals(block.type()) ? shrines.at(block) : null;
  }

  private void showShrine(PlayerRef player, SeaborneShrines.Shrine shrine, int level) {
    Loc center = new Loc(player.world(), shrine.x() + 0.5D, shrine.y() + 0.5D, shrine.z() + 0.5D);
    BlockStateRef lantern = AdaptApi.platform().host().items().blockState(SEA_LANTERN);
    if (lantern != null) {
      outline.outline(player, "shrine-" + shrine.x() + ":" + shrine.y() + ":" + shrine.z(), center.add(-0.5D, -0.5D, -0.5D),
          lantern, OUTLINE_TICKS);
    }
    fx(center, FxPriority.GAMEPLAY).ring(Particles.GLOW, getShrineRadius(level), 48, 0D);
  }

  private void refreshShrine(PlayerRef player, int level, boolean submerged) {
    SeaborneShrines.Shrine shrine = coveringShrine(player, level);
    if (shrine == null) {
      leaveShrine(player);
      return;
    }
    calm(player, level);
    if (inShrine.put(player.uuid(), true) == null) {
      showShrine(player, shrine, level);
      cue(player, MutationCue.BOON);
    }
    if (!submerged) {
      removeShrineMining(player);
      return;
    }
    int ticks = getConfig().shrineEffectTicks;
    if (available(List.of(SeaborneBreath.NEED))) {
      SeaborneBreath.apply(this, player, SeaborneBreath.oxygenBonus(1D));
    } else {
      player.addEffect(EffectInstance.of(WATER_BREATHING, ticks, 0).withAmbient(true).withParticles(false));
    }
    player.addEffect(EffectInstance.of(NIGHT_VISION, ticks, 0).withAmbient(true).withParticles(false));
    if (available(SeaborneTurtlesMiningSpeed.NEEDS)) {
      SeaborneTurtlesMiningSpeed.applySubmerged(this, player, getConfig().shrineMiningMultiplier, ticks);
      if (player.isOnGround() || !SeaborneTurtlesMiningSpeed.applyFloating(this, player, getConfig().shrineFloatingMultiplier,
          ticks)) {
        SeaborneTurtlesMiningSpeed.removeFloating(this, player);
      }
    }
  }

  private void calm(PlayerRef player, int level) {
    double radius = getShrineRadius(level);
    for (EntityRef entity : player.world().nearbyEntities(player.location().position(), radius, radius, radius)) {
      if (entity instanceof MobRef mob && CALMED.contains(mob.type()) && mob.target() != null
          && player.uuid().equals(mob.target().uuid())) {
        mob.setTarget(null);
      }
    }
  }

  private void leaveShrine(PlayerRef player) {
    if (inShrine.remove(player.uuid()) != null) {
      removeShrineMining(player);
    }
  }

  private void removeShrineMining(PlayerRef player) {
    if (available(SeaborneTurtlesMiningSpeed.NEEDS)) {
      SeaborneTurtlesMiningSpeed.removeSubmerged(this, player);
      SeaborneTurtlesMiningSpeed.removeFloating(this, player);
    }
  }

  private void refresh(PlayerRef player) {
    if (!player.isOnline()) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      release(player);
      return;
    }

    boolean submerged = submerged(player);
    if (available(List.of(SeaborneBreath.NEED))) {
      SeaborneBreath.apply(this, player, SeaborneBreath.oxygenBonus(getSavedAirFraction(level)));
    }
    if (available(List.of(SeaborneSwim.NEED))) {
      if (submerged && !SeaborneSwim.wearsDepthStrider(player)) {
        SeaborneSwim.applyBoost(this, player, getSwimEfficiency(level));
        SeaborneSwim.applyGrace(player, getGraceTicks(level));
      } else {
        SeaborneSwim.removeBoost(this, player);
      }
    }
    refreshWet(player, level);
    refreshShrine(player, level, submerged);
    long now = System.currentTimeMillis();
    SeaborneTidalWard ward = lentOriginal(SeaborneTidalWard.class, player);
    if (ward != null) {
      ward.refresh(player, now, getInterval() / 1000D);
    }
    SeaborneRainFed fed = lentOriginal(SeaborneRainFed.class, player);
    if (fed != null) {
      fed.refill(player, now, getInterval() / 1000D);
    }
  }

  /** The skill's original of {@code type} when this upgrade lends it to the player, who does not hold it themselves. */
  private <A extends Adaptation<?>> A lentOriginal(Class<A> type, PlayerRef player) {
    if (getSkill() == null) {
      return null;
    }
    PlayerProfile profile = getPlayer(player);
    for (Adaptation<?> original : getSkill().getAdaptations()) {
      if (type.isInstance(original) && fuses(original) && (profile == null || profile.level(original.getId()) <= 0)) {
        return type.cast(original);
      }
    }
    return null;
  }

  private void refreshWet(PlayerRef player, int level) {
    UUID id = player.uuid();
    if (!SeaborneWet.isWet(player)) {
      stopWet(id);
      return;
    }

    SeaborneWet.refreshRegeneration(player, getRegenAmplifier(level));
    if (!wet.containsKey(id)) {
      wet.put(id, linger(player));
      cue(player, MutationCue.BOON);
    }
  }

  private void release(PlayerRef player) {
    stopWet(player.uuid());
    leaveShrine(player);
    SeaborneSwim.removeBoost(this, player);
    SeaborneBreath.remove(this, player);
    SeaborneTidalWard ward = lentOriginal(SeaborneTidalWard.class, player);
    if (ward != null) {
      ward.drop(player);
    }
    SeaborneRainFed fed = lentOriginal(SeaborneRainFed.class, player);
    if (fed != null) {
      fed.forget(player);
    }
  }

  private void stopWet(UUID id) {
    LingeringSound sound = wet.remove(id);
    if (sound != null) {
      sound.stop();
    }
  }

  private static boolean submerged(PlayerRef player) {
    return player.isInWater() || player.isSwimming();
  }

  private static boolean available(Collection<PlatformNeed> needs) {
    return PlatformNeed.usable(needs);
  }

  double getSavedAirFraction(int level) {
    return SeaborneBreath.savedAirFraction(level, getConfig().airTicksPerLevel, SeaborneBreath.TICKS_PER_PULSE);
  }

  double getSwimEfficiency(int level) {
    return Math.min(1D, getConfig().swimEfficiencyBase + (level * getConfig().swimEfficiencyPerLevel));
  }

  int getGraceTicks(int level) {
    return Math.max(0, getConfig().graceTicksBase + (level * getConfig().graceTicksPerLevel));
  }

  double getShrineRadius(int level) {
    return Math.max(1D, getConfig().shrineRadiusBase + (level * getConfig().shrineRadiusPerLevel));
  }

  int getMaxShrines(int level) {
    return level >= getMaxLevel() ? getConfig().shrinesAtTopLevel : getConfig().shrinesBelowTopLevel;
  }

  int getRegenAmplifier(int level) {
    return Math.max(0, Math.min(getConfig().maxRegenAmplifier, level - 1));
  }

  @ConfigDescription("More air, a faster swim and regeneration while wet, with the tidal ward and rain feeding of the originals it fuses.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Air ticks saved per level out of every 75 ticks underwater.", impact = "Higher values stretch your breath further; 25 per level never runs out at level 3.")
    int airTicksPerLevel = 20;
    @ConfigDoc(value = "Water movement efficiency before level scaling.", impact = "Higher values make every level swim faster.")
    double swimEfficiencyBase = 0.4;
    @ConfigDoc(value = "Water movement efficiency added per level.", impact = "Higher values widen the gap between levels; the total caps at 1.")
    double swimEfficiencyPerLevel = 0.2;
    @ConfigDoc(value = "Dolphin's Grace ticks granted while swimming, before level scaling.", impact = "Higher values keep the grace alive between refreshes.")
    int graceTicksBase = 40;
    @ConfigDoc(value = "Dolphin's Grace ticks added per level.", impact = "Higher values let the grace linger longer at higher levels.")
    int graceTicksPerLevel = 20;
    @ConfigDoc(value = "Highest Regeneration amplifier while wet; level 1 starts at 0.", impact = "Higher values allow faster healing at the top level.")
    int maxRegenAmplifier = 2;
    @ConfigDoc(value = "Tide Shrine radius in blocks before level scaling.", impact = "Higher values make every shrine reach farther.")
    double shrineRadiusBase = 4;
    @ConfigDoc(value = "Tide Shrine radius added per level, in blocks.", impact = "Higher values widen the gap between levels.")
    double shrineRadiusPerLevel = 4;
    @ConfigDoc(value = "Tide Shrines you can have placed below the top level.", impact = "Higher values allow more shrines early.")
    int shrinesBelowTopLevel = 1;
    @ConfigDoc(value = "Tide Shrines you can have placed at the top level.", impact = "Higher values allow more shrines at the top level.")
    int shrinesAtTopLevel = 2;
    @ConfigDoc(value = "Underwater mining speed multiplier inside your shrine.", impact = "5 cancels the vanilla underwater mining penalty.")
    double shrineMiningMultiplier = 5;
    @ConfigDoc(value = "Block break speed multiplier while floating inside your shrine.", impact = "5 cancels the vanilla floating mining penalty.")
    double shrineFloatingMultiplier = 5;
    @ConfigDoc(value = "Length of each refreshed breathing, night vision and mining effect inside your shrine, in ticks.", impact = "Higher values keep the effects longer after you leave.")
    int shrineEffectTicks = 220;
  }
}
