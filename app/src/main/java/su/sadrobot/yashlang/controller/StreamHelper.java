package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ContentLoader.java is part of YaShlang.
 *
 * YaShlang is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YaShlang is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with YaShlang.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.content.Context;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

public class StreamHelper {

    public static class StreamInfo {

        private StreamCache.StreamType streamType;

        private boolean online;

        private String resolution;

        private String quality;

        private String formatName;

        private String formatMimeType;

        private String formatSuffix;

        private String url;

        public StreamInfo(final VideoStream videoStream) {
            this.online = true;
            if(videoStream.isVideoOnly) {
                this.streamType = StreamCache.StreamType.VIDEO;
            } else {
                this.streamType = StreamCache.StreamType.BOTH;
            }
            this.formatName = videoStream.getFormat().getName();
            this.formatMimeType = videoStream.getFormat().getMimeType();
            this.formatSuffix = videoStream.getFormat().getSuffix();
            this.resolution = videoStream.getResolution();
            this.quality = videoStream.getQuality();
            this.url = videoStream.getUrl();
        }

        public StreamInfo(final AudioStream audioStream) {
            this.online = true;
            this.streamType = StreamCache.StreamType.AUDIO;
            this.formatName = audioStream.getFormat().getName();
            this.formatMimeType = audioStream.getFormat().getMimeType();
            this.formatSuffix = audioStream.getFormat().getSuffix();
            this.resolution = String.valueOf(audioStream.getBitrate());
            this.quality = audioStream.getQuality();
            this.url = audioStream.getUrl();
        }

        public StreamInfo(final Context context, final StreamCache streamCache) throws MalformedURLException {
            this.online = false;
            this.streamType = streamCache.getStreamTypeEnum();
            this.formatName = streamCache.getStreamFormat();
            this.formatMimeType = streamCache.getStreamMimeType();
            this.formatSuffix = streamCache.getStreamFormatSuffix();
            this.resolution = streamCache.getStreamRes();
            this.url = StreamCacheFsManager.getFileForStream(context, streamCache).toURI().toURL().toString();
        }

        public StreamCache.StreamType getStreamType() {
            return streamType;
        }

        public boolean isOnline() {
            return online;
        }

        public String getResolution() {
            return resolution;
        }

        public String getQuality() {
            return quality;
        }

        public String getFormatName() {
            return formatName;
        }

        public String getFormatMimeType() {
            return formatMimeType;
        }

