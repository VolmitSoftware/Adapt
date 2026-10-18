package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.function.IntSupplier;

final class CopperGolemController implements GolemController {
  private final CopperGolem golem;
  private final GolemPathing pathing;
  private Block interaction;
  private int interactionEndsAt;

  CopperGolemController(CopperGolem golem, IntSupplier repathTicks) {
    this.golem = Objects.requireNonNull(golem);
    this.pathing = new GolemPathing(golem, repathTicks);
  }

  @Override
  public void attach() {
  }

  @Override
  public void moveTo(Location destination, double speed) {
    pathing.moveTo(destination, speed);
  }

  @Override
  public void stop() {
    pathing.stop();
  }

  @Override
  public void setTarget(LivingEntity target) {
    golem.setTarget(target);
  }

  @Override
  public void interact(Block container, int ticks) {
    interaction = Objects.requireNonNull(container);
    interactionEndsAt = golem.getTicksLived() + Math.max(1, ticks);
    golem.setGolemState(CopperGolem.State.DROPPING_ITEM);
  }

  @Override
  public boolean interacting(Block block) {
    return interaction != null && golem.getTicksLived() < interactionEndsAt && sameBlock(interaction, block);
  }

  @Override
  public void tick() {
    if (interaction != null && golem.getTicksLived() >= interactionEndsAt) {
      interaction = null;
    }
    if (interaction == null && golem.getGolemState() != CopperGolem.State.IDLE) {
      golem.setGolemState(CopperGolem.State.IDLE);
      pathing.restart();
    }
    pathing.tick();
  }

  @Override
  public void release() {
    interaction = null;
    pathing.stop();
  }

  private static boolean sameBlock(Block first, Block second) {
    return first.getX() == second.getX() && first.getY() == second.getY() && first.getZ() == second.getZ()
        && Objects.equals(first.getWorld(), second.getWorld());
  }
}
