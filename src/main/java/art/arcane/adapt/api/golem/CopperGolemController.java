package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.function.IntSupplier;

final class CopperGolemController implements GolemController {
  private final CopperGolem golem;
  private final GolemPathing pathing;

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
  public void tick() {
    if (golem.getGolemState() != CopperGolem.State.IDLE) {
      golem.setGolemState(CopperGolem.State.IDLE);
      pathing.restart();
    }
    pathing.tick();
  }

  @Override
  public void release() {
    pathing.stop();
  }
}
