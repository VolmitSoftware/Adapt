package art.arcane.adapt.content.protector;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ResidenceProtector implements Protector {

  public ResidenceProtector() {
    FlagPermissions.addFlag("use-adaptations");
  }

  @Override
  public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
    return checkPerm(player, location, "use-adaptations");
  }

  @Override
  public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.destroy);
  }

  @Override
  public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
    return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.place);
  }

  @Override
  public boolean canPVP(Player player, Location entityLocation, Adaptation<?> adaptation) {
    return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.pvp);
  }

  @Override
  public boolean canPVE(Player player, Location entityLocation, Adaptation<?> adaptation) {
    return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.damage);
  }

  @Override
  public boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation) {
    return checkRegion(player, targetLocation, adaptation) && checkPerm(player, targetLocation, Flags.use);
  }

  @Override
  public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
    return checkRegion(player, chestLocation, adaptation) && checkPerm(player, chestLocation, Flags.container);
  }

  private boolean checkPerm(Player player, Location location, Flags flag) {
    Residence residence = Residence.getInstance();
    if (residence.isDisabledWorld(location.getWorld())) {
      return true;
    }
    ClaimedResidence claimedResidence = residence.getResidenceManager().getByLoc(location);
    return claimedResidence == null
        || claimedResidence.getPermissions().playerHas(player.getName(), flag, true);
  }

  private boolean checkPerm(Player player, Location location, String flag) {
    Residence residence = Residence.getInstance();
    if (residence.isDisabledWorld(location.getWorld())) {
      return true;
    }
    ClaimedResidence claimedResidence = residence.getResidenceManager().getByLoc(location);
    return claimedResidence == null
        || claimedResidence.getPermissions().playerHas(player.getName(), flag, true);
  }

  @Override
  public String getName() {
    return "Residence";
  }

  @Override
  public boolean isEnabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isResidence();
  }

}
