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

import java.util.List;

public final class ClaimsHomestead extends SimpleAdaptation<ClaimsHomestead.Config> {
  public ClaimsHomestead() {
    super("claims-homestead");
    registerConfiguration(Config.class);
    setIcon(Material.WHITE_BED);
    setInterval(60000);
  }

  @Override
  public void addStats(int level, Element element) {
    element.addLore(C.GREEN + AdaptLanguage.text(ClaimsMessages.HOMESTEAD_LORE));
    ClaimsGuide.attach(this, level, element);
  }

  @Override
  public void onLevelChanged(Player player, int level) {
    ClaimsService service = Adapt.instance == null ? null : Adapt.instance.getClaimsService();
    if (service != null) {
      service.refreshRanks(player);
    }
  }

  @ConfigDescription("Homestead unlocks for raidable chunk claims.")
  public static final class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      initialCost = 0;
      maxLevel = 1;
      requires = List.of("claims-founding:1");
    }
  }
}
