package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.pack.link.PackRelease;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackReleasesTest {
    private static final String SHA1 = "ad715165fe044796723eb0b81dde367cd15e3c4f";

    @Test
    void defaultAddressIsTheReleaseAssetGitHubServesForThisVersion() {
        PackRelease release = PackReleases.of(new AdaptConfig.Pack().getReleaseUrl(), "3.0.0", SHA1);
        assertEquals("https://github.com/VolmitSoftware/Adapt/releases/download/ResourcePack/Adapt.Pack.v3.0.0.zip", release.url());
        assertEquals(SHA1, release.sha1Hex());
        assertEquals("Adapt.Pack.v3.0.0.zip", PackZips.name("3.0.0").replace(' ', '.'));
    }

    @Test
    void blankAddressOrUnknownStockHashMeansNoRelease() {
        assertNull(PackReleases.of("", "3.0.0", SHA1));
        assertNull(PackReleases.of("https://example.test/{version}.zip", "3.0.0", null));
    }

    @Test
    void stockHashIsReadOnlyWhenItIsASha1() {
        assertEquals(SHA1, PackReleases.sha1(SHA1 + "\n"));
        assertNull(PackReleases.sha1("not a hash"));
        assertNull(PackReleases.stockSha1(PackReleasesTest.class));
    }
}