        public String getFormatSuffix() {
            return formatSuffix;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class StreamSources {
        private List<StreamInfo> videoStreams;
        private List<StreamInfo> audioStreams;
        public final List<Exception> problems = new ArrayList<>();

        public StreamSources(final List<StreamInfo> videoStreams, final List<StreamInfo> audioStreams) {
            this.videoStreams = videoStreams;
            this.audioStreams = audioStreams;
        }

        public List<StreamInfo> getVideoStreams() {
            return videoStreams;
        }

        public List<StreamInfo> getAudioStreams() {
            return audioStreams;
        }
    }

    public static class StreamPair {
        private StreamInfo videoStream;
        private StreamInfo audioStream;

        public StreamPair(final StreamInfo videoStream, final StreamInfo audioStream) {
            this.videoStream = videoStream;
            this.audioStream = audioStream;
        }

        public StreamInfo getVideoStream() {
            return videoStream;
        }

        public StreamInfo getAudioStream() {
            return audioStream;
        }
    }

    /**
     * Добыть потоки для ролика - локальные (из базы данных) и онлайн (по url через NewPipeExtractor)
     *
     * @param context
     * @param videoItem
     * @return
     */
    public static StreamHelper.StreamSources fetchStreams(final Context context, final VideoItem videoItem) {
        final List<StreamInfo> allVideoStreams = new ArrayList<>();
        final List<StreamInfo> allAudioStreams = new ArrayList<>();
        final List<Exception> problems = new ArrayList<>();

        // получить локальные потоки (оффлайн)
        final List<StreamCache> streamCacheList = VideoDatabase.getDbInstance(context).streamCacheDao().getFinishedForVideo(videoItem.getId());
        for (final StreamCache streamCache : streamCacheList) {
            try {
                final StreamInfo streamInfo = new StreamInfo(context, streamCache);
                if (streamCache.getStreamTypeEnum() != StreamCache.StreamType.AUDIO) {
                    // VIDEO или BOTH
                    allVideoStreams.add(streamInfo);
                } else {
                    allAudioStreams.add(streamInfo);
                }
            } catch (final MalformedURLException e) {
                problems.add(e);
            }
        }

        // получить онлайн-потоки
        try {
            StreamHelper.StreamSources onlineStreamSources = ContentLoader.getInstance().extractStreams(videoItem.getItemUrl());

            allVideoStreams.addAll(onlineStreamSources.getVideoStreams());
            allAudioStreams.addAll(onlineStreamSources.getAudioStreams());
        } catch (ExtractionException | IOException e) {
            problems.add(e);
        }

        final StreamHelper.StreamSources streamSources = new StreamSources(allVideoStreams, allAudioStreams);
        streamSources.problems.addAll(problems);
        return streamSources;
    }

    public static List<StreamInfo> toStreamInfoListFromVideoList(final List<VideoStream> videoStreams) {
        final List<StreamInfo> _streams = new ArrayList<>();
        for(VideoStream videoStream : videoStreams) {
            _streams.add(new StreamInfo(videoStream));
        }
        return _streams;
    }

    public static List<StreamInfo> toStreamInfoListFromAudioList(final List<AudioStream> audioStreams) {
        final List<StreamInfo> _streams = new ArrayList<>();
        for(AudioStream audioStream : audioStreams) {
            _streams.add(new StreamInfo(audioStream));
        }
        return _streams;
    }

    public static StreamInfo getNextPlaybackStreamMaxRes(
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {
        StreamInfo _videoStream;
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

    public static StreamInfo getNextPlaybackStreamMinRes(
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {
        StreamInfo _videoStream;
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

    public static StreamInfo getNextPlaybackStreamForRes(
            final String targetRes,
            final ConfigOptions.VideoStreamSelectPreferRes preferRes,
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {
        StreamInfo _videoStream;
        // https://ru.wikipedia.org/wiki/480p
        // разрешение может быть указано как 480p или 480p60 (60 - количество кадров в секунду)
        final int _targetRes = Integer.valueOf(targetRes.replaceAll("p.*", ""));

        // Преполагаем, что videoStreams уже отсортированы по качеству resolution (численно) по убыванию
        // (в начале списка наиболее высокое качество)
        if(currVideoStream == null) {
            StreamInfo streamWithTargetRes = null;

            // Ищем поток с целевым качеством. Его в списке доступных разрешений может не быть.
            // В таком случае выбираем наиболее близкое к нему в зависимости от настроек:
            // если "предпочитать лучшее", то находим следующее лучшее качество
            // если "предпочитать худшее", то находим следующее худшее качество
            if (preferRes == ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES) {
                // движемся по качеству от худшего к лучшим до тех пор, пока не найдем ролик с нужным разрешением или
                // с разрешением больше, чем указано в настройках
                for (int i = videoStreams.size() - 1; i >= 0; i--) {
                    final StreamInfo vidStream = videoStreams.get(i);
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
                    final StreamInfo vidStream = videoStreams.get(i);
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
                    final StreamInfo vidStream = videoStreams.get(i);
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
                    final StreamInfo vidStream = videoStreams.get(i);
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
            final List<StreamInfo> videoStreams, final List<StreamInfo> audioStreams,
            final StreamInfo currVideoStream) {
        StreamInfo _videoStream;
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

        final StreamInfo _audioStream = _videoStream.getStreamType() == StreamCache.StreamType.VIDEO && audioStreams.size() > 0 ? audioStreams.get(0) : null;
        return new StreamPair(_videoStream, _audioStream);
    }

    public static StreamInfo findPlaybackStream(
            final StreamHelper.StreamSources streamSources,
            final StreamCache.StreamType streamType,
            final String resolution,
            final String formatName) {
        StreamInfo streamInfo = null;
        if (streamType == StreamCache.StreamType.VIDEO || streamType == StreamCache.StreamType.BOTH) {
            for (final StreamInfo _streamInfo : streamSources.getVideoStreams()) {
                if (_streamInfo.getStreamType() == streamType &&
                        _streamInfo.getResolution().equals(resolution) &&
                        _streamInfo.getFormatName().equals(formatName)) {
                    streamInfo = _streamInfo;
                    break;
                }
            }
        } else if (streamType == StreamCache.StreamType.AUDIO) {
            for (final StreamInfo _streamInfo : streamSources.getAudioStreams()) {
                if (_streamInfo.getStreamType() == streamType &&
                        _streamInfo.getResolution().equals(resolution) &&
                        _streamInfo.getFormatName().equals(formatName)) {
                    streamInfo = _streamInfo;
                    break;
                }
            }
        }
        return streamInfo;
    }

    /**
     * Получить объект StreamPair для потока видео: если поток видео содержит дорожку аудио, то
     * вернуть только исходный поток видео videoStream. Если аудио дорожки нет, то выбрать поток аудио
     * из доступных audioStreams
     * @param videoStream
     * @param audioStreams
     * @return
     */
    public static StreamPair getPlaybackStreamPair(final StreamInfo videoStream, final List<StreamInfo> audioStreams) {
        final StreamInfo _audioStream = videoStream.getStreamType() == StreamCache.StreamType.VIDEO && audioStreams.size() > 0 ? audioStreams.get(0) : null;
        return new StreamPair(videoStream, _audioStream);
    }
}
