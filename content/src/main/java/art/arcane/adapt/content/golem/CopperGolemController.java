package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;
import java.util.function.IntSupplier;

final class CopperGolemController implements GolemController {
  private final MobRef golem;
  private final GolemPathing pathing;
  private boolean interacting;
  private int interactionEndsAt;

  CopperGolemController(MobRef golem, IntSupplier repathTicks) {
    this.golem = Objects.requireNonNull(golem);
    this.pathing = new GolemPathing(golem, repathTicks, true);
  }

  @Override
  public void attach() {
  }

  @Override
  public void moveTo(Loc destination, double speed) {
    pathing.moveTo(destination, speed);
  }

  @Override
  public void stop() {
    interacting = false;
    if (golem.copperGolemState() != CopperGolemState.IDLE) {
      golem.setCopperGolemState(CopperGolemState.IDLE);
    }
    pathing.stop();
  }

  @Override
  public void setTarget(LivingRef target) {
    golem.setTarget(target);
  }

  @Override
  public void interact(BlockRef container, int ticks) {
    interacting = true;
    interactionEndsAt = golem.ticksLived() + Math.max(1, ticks);
    golem.setCopperGolemState(CopperGolemState.DROPPING_ITEM);
  }

  @Override
  public void tick() {
    if (interacting && golem.ticksLived() >= interactionEndsAt) {
      interacting = false;
    }
    if (!interacting && golem.copperGolemState() != CopperGolemState.IDLE) {
      golem.setCopperGolemState(CopperGolemState.IDLE);
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
