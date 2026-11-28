package art.arcane.adapt.pack.link;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackInviteTest {
  private final FakePlayer viewer = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));

  @Test
  void aPlayerWhoCanGetThePackSeesTheItem() {
    PackInvite.Item item = PackInvite.item(viewer, PackOffers.Standing.INVITED, true);

    assertThat(item).isNotNull();
    assertThat(item.icon()).isEqualTo(PackInvite.ICON);
    assertThat(item.name()).contains("Get the book menus");
    assertThat(item.lore()).anyMatch((String line) -> line.contains("client mod"));
  }

  @Test
  void aRefusingClientSeesTheStepsInTheLore() {
    PackInvite.Item item = PackInvite.item(viewer, PackOffers.Standing.REFUSING, true);

    assertThat(item.lore()).anyMatch((String line) -> line.contains("Server Resource Packs"));
  }

  @Test
  void noItemWhenSwitchedOffOrNotWanted() {
    assertThat(PackInvite.item(viewer, PackOffers.Standing.INVITED, false)).isNull();
    assertThat(PackInvite.item(viewer, PackOffers.Standing.NONE, true)).isNull();
  }

  @Test
  void aClickWithDeliveryOffDoesNothing() {
    PackInvite.clicked(viewer);

    assertThat(viewer.messages()).isEmpty();
  }
}
