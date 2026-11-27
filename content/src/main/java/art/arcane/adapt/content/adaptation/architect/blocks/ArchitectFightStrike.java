package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.architect.ArchitectTemporaryBlocks;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

/**
 * A 2-high wall rises halfway between the player and the target, across the line between them. It is the same
 * temporary glass Magic Foundation raises, journaled and protected on its own, so it works whether or not Foundation is
 * enabled.
 */
public class ArchitectFightStrike extends StrikeBlock<ArchitectFightStrike.Config> {
  private static final int HEIGHT = 2;
  private final ArchitectTemporaryBlocks wall = new ArchitectTemporaryBlocks(this, "architect-wall", (BlockRef block) ->
      fx(block.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
          .burst(Particles.ASH, 4, 0.2D)
          .sound(Sounds.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.0f));

  public ArchitectFightStrike() {
    super(SkillArchitect.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("tinted_glass"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)) {
      return Outcome.MISSED;
    }
    Loc from = player.location();
    Loc to = target.location();
    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    double distance = Math.sqrt((dx * dx) + (dz * dz));
    if (distance < getConfig().minDistance || !from.sameWorld(to)) {
      return Outcome.MISSED;
    }
    WorldRef world = from.world();
    double midX = from.x() + (dx / 2D);
    double midZ = from.z() + (dz / 2D);
    int baseY = Math.min(from.blockY(), to.blockY());
    int half = (int) Math.round(use.scaled(getConfig().widthAtFirstLevel, getConfig().widthAtTopLevel)) / 2;
    int ticks = (int) Math.max(1L, getConfig().wallMillis / 50L);
    int raised = 0;
    for (int across = -half; across <= half; across++) {
      int x = (int) Math.floor(midX - ((dz / distance) * across));
      int z = (int) Math.floor(midZ + ((dx / distance) * across));
      for (int up = 0; up < HEIGHT; up++) {
        BlockRef cell = world.blockAt(x, baseY + up, z);
        if (!occupied(cell, from) && !occupied(cell, to) && wall.raise(player, cell, ticks)) {
          raised++;
        }
      }
    }
    return raised > 0 ? Outcome.INSTANT : Outcome.MISSED;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    wall.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    wall.spare(e.blocks());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    wall.on(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    wall.spare(e.blocks());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    wall.on(e);
  }

  @Override
  protected void onRuntimeActivated() {
    wall.recoverLoadedChunks();
  }

  @Override
  public void unregister() {
    super.unregister();
    wall.clear();
  }

  private static boolean occupied(BlockRef cell, Loc entity) {
    return cell.x() == entity.blockX() && cell.z() == entity.blockZ()
        && cell.y() >= entity.blockY() && cell.y() <= entity.blockY() + 1;
  }

  @ConfigDescription("A temporary 2-high wall rises between you and the target.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Width of the wall in blocks at level 1.", impact = "Higher values build a wider wall.")
    double widthAtFirstLevel = 3D;
    @ConfigDoc(value = "Width of the wall in blocks at the top level.", impact = "Higher values build a wider wall.")
    double widthAtTopLevel = 5D;
    @ConfigDoc(value = "Closest the target may be for a wall to fit, in blocks.", impact = "Lower values raise walls at closer range.")
    double minDistance = 2D;
    @ConfigDoc(value = "How long the wall stands, in milliseconds.", impact = "Higher values keep the wall up longer.")
    long wallMillis = 3_000L;
  }
}
