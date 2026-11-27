package com.example.geology;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;

/** The whole example addon: one skill with its own adaptations, one adaptation for a built-in skill. */
public final class GeologyAddon implements AdaptAddon {
  public static final String NAMESPACE = "example";
  public static final Id GEOLOGY = Id.of(NAMESPACE, "geology");
  public static final Id PICKAXE = Id.adapt("pickaxe");
  private volatile AddonHandle handle;
  private volatile PlayerSection<FieldNotes> notes;

  /** Installs the addon and keeps the handle: only the installer may refresh or uninstall it. */
  public static AddonHandle install() {
    GeologyAddon addon = new GeologyAddon();
    addon.handle = AdaptApi.addons().install(addon);
    return addon.handle;
  }

  /** The handle {@link #install()} got; empty before it returns. */
  Optional<AddonHandle> handle() {
    return Optional.ofNullable(handle);
  }

  /** The field notes section the last {@code register} declared; empty before the first. */
  Optional<PlayerSection<FieldNotes>> notes() {
    return Optional.ofNullable(notes);
  }

  @Override
  public String namespace() {
    return NAMESPACE;
  }

  @Override
  public void register(ContentRegistrar registrar) {
    registrar.messages(MessageBundle.resources(GeologyAddon.class, "example/lang"));
    registrar.assets(AssetBundle.resources(GeologyAddon.class, "assets/example"));
    notes = registrar.playerSection("field_notes", FieldNotes.class, FieldNotes::new);
    registrar.skill(GEOLOGY, GeologySkill::new);
    registrar.adaptation(PICKAXE, Aftershock.ID, Aftershock::new);
    GeologyConsole console = new GeologyConsole(this::handle, this::notes);
    registrar.listener(console);
    registrar.command(console.command());
  }
}
