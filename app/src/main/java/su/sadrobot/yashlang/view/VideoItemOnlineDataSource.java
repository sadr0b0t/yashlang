package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItemOnlineDataSource.java is part of YaShlang.
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
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.List;

import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.VideoThumbManager;
import su.sadrobot.yashlang.model.VideoItem;

/**
 * Uses NewPipeExtractor https://github.com/TeamNewPipe/NewPipeExtractor library
 * Example code:
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java
 */
public class VideoItemOnlineDataSource extends AbstractVideoItemOnlineDataSource {
    protected String playlistUrl;

    protected ListExtractor<StreamInfoItem> extractor;
    protected ListExtractor.InfoItemsPage<StreamInfoItem> loadedPage;

    public VideoItemOnlineDataSource(final Context context, final String playlistUrl, final boolean loadThumbs,
                                     final DataSourceListener dataSourceListener) {
        super(context, loadThumbs, dataSourceListener);
        this.playlistUrl = playlistUrl;
    }

    @NonNull
    @Override
    public String getKey(@NonNull VideoItem item) {
        return item.getItemUrl();
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<String> params, @NonNull LoadInitialCallback<VideoItem> callback) {

        try {
            // Выкачать список видео с 1-й страницы канала
            NewPipe.init(DownloaderTestImpl.getInstance());

            extractor = ContentLoader.getInstance().getListExtractor(playlistUrl);

            // загрузить первую страницу
            extractor.fetchPage();
            loadedPage = extractor.getInitialPage();

            // загрузили, можно обновлять список
            final List<VideoItem> videoItems = ContentLoader.getInstance().extractVideoItems(loadedPage.getItems());
            if(loadThumbs) {
                VideoThumbManager.getInstance().loadThumbs(context, videoItems);
            }
            callback.onResult(videoItems);
        } catch (ExtractionException | IOException e) {
            if(dataSourceListener != null) {
                dataSourceListener.onLoadInitialError(e);
            }
        }
    }

    @Override
    public void loadAfter(@NonNull LoadParams<String> params, @NonNull LoadCallback<VideoItem> callback) {
        if(loadedPage.hasNextPage() ) {
            boolean done = false;
            // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
            // ошибку вместо страницы
            int retryCount = 3;
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
                // загрузили страницу, можно обновлять список
                final List<VideoItem> videoItems = ContentLoader.getInstance().extractVideoItems(loadedPage.getItems());
                if(loadThumbs) {
                    VideoThumbManager.getInstance().loadThumbs(context, videoItems);
                }
                callback.onResult(videoItems);

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
