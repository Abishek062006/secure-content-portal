package com.secureportal.video;

import java.util.ArrayList;
import java.util.List;

/** The parts of HLS packaging that are plain logic: which renditions to make and what the master playlist says. */
public final class Hls {

    private Hls() {
    }

    public record Rendition(int height, int width, int videoKbps) {
        public String directory() {
            return height + "p";
        }

        public int bandwidth() {
            return (videoKbps + 128) * 1000;
        }
    }

    /**
     * The renditions worth making for a source of the given size: the configured heights that don't upscale it, or
     * just the source's own size when it is smaller than all of them.
     */
    public static List<Rendition> plan(int sourceWidth, int sourceHeight, List<Integer> heights) {
        List<Rendition> renditions = new ArrayList<>();
        for (int height : heights.stream().sorted().toList()) {
            if (height <= sourceHeight) {
                renditions.add(new Rendition(height, evenWidth(sourceWidth, sourceHeight, height), bitrate(height)));
            }
        }
        if (renditions.isEmpty()) {
            int height = sourceHeight - sourceHeight % 2;
            renditions.add(new Rendition(height, evenWidth(sourceWidth, sourceHeight, height), bitrate(height)));
        }
        return renditions;
    }

    /** Width that keeps the source's shape at the new height, rounded to an even number as H.264 requires. */
    static int evenWidth(int sourceWidth, int sourceHeight, int height) {
        int width = (int) Math.round((double) sourceWidth * height / sourceHeight);
        return Math.max(2, width - width % 2);
    }

    static int bitrate(int height) {
        if (height >= 1080) {
            return 4500;
        }
        if (height >= 720) {
            return 2500;
        }
        if (height >= 480) {
            return 1200;
        }
        return 700;
    }

    /** The master playlist: one entry per rendition, with relative addresses so it works behind any URL prefix. */
    public static String masterPlaylist(List<Rendition> renditions) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (Rendition r : renditions) {
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(r.bandwidth()).append(",RESOLUTION=")
                    .append(r.width()).append('x').append(r.height()).append('\n')
                    .append(r.directory()).append("/index.m3u8\n");
        }
        return sb.toString();
    }

    public static String contentType(String path) {
        if (path.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (path.endsWith(".ts")) {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }

    /** Only the files this app writes may be requested: {@code master.m3u8}, {@code 720p/index.m3u8}, {@code 720p/seg_0001.ts}. */
    public static boolean isSafePath(String path) {
        return path != null && path.matches("master\\.m3u8|[0-9]{3,4}p/index\\.m3u8|[0-9]{3,4}p/seg_[0-9]{1,6}\\.ts");
    }
}
