package su.sadrobot.yashlang.controller;

import android.content.Context;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

/**
 * Управление кэшем потоков - работа с базой данных.
 */
public class StreamCacheManager {
    private static StreamCacheManager _instance;
    static {
        _instance = new StreamCacheManager();
    }
    public static StreamCacheManager getInstance() {
        return _instance;
    }

    private StreamCacheManager() {
    }

    // много фоновых потоков здесь, пожалуй, незачем
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public void queueForDownload(final Context context, final VideoItem videoItem,
                                 final StreamHelper.StreamInfo videoStream,
                                 final StreamHelper.StreamInfo audioStream) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
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
        });
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
}
