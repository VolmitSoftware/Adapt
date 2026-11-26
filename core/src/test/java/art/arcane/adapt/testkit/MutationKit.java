package art.arcane.adapt.testkit;

import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

/** Mutating, learning a mutation's own adaptations and landing hits, for block and engine tests. */
public final class MutationKit {
  private final TestAdapt adapt;
  private final AdaptPlayer profile;

  public MutationKit(TestAdapt adapt, AdaptPlayer profile) {
    this.adapt = adapt;
    this.profile = profile;
  }

  public SkillRegistry registry() {
    return adapt.core().getAdaptServer().getSkillRegistry();
  }

  /** Raises both parents to level 20 and mutates them; fails the test when the merge is refused. */
  public SkillMutation mutate(String lead, String partner) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of(lead, partner)) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    SkillMutations.Result result = SkillMutations.mutate(profile.getData(), registry().getSkill(lead),
        registry().getSkill(partner), "Tester");
    if (result != SkillMutations.Result.MUTATED) {
      throw new AssertionError("Mutation " + lead + " + " + partner + " refused: " + result);
    }
    return new SkillMutation(lead, partner);
  }

  public MutatedSkill mutated(String lead, String partner) {
    return registry().getMutatedSkill(new SkillMutation(lead, partner).id());
  }

  public MutationAdaptation adaptation(String lead, String partner, Recipe recipe) {
    MutationAdaptation adaptation = mutated(lead, partner).getMutationAdaptation(recipe);
    if (adaptation == null) {
      throw new AssertionError("No " + recipe + " for " + lead + " + " + partner);
    }
    return adaptation;
  }

  /** Sets the recipe's level on the mutated line, as if learned. */
  public MutationAdaptation learn(String lead, String partner, Recipe recipe, int level) {
    MutationAdaptation adaptation = adaptation(lead, partner, recipe);
    profile.getData().getSkillLine(lead).setAdaptation(adaptation, level);
    return adaptation;
  }

  public static FakeLiving zombie(FakeWorld world, double x, double y, double z) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(world, x, y, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return world.add(zombie);
  }

  public EntityDamageByEntityEvent hit(FakePlayer player, FakeLiving target, double damage, boolean critical) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, critical, FinalDamage.IDENTITY));
  }
}
