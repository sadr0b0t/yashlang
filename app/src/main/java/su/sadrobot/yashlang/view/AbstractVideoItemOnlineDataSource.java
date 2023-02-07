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
import androidx.paging.ItemKeyedDataSource;

import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import su.sadrobot.yashlang.model.VideoItem;

/**
 * Uses NewPipeExtractor https://github.com/TeamNewPipe/NewPipeExtractor library
 * Example code:
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
 * https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java
 */
public abstract class AbstractVideoItemOnlineDataSource extends ItemKeyedDataSource<String, VideoItem> {
    protected Context context;
    protected boolean loadThumbs;
    protected DataSourceListener dataSourceListener;

    protected ListExtractor<StreamInfoItem> extractor;
    protected ListExtractor.InfoItemsPage<StreamInfoItem> loadedPage;

    public AbstractVideoItemOnlineDataSource(final Context context, final boolean loadThumbs,
                                             final DataSourceListener dataSourceListener) {
        this.context = context;
        this.loadThumbs = loadThumbs;
        this.dataSourceListener = dataSourceListener;
    }

    @NonNull
    @Override
    public String getKey(@NonNull VideoItem item) {
        return item.getItemUrl();
    }
}
