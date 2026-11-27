package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.engine.mutation.pair.PairClaims;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.world.BlockSlice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Everything one addon declared in one {@code register} call, validated against its own namespace. */
public record AddonContent(String namespace, List<SkillSource> skills, List<ExtensionSource> adaptations,
                           List<MessageBundle> messages, Optional<AssetBundle> assets,
                           List<CustomItem.Definition<?>> items, List<Object> listeners,
                           List<AdaptCommand> commands, PairClaims pairs,
                           List<EnginePlayerSection<?>> sections, List<BlockSlice<?>> slices) {
  public AddonContent {
    skills = List.copyOf(skills);
    adaptations = List.copyOf(adaptations);
    messages = List.copyOf(messages);
    items = List.copyOf(items);
    listeners = List.copyOf(listeners);
    commands = List.copyOf(commands);
    sections = List.copyOf(sections);
    slices = List.copyOf(slices);
    Objects.requireNonNull(pairs, "pairs");
    if (!pairs.owner().equals(namespace)) {
      throw new IllegalArgumentException("Pair claims of " + pairs.owner() + " cannot be installed as " + namespace);
    }
  }
}
