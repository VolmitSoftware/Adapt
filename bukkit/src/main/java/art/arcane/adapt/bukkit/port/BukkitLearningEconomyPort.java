package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.LearningEconomyPort;
import art.arcane.volmlib.integration.VaultEconomy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

final class BukkitLearningEconomyPort implements LearningEconomyPort {
  @Override
  public boolean isPresent() {
    return economy() != null;
  }

  @Override
  public boolean isAvailable() {
    VaultEconomy economy = economy();
    return economy != null && economy.isAvailable();
  }

  @Override
  public String format(double amount) {
    VaultEconomy economy = economy();
    return economy == null ? "" : economy.format(amount);
  }

  @Override
  public Withdrawal withdraw(UUID playerId, double amount, String context) {
    VaultEconomy economy = economy();
    if (economy == null) {
      return new Withdrawal(Status.VAULT_UNAVAILABLE, null);
    }
    VaultEconomy.ChargeResult result = economy.withdraw(player(playerId), amount, context);
    VaultEconomy.Charge charge = result.charge();
    return new Withdrawal(status(result.status()), charge == null ? null : new VaultCharge(charge));
  }

  @Override
  public boolean deposit(UUID playerId, double amount, String context) {
    VaultEconomy economy = economy();
    return economy != null && economy.deposit(player(playerId), amount, context);
  }

  private static VaultEconomy economy() {
    Adapt active = Adapt.instance;
    return active == null ? null : active.getVaultEconomy();
  }

  private static OfflinePlayer player(UUID playerId) {
    Player online = Bukkit.getPlayer(playerId);
    return online != null ? online : Bukkit.getOfflinePlayer(playerId);
  }

  private static Status status(VaultEconomy.ChargeStatus status) {
    return switch (status) {
      case SUCCESS -> Status.SUCCESS;
      case INVALID_AMOUNT -> Status.INVALID_AMOUNT;
      case VAULT_UNAVAILABLE -> Status.VAULT_UNAVAILABLE;
      case PROVIDER_UNAVAILABLE -> Status.PROVIDER_UNAVAILABLE;
      case INSUFFICIENT_FUNDS -> Status.INSUFFICIENT_FUNDS;
      case TRANSACTION_FAILED -> Status.TRANSACTION_FAILED;
    };
  }

  private record VaultCharge(VaultEconomy.Charge charge) implements Charge {
    @Override
    public double amount() {
      return charge.amount();
    }

    @Override
    public boolean commit() {
      return charge.commit();
    }

    @Override
    public boolean refund() {
      return charge.refund();
    }
  }
}
