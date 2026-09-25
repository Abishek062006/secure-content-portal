package com.secureportal.course;

import com.secureportal.course.dto.TranscriptCue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VttParserTest {

    @Test
    void parsesZoomStyleCuesAndSkipsTheHeader() {
        String vtt = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:04.500\nAbishek: Welcome to the course.\n\n"
                + "2\n00:01:05.250 --> 00:01:09.000\nToday we cover\nsigned tickets.\n";

        List<TranscriptCue> cues = VttParser.parse(vtt);

        assertThat(cues).hasSize(2);
        assertThat(cues.get(0).start()).isEqualTo(1.0);
        assertThat(cues.get(0).end()).isEqualTo(4.5);
        assertThat(cues.get(0).text()).isEqualTo("Abishek: Welcome to the course.");
        assertThat(cues.get(1).start()).isEqualTo(65.25);
        assertThat(cues.get(1).text()).isEqualTo("Today we cover signed tickets.");
    }

    @Test
    void handlesShortTimestampsCrlfTagsAndNoteBlocks() {
        String vtt = "﻿WEBVTT\r\n\r\nNOTE a comment\r\n\r\n00:02.000 --> 00:03.000 align:start\r\n<v Host>Hello <c>there</c></v>\r\n";

        List<TranscriptCue> cues = VttParser.parse(vtt);

        assertThat(cues).hasSize(1);
        assertThat(cues.get(0).start()).isEqualTo(2.0);
        assertThat(cues.get(0).text()).isEqualTo("Hello there");
    }

    @Test
    void ignoresCuesWithUnreadableTimingsOrEmptyText() {
        String vtt = "WEBVTT\n\nnope --> alsonope\ntext\n\n00:00:01.000 --> 00:00:02.000\n<b></b>\n";

        assertThat(VttParser.parse(vtt)).isEmpty();
    }
}
