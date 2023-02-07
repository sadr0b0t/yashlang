package su.sadrobot.yashlang.model;

/*
 * Copyright (C) Anton Moiseev 2023 <github.com/sadr0b0t>
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
import androidx.room.Query;

import java.util.List;

/**
 * Cписки роликов в публичной части приложения. Главное условие для всех запросов -
 * фильтровать ролики по флагам: enabled AND NOT blacklisted
 */
@Dao
public interface VideoItemPubListsDao {

    // https://developer.android.com/topic/libraries/architecture/paging/
    // Здесь и ниже логично использовать Factory<Long, VideoItem>
    // вместо Factory<Integer, VideoItem>, но мы не можем так делать,
    // т.к с Long генератор генерит некомпилируемый код.
    // Впрочем, с Factory<Integer, VideoItem> при типе ID Long
    // код тоже компилируется и работает, поэтому пока фиг с ним и так.
    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted ORDER BY name")
    DataSource.Factory<Integer, VideoItem> getAllDs();

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId ORDER BY fake_timestamp DESC")
    List<VideoItem> getByPlaylist(long playlistId);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId ORDER BY fake_timestamp DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistDs(long playlistId);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY fake_timestamp DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY name ASC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByNameAscDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY name DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByNameDescDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY fake_timestamp ASC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByTimeAddedAscDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY fake_timestamp DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByTimeAddedDescDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY duration ASC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByDurationAscDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY duration DESC")
    DataSource.Factory<Integer, VideoItem> getByPlaylistSortByDurationDescDs(long playlistId, String filterStr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId ORDER BY RANDOM() LIMIT :lim")
    List<VideoItem> getByPlaylistShuffle(long playlistId, int lim);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId AND name LIKE '%'||:filterStr||'%' ORDER BY RANDOM() LIMIT :lim")
    List<VideoItem> getByPlaylistShuffle(long playlistId, String filterStr, int lim);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND name LIKE '%'||:sstr||'%' ORDER BY name")
    DataSource.Factory<Integer, VideoItem> searchVideosDs(String sstr);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND name LIKE '%'||:sstr||'%' ORDER BY RANDOM() LIMIT :lim")
    List<VideoItem> searchVideosShuffle(String sstr, int lim);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND view_count > 0 ORDER BY last_viewed_date DESC")
    DataSource.Factory<Integer, VideoItem> getHistoryOrderByLastViewedDs();

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND starred ORDER BY starred_date DESC")
    List<VideoItem> getStarred();

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND starred ORDER BY starred_date DESC")
    DataSource.Factory<Integer, VideoItem> getStarredDs();

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted AND starred ORDER BY RANDOM() LIMIT :lim")
    List<VideoItem> getStarredShuffle(int lim);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted ORDER BY RANDOM() LIMIT :lim")
    List<VideoItem> recommendVideos(int lim);

    @Query("SELECT * FROM video_item WHERE enabled AND NOT blacklisted ORDER BY RANDOM()")
    DataSource.Factory<Integer, VideoItem> recommendVideosDs();

    @Query("SELECT COUNT (_id) FROM video_item WHERE enabled AND NOT blacklisted AND playlist_id = :playlistId")
    int countVideos(long playlistId);
}
