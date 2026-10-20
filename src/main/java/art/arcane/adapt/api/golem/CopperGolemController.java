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
  private boolean interacting;
  private int interactionEndsAt;

  CopperGolemController(CopperGolem golem, IntSupplier repathTicks) {
    this.golem = Objects.requireNonNull(golem);
    this.pathing = new GolemPathing(golem, repathTicks, true);
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
    interacting = false;
    if (golem.getGolemState() != CopperGolem.State.IDLE) {
      golem.setGolemState(CopperGolem.State.IDLE);
    }
    pathing.stop();
  }

  @Override
  public void setTarget(LivingEntity target) {
    golem.setTarget(target);
  }

  @Override
  public void interact(Block container, int ticks) {
    interacting = true;
    interactionEndsAt = golem.getTicksLived() + Math.max(1, ticks);
    golem.setGolemState(CopperGolem.State.DROPPING_ITEM);
  }

  @Override
  public void tick() {
    if (interacting && golem.getTicksLived() >= interactionEndsAt) {
      interacting = false;
    }
    if (!interacting && golem.getGolemState() != CopperGolem.State.IDLE) {
      golem.setGolemState(CopperGolem.State.IDLE);
      pathing.restart();
    }
    pathing.tick();
  }

  @Override
  public void release() {
    interacting = false;
    pathing.stop();
  }
}
