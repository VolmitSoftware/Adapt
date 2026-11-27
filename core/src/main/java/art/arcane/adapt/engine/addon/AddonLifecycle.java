package art.arcane.adapt.engine.addon;

import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.api.event.AdaptStartedEvent;
import art.arcane.adapt.api.event.AdaptStoppingEvent;
import art.arcane.adapt.api.event.AddonInstalledEvent;
import art.arcane.adapt.api.event.AddonUninstalledEvent;
import art.arcane.adapt.api.event.CatalogChangedEvent;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.InstalledAddon;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.command.AddonCommands;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.asset.InstalledBundle;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies installed addons to one core runtime and removes them again. An addon is applied whole or not at all: its
 * messages, art and content go in together, and a failure anywhere takes back what went in before it and leaves
 * other addons alone. Vetoes live here too and end with the runtime.
 */
public final class AddonLifecycle {
  private final AdaptCore core;
  private final Map<String, Applied> applied = new LinkedHashMap<>();
  private final List<Veto> vetoes = new ArrayList<>();
  private final Set<Object> bound = Collections.newSetFromMap(new IdentityHashMap<>());
  private final EngineCatalog catalog;
  private volatile SkillRegistry registry;

  public AddonLifecycle(AdaptCore core) {
    this.core = Objects.requireNonNull(core, "core");
    this.catalog = new EngineCatalog(this);
  }

  public ContentCatalog catalog() {
    return catalog;
  }

  /** Called once by the starting runtime's registry: binds subscribed listeners, then applies every addon. */
  public synchronized void bootstrap(SkillRegistry starting) {
    registry = Objects.requireNonNull(starting, "starting");
    for (Object listener : Engine.events().snapshot(HostKeys.key())) {
      bind(listener);
    }
    for (InstalledAddon addon : Engine.addons().snapshot(HostKeys.key())) {
      apply(addon);
    }
    dropStaleBundles();
  }

  /** Bundles of addons that were removed while no runtime ran. */
  private void dropStaleBundles() {
    for (TextBundle bundle : AdaptLanguage.texts().bundles()) {
      String namespace = bundle.namespace();
      if (!Id.ADAPT.equals(namespace) && !applied.containsKey(namespace)) {
        AdaptLanguage.texts().uninstall(namespace);
      }
    }
    for (InstalledBundle bundle : core.assets().bundles()) {
      String namespace = bundle.namespace();
      if (!Id.ADAPT.equals(namespace) && !applied.containsKey(namespace)) {
        core.assetInstaller().uninstall(namespace);
      }
    }
  }

  /** The runtime finished starting. */
  public void started() {
    core.assetInstaller().skillsChanged();
    core.getBus().post(new AdaptStartedEvent());
  }

  /** The runtime is about to stop; player data is still loaded. */
  public void stopping() {
    core.getBus().post(new AdaptStoppingEvent());
  }

  /** The runtime stopped: every addon waits for the next one, and vetoes end. */
  public synchronized void stopped() {
    for (InstalledAddon addon : Engine.addons().snapshot(HostKeys.key())) {
      addon.waiting(HostKeys.key());
    }
    applied.clear();
    DeclaredData.retireAll();
    AddonCommands.clear();
    vetoes.forEach(Veto::end);
    vetoes.clear();
    bound.clear();
    registry = null;
  }

  public synchronized void install(InstalledAddon addon) {
    if (registry != null) {
      apply(addon);
    }
  }

  public synchronized void refresh(InstalledAddon addon) {
    if (registry == null) {
      return;
    }
    if (remove(addon)) {
      core.getBus().post(new AddonUninstalledEvent(addon.namespace()));
    }
    apply(addon);
  }

  public synchronized void uninstall(InstalledAddon addon) {
    if (registry != null && remove(addon)) {
      core.getBus().post(new AddonUninstalledEvent(addon.namespace()));
    }
  }

  public synchronized void subscribe(Object listener) {
    if (registry != null) {
      bind(listener);
    }
  }

  public synchronized void unsubscribe(Object listener) {
    if (bound.remove(listener)) {
      core.getRegistrar().unregister(listener);
    }
  }

  /** The registry's catalog moved to {@code revision}. */
  public void catalogChanged(long revision) {
    core.assetInstaller().skillsChanged();
    core.getBus().post(new CatalogChangedEvent(revision));
  }

  SkillRegistry registry() {
    return registry;
  }

  synchronized List<CustomItem.Definition<?>> registeredItems() {
    List<CustomItem.Definition<?>> items = new ArrayList<>();
    for (Applied entry : applied.values()) {
      items.addAll(entry.content().items());
    }
    return items;
  }

  synchronized List<ContentVeto> vetoes() {
    return List.copyOf(vetoes);
  }

  public synchronized ContentVeto disable(Id content, String by) {
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(by, "by");
    if (Engine.addons().find(by).isEmpty()) {
      throw new IllegalArgumentException("Addon " + by + " is not installed");
    }
    SkillRegistry live = registry;
    if (live == null || !live.isRegistered(content)) {
      throw new IllegalArgumentException("No skill or adaptation " + content + " is registered");
    }
    if (!AdaptConfig.get().getAddons().allowsVetoOn(content.namespace())) {
      AdaptLog.warn("Addon " + by + " tried to disable " + content + "; adapt.toml forbids vetoes on namespace "
          + content.namespace() + ".");
      throw new IllegalStateException("The server forbids vetoes on namespace " + content.namespace());
    }
    Veto veto = new Veto(content, by);
    vetoes.add(veto);
    AdaptLog.info("Addon " + by + " disabled " + content + ".");
    live.veto(blocked());
    return veto;
  }

