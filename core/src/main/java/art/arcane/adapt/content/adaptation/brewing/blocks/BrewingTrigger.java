package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.LingeringPotionSplashEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fires when the player drinks a brewed potion or one they threw breaks, unless a signature took that splash. */
public class BrewingTrigger extends TriggerBlock<BrewingTrigger.Config> {
  private static final Map<UUID, Long> TAKEN = new ConcurrentHashMap<>();

  public BrewingTrigger() {
    super("block-brewing-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("potion"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    used(e.player(), e.item(), e.player().location());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PotionSplashEvent e) {
    if (e.potion().shooter() instanceof PlayerRef player && !TAKEN.remove(player.uuid(), e.dispatchId())) {
      used(player, e.item(), e.potion().location());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(LingeringPotionSplashEvent e) {
    if (e.potion().shooter() instanceof PlayerRef player) {
      used(player, e.item(), e.potion().location());
    }
  }

  /** A signature used this splash before MONITOR; the trigger leaves it. */
  public static void take(PotionSplashEvent e, PlayerRef thrower) {
    TAKEN.put(thrower.uuid(), e.dispatchId());
  }

  private void used(PlayerRef player, ItemRef potion, Loc at) {
    Optional<PotionContents> contents = potion.potionContents();
    if (contents.isEmpty() || !SkillBrewing.isBrewed(contents.get()) || !hasActiveAdaptation(player)) {
      return;
    }
    fire(player, null, at, false);
  }

  @ConfigDescription("Drink or throw a potion.")
  public static class Config extends MutationBlock.Config {
  }
}
