package su.sadrobot.yashlang.controller;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;

public class StreamHelper {
    public static class StreamSources {
        private List<VideoStream> videoStreams;
        private List<AudioStream> audioStreams;

        public StreamSources(final List<VideoStream> videoStreams, final List<AudioStream> audioStreams) {
            this.videoStreams = videoStreams;
            this.audioStreams = audioStreams;
        }

        public List<VideoStream> getVideoStreams() {
            return videoStreams;
        }

        public List<AudioStream> getAudioStreams() {
            return audioStreams;
        }
    }

    public static class StreamPair {
        private VideoStream videoStream;
        private AudioStream audioStream;

        public StreamPair(final VideoStream videoStream, final AudioStream audioStream) {
            this.videoStream = videoStream;
            this.audioStream = audioStream;
        }

        public VideoStream getVideoStream() {
            return videoStream;
        }

        public AudioStream getAudioStream() {
            return audioStream;
        }
    }

    /**
     * Выбрать из списка следующий поток:
     * - если текущий поток null, вернуть поток по умолчанию (из логики настроек)
     * - если не null, выбрать поток, соответствующий логике настроек
     * - если следующего потока нет, то вернуть null
     *
     * Если видео-поток содержит дорожку аудио, то вернуть только поток видео, аудио-поток будет null
     * Если видео-поток не содержит аудио, то выбрать и вернуть дорожку аудио
     *
     * @param videoStreams
     * @param audioStreams
     * @param currVideoStream
     * @return следующий поток ролика для проигрывания
     */
    public static StreamPair getNextPlaybackStream(final List<VideoStream> videoStreams, final List<AudioStream> audioStreams, final VideoStream currVideoStream) {
        if (ConfigOptions.DEVEL_MODE_ON) {
            printStreamsInfo(videoStreams, audioStreams);
        }
        VideoStream _videoStream;
        if (currVideoStream == null) {
            // выбирать стрим с наилучшим качеством (в начале)
            _videoStream = videoStreams.size() > 0 ? videoStreams.get(0) : null;
        } else {
            // взять стрим последний с списке (с наименьшим качеством - обычно это 144p mp4)
            _videoStream = videoStreams.size() > 0 ? videoStreams.get(videoStreams.size() - 1) : null;
        }

        final AudioStream _audioStream = _videoStream.isVideoOnly && audioStreams.size() > 0 ? audioStreams.get(0) : null;
        return new StreamPair(_videoStream, _audioStream);
    }

    /**
     * Получить объект StreamPair для потока видео: если поток видео содержит дорожку аудио, то
     * вернуть только исходный поток видео videoStream. Если аудио дорожки нет, то выбрать поток аудио
     * из доступных audioStreams
     * @param videoStream
     * @param audioStreams
     * @return
     */
    public static StreamPair getPlaybackStreamPair(final VideoStream videoStream, final List<AudioStream> audioStreams) {
        final AudioStream _audioStream = videoStream.isVideoOnly && audioStreams.size() > 0 ? audioStreams.get(0) : null;
        return new StreamPair(videoStream, _audioStream);
    }

    private static void printStreamsInfo(final List<VideoStream> videoStreams, final List<AudioStream> audioStreams) {
        for (final VideoStream stream : videoStreams) {
            System.out.println(stream.getResolution() + " " + stream.getFormat().getName() + " " + stream.getFormat().getMimeType() + " " + stream.getFormat().getSuffix());
        }
        for (final AudioStream stream : audioStreams) {
            System.out.println(stream.getBitrate() + " " + stream.getFormat().getMimeType() + " " + stream.getFormat().getSuffix());
        }
    }
}