  private synchronized void lift(Veto veto) {
    if (!vetoes.remove(veto)) {
      return;
    }
    veto.end();
    AdaptLog.info("Veto by " + veto.by() + " on " + veto.content() + " lifted.");
    SkillRegistry live = registry;
    if (live != null) {
      live.veto(blocked());
    }
  }

  private Set<Id> blocked() {
    Set<Id> ids = new HashSet<>();
    for (Veto veto : vetoes) {
      ids.add(veto.content());
    }
    return ids;
  }

  private void bind(Object listener) {
    if (bound.add(listener)) {
      core.getRegistrar().register(listener);
    }
  }

  private void apply(InstalledAddon addon) {
    String namespace = addon.namespace();
    Applied existing = applied.get(namespace);
    if (existing != null && existing.handle() == addon) {
      return;
    }
    AddonContent content;
    try {
      EngineRegistrar registrar = new EngineRegistrar(namespace);
      addon.addon().register(registrar);
      content = registrar.content();
      requireDistinctItems(content);
      AddonCommands.requireFree(namespace, content.commands());
    } catch (RuntimeException | LinkageError failure) {
      reject(addon, failure);
      return;
    }
    boolean textInstalled = false;
    boolean assetsInstalled = false;
    boolean contentInstalled = false;
    try {
      if (content.assets().isPresent()) {
        core.assetInstaller().install(content.assets().get(), namespace);
        assetsInstalled = true;
      }
      if (!content.messages().isEmpty()) {
        // Skills and items resolve their own text while they register; the config text needs them registered.
        AdaptLanguage.texts().install(TextBundle.messages(namespace, content.messages(), List.of()));
        textInstalled = true;
      }
      registry.install(content);
      contentInstalled = true;
      List<MessageKey> configText = ConfigFieldMessages.declaredBy(namespace, registry.getAllSkills());
      if (!content.messages().isEmpty() || !configText.isEmpty()) {
        TextBundle text = TextBundle.messages(namespace, content.messages(), configText);
        AdaptLanguage.texts().install(text);
        textInstalled = true;
        text.problems().forEach(problem -> AdaptLog.warn("Addon " + namespace + ": " + problem));
      }
    } catch (RuntimeException | LinkageError failure) {
      if (textInstalled) {
        AdaptLanguage.texts().uninstall(namespace);
      }
      if (contentInstalled) {
        registry.retire(namespace);
      }
      if (assetsInstalled) {
        core.assetInstaller().uninstall(namespace);
      }
      reject(addon, failure);
      return;
    }
    for (Object listener : content.listeners()) {
      core.getRegistrar().register(listener);
    }
    AddonCommands.install(namespace, content.commands(), core.getPlatform().permissions());
    applied.put(namespace, new Applied(addon, content, textInstalled, assetsInstalled));
    DeclaredData.commit(namespace, content.sections(), content.slices());
    addon.applied(HostKeys.key());
    core.getBus().post(new AddonInstalledEvent(namespace));
  }

  private boolean remove(InstalledAddon addon) {
    Applied entry = applied.get(addon.namespace());
    if (entry == null || entry.handle() != addon) {
      return false;
    }
    applied.remove(addon.namespace());
    String namespace = addon.namespace();
    DeclaredData.retire(namespace);
    for (Object listener : entry.content().listeners()) {
      core.getRegistrar().unregister(listener);
    }
    List<Veto> placed = new ArrayList<>();
    for (Veto veto : vetoes) {
      if (veto.by().equals(namespace)) {
        placed.add(veto);
      }
    }
    vetoes.removeAll(placed);
    for (Veto veto : placed) {
      veto.end();
      AdaptLog.info("Veto by " + namespace + " on " + veto.content() + " lifted: the addon was removed.");
    }
    AddonCommands.uninstall(namespace, core.getPlatform().permissions());
    registry.retire(namespace);
    registry.veto(blocked());
    if (entry.text()) {
      AdaptLanguage.texts().uninstall(namespace);
    }
    if (entry.assets()) {
      core.assetInstaller().uninstall(namespace);
    }
    return true;
  }

  private void requireDistinctItems(AddonContent content) {
    Map<Id, CustomItem.Definition<?>> held = new LinkedHashMap<>();
    for (Applied entry : applied.values()) {
      if (entry.content().namespace().equals(content.namespace())) {
        continue;
      }
      for (CustomItem.Definition<?> item : entry.content().items()) {
        held.put(item.id(), item);
      }
    }
    for (CustomItem.Definition<?> item : content.items()) {
      CustomItem.Definition<?> other = held.get(item.id());
      if (other != null && other != item) {
        throw new IllegalStateException("Item " + item.id() + " is already registered; addon "
            + content.namespace() + " cannot register it too");
      }
    }
  }

  private void reject(InstalledAddon addon, Throwable failure) {
    String reason = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
    AdaptLog.warn("Addon " + addon.namespace() + " (" + addon.addon().getClass().getName() + ") was rejected: "
        + reason);
    addon.rejected(HostKeys.key(), reason);
  }

  private record Applied(InstalledAddon handle, AddonContent content, boolean text, boolean assets) {
  }

  private final class Veto implements ContentVeto {
    private final Id content;
    private final String by;
    private volatile boolean active = true;

    private Veto(Id content, String by) {
      this.content = content;
      this.by = by;
    }

    @Override
    public Id content() {
      return content;
    }

    @Override
    public String by() {
      return by;
    }

    @Override
    public boolean isActive() {
      return active;
    }

    @Override
    public void lift() {
      AddonLifecycle.this.lift(this);
    }

    private void end() {
      active = false;
    }
  }
}
