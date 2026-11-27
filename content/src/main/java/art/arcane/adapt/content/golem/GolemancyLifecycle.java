package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.event.AdaptStartedEvent;
import art.arcane.adapt.api.event.AdaptStoppingEvent;
import art.arcane.adapt.content.golem.command.GolemCommand;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionRegistration;

/** Starts the golemancy runtime with the core and saves bound golems when it stops. */
public final class GolemancyLifecycle {
  private static final long SHUTDOWN_TIMEOUT_MS = 5_000L;
  private PermissionRegistration adminPermission;

  @Handle
  public void on(AdaptStartedEvent event) {
    adminPermission = AdaptApi.platform().permissions().register(GolemCommand.ADMIN_PERMISSION,
        "Allows listing, releasing, and waking other players' golems", PermissionDefault.OP);
    GolemancyRuntime.startRuntime();
  }

  @Handle
  public void on(AdaptStoppingEvent event) {
    if (!GolemancyRuntime.shutdown(SHUTDOWN_TIMEOUT_MS)) {
      AdaptApi.log().warn("Bound golem saving was incomplete at shutdown.");
    }
    if (adminPermission != null) {
      adminPermission.remove();
      adminPermission = null;
    }
  }
}
