package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.golemancy.OwnedGolems;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemWorkSpeed;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player's golems act faster for a while, through the same work speed hook as Copper Frame's overdrive. Misses when
 * the player has no golem nearby to speed up.
 */
public class GolemancyBoon extends BoonBlock<GolemancyBoon.Config> {
  private final Map<UUID, Haste> hasted = new ConcurrentHashMap<>();
  private final GolemWorkSpeed.Source speed = new GolemWorkSpeed.Source() {
    @Override
    public String id() {
      return getId().toString();
    }

    @Override
    public double intervalMultiplier(GolemHandle handle) {
      Haste haste = hasted.get(handle.owner());
      if (haste == null) {
        return 1D;
      }
      if (System.currentTimeMillis() >= haste.until()) {
        hasted.remove(handle.owner(), haste);
        return 1D;
      }
      return 1D / (1D + haste.bonus());
    }
  };

  public GolemancyBoon() {
    super(SkillGolemancy.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone_torch"));
  }

  @Override
  protected void onRuntimeActivated() {
    GolemWorkSpeed.register(speed);
  }

  @Override
  public void unregister() {
    GolemWorkSpeed.unregister(speed);
    hasted.clear();
    super.unregister();
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    if (OwnedGolems.nearest(player, player.location(), getConfig().golemRadius, (GolemHandle handle) -> true) == null) {
      return Outcome.MISSED;
    }
    long millis = getConfig().durationMillis;
    hasted.put(player.uuid(), new Haste(System.currentTimeMillis() + millis,
        use.scaled(getConfig().workSpeedAtFirstLevel, getConfig().workSpeedAtTopLevel)));
    return Outcome.lasting(player, (int) (millis / 50L));
  }

  private record Haste(long until, double bonus) {
  }

  @ConfigDescription("50% faster golem work for 10 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long golems work faster, in milliseconds.", impact = "Higher values keep golems fast longer.")
    long durationMillis = 10_000L;
    @ConfigDoc(value = "Extra work speed at level 1, as a fraction (0.5 is 50% faster).", impact = "Higher values make golems act sooner.")
    double workSpeedAtFirstLevel = 0.5D;
    @ConfigDoc(value = "Extra work speed at the top level, as a fraction (0.5 is 50% faster).", impact = "Higher values make golems act sooner.")
    double workSpeedAtTopLevel = 1D;
    @ConfigDoc(value = "How far from you a golem must be to count, in blocks.", impact = "Higher values let the boon land with golems farther away.")
    double golemRadius = 32D;
  }
}
