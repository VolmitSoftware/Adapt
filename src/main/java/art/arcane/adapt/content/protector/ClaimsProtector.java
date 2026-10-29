package art.arcane.adapt.content.protector;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.claims.ClaimsService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ClaimsProtector implements Protector {
  @Override
  public boolean canBlockBreak(Player player, Location location, Adaptation<?> adaptation) {
    return ClaimsService.permitsEdit(player, location);
  }

  @Override
  public boolean canBlockPlace(Player player, Location location, Adaptation<?> adaptation) {
    return ClaimsService.permitsEdit(player, location);
  }

  @Override
  public boolean canInteract(Player player, Location location, Adaptation<?> adaptation) {
    ClaimsService service = ClaimsService.current();
    return service == null || service.canReach(player, location);
  }

  @Override
  public boolean canAccessChest(Player player, Location location, Adaptation<?> adaptation) {
    return canInteract(player, location, adaptation);
  }

  @Override
  public String getName() {
    return "Claims";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
