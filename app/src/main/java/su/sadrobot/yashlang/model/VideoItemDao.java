package su.sadrobot.yashlang.model;

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

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;


@Dao
public interface VideoItemDao {

    @Insert
    long insert(VideoItem video);

    @Insert
    long[] insertAll(VideoItem... vids);

    @Delete
    void delete(VideoItem vid);

    @Query("SELECT * FROM video_item WHERE _id = :id LIMIT 1")
    VideoItem getById(long id);

    @Query("SELECT * FROM video_item WHERE playlist_id = :plId AND item_url = :itemUrl LIMIT 1")
    VideoItem getByItemUrl(long plId, String itemUrl);

    @Query("SELECT * FROM video_item WHERE playlist_id = :playlistId ORDER BY fake_timestamp DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistAllDs(long playlistId);

    @Query("SELECT * FROM video_item WHERE playlist_id = :playlistId ORDER BY fake_timestamp DESC")
    List<VideoItem> getByPlaylistAll(long playlistId);

    @Query("SELECT * FROM video_item WHERE playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY fake_timestamp DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistAllDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE blacklisted ORDER BY name")
    List<VideoItem> getBlacklist();

    @Query("SELECT * FROM video_item WHERE blacklisted ORDER BY name")
    DataSource.Factory<Integer, VideoItem> getBlacklistDs();

    @Query("SELECT COUNT (_id) FROM video_item WHERE playlist_id = :playlistId")
    int countAllVideos(long playlistId);

    @Query("UPDATE video_item SET blacklisted = :blacklisted WHERE _id = :id")
    void setBlacklisted(long id, boolean blacklisted);

    @Query("UPDATE video_item SET paused_at = :pausedAt WHERE _id = :id")
    void setPausedAt(long id, long pausedAt);

    // если starred=false, starred_date все равно обновится, но и пофиг
    @Query("UPDATE video_item SET starred = :starred, starred_date = datetime('now')  WHERE _id = :id")
    void setStarred(long id, boolean starred);

    @Query("SELECT MAX (fake_timestamp) FROM video_item WHERE playlist_id = :playlistId")
    long getMaxFakeTimestamp(long playlistId);

    /**
     * Посчитать просмотр: увеличить счетчик просмотров view_count, выставить
     * время последнего просмотра last_viewed_at на текущее.
     */
    @Query("UPDATE video_item SET view_count = view_count + 1, last_viewed_date = datetime('now') WHERE _id = :id")
    void countView(long id);
}
