package art.arcane.adapt.api.addon;

import org.jetbrains.annotations.ApiStatus;

/**
 * A provider of content: skills, adaptations, mutation pairs, messages and art.
 *
 * <p>An addon is installed once, with {@code AdaptApi.addons().install(addon)}, from the place a platform expects
 * registration: {@code onLoad} or {@code onEnable} of a Bukkit plugin, {@code onInitialize} of a Fabric mod, the
 * constructor of a Forge or NeoForge mod. Adapt keeps the addon and calls {@link #register} every time its core
 * starts, and at once if the core is already running, so neither install order nor install time matters.
 *
 * <p>Adapt's own 24 skills are an addon with the namespace {@code adapt}, installed the same way.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.OverrideOnly
public interface AdaptAddon {
  /**
   * The namespace of every id this addon registers, and the key of its message bundle, config folder and permission
   * nodes. One addon per namespace; {@code adapt} and {@code minecraft} are taken. Must not change between calls.
   */
  String namespace();

  /**
   * Declares everything the addon provides. Called on every core start and on {@link AddonHandle#refresh()}, so it
   * must give the same result each time and keep no reference to the registrar. Content is passed as suppliers:
   * Adapt builds it, and builds it again after a config reload.
   *
   * <p>An exception thrown here rejects this addon only; the reason is logged and kept on its handle.
   */
  void register(ContentRegistrar registrar);
}
