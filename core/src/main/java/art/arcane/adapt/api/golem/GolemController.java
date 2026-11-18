package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

public interface GolemController {
  void attach();

  void moveTo(Loc destination, double speed);

  void stop();

  void setTarget(LivingRef target);

  default void setCombat(boolean enabled) {
  }

  void interact(BlockRef container, int ticks);

  void tick();

  void release();
}
