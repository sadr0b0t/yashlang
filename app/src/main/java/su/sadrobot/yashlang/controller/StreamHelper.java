package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
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
import java.util.Collections;
import java.util.Comparator;
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

        StreamInfo(
                final StreamCache.StreamType streamType,
                final boolean online,
                final String resolution,
                final String quality,
                final String formatName,
                final String formatMimeType,
                final String formatSuffix,
                final String url) {
            this.streamType = streamType;
            this.online = online;
            this.resolution = resolution;
            this.quality = quality;
            this.formatName = formatName;
            this.formatMimeType = formatMimeType;
            this.formatSuffix = formatSuffix;
            this.url = url;
        }

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

    private static int resolutionToInt(final String resolution) {
        // https://ru.wikipedia.org/wiki/480p
        // разрешение может быть указано как 480p или 480p60 (60 - количество кадров в секунду)
        return Integer.valueOf(resolution.replaceAll("p.*", ""));
    }

    /**
     * Добыть потоки для ролика - онлайн (по url через NewPipeExtractor)
     *
     * @param videoItem
     *
     * @return
     */
    public static StreamHelper.StreamSources fetchOnlineStreams(final VideoItem videoItem) {
        final List<StreamInfo> allVideoStreams = new ArrayList<>();
        final List<StreamInfo> allAudioStreams = new ArrayList<>();
        final List<Exception> problems = new ArrayList<>();

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

    /**
     * Добыть потоки для ролика - локальные (из базы данных)
     *
     * @param context
     * @param videoItem
     *
     * @return
     */
    public static StreamHelper.StreamSources fetchOfflineStreams(
            final Context context, final VideoItem videoItem) {
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

        final StreamHelper.StreamSources streamSources = new StreamSources(allVideoStreams, allAudioStreams);
        streamSources.problems.addAll(problems);
        return streamSources;
    }

    /**
     * Добыть потоки для ролика - локальные (из базы данных) и онлайн (по url через NewPipeExtractor)
     *
     * @param context
     * @param videoItem
     *
     * @return
     */
    public static StreamHelper.StreamSources fetchStreams(
            final Context context, final VideoItem videoItem) {
        final List<StreamInfo> allVideoStreams = new ArrayList<>();
        final List<StreamInfo> allAudioStreams = new ArrayList<>();
        final List<Exception> problems = new ArrayList<>();

        final StreamHelper.StreamSources offlineStreamSources = fetchOfflineStreams(context, videoItem);
        allVideoStreams.addAll(offlineStreamSources.getVideoStreams());
        allAudioStreams.addAll(offlineStreamSources.getAudioStreams());
        problems.addAll(offlineStreamSources.problems);

        if (!ConfigOptions.getOfflineModeOn(context)) {
            final StreamHelper.StreamSources onlineStreamSources = fetchOnlineStreams(videoItem);
            allVideoStreams.addAll(onlineStreamSources.getVideoStreams());
            allAudioStreams.addAll(onlineStreamSources.getAudioStreams());
            problems.addAll(onlineStreamSources.problems);
        }

        final StreamHelper.StreamSources streamSources = new StreamSources(allVideoStreams, allAudioStreams);
        streamSources.problems.addAll(problems);
        return streamSources;
    }

    public static List<StreamInfo> toStreamInfoListFromVideoList(final List<VideoStream> videoStreams) {
        final List<StreamInfo> _streams = new ArrayList<>();
        for (VideoStream videoStream : videoStreams) {
            _streams.add(new StreamInfo(videoStream));
        }
        return _streams;
    }

    public static List<StreamInfo> toStreamInfoListFromAudioList(final List<AudioStream> audioStreams) {
        final List<StreamInfo> _streams = new ArrayList<>();
        for (AudioStream audioStream : audioStreams) {
            _streams.add(new StreamInfo(audioStream));
        }
        return _streams;
    }

    /**
     * Сортировка потоков видео для отображения в списках на экране.
     * @param videoStreams
     */
    public static void sortVideoStreamsDefault(final List<StreamInfo> videoStreams) {

        // сортируем потоки видео по правилу:
        // - в начале потоки с самым высоким качеством, дальше по убыванию
        // - для потоков с одинаковым качеством сначала идут оффлайн-потоки, потом онлайн
        // - для потоков с одинаковым качеством и одинаковым статусом оффлайн-онлайн,
        // сначала идут совмещенные потоки видео+аудио

        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        Collections.sort(videoStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                if (resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()) != 0) {
                    return -(resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()));
                } else if (!o1.isOnline() && o2.isOnline()) {
                    return -1;
                } else if (o1.isOnline() && !o2.isOnline()) {
                    return 1;
                } else if (o1.getStreamType() == StreamCache.StreamType.BOTH && o2.getStreamType() != StreamCache.StreamType.BOTH) {
                    return -1;
                } else if (o1.getStreamType() != StreamCache.StreamType.BOTH && o2.getStreamType() == StreamCache.StreamType.BOTH) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
    }

    /**
     * Сортировка потоков аудио для отображения в списках на экране.
     * @param audioStreams
     */
    public static void sortAudioStreamsDefault(final List<StreamInfo> audioStreams) {

        // сортируем потоки аудио по правилу:
        // - в начале потоки оффлайн, потом онлайн
        // - внутри групп оффлайн-онлайн потоки отсортированы по качеству (битрейт, в StreamInfo - resolution)

        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        Collections.sort(audioStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                if (!o1.isOnline() && o2.isOnline()) {
                    return -1;
                } else if (o1.isOnline() && !o2.isOnline()) {
                    return 1;
                } else {
                    return -(resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()));
                }
            }
        });
    }

    // в отдельном методе для удобства тестов
    static List<StreamInfo> sortVideoStreamsForMaxRes(
            final List<StreamInfo> videoStreams) {

        // сортируем от наилучшего качества к наихудшему
        // - при этом в каждой из групп с одинаковым качеством сначала идут потоки оффлайн, потому онлайн
        // - в каждой из групп с одинаковым качеством и статусом онлайн-оффлайн сначала идут потоки
        // видео+аудио, потом просто видео

        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        final List<StreamInfo> sortedStreams = new ArrayList<>(videoStreams);
        Collections.sort(sortedStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                if (resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()) != 0) {
                    return -(resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()));
                } else if (!o1.isOnline() && o2.isOnline()) {
                    return -1;
                } else if (o1.isOnline() && !o2.isOnline()) {
                    return 1;
                } else if (o1.getStreamType() == StreamCache.StreamType.BOTH && o2.getStreamType() != StreamCache.StreamType.BOTH) {
                    return -1;
                } else if (o1.getStreamType() != StreamCache.StreamType.BOTH && o2.getStreamType() == StreamCache.StreamType.BOTH) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return sortedStreams;
    }

    // в отдельном методе для удобства тестов
    static List<StreamInfo> sortVideoStreamsForMinRes(final List<StreamInfo> videoStreams) {

        // сортируем от наихудшего качества к наилучшему
        // - при этом в каждой из групп с одинаковым качеством сначала идут потоки оффлайн, потому онлайн
        // - в каждой из групп с одинаковым качеством и статусом онлайн-оффлайн сначала идут потоки
        // видео+аудио, потом просто видео

        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        final List<StreamInfo> sortedStreams = new ArrayList<>(videoStreams);
        Collections.sort(sortedStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                if (resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()) != 0) {
                    return (resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()));
                } else if (!o1.isOnline() && o2.isOnline()) {
                    return -1;
                } else if (o1.isOnline() && !o2.isOnline()) {
                    return 1;
                } else if (o1.getStreamType() == StreamCache.StreamType.BOTH && o2.getStreamType() != StreamCache.StreamType.BOTH) {
                    return -1;
                } else if (o1.getStreamType() != StreamCache.StreamType.BOTH && o2.getStreamType() == StreamCache.StreamType.BOTH) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return sortedStreams;
    }

    // в отдельном методе для удобства тестов
    static List<StreamInfo> sortVideoStreamsForRes(
            final String targetRes,
            final ConfigOptions.VideoStreamSelectPreferRes preferRes,
            final List<StreamInfo> videoStreams) {
        final int _targetRes = resolutionToInt(targetRes);

        // Ищем поток с целевым качеством. Его в списке доступных разрешений может не быть.
        // В таком случае выбираем наиболее близкое к нему в зависимости от настроек:
        // если "предпочитать лучшее", то находим следующее лучшее качество
        // если "предпочитать худшее", то находим следующее худшее качество

        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        final List<StreamInfo> sortedStreams = new ArrayList<>(videoStreams);
        if (preferRes == ConfigOptions.VideoStreamSelectPreferRes.HIGHER_RES) {
            // Движемся по качеству от худшего к лучшим до тех пор, пока не найдем ролик с нужным
            // разрешением или с разрешением больше, чем указано в настройках.
            // Если не нашли в списке разрешение большее или равное целевому, берем максимальное
            // из доступных.
            Collections.sort(sortedStreams, new Comparator<StreamInfo>() {
                @Override
                public int compare(StreamInfo o1, StreamInfo o2) {
                    final int resO1 = resolutionToInt(o1.getResolution());
                    final int resO2 = resolutionToInt(o2.getResolution());
                    if (resO1 == resO2) {
                        // Оба разрашения равны. Раньше идет тот, кто оффлайн
                        // Если оба оффлайн, то раньше идет поток с совмещением видео+аудио
                        if (!o1.isOnline() && o2.isOnline()) {
                            return -1;
                        } else if (o1.isOnline() && !o2.isOnline()) {
                            return 1;
                        } else if (o1.getStreamType() == StreamCache.StreamType.BOTH &&
                                o2.getStreamType() != StreamCache.StreamType.BOTH) {
                            return -1;
                        } else if (o1.getStreamType() != StreamCache.StreamType.BOTH &&
                                o2.getStreamType() == StreamCache.StreamType.BOTH) {
                            return 1;
                        } else {
                            return 0;
                        }
                    } else if (resO1 == _targetRes && resO2 != _targetRes) {
                        return -1;
                    } else if (resO1 != _targetRes && resO2 == _targetRes) {
                        return 1;
                    } else if (resO1 > _targetRes && resO2 < _targetRes) {
                        // Первое разрешение больше, второе меньше, чем целевое - первое идёт раньше
                        return -1;
                    } else if (resO1 < _targetRes && resO2 > _targetRes) {
                        // Одно разрешение меньше, второе больше, чем целевое - второе идёт раньше
                        return 1;
                    } else if (resO1 > _targetRes && resO2 > _targetRes) {
                        // Оба разрашения больше, чем целевое. Раньше идет то, что меньше
                        return (resO1 - resO2);
                    } else if (resO1 < _targetRes && resO2 < _targetRes) {
                        // Оба разрашения меньше, чем целевое. Раньше идет то, что больше
                        return -(resO1 - resO2);
                    } else {
                        // сюда не попадем
                        return 0;
                    }
                }
            });
        } else {
            // preferRes == ConfigOptions.VideoStreamSelectPreferRes.LOWER_RES
            // Движемся по качеству от лучшего к худшим до тех пор, пока не найдем ролик с нужным
            // разрешением или с разрешением меньше, чем указано в настройках.
            // Если не нашли в списке разрешение меньшее или равное целевому, берем минимальное
            // из доступных.
            Collections.sort(sortedStreams, new Comparator<StreamInfo>() {
                @Override
                public int compare(StreamInfo o1, StreamInfo o2) {
                    final int resO1 = resolutionToInt(o1.getResolution());
                    final int resO2 = resolutionToInt(o2.getResolution());
                    if (resO1 == resO2) {
                        // Оба разрашения равны. Раньше идет тот, кто оффлайн
                        // Если оба оффлайн, то раньше идет поток с совмещением видео+аудио
                        if (!o1.isOnline() && o2.isOnline()) {
                            return -1;
                        } else if (o1.isOnline() && !o2.isOnline()) {
                            return 1;
                        } else if (o1.getStreamType() == StreamCache.StreamType.BOTH &&
                                o2.getStreamType() != StreamCache.StreamType.BOTH) {
                            return -1;
                        } else if (o1.getStreamType() != StreamCache.StreamType.BOTH &&
                                o2.getStreamType() == StreamCache.StreamType.BOTH) {
                            return 1;
                        } else {
                            return 0;
                        }
                    } else if (resO1 == _targetRes && resO2 != _targetRes) {
                        return -1;
                    } else if (resO1 != _targetRes && resO2 == _targetRes) {
                        return 1;
                    } else if (resO1 > _targetRes && resO2 < _targetRes) {
                        // Первое разрешение больше, второе меньше, чем целевое - второе идёт раньше
                        return 1;
                    } else if (resO1 < _targetRes && resO2 > _targetRes) {
                        // Одно разрешение меньше, второе больше, чем целевое - первое идёт раньше
                        return -1;
                    } else if (resO1 > _targetRes && resO2 > _targetRes) {
                        // Оба разрашения больше, чем целевое. Раньше идет то, что меньше
                        return (resO1 - resO2);
                    } else if (resO1 < _targetRes && resO2 < _targetRes) {
                        // Оба разрашения меньше, чем целевое. Раньше идет то, что больше
                        return -(resO1 - resO2);
                    } else {
                        // сюда не попадем
                        return 0;
                    }
                }
            });
        }
        return sortedStreams;
    }

    public static StreamInfo getNextVideoPlaybackStreamMaxRes(
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {

        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка
        final List<StreamInfo> sortedStreams = sortVideoStreamsForMaxRes(videoStreams);

        StreamInfo _videoStream;
        if (currVideoStream == null) {
            // выбирать стрим с наилучшим качеством (в начале)
            _videoStream = sortedStreams.size() > 0 ? sortedStreams.get(0) : null;
        } else {
            // найти в списке текущий поток, вернуть следующий по списку (хуже по качеству)
            // или null, если это был последний
            int currInd = sortedStreams.indexOf(currVideoStream);
            if (currInd != -1 && currInd < sortedStreams.size() - 1) {
                _videoStream = sortedStreams.get(currInd + 1);
            } else {
                _videoStream = null;
            }
        }
        return _videoStream;
    }

    public static StreamInfo getNextVideoPlaybackStreamMinRes(
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {

        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка
        final List<StreamInfo> sortedStreams = sortVideoStreamsForMinRes(videoStreams);

        StreamInfo _videoStream;
        if (currVideoStream == null) {
            // выбирать стрим с наихудшим качеством (в начале)
            _videoStream = sortedStreams.size() > 0 ? sortedStreams.get(0) : null;
        } else {
            // найти в списке текущий поток, вернуть следующий по списку (лучше по качеству)
            // или null, если это был последний
            int currInd = sortedStreams.indexOf(currVideoStream);
            if (currInd != -1 && currInd < sortedStreams.size() - 1) {
                _videoStream = sortedStreams.get(currInd + 1);
            } else {
                _videoStream = null;
            }
        }
        return _videoStream;
    }

    public static StreamInfo getNextVideoPlaybackStreamForRes(
            final String targetRes,
            final ConfigOptions.VideoStreamSelectPreferRes preferRes,
            final List<StreamInfo> videoStreams,
            final StreamInfo currVideoStream) {

        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка
        final List<StreamInfo> sortedStreams = sortVideoStreamsForRes(targetRes, preferRes, videoStreams);

        StreamInfo _videoStream;
        if (currVideoStream == null) {
            _videoStream = sortedStreams.size() > 0 ? sortedStreams.get(0) : null;
        } else {
            // найти в списке текущий поток, вернуть следующий по списку
            // или null, если это был последний
            int currInd = sortedStreams.indexOf(currVideoStream);
            if (currInd != -1 && currInd < sortedStreams.size() - 1) {
                _videoStream = sortedStreams.get(currInd + 1);
            } else {
                _videoStream = null;
            }
        }
        return _videoStream;
    }

    public static StreamInfo getOfflineVideoPlaybackStreamMaxRes(final List<StreamInfo> videoStreams) {

        StreamInfo _videoStream = null;
        for (final StreamInfo _stream : videoStreams) {
            if (!_stream.isOnline()) {
                if (_videoStream == null) {
                    // нашли хоть что-то оффлайн
                    _videoStream = _stream;
                } else if (resolutionToInt(_stream.getResolution()) > resolutionToInt(_videoStream.getResolution())) {
                    // нашли еще один оффлайн-поток и его разрешение больше, чем у предыдущего
                    _videoStream = _stream;
                } else if (resolutionToInt(_stream.getResolution()) == resolutionToInt(_videoStream.getResolution()) &&
                        _stream.getStreamType() == StreamCache.StreamType.BOTH &&
                        _videoStream.getStreamType() != StreamCache.StreamType.BOTH) {
                    // нашли еще один оффлайн-поток с таким же разрешением, как у предыдущего найденного,
                    // но у него совмещены дорожки аудио и видео, а у предыдущго не были совмещены
                    // (в принципе, если совмещены у обоих, то без раницы, какой выбрать)
                    _videoStream = _stream;
                }
            }
        }
        return _videoStream;
    }

    public static List<StreamInfo> sortAudioStreams(final List<StreamInfo> audioStreams) {

        // сортируем потоки аудио по правилу:
        // - в начале потоки оффлайн, потом онлайн
        // - внутри групп оффлайн-онлайн потоки отсортированы по качеству (битрейт, в StreamInfo - resolution)

        // сортируем так, что наиболее предпочтительный элемент находится в начале списка,
        // т.е. более предпочительные элементы имеют меньший индекс по сравнению менее предпочительными
        // Например, когда срваниванием o1 и o2:
        // "-1" (или любое отрицательное число) - значит, что o1 появится в списке раньше, чем o2 (o1 более предпочтителен)
        // "1" (или любое положительное число) - значит, что o1 появится в списке позже, чем o2 (o1 менее предпочтителен)
        // "0" - значит элементы с точки зрения сортировщика равны
        final List<StreamInfo> sortedStreams = new ArrayList<>(audioStreams);
        Collections.sort(sortedStreams, new Comparator<StreamInfo>() {
            @Override
            public int compare(StreamInfo o1, StreamInfo o2) {
                if (!o1.isOnline() && o2.isOnline()) {
                    return -1;
                } else if (o1.isOnline() && !o2.isOnline()) {
                    return 1;
                } else {
                    return -(resolutionToInt(o1.getResolution()) - resolutionToInt(o2.getResolution()));
                }
            }
        });
        return sortedStreams;
    }

    public static StreamInfo getAudioPlaybackStream(
            final List<StreamInfo> audioStreams,
            final StreamInfo currVideoStream) {
        // будем полагаться на сортировку
        // сортируем так, что наиболее предпочтительный элемент находится в начале списка
        final List<StreamInfo> sortedStreams = sortAudioStreams(audioStreams);

        // если поток видео совмещен с аудио, возвращаем null,
        // если не совмещен, то просто берем первый элемент в списке аудио
        // (список предварительно сортируем как надо)
        return (currVideoStream == null || currVideoStream.getStreamType() == StreamCache.StreamType.VIDEO) &&
                sortedStreams.size() > 0 ? sortedStreams.get(0) : null;
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
    public static StreamPair getNextPlaybackStreamPair(
            final Context context,
            final List<StreamInfo> videoStreams, final List<StreamInfo> audioStreams,
            final StreamInfo currVideoStream) {
        StreamInfo _videoStream = null;
        if (currVideoStream == null && ConfigOptions.getVideoStreamSelectOffline(context)) {
            // выбираем оффлайн-поток видео с наилучшим качеством, если есть
            // (работает только для роликов, для которых еще не выбирали поток, т.е. это выбор
            // потока по умолчанию)
            _videoStream = getOfflineVideoPlaybackStreamMaxRes(videoStreams);
        }

        // настройка "играть оффлайн" не выбрана или нет оффлайн-потоков или для ролика уже выбирали поток
        if (_videoStream == null) {
            switch (ConfigOptions.getVideoStreamSelectStrategy(context)) {
                case MAX_RES:
                    _videoStream = getNextVideoPlaybackStreamMaxRes(videoStreams, currVideoStream);
                    break;
                case MIN_RES:
                    _videoStream = getNextVideoPlaybackStreamMinRes(videoStreams, currVideoStream);
                    break;
                case CUSTOM_RES:
                    _videoStream = getNextVideoPlaybackStreamForRes(
                            ConfigOptions.getVideoStreamCustomRes(context),
                            ConfigOptions.getVideoStreamSelectCustomPreferRes(context),
                            videoStreams, currVideoStream);
                    break;
                case LAST_CHOSEN:
                    _videoStream = getNextVideoPlaybackStreamForRes(
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
        }

        final StreamInfo _audioStream = getAudioPlaybackStream(audioStreams, _videoStream);
        return new StreamPair(_videoStream, _audioStream);
    }

    public static StreamInfo findStreamByParams(
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
}
