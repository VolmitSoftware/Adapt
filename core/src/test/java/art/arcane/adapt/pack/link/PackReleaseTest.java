package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackReleaseTest {
  private final PackArtifact stock = TestPacks.pack("stock");
  private final PackArtifact changed = TestPacks.pack("changed");
  private final PackRelease release = new PackRelease("https://example.test/Adapt.Pack.v3.0.0.zip", stock.sha1Hex());

  @Test
  void servesOnlyThePackWithTheReleasedHash() {
    assertThat(release.serves(stock)).isTrue();
    assertThat(release.serves(changed)).isFalse();
    assertThat(release.serves(null)).isFalse();
  }

  @Test
  void theFirstFailedDownloadBeforeAnyLoadEndsReleaseDelivery() {
    assertThat(release.report(stock, PackState.ACCEPTED)).isFalse();
    assertThat(release.report(stock, PackState.DOWNLOAD_FAILED)).isTrue();

    assertThat(release.serves(stock)).isFalse();
    assertThat(release.report(stock, PackState.DOWNLOAD_FAILED)).isFalse();
  }

  @Test
  void afterALoadAFailedDownloadIsThePlayersOwn() {
    assertThat(release.report(stock, PackState.LOADED)).isFalse();
    assertThat(release.report(stock, PackState.DOWNLOAD_FAILED)).isFalse();

    assertThat(release.serves(stock)).isTrue();
  }

  @Test
  void declinesAndOtherFailuresKeepTheRelease() {
    assertThat(release.report(stock, PackState.DECLINED)).isFalse();
    assertThat(release.report(stock, PackState.FAILED)).isFalse();
    assertThat(release.report(changed, PackState.DOWNLOAD_FAILED)).isFalse();

    assertThat(release.serves(stock)).isTrue();
  }
}
