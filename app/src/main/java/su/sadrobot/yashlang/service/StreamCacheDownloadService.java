package su.sadrobot.yashlang.service;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistActivity.java is part of YaShlang.
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.controller.StreamCacheFsManager;
import su.sadrobot.yashlang.controller.StreamHelper;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoStreamDownloader;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

/**
 * Здесь активные на текущий момент закачки.
 *
 * В базе данных хранится флаг скачано/не скачано. Если в базе стоит флаг "не зкачано", то
 * такая закачка на текущий момент может скачиваться, а может не скачиваться.
 *
 * Для сиюминутного статуса АКТИВНА/НА ПАУЗЕ нужно обращаться к сервису, который этим занимается,
 * т.е. сюда.
 */
public class StreamCacheDownloadService {

    private static StreamCacheDownloadService _instance;
    static {
        _instance = new StreamCacheDownloadService();
    }
    public static StreamCacheDownloadService getInstance() {
        return _instance;
    }

    private StreamCacheDownloadService() {
    }

    public interface StreamCacheDownloadServiceListener {
        void onCacheProgressListChange(List<CacheProgressItem> cacheProgressList);
    }

    public static class CacheProgressItem {
        // https://stackoverflow.com/questions/24328679/does-java-se-8-have-pairs-or-tuples
        // Нужно передавать два объекта в качестве элемента. Стандартного класса нет, придется
        // городить свои костыли. Как вариант - использовать Map.Entry, но нет
        public final StreamCache streamCacheItem;
        public final TaskController downloadTaskController;

        public CacheProgressItem(final StreamCache streamCacheItem, final TaskController downloadTaskController) {
            this.streamCacheItem = streamCacheItem;
            this.downloadTaskController = downloadTaskController;
        }
    }

    private final List<CacheProgressItem> cacheProgressList = new ArrayList<>();

    private StreamCacheDownloadServiceListener serviceListener;

    private final ExecutorService dbExecutor =
            new ThreadPoolExecutor(1, 5, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>() {
                        @Override
                        public boolean offer(Runnable o) {
                            super.clear();
                            return super.offer(o);
                        }
                    });

    private final ExecutorService downloadExecutor =
            new ThreadPoolExecutor(1, 5, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>() {
                        @Override
                        public boolean offer(Runnable o) {
                            super.clear();
                            return super.offer(o);
                        }
                    });

    public void setServiceListener(final StreamCacheDownloadServiceListener serviceListener) {
        this.serviceListener = serviceListener;
    }

    public List<CacheProgressItem> initCacheProgressList(final Context context) {
        cacheProgressList.clear();
        final List<StreamCache> notFinished = VideoDatabase.getDbInstance(context).streamCacheDao().getNotFinished(15);
        for(final StreamCache streamCache : notFinished) {
            final TaskController taskController = new TaskController();
            cacheProgressList.add(new StreamCacheDownloadService.CacheProgressItem(streamCache, taskController));
        }
        return cacheProgressList;
    }

