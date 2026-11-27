package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSecondWind;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;

/**
 * A regeneration burst, then Second Wind for a while: each bare-hand kill of a natural mob restores food and
 * regeneration through Second Wind's own restore. A kill the boon pays for does not also pay a kept Second Wind.
 */
public class UnarmedBoon extends BoonBlock<UnarmedBoon.Config> {
  private final Map<UUID, Wind> winds = playerState();

  public UnarmedBoon() {
    super(SkillUnarmed.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ghast_tear"));
  }

  @Override
  public Outcome grant(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (getConfig().durationMillis / 50L);
    int amplifier = (int) Math.round(use.scaled(getConfig().amplifierAtFirstLevel, getConfig().amplifierAtTopLevel));
    int food = (int) Math.round(use.scaled(getConfig().foodAtFirstLevel, getConfig().foodAtTopLevel));
    player.addEffect(EffectInstance.of(UnarmedSecondWind.REGENERATION, ticks, amplifier));
    winds.put(player.uuid(), new Wind(food, amplifier, System.currentTimeMillis() + getConfig().windMillis));
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    PlayerRef p = UnarmedSecondWind.directPlayerKiller(e);
    if (p == null || victim instanceof PlayerRef) {
      return;
    }
    Wind wind = winds.get(p.uuid());
    if (wind == null) {
      return;
    }
    if (System.currentTimeMillis() > wind.expiresAt()) {
      winds.remove(p.uuid());
      return;
    }
    if (ItemKinds.isTool(p.inventory().mainHand()) || ItemKinds.isTool(p.inventory().offHand()) || isProtectedFriendly(p, victim)
        || Mutations.farmed(p, victim) || !UnarmedSecondWind.claim(victim)) {
      return;
    }
    UnarmedSecondWind.restore(p, wind.food(), getConfig().saturation, (int) (getConfig().durationMillis / 50L),
        wind.amplifier());
  }

  private record Wind(int food, int amplifier, long expiresAt) {
  }

  @ConfigDescription("Regeneration for 3 s, and bare-hand kills restore food for 15 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long each regeneration lasts, in milliseconds.", impact = "Higher values heal for longer.")
    long durationMillis = 3_000L;
    @ConfigDoc(value = "Regeneration amplifier at level 1; 0 is Regeneration I.", impact = "Higher values heal faster.")
    double amplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Regeneration amplifier at the top level; 0 is Regeneration I.", impact = "Higher values heal faster.")
    double amplifierAtTopLevel = 1D;
    @ConfigDoc(value = "How long bare-hand kills restore food after the boon, in milliseconds.", impact = "Higher values keep Second Wind going longer.")
    long windMillis = 15_000L;
    @ConfigDoc(value = "Food points a bare-hand kill restores at level 1.", impact = "Higher values feed you more per kill.")
    double foodAtFirstLevel = 2D;
    @ConfigDoc(value = "Food points a bare-hand kill restores at the top level.", impact = "Higher values feed you more per kill.")
    double foodAtTopLevel = 4D;
    @ConfigDoc(value = "Saturation a bare-hand kill restores.", impact = "Higher values keep the food bar full longer.")
    double saturation = 1.5D;
  }
}
