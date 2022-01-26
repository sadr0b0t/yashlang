package su.sadrobot.yashlang.controller;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;

public class StreamHelperTest {
    private static boolean assertEqual(final String actual, final String expected) {
        //System.out.println(message + ": ");
        final boolean pass = actual.equals(expected);
        if (pass) {
            System.out.print("PASS: ");
        } else {
            System.out.print("FAIL: ");
        }
        System.out.println("test if actual value '" + actual + "' is EQUAL to expected '" + expected + "'");

        return pass;
    }

    private static boolean assertNull(final Object actual) {
        //System.out.println(message + ": ");
        final boolean pass = actual == null;
        if (pass) {
            System.out.print("PASS: ");
        } else {
            System.out.print("FAIL: ");
        }
        System.out.println("test if actual value is NULL");

        return pass;
    }

    public static void main(String args[]) {
        // https://ru.wikipedia.org/wiki/480p
        // разрешение может быть указано как 480p или 480p60 (60 - количество кадров в секунду)
        System.out.println("480p60".replaceAll("p.*", "") );

        testNextPlaybackStreamMaxRes();
        testNextPlaybackStreamMinRes();
        testNextPlaybackStreamForRes();
    }

    public static void testNextPlaybackStreamMaxRes() {
        System.out.println("***** TEST: testNextPlaybackStreamMaxRes *****");

        final List<VideoStream> videoSources1 = new ArrayList<>();
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "1080p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "480p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "360p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "240p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "144p"));

        VideoStream nextStream;
        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, null);
        assertEqual(nextStream.getResolution(), "1080p");

        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "360p");

        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "240p");

        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "144p");

        nextStream = StreamHelper.getNextPlaybackStreamMaxRes(videoSources1, nextStream);
        assertNull(nextStream);
    }

    public static void testNextPlaybackStreamMinRes() {
        System.out.println("***** TEST: testNextPlaybackStreamMinRes *****");

        final List<VideoStream> videoSources1 = new ArrayList<>();
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "1080p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "480p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "360p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "240p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "144p"));

        VideoStream nextStream;
        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, null);
        assertEqual(nextStream.getResolution(), "144p");

        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "240p");

        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "360p");

        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "1080p");

        nextStream = StreamHelper.getNextPlaybackStreamMinRes(videoSources1, nextStream);
        assertNull(nextStream);
    }

    public static void testNextPlaybackStreamForRes() {
        System.out.println("***** TEST: testNextPlaybackStreamForRes *****");

        // video sources list - v1
        final List<VideoStream> videoSources1 = new ArrayList<>();
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "1080p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "480p"));
        // пусть этого варианта нет в списке
        //videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "360p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "240p"));
        videoSources1.add(new VideoStream("urll", MediaFormat.MPEG_4, "144p"));

        VideoStream nextStream;

        // ***** TEST-0 *****
        // выбор первого элемента
        // проверка точного совпадения
        nextStream = StreamHelper.getNextPlaybackStreamForRes("480p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("1080p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "1080p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("1081p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "1080p");


        nextStream = StreamHelper.getNextPlaybackStreamForRes("480p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("144p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "144p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("140p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "144p");

        // ***** TEST-1 *****
        // Последовательный выбор элементов
        // предпочитать видео с более высоким разрешением, следующее за текущим
        // здесь в настройках указано разрешение, которого нет в списке
        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "1080p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "240p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "144p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES, videoSources1, nextStream);
        assertNull(nextStream);

        // ***** TEST-2 *****
        // Последовательный выбор элементов:
        // предпочитать видео с более низким разрешением, следующее за текущим
        // здесь в настройках указано разрешение, которого нет в списке
        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, null);
        assertEqual(nextStream.getResolution(), "240p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "144p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "480p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, nextStream);
        assertEqual(nextStream.getResolution(), "1080p");

        nextStream = StreamHelper.getNextPlaybackStreamForRes("360p",
                ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES, videoSources1, nextStream);
        assertNull(nextStream);
    }
}
