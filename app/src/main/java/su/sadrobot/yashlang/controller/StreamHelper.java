package su.sadrobot.yashlang.controller;

import android.content.Context;

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

    public static VideoStream getNextPlaybackStreamMaxRes(
            final List<VideoStream> videoStreams,
            final VideoStream currVideoStream) {
        VideoStream _videoStream;
        if (currVideoStream == null) {
            // выбирать стрим с наилучшим качеством (в начале)
            _videoStream = videoStreams.size() > 0 ? videoStreams.get(0) : null;
        } else {
            // найти в вписке текущий поток, вернуть следующий по списку (хуже по качеству)
            // или null, если это был последний
            int currInd = videoStreams.indexOf(currVideoStream);
            if(currInd != -1 && currInd < videoStreams.size() - 1) {
                _videoStream = videoStreams.get(currInd + 1);
            } else {
                _videoStream = null;
            }
        }
        return _videoStream;
    }

    public static VideoStream getNextPlaybackStreamMinRes(
            final List<VideoStream> videoStreams,
            final VideoStream currVideoStream) {
        VideoStream _videoStream;
        if (currVideoStream == null) {
            // выбирать стрим с наихудшим качеством (в конце)
            _videoStream = videoStreams.size() > 0 ? videoStreams.get(videoStreams.size() - 1) : null;
        } else {
            // найти в вписке текущий поток, вернуть предыдущий по списку  (лучше по качеству)
            // или null, если это был последний
            int currInd = videoStreams.indexOf(currVideoStream);
            if(currInd != -1 && currInd > 0) {
                _videoStream = videoStreams.get(currInd - 1);
            } else {
                _videoStream = null;
            }
        }
        return _videoStream;
    }

    public static VideoStream getNextPlaybackStreamForRes(
            final String targetRes,
            final ConfigOptions.VideoStreamSelectPreferRes preferRes,
            final List<VideoStream> videoStreams,
            final VideoStream currVideoStream) {
        VideoStream _videoStream;
        // https://ru.wikipedia.org/wiki/480p
        // разрешение может быть указано как 480p или 480p60 (60 - количество кадров в секунду)
        final int _targetRes = Integer.valueOf(targetRes.replaceAll("p.*", ""));

        // Преполагаем, что videoStreams уже отсортированы по качеству resolution (численно) по убыванию
        // (в начале списка наиболее высокое качество)
        if(currVideoStream == null) {
            VideoStream streamWithTargetRes = null;

            // Ищем поток с целевым качеством. Его в списке доступных разрешений может не быть.
            // В таком случае выбираем наиболее близкое к нему в зависимости от настроек:
            // если "предпочитать лучшее", то находим следующее лучшее качество
            // если "предпочитать худшее", то находим следующее худшее качество
            if (preferRes == ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES) {
                // движемся по качеству от худшего к лучшим до тех пор, пока не найдем ролик с нужным разрешением или
                // с разрешением больше, чем указано в настройках
                for (int i = videoStreams.size() - 1; i >= 0; i--) {
                    final VideoStream vidStream = videoStreams.get(i);
                    final int vidRes = Integer.valueOf(vidStream.getResolution().replace("p", ""));
                    if (vidRes >= _targetRes) {
                        streamWithTargetRes = vidStream;
                        break;
                    }
                }

                if (streamWithTargetRes != null) {
                    _videoStream = streamWithTargetRes;
                } else {
                    // не нашли в списке разрешение большее или равное целевому -
                    // берем максимальное из доступных
                    _videoStream = videoStreams.size() > 0 ? videoStreams.get(0) : null;
                }
            } else {
                // preferRes == ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES
                // движемся по качеству от лучшего к худшим до тех пор, пока не найдем ролик с нужным разрешением или
                // с разрешением меньше, чем указано в настройках
                for (int i = 0; i < videoStreams.size(); i++) {
                    final VideoStream vidStream = videoStreams.get(i);
                    final int vidRes = Integer.valueOf(vidStream.getResolution().replace("p", ""));
                    if (vidRes <= _targetRes) {
                        streamWithTargetRes = vidStream;
                        break;
                    }
                }

                if (streamWithTargetRes != null) {
                    _videoStream = streamWithTargetRes;
                } else {
                    // не нашли в списке разрешение большее или равное целевому -
                    // берем минимальное из доступных
                    _videoStream = videoStreams.size() > 0 ? videoStreams.get(videoStreams.size() - 1) : null;
                }
            }
        } else {
            // мы уже находили стрим, теперь нужно взять следующий по алгоритму
            int currInd = videoStreams.indexOf(currVideoStream);
            if (currInd == -1) {
                // но почему-то в списке не нашли: так быть не должно но считаем,
                // что следующего по качеству потока нет
                _videoStream = null;
            } else if (preferRes == ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES) {
                // нужно понять в какой пловине списка мы находимся:
                // если мы находимся в половине списка, которая выше целевого разрешения, то
                //   - если есть поток с большим разрешением, то просто берем его
                //   - если такого потока нет, то спускаемся в нижнюю половину списка -
                //     выбираем первое разрешение, которое меньше целевого
                // если мы находимся в половине списка, которая ниже целевого разрешения, то
                //   - берем разрешение, которое меньше текущего
                //   - если такого нет, то все варианты закончились - возвращаем null

                int targetInd = -1;

                // движемся по качеству от худшего к лучшим
                for (int i = videoStreams.size() - 1; i >= 0; i--) {
                    final VideoStream vidStream = videoStreams.get(i);
                    final int vidRes = Integer.valueOf(vidStream.getResolution().replace("p", ""));
                    if (vidRes >= _targetRes) {
                        targetInd = i;
                        break;
                    }
                }

                // targetInd - индекс потока в настройках (разрешение из настроек или выше)
                // currInd - индекс текущего потока
                if (targetInd != -1 && currInd <= targetInd) {
                    // поток в настройках есть и качество текущего потока выше настроечного
                    if (currInd - 1 >= 0) {
                        // есть поток следующий по качеству - берем его
                        _videoStream = videoStreams.get(currInd - 1);
                    } else {
                        // потока выше качества нет - берем поток, который на одну позицию хуже качеством, чем настроечный
                        // или null - больше нет подходящих потоков
                        _videoStream = (targetInd + 1) < videoStreams.size() ? videoStreams.get(targetInd + 1) : null;
                    }
                } else {
                    // не нашли поток с разрешением больше или равным настроечному
                    // или качество текущего потока меньше настроечного
                    // значит берем максимальное из доступных, но меньше текущего
                    _videoStream = (currInd + 1) < videoStreams.size() ? videoStreams.get(currInd + 1) : null;
                }
            } else {
                // preferRes == ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES

                // нужно понять в какой пловине списка мы находимся:
                // если мы находимся в половине списка, которая ниже целевого разрешения, то
                //   - если есть поток с меньшим разрешением, то просто берем его
                //   - если такого потока нет, то поднимаемся в верхнюю половину списка -
                //     выбираем первое разрешение, которое больше целевого
                // если мы находимся в половине списка, которая выше целевого разрешения, то
                //   - берем разрешение, которое больше текущего
                //   - если такого нет, то все варианты закончились - возвращаем null

                int targetInd = -1;
                // движемся по качеству от лучшего к худшим
                for (int i = 0; i < videoStreams.size(); i++) {
                    final VideoStream vidStream = videoStreams.get(i);
                    final int vidRes = Integer.valueOf(vidStream.getResolution().replace("p", ""));
                    if (vidRes <= _targetRes) {
                        targetInd = i;
                        break;
                    }
                }

                // targetInd - индекс потока в настройках (разрешение из настроек или ниже)
                // currInd - индекс текущего потока
                if (targetInd != -1 && currInd >= targetInd) {
                    // поток в настройках есть и качество текущего потока ниже настроечного
                    if (currInd + 1 < videoStreams.size()) {
                        // есть поток следующий по качеству - берем его
                        _videoStream = videoStreams.get(currInd + 1);
                    } else {
                        // потока выше качества нет - берем поток, который на одну позицию лучше качеством, чем настроечный
                        // или null - больше нет подходящих потоков
                        _videoStream = targetInd - 1 >= 0 ? videoStreams.get(targetInd - 1) : null;
                    }
                } else {
                    // не нашли поток с разрешением меньше или равным настроечному
                    // или качество текущего потока выше настроечного
                    // значит берем минимальное из доступных, но больше текущего
                    _videoStream = (currInd - 1) >= 0  ? videoStreams.get(currInd - 1) : null;
                }
            }
        }

        return _videoStream;
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
    public static StreamPair getNextPlaybackStream(
            final Context context,
            final List<VideoStream> videoStreams, final List<AudioStream> audioStreams,
            final VideoStream currVideoStream) {
        if (ConfigOptions.DEVEL_MODE_ON) {
            printStreamsInfo(videoStreams, audioStreams);
        }
        VideoStream _videoStream;
        switch (ConfigOptions.getVideoStreamSelectStrategy(context)) {
            case MAX_RES:
                _videoStream = getNextPlaybackStreamMaxRes(videoStreams, currVideoStream);
                break;
            case MIN_RES:
                _videoStream = getNextPlaybackStreamMinRes(videoStreams, currVideoStream);
                break;
            case CUSTOM_RES:
                _videoStream = getNextPlaybackStreamForRes(
                        ConfigOptions.getVideoStreamCustomRes(context),
                        ConfigOptions.getVideoStreamSelectCustomPreferRes(context),
                        videoStreams, currVideoStream);
                break;
            case LAST_CHOSEN:
                _videoStream = getNextPlaybackStreamForRes(
                        ConfigOptions.getVideoStreamLastSelectedRes(context),
                        ConfigOptions.getVideoStreamSelectLastPreferRes(context),
                        videoStreams, currVideoStream);
                break;
            default:
                // сюда не попадем
                if (currVideoStream == null) {
                    // выбирать стрим с наилучшим качеством (в начале)
                    _videoStream = videoStreams.size() > 0 ? videoStreams.get(0) : null;
                } else {
                    // взять стрим последний с списке (с наименьшим качеством - обычно это 144p mp4)
                    _videoStream = videoStreams.size() > 0 ? videoStreams.get(videoStreams.size() - 1) : null;
                }
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
