package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/**
 * The kit Adapt generates for a skill that registers none: earning the skill's XP is its Trigger, a short Weakness and
 * Slowness or a short Haste its Strikes, a little Regeneration its Boon, a little hunger its Fuel, and having earned
 * the skill's XP in the last few seconds its State. Its tint is the skill's colour, its words the skill's name, and it
 * has no upgrades and reserves no gesture.
 */
public final class DefaultKit {
  /** Sounds no built-in skill uses. */
  public static final SoundPalette SOUNDS = SoundPalette.of(Id.minecraft("entity.player.attack.nodamage"),
      Id.minecraft("block.tuff.hit"), Id.minecraft("entity.chicken.egg"), Id.minecraft("block.wooden_button.click_on"));

  private DefaultKit() {
  }

  static MutationKit of(Skill<?> skill) {
    Id id = skill.getId();
    return MutationKit.builder()
        .trigger(TriggerKind.EITHER, () -> new DefaultTrigger(id))
        .strikes(() -> new DefaultFightStrike(id), () -> new DefaultWorkStrike(id))
        .boon(() -> new DefaultBoon(id))
        .fuel(() -> new DefaultFuel(id))
        .state(() -> new DefaultState(id))
        .sounds(SOUNDS)
        .build();
  }

  /** The player earned XP in {@code skill}: sets off its generated Trigger and refreshes its generated State. */
  public static void earned(PlayerRef player, Skill<?> skill) {
    if (skill.getMutationKit().isPresent() || !(SkillHost.find(skill) instanceof SkillHost host)) {
      return;
    }
    for (Adaptation<?> block : host.getBlocks()) {
      if (block instanceof DefaultState state) {
        state.earned(player);
      } else if (block instanceof DefaultTrigger trigger) {
        trigger.earned(player);
      }
    }
  }
}
