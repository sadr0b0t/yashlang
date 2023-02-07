package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
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

import androidx.paging.DataSource;

import su.sadrobot.yashlang.model.VideoItem;

public class VideoItemOnlyNewOnlineDataSourceFactory extends DataSource.Factory<String, VideoItem> {
    private Context context;
    private long playlistId;
    private boolean loadThumbs;
    private DataSourceListener dataSourceListener;

    public VideoItemOnlyNewOnlineDataSourceFactory(final Context context,
                                                   final long playlistId, final boolean loadThumbs,
                                                   final DataSourceListener dataSourceListener) {
        this.context = context;
        this.playlistId = playlistId;
        this.loadThumbs = loadThumbs;
        this.dataSourceListener = dataSourceListener;
    }

    @Override
    public DataSource<String, VideoItem> create() {
        return new VideoItemOnlyNewOnlineDataSource(context, playlistId, loadThumbs, dataSourceListener);
    }
}
