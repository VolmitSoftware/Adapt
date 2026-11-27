package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.agility.AgilityRunRamp;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;

/** Spends the player's running momentum: payable while moving on foot, and paying stops them dead. */
public class AgilityFuel extends FuelBlock<AgilityFuel.Config> {
  private final Map<UUID, Long> lastStride = playerState();

  public AgilityFuel() {
    super(SkillAgility.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather_boots"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    double stride = getConfig().minStride;
    if ((dx * dx) + (dz * dz) < stride * stride || !from.sameWorld(to)) {
      return;
    }
    lastStride.put(e.player().uuid(), System.currentTimeMillis());
  }

  @Override
  public boolean canPay(BlockUse use) {
    PlayerRef player = use.player();
    Long moved = lastStride.get(player.uuid());
    return moved != null && System.currentTimeMillis() - moved <= getConfig().momentumWindowMillis
        && AgilityRunRamp.eligible(player, true) && player.foodLevel() >= getConfig().hunger;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    if (!lastStride.containsKey(player.uuid()) || !payHunger(use, getConfig().hunger)) {
      return false;
    }
    lastStride.remove(player.uuid());
    player.setSprinting(false);
    player.setVelocity(new Vec3(0D, Math.min(0D, player.velocity().y()), 0D));
    return true;
  }

  @ConfigDescription("Your momentum and 2 points of hunger (you stop).")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Shortest horizontal step in one movement, in blocks, that counts as moving.", impact = "Higher values ask for a faster pace before momentum can be spent.")
    double minStride = 0.03D;
    @ConfigDoc(value = "How long after the last step the momentum can still be spent, in milliseconds.", impact = "Higher values forgive a short pause.")
    long momentumWindowMillis = 300L;
    @ConfigDoc(value = "Hunger points taken per use.", impact = "Higher values cost more food.")
    int hunger = 2;
  }
}
