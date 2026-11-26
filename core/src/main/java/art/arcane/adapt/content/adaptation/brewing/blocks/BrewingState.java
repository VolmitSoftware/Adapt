package art.arcane.adapt.content.adaptation.brewing.blocks;

import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.StateBlock;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Holds while an effect from the last potion the player drank is still on them. */
public class BrewingState extends StateBlock<BrewingState.Config> {
  private final Map<UUID, Set<Id>> drunk = playerState();

  public BrewingState() {
    super("block-brewing-state");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("dragon_breath"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemConsumeEvent e) {
    Optional<PotionContents> contents = e.item().potionContents();
    if (contents.isEmpty() || !SkillBrewing.isBrewed(contents.get()) || !hasActiveAdaptation(e.player())) {
      return;
    }
    Set<Id> types = new HashSet<>();
    for (EffectInstance effect : BrewingDoses.effects(contents.get())) {
      types.add(effect.type());
    }
    drunk.put(e.player().uuid(), Set.copyOf(types));
  }

  @Override
  public boolean holds(PlayerRef player) {
    Set<Id> types = drunk.get(player.uuid());
    if (types == null) {
      return false;
    }
    for (Id type : types) {
      if (player.hasEffect(type)) {
        return true;
      }
    }
    return false;
  }

  @ConfigDescription("A potion you drank is active.")
  public static class Config extends MutationBlock.Config {
  }
}
