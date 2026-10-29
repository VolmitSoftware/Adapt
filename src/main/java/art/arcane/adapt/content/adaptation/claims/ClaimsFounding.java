package art.arcane.adapt.content.adaptation.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.claims.ClaimsGuide;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ClaimsFounding extends SimpleAdaptation<ClaimsFounding.Config> {
  public ClaimsFounding() {
    super("claims-founding");
    registerConfiguration(Config.class);
    setIcon(Material.LODESTONE);
    setInterval(60000);
  }

  @Override
  public void addStats(int level, Element element) {
    element.addLore(C.GREEN + AdaptLanguage.text(ClaimsMessages.FOUNDING_LORE));
    ClaimsGuide.attach(this, level, element);
  }

  @Override
  public void onLevelChanged(Player player, int level) {
    ClaimsService service = Adapt.instance == null ? null : Adapt.instance.getClaimsService();
    if (service != null) {
      service.refreshRanks(player);
    }
  }

  @ConfigDescription("Founding unlocks for raidable chunk claims.")
  public static final class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      initialCost = 0;
      maxLevel = 1;
    }
  }
}
