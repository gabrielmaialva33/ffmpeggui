package com.silentlexx.ffmpeggui_os.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.Arrays;

public class FfmpegCommandParserTest {

    @Test
    public void preservesHashInQuotedPaths() throws Exception {
        assertEquals(
                Arrays.asList("ffmpeg", "-i", "/storage/My # clip.mp4",
                        "/storage/out#1.mp4"),
                FfmpegCommandParser.parseArguments(
                        "ffmpeg -i \"/storage/My # clip.mp4\" \"/storage/out#1.mp4\""));
    }

    @Test
    public void groupsWhitespaceInsideDoubleQuotes() throws Exception {
        assertEquals(
                Arrays.asList("-vf", "scale=1280:720,drawtext=text=hello world", "out.mp4"),
                FfmpegCommandParser.parseArguments(
                        "-vf \"scale=1280:720,drawtext=text=hello world\" out.mp4"));
    }

    @Test
    public void preservesExplicitEmptyArguments() throws Exception {
        assertEquals(
                Arrays.asList("-metadata", "comment=", ""),
                FfmpegCommandParser.parseArguments("-metadata comment=\"\" \"\""));
    }

    @Test
    public void rejectsUnmatchedDoubleQuoteWithExplicitError() {
        FfmpegCommandParser.ParseException error = assertThrows(
                FfmpegCommandParser.ParseException.class,
                () -> FfmpegCommandParser.parseArguments(
                        "ffmpeg -i \"/storage/in.mp4 out.mp4"));

        assertEquals("The FFmpeg command contains an unmatched double quote",
                error.getMessage());
    }

    @Test
    public void extractsFilesWithoutTreatingQuotedFilterAsAFile() throws Exception {
        FfmpegCommandParser.FileArguments files =
                FfmpegCommandParser.extractFileArguments(
                        "ffmpeg -i \"/storage/in.mp4\" " +
                                "-vf \"scale=1280:720,drawtext=text=hello world\" " +
                                "\"/storage/out.mp4\"");

        assertEquals(Arrays.asList("/storage/in.mp4"), files.getInputs());
        assertEquals("/storage/out.mp4", files.getOutput());
    }

    @Test
    public void preservesHashWhenExtractingFiles() throws Exception {
        FfmpegCommandParser.FileArguments files =
                FfmpegCommandParser.extractFileArguments(
                        "ffmpeg -i \"/storage/in#1 clip.mp4\" \"/storage/out#2.mp4\"");

        assertEquals(Arrays.asList("/storage/in#1 clip.mp4"), files.getInputs());
        assertEquals("/storage/out#2.mp4", files.getOutput());
    }

    @Test
    public void extractsEveryInputInCommandOrder() throws Exception {
        FfmpegCommandParser.FileArguments files =
                FfmpegCommandParser.extractFileArguments(
                        "ffmpeg -i \"/storage/first.mp4\" -ss 1 " +
                                "-i \"/storage/second audio.m4a\" " +
                                "-c copy \"/storage/combined.mp4\"");

        assertEquals(Arrays.asList("/storage/first.mp4", "/storage/second audio.m4a"),
                files.getInputs());
        assertEquals("/storage/combined.mp4", files.getOutput());
    }
}
