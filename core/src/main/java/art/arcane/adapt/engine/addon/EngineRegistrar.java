package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.engine.mutation.pair.PairClaims;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collects one addon's declarations during its {@code register} call. Every id is checked against the addon's
 * namespace and the content grammar as it is declared; {@link #content()} closes the registrar.
 */
public final class EngineRegistrar implements ContentRegistrar {
  private final String namespace;
  private final List<SkillSource> skills = new ArrayList<>();
  private final List<ExtensionSource> adaptations = new ArrayList<>();
  private final List<MessageBundle> messages = new ArrayList<>();
  private final List<AssetBundle> assets = new ArrayList<>();
  private final List<CustomItem.Definition<?>> items = new ArrayList<>();
  private final List<Object> listeners = new ArrayList<>();
  private final List<AdaptCommand> commands = new ArrayList<>();
  private final List<EnginePlayerSection<?>> sections = new ArrayList<>();
  private final List<BlockSlice<?>> slices = new ArrayList<>();
  private final Set<Id> declared = new HashSet<>();
  private final Set<Id> sectionIds = new HashSet<>();
  private final Set<Id> sliceIds = new HashSet<>();
  private final PairClaims.Builder pairs;
  private boolean open = true;

  public EngineRegistrar(String namespace) {
    this.namespace = Objects.requireNonNull(namespace, "namespace");
    this.pairs = new PairClaims.Builder(namespace);
  }

  @Override
  public String namespace() {
    return namespace;
  }

  @Override
  public void messages(MessageBundle bundle) {
    requireOpen();
    messages.add(Objects.requireNonNull(bundle, "bundle"));
  }

  @Override
  public void assets(AssetBundle bundle) {
    requireOpen();
    Objects.requireNonNull(bundle, "bundle");
    if (!namespace.equals(bundle.namespace())) {
      throw new IllegalArgumentException("Addon " + namespace + " registers an asset bundle of namespace '"
          + bundle.namespace() + "'");
    }
    assets.add(bundle);
  }

  @Override
  public void skill(Id id, Supplier<? extends SimpleSkill<?>> factory) {
    requireOpen();
    ContentIds.requireSingleSegment(id, "skill");
    Objects.requireNonNull(factory, "factory");
    declare(id, "skill");
    skills.add(new SkillSource(id, namespace, () -> ComponentBuilder.buildSkill(factory)));
  }

  @Override
  public void adaptation(Id skill, Id id, Supplier<? extends SimpleAdaptation<?>> factory) {
    requireOpen();
    ContentIds.requireSingleSegment(skill, "skill");
    ContentIds.require(id, "adaptation");
    Objects.requireNonNull(factory, "factory");
    declare(id, "adaptation");
    adaptations.add(new ExtensionSource(skill, id, namespace, () -> ComponentBuilder.buildAdaptation(skill, factory)));
  }

  @Override
  public void item(CustomItem.Definition<?> definition) {
    requireOpen();
    Objects.requireNonNull(definition, "definition");
    ContentIds.require(definition.id(), "item");
    declare(definition.id(), "item");
    items.add(definition);
  }

  @Override
  public void signature(Id lead, Id partner, Recipe recipe, Function<MutationSlot, ? extends Signature<?>> factory) {
    requireOpen();
    pairs.signature(slot(lead, partner, recipe), factory);
  }

  @Override
  public void pairText(Id lead, Id partner, Recipe recipe, PairText text) {
    requireOpen();
    pairs.text(slot(lead, partner, recipe), text);
  }

  @Override
  public void pairName(Id lead, Id partner, Text name) {
    requireOpen();
    pairs.name(new SkillPair(lead, partner), name);
  }

  @Override
  public void listener(Object listener) {
    requireOpen();
    listeners.add(Objects.requireNonNull(listener, "listener"));
  }

  @Override
  public void command(AdaptCommand command) {
    requireOpen();
    commands.add(Objects.requireNonNull(command, "command"));
  }

  @Override
  public <T> PlayerSection<T> playerSection(String path, Class<T> type, Supplier<T> initial) {
    requireOpen();
    Id id = dataId(path, "player section", sectionIds);
    EnginePlayerSection<T> section = DeclaredData.issue(id, namespace, Objects.requireNonNull(type, "type"),
        Objects.requireNonNull(initial, "initial"));
    sections.add(section);
    return section;
  }

  @Override
  public <T> BlockSlice<T> blockSlice(String path, Class<T> type, BlockSlice.Codec<T> codec) {
    requireOpen();
    Id id = dataId(path, "block slice", sliceIds);
    BlockSlice<T> slice = new EngineBlockSlice<>(id, Objects.requireNonNull(type, "type"),
        Objects.requireNonNull(codec, "codec"));
    slices.add(slice);
    return slice;
  }

  /** Closes the registrar and returns what was declared. */
  public AddonContent content() {
    requireOpen();
    open = false;
    Optional<AssetBundle> art = switch (assets.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(assets.get(0));
      default -> Optional.of(combined(namespace, List.copyOf(assets)));
    };
    return new AddonContent(namespace, skills, adaptations, messages, art, items, listeners, commands, pairs.build(),
        sections, slices);
  }

  private void declare(Id id, String what) {
    if (!namespace.equals(id.namespace())) {
      throw new IllegalArgumentException("Addon " + namespace + " cannot register " + what + " " + id
          + " outside its namespace");
    }
    if (!declared.add(id)) {
      throw new IllegalArgumentException("Addon " + namespace + " registers " + what + " " + id + " twice");
    }
  }

  private Id dataId(String path, String what, Set<Id> ids) {
    Id id = Id.of(namespace, Objects.requireNonNull(path, "path"));
    ContentIds.require(id, what);
    if (!ids.add(id)) {
      throw new IllegalArgumentException("Addon " + namespace + " declares " + what + " " + id + " twice");
    }
    return id;
  }

  private static MutationSlot slot(Id lead, Id partner, Recipe recipe) {
    return new MutationSlot(new SkillPair(lead, partner), Objects.requireNonNull(recipe, "recipe"));
  }

  private void requireOpen() {
    if (!open) {
      throw new IllegalStateException("The registrar of addon " + namespace + " is only valid inside register");
    }
  }

  private static AssetBundle combined(String namespace, List<AssetBundle> parts) {
    List<AssetEntry> entries = new ArrayList<>();
    for (AssetBundle part : parts) {
      entries.addAll(part.entries());
    }
    List<AssetEntry> all = List.copyOf(entries);
    return new AssetBundle() {
      @Override
      public String namespace() {
        return namespace;
      }

      @Override
      public List<AssetEntry> entries() {
        return all;
      }

      @Override
      public byte[] read(AssetEntry entry) {
        for (AssetBundle part : parts) {
          if (part.entries().contains(entry)) {
            return part.read(entry);
          }
        }
        throw new IllegalArgumentException("Not an entry of " + namespace + ": " + entry.path());
      }
    };
  }
}
