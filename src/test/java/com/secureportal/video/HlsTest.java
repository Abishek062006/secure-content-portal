package com.secureportal.video;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HlsTest {

    private static final List<Integer> LADDER = List.of(360, 720, 1080);

    @Test
    void aFullHdSourceGetsThreeRenditionsAndASmallerOneIsNeverUpscaled() {
        assertThat(Hls.plan(1920, 1080, LADDER)).extracting(Hls.Rendition::height).containsExactly(360, 720, 1080);
        assertThat(Hls.plan(1280, 720, LADDER)).extracting(Hls.Rendition::height).containsExactly(360, 720);
        assertThat(Hls.plan(640, 480, LADDER)).extracting(Hls.Rendition::height).containsExactly(360);
    }

    @Test
    void aSourceSmallerThanEveryRenditionKeepsItsOwnSize() {
        List<Hls.Rendition> plan = Hls.plan(320, 240, LADDER);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).height()).isEqualTo(240);
        assertThat(plan.get(0).width()).isEqualTo(320);
    }

    @Test
    void widthsKeepTheShapeAndAreAlwaysEvenBecauseH264NeedsThat() {
        assertThat(Hls.plan(1920, 1080, LADDER)).extracting(Hls.Rendition::width).containsExactly(640, 1280, 1920);
        // 4:3 and odd-shaped sources still give even numbers.
        assertThat(Hls.plan(1000, 750, LADDER).get(0).width() % 2).isZero();
        assertThat(Hls.evenWidth(1001, 999, 360) % 2).isZero();
    }

    @Test
    void theMasterPlaylistListsEachRenditionWithRelativeAddressesAndBandwidth() {
        String master = Hls.masterPlaylist(Hls.plan(1280, 720, LADDER));

        assertThat(master).startsWith("#EXTM3U\n#EXT-X-VERSION:3\n")
                .contains("#EXT-X-STREAM-INF:BANDWIDTH=828000,RESOLUTION=640x360\n360p/index.m3u8\n")
                .contains("#EXT-X-STREAM-INF:BANDWIDTH=2628000,RESOLUTION=1280x720\n720p/index.m3u8\n");
        assertThat(master).doesNotContain("http");
    }

    @Test
    void onlyTheFilesTheAppWritesCanBeRequested() {
        assertThat(Hls.isSafePath("master.m3u8")).isTrue();
        assertThat(Hls.isSafePath("720p/index.m3u8")).isTrue();
        assertThat(Hls.isSafePath("1080p/seg_0042.ts")).isTrue();
        for (String bad : List.of("../video", "720p/../../secrets", "720p/evil.txt", "/etc/passwd", "master.m3u8/x", "72p/index.m3u8",
                "720p/seg_.ts", "720p\\index.m3u8", "", "%2e%2e/x")) {
            assertThat(Hls.isSafePath(bad)).as(bad).isFalse();
        }
        assertThat(Hls.isSafePath(null)).isFalse();
    }

    @Test
    void contentTypesMatchWhatPlayersExpect() {
        assertThat(Hls.contentType("a/index.m3u8")).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(Hls.contentType("a/seg_0001.ts")).isEqualTo("video/mp2t");
    }
}
