package com.secureportal.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "transcode")
public class TranscodeProperties {

    private boolean enabled = true;
    private String ffmpeg = "ffmpeg";
    private String ffprobe = "ffprobe";
    private int timeoutMinutes = 240;
    /** Renditions to make, smallest first; ones taller than the source are skipped. */
    private List<Integer> heights = List.of(360, 720, 1080);
    private int segmentSeconds = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public String getFfprobe() {
        return ffprobe;
    }

    public void setFfprobe(String ffprobe) {
        this.ffprobe = ffprobe;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public List<Integer> getHeights() {
        return heights;
    }

    public void setHeights(List<Integer> heights) {
        this.heights = heights;
    }

    public int getSegmentSeconds() {
        return segmentSeconds;
    }

    public void setSegmentSeconds(int segmentSeconds) {
        this.segmentSeconds = segmentSeconds;
    }
}
