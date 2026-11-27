package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The target is marked: hits from the marking player, melee or projectile, deal extra damage until it fades. */
public class HunterFightStrike extends StrikeBlock<HunterFightStrike.Config> {
  private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

  public HunterFightStrike() {
    super(SkillHunter.ID, StrikeForm.FIGHT);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("target"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target)
        || !(target instanceof PlayerRef ? canPVP(player, target.location()) : canPVE(player, target.location()))) {
      return Outcome.MISSED;
    }
    int ticks = (int) (getConfig().durationMillis / 50L);
    Mark mark = new Mark(player.uuid(), System.currentTimeMillis() + getConfig().durationMillis,
        use.scaled(getConfig().bonusAtFirstLevel, getConfig().bonusAtTopLevel));
    UUID targetId = target.uuid();
    marks.put(targetId, mark);
    if (!AdaptApi.scheduler().runEntity(target, () -> marks.remove(targetId, mark), ticks, () -> marks.remove(targetId, mark))) {
      marks.remove(targetId, mark);
      return Outcome.MISSED;
    }
    fx(target.location().add(0D, target.height() + 0.3D, 0D), FxPriority.COMBAT)
        .particle(Particles.DAMAGE_INDICATOR, 4, 0D, 0D, 0D, 0.15D, 0.02D);
    return Outcome.lasting(target, ticks);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    Mark mark = marks.get(e.entity().uuid());
    if (mark == null) {
      return;
    }
    if (System.currentTimeMillis() > mark.until()) {
      marks.remove(e.entity().uuid(), mark);
      return;
    }
    EntityRef damager = e.damager();
    EntityRef source = damager instanceof ProjectileRef projectile ? projectile.shooter() : damager;
    if (source instanceof PlayerRef && source.uuid().equals(mark.hunter())) {
      e.setDamage(e.damage() * (1D + mark.bonus()));
    }
  }

  private record Mark(UUID hunter, long until, double bonus) {
  }

  @ConfigDescription("The target is marked and takes 15% more damage from you for 6 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long the mark lasts, in milliseconds.", impact = "Higher values keep the mark longer.")
    long durationMillis = 6_000L;
    @ConfigDoc(value = "Extra damage from you at level 1, as a fraction of the hit.", impact = "Higher values make marked targets fall faster.")
    double bonusAtFirstLevel = 0.15D;
    @ConfigDoc(value = "Extra damage from you at the top level, as a fraction of the hit.", impact = "Higher values make marked targets fall faster.")
    double bonusAtTopLevel = 0.25D;
  }
}