    public void initCacheProgressListBg(final Context context) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                initCacheProgressList(context);
                if(serviceListener != null) {
                    serviceListener.onCacheProgressListChange(cacheProgressList);
                }
            }
        });
    }

    public List<CacheProgressItem> getCacheProgressList() {
        return cacheProgressList;
    }

    public void queueForDownload(final Context context, final VideoItem videoItem,
                                        final StreamHelper.StreamInfo videoStream,
                                        final StreamHelper.StreamInfo audioStream) {
        if (videoStream != null) {
            final StreamCache streamCache = new StreamCache();
            streamCache.setVideoId(videoItem.getId());
            streamCache.setStreamType(videoStream.getStreamType());
            streamCache.setStreamRes(videoStream.getResolution());
            streamCache.setStreamFormat(videoStream.getFormatName());
            streamCache.setStreamMimeType(videoStream.getFormatMimeType());
            streamCache.setStreamFormatSuffix(videoStream.getFormatSuffix());
            streamCache.setDownloaded(false);

            final String fileName = StreamCacheFsManager.getFileNameForStream(streamCache);
            streamCache.setFileName(fileName);

            VideoDatabase.getDbInstance(context).streamCacheDao().insertStreamCache(streamCache);
        }

        if (audioStream != null) {
            final StreamCache audioStreamCache = new StreamCache();
            audioStreamCache.setVideoId(videoItem.getId());
            audioStreamCache.setStreamType(audioStream.getStreamType());
            audioStreamCache.setStreamRes(audioStream.getResolution());
            audioStreamCache.setStreamFormat(audioStream.getFormatName());
            audioStreamCache.setStreamMimeType(audioStream.getFormatMimeType());
            audioStreamCache.setStreamFormatSuffix(audioStream.getFormatSuffix());
            audioStreamCache.setDownloaded(false);

            final String fileName = StreamCacheFsManager.getFileNameForStream(audioStreamCache);
            audioStreamCache.setFileName(fileName);

            VideoDatabase.getDbInstance(context).streamCacheDao().insertStreamCache(audioStreamCache);
        }
    }

    public void pauseAll() {
        for(final StreamCacheDownloadService.CacheProgressItem cacheProgressItem : cacheProgressList) {
            pause(cacheProgressItem);
        }
    }

    public void pause(final CacheProgressItem cacheProgressItem) {
        if (cacheProgressItem.downloadTaskController.getState() == TaskController.TaskState.ENQUEUED) {
            cacheProgressItem.downloadTaskController.setState(TaskController.TaskState.WAIT);
        }
        // cancel делаем в любом случае на всякий случай
        // например: фоновый поток сначала проверил флаг ENQUEUED и решил запустить процесс закачки,
        // потом был вызыван этот участок кода, а уже потом фоновый поток переключил состояние на ACTIVE
        cacheProgressItem.downloadTaskController.cancel();
    }

    public void startAll(final Context context) {
        downloadAll(context);
    }

    public void start(final Context context, final CacheProgressItem cacheProgressItem) {
        if (cacheProgressItem.downloadTaskController.getState() == TaskController.TaskState.WAIT) {
            cacheProgressItem.downloadTaskController.setState(TaskController.TaskState.ENQUEUED);
            downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // пока ждали очедь на закачку, состояние могло поменяться - например, на WAIT,
                    // в таком случае игнорировать первоначальный запрос на закачку
                    if (cacheProgressItem.downloadTaskController.getState() == TaskController.TaskState.ENQUEUED) {
                        cacheProgressItem.downloadTaskController.setState(TaskController.TaskState.ACTIVE);
                        VideoStreamDownloader.downloadStream(context,
                                cacheProgressItem.streamCacheItem, cacheProgressItem.downloadTaskController);
                    }
                }
            });
        }
    }

    public void deleteNotFinished(final Context context) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // будем удалять по одному элементу, т.к. нужно не только очистить базу, но и удалить файлы
                // VideoDatabase.getDbInstance(context).streamCacheDao().deleteNotFinished();
                final List<StreamCache> notFinished = VideoDatabase.getDbInstance(context).streamCacheDao().getNotFinished();
                for (StreamCache streamCacheItem : notFinished) {
                    final File cachePartFile = StreamCacheFsManager.getPartFileForStream(context, streamCacheItem);
                    if (cachePartFile.exists()) {
                        cachePartFile.delete();
                    }
                    final File cacheFile = StreamCacheFsManager.getPartFileForStream(context, streamCacheItem);
                    if (cacheFile.exists()) {
                        cacheFile.delete();
                    }

                    VideoDatabase.getDbInstance(context).streamCacheDao().deleteStreamCache(streamCacheItem);
                    cacheProgressList.remove(streamCacheItem);
                }
                if (serviceListener != null) {
                    serviceListener.onCacheProgressListChange(cacheProgressList);
                }
            }
        });
    }

    public void delete(final Context context, final StreamCache streamCacheItem) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {

                final File cachePartFile = StreamCacheFsManager.getPartFileForStream(context, streamCacheItem);
                if (cachePartFile.exists()) {
                    cachePartFile.delete();
                }
                final File cacheFile = StreamCacheFsManager.getFileForStream(context, streamCacheItem);
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }

                VideoDatabase.getDbInstance(context).streamCacheDao().deleteStreamCache(streamCacheItem);
                cacheProgressList.remove(streamCacheItem);

                if (serviceListener != null) {
                    serviceListener.onCacheProgressListChange(cacheProgressList);
                }
            }
        });
    }

    public void redownload(final Context context, final StreamCache streamCacheItem) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // удалить файлы (скорее всего их уже и так нет)
                final File cachePartFile = StreamCacheFsManager.getPartFileForStream(context, streamCacheItem);
                if (cachePartFile.exists()) {
                    cachePartFile.delete();
                }
                final File cacheFile = StreamCacheFsManager.getPartFileForStream(context, streamCacheItem);
                if (cacheFile.exists()) {
                    cacheFile.delete();
                }

                // отметить в базе как не выкачанный
                VideoDatabase.getDbInstance(context).streamCacheDao().setDownloaded(
                        streamCacheItem.getId(), streamCacheItem.getVideoId(), false);
                streamCacheItem.setDownloaded(false);
            }
        });
    }

    private void downloadAll(final Context context) {
        // список роликов для загрузки
        for(final StreamCacheDownloadService.CacheProgressItem cacheProgressItem : cacheProgressList) {
            start(context, cacheProgressItem);
        }
    }
}
