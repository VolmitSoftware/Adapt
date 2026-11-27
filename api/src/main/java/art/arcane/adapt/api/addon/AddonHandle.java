package art.arcane.adapt.api.addon;

import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.nio.file.Path;

/**
 * An installed addon as its installer holds it. Returned by {@link AddonRegistry#install} only; keep it to refresh or
 * uninstall. Other code sees an addon as an {@link AddonInfo}.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AddonHandle extends AddonInfo {
  /** The addon instance that was installed. */
  AdaptAddon addon();

  /**
   * The addon's own folder for files it keeps, {@code <Adapt data>/addons/<namespace>}, created when missing. Its
   * skill and adaptation configs stay where Adapt writes them. Needs a running core.
   *
   * @throws IllegalStateException while no core runs
   */
  Path dataDirectory();

  /**
   * Blocks a skill or adaptation of any addon, in this addon's name, until the veto is lifted or this addon is
   * refreshed or uninstalled. Needs a running core.
   *
   * @throws IllegalArgumentException when this addon is no longer installed or the id is not registered
   * @throws IllegalStateException    when no core runs, or the server's config forbids vetoes on the content's
   *                                  namespace
   */
  ContentVeto veto(Id content);

  /**
   * Drops everything the addon registered and runs its {@code register} again, for an addon that reloaded its own
   * data. Players keep their progress. Does nothing while no core is running.
   */
  void refresh();

  /**
   * Removes the addon. Its content leaves the catalog at once; players keep what they learned in their save,
   * uncounted, and get it back when the addon returns. Vetoes and listeners the addon placed are lifted.
   */
  void uninstall();
}
