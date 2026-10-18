package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface GolemController {
  void attach();

  void moveTo(Location destination, double speed);

  void stop();

  void setTarget(LivingEntity target);

  void tick();

  void release();
}
