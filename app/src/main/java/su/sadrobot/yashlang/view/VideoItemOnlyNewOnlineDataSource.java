package su.sadrobot.yashlang.view;

/*
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

import androidx.annotation.NonNull;

import org.schabi.newpipe.DownloaderTestImpl;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;

/**
 * Uses NewPipeExtractor https://github.com/TeamNewPipe/NewPipeExtractor library
 * Example code:
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java
 */
public class VideoItemOnlyNewOnlineDataSource extends AbstractVideoItemOnlineDataSource {
    // Загрузить новые видео в плейлисте - видео, добавленные после того,
    // как плейлист был сохранен локально или последний раз обновлялся
    // Алгоритм такой:
    // 1) Мы считаем, что самые новые видео появляются первыми начиная с первой страницы
    // 2) Загружаем все видео страница за страницей до тех пор, пока не встретится
    // видео, которое уже было добавлено в локальную базу для этого плейлиста
    // 3) У видео есть дата, но ее использовать сложнее и накладнее, чем предложенный
    // алгоритм: во-первых, при загрузке информации о видео со страницы плейлиста
    // вместо даты загрузки приходит бесполезная поебень вида "1 год назад", "4 месяца назад" и т.п.;
    // на странице видео есть дата получше (например: "12 авг. 2008 г."),
    // но ее парсить тоже не очень удобно с учетом локализации и сокращений.

    private long playlistId;

    // дошли до старых видео
    private boolean foundOld = false;

    public VideoItemOnlyNewOnlineDataSource(final Context context,
                                            final long playlistId, final boolean loadThumbs,
                                            final DataSourceListener dataSourceListener) {
        super(context, loadThumbs, dataSourceListener);

        this.playlistId = playlistId;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<String> params, @NonNull LoadInitialCallback<VideoItem> callback) {

        try {
            final String playlistUrl = VideoDatabase.getDbInstance(context).playlistInfoDao().getById(playlistId).getUrl();

            // Выкачать список видео с 1-й страницы канала
            NewPipe.init(DownloaderTestImpl.getInstance());

            extractor = ContentLoader.getInstance().getListExtractor(playlistUrl);

            // загрузить первую страницу
            extractor.fetchPage();
            loadedPage = extractor.getInitialPage();

            // загрузили страницу, проверим, есть ли на ней новые элементы
            final List<StreamInfoItem> pageNewItems = new ArrayList<StreamInfoItem>();

            for (final StreamInfoItem item : loadedPage.getItems()) {
                if (VideoDatabase.getDbInstance(context).videoItemDao().getByItemUrl(playlistId, item.getUrl()) == null) {
                    pageNewItems.add(item);
                } else {
                    foundOld = true;
                    break;
                }
            }

            // можно обновлять список
            if(pageNewItems.size() > 0) {
                final List<VideoItem> videoItems = ContentLoader.getInstance().extractVideoItems(pageNewItems, playlistId);
                if (loadThumbs) {
                    ThumbManager.getInstance().loadVideoThumbs(context, videoItems);
                }
                callback.onResult(videoItems);
            }
        } catch (final ExtractionException | IOException e) {
            if(dataSourceListener != null) {
                dataSourceListener.onLoadInitialError(e);
            }
        } catch (final Exception e) {
            // было дело - словили непредвиденный NullPointer
            if(dataSourceListener != null) {
                dataSourceListener.onLoadInitialError(e);
            }
        }
    }

    @Override
    public void loadAfter(@NonNull LoadParams<String> params, @NonNull LoadCallback<VideoItem> callback) {
        if(!foundOld && loadedPage.hasNextPage() ) {
            boolean done = false;
            // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
            // ошибку вместо страницы
            int retryCount = ConfigOptions.LOAD_PAGE_RETRY_COUNT;
            Exception retryEx = null;
            while (!done && retryCount > 0) {
                try {
                    loadedPage = extractor.getPage(loadedPage.getNextPage());
                    done = true;
                } catch (Exception e) {
                    retryEx = e;
                    retryCount--;
                }
            }

            if (done) {
                // загрузили страницу, проверим, есть ли на ней новые элементы
                final List<StreamInfoItem> pageNewItems = new ArrayList<StreamInfoItem>();

                for (final StreamInfoItem item : loadedPage.getItems()) {
                    if (VideoDatabase.getDbInstance(context).videoItemDao().getByItemUrl(playlistId, item.getUrl()) == null) {
                        pageNewItems.add(item);
                    } else {
                        foundOld = true;
                        break;
                    }
                }

                if(pageNewItems.size() > 0) {
                    final List<VideoItem> videoItems = ContentLoader.getInstance().extractVideoItems(pageNewItems, playlistId);
                    if (loadThumbs) {
                        ThumbManager.getInstance().loadVideoThumbs(context, videoItems);
                    }
                    callback.onResult(videoItems);
                }
            } else {
                // страница так и не загрузилась
                //throw new RuntimeException("Error loading page, retry count exceeded");
                if(dataSourceListener != null) {
                    dataSourceListener.onLoadAfterError(new IOException("Error loading page, retry count exceeded", retryEx));
                }
            }
        }
    }

    @Override
    public void loadBefore(@NonNull LoadParams<String> params, @NonNull LoadCallback<VideoItem> callback) {

    }
}
