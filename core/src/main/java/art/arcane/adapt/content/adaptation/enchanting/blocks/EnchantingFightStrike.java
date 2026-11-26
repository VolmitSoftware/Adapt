package art.arcane.adapt.content.adaptation.enchanting.blocks;

import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/**
 * The target glows, and for a few seconds every natural mob the player kills drops more vanilla XP. Farmed mobs drop
 * their usual XP.
 */
public class EnchantingFightStrike extends StrikeBlock<EnchantingFightStrike.Config> {
  private static final Id GLOWING = Id.minecraft("glowing");
  private final Map<UUID, Bonus> bonuses = playerState();

  public EnchantingFightStrike() {
    super("block-enchanting-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("experience_bottle"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    target.addEffect(EffectInstance.of(GLOWING, ticks, 0));
    bonuses.put(player.uuid(), new Bonus(System.currentTimeMillis() + getConfig().durationMillis,
        use.scaled(getConfig().xpMultiplierAtFirstLevel, getConfig().xpMultiplierAtTopLevel)));
    fx(target.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .particle(Particles.ENCHANT, 12, 0D, 0D, 0D, 0.5D, 0.4D);
    return Outcome.lasting(target, ticks);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    if (killer == null || e.platformCancelled() || e.droppedExp() <= 0) {
      return;
    }
    Bonus bonus = bonuses.get(killer.uuid());
    if (bonus == null || System.currentTimeMillis() > bonus.until()
        || MutationPayouts.farmed(killer, e.entity())) {
      return;
    }
    int dropped = (int) Math.round(e.droppedExp() * bonus.multiplier());
    addStat(killer, "enchanting.mutated.bonus-xp", dropped - e.droppedExp());
    e.setDroppedExp(dropped);
  }

  private record Bonus(long until, double multiplier) {
  }

  @ConfigDescription("The target glows, and a natural mob killed within 5 s drops half again as much XP.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the glow and the XP bonus last, in milliseconds.", impact = "Higher values leave more time for the kill.")
    long durationMillis = 5_000L;
    @ConfigDoc(value = "Multiplier on dropped XP at level 1.", impact = "Higher values drop more XP.")
    double xpMultiplierAtFirstLevel = 1.5D;
    @ConfigDoc(value = "Multiplier on dropped XP at the top level.", impact = "Higher values drop more XP.")
    double xpMultiplierAtTopLevel = 2D;
  }
}
