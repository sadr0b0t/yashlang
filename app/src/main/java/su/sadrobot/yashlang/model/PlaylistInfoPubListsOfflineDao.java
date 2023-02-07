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

@Dao
public abstract class PlaylistInfoPubListsOfflineDao extends PlaylistInfoPubListsDao {

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY _id ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledAscDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY _id DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledDescDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY name ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledSortByNameAscDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY name DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledSortByNameDescDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY url ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledSortByUrlAscDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY url DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getEnabledSortByUrlDescDs();

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY _id ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY _id DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledDescDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY name ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledSortByNameAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY name DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledSortByNameDescDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY url ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledSortByUrlAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE enabled AND (name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%') AND (_id IN (SELECT playlist_id from video_item WHERE enabled AND NOT blacklisted AND has_offline)) ORDER BY url DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchEnabledSortByUrlDescDs(String sstr);
}
