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
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class PlaylistInfoDao {

    @Insert
    public abstract long insert(final PlaylistInfo playlist);

    @Delete
    public abstract void delete(final PlaylistInfo playlist);

    @Query("SELECT * FROM playlist_info WHERE _id = :id LIMIT 1")
    public abstract PlaylistInfo getById(final long id);

    @Query("SELECT _id FROM playlist_info")
    public abstract List<Long> getAllIds();

    @Query("SELECT _id FROM playlist_info WHERE enabled")
    public abstract List<Long> getEnabledIds();

    @Query("SELECT * FROM playlist_info WHERE url LIKE :url LIMIT 1")
    public abstract PlaylistInfo findByUrl(final String url);

    @Query("SELECT * FROM playlist_info")
    public abstract List<PlaylistInfo> getAll();

    @Query("SELECT * FROM playlist_info WHERE enabled")
    public abstract List<PlaylistInfo> getEnabled();

    @Query("SELECT * FROM playlist_info ORDER BY _id ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllAscDs();

    @Query("SELECT * FROM playlist_info ORDER BY _id DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllDescDs();

    @Query("SELECT * FROM playlist_info ORDER BY name ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllSortByNameAscDs();

    @Query("SELECT * FROM playlist_info ORDER BY name DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllSortByNameDescDs();

    @Query("SELECT * FROM playlist_info ORDER BY url ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllSortByUrlAscDs();

    @Query("SELECT * FROM playlist_info ORDER BY url DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> getAllSortByUrlDescDs();

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY _id ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY _id DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllDescDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY name ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllSortByNameAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY name DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllSortByNameDescDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY url ASC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllSortByUrlAscDs(String sstr);

    @Query("SELECT * FROM playlist_info WHERE name LIKE '%'||:sstr||'%' OR url LIKE '%'||:sstr||'%' ORDER BY url DESC")
    public abstract DataSource.Factory<Integer, PlaylistInfo> searchAllSortByUrlDescDs(String sstr);

    @Query("SELECT enabled FROM playlist_info WHERE _id = :id LIMIT 1")
    public abstract boolean isEnabled(final long id);

    //
    @Query("UPDATE video_item SET enabled = :enabled WHERE playlist_id = :playlistId")
    protected abstract void setVideoItemsEnabled4Playlist(final long playlistId, final boolean enabled);

    @Query("UPDATE video_item SET enabled = :enabled")
    protected abstract void setVideoItemsEnabled4All(boolean enabled);

    @Query("UPDATE video_item SET enabled = :enabled WHERE playlist_id IN " +
            "(SELECT _id FROM playlist_info WHERE  type = 'YT_USER' OR type = 'YT_CHANNEL' OR type = 'YT_PLAYLIST')")
    protected abstract void setVideoItemsEnabled4Yt(final boolean enabled);
    ///

    @Query("UPDATE playlist_info SET enabled = :enabled WHERE _id = :id")
    protected abstract void setPlaylistEnabled(final long id, final boolean enabled);

    @Query("UPDATE playlist_info SET enabled = :enabled")
    protected abstract void setPlaylistsEnabled4All(final boolean enabled);

    @Query("UPDATE playlist_info SET enabled = :enabled WHERE type = 'YT_USER' OR type = 'YT_CHANNEL' OR type = 'YT_PLAYLIST'")
    protected abstract void setPlaylistsEnabled4Yt(final boolean enabled);

    @Transaction
    public void setEnabled(final long id, final boolean enabled) {
        setVideoItemsEnabled4Playlist(id, enabled);
        setPlaylistEnabled(id, enabled);
    }

    @Transaction
    public void setEnabled4All(final boolean enabled) {
        setVideoItemsEnabled4All(enabled);
        setPlaylistsEnabled4All(enabled);
    }

    @Transaction
    public void setEnabled4Yt(final boolean enabled) {
        setVideoItemsEnabled4Yt(enabled);
        setPlaylistsEnabled4Yt(enabled);
    }

    @Transaction
    public void enableOnlyPlaylists(final List<Long> plIds) {
        setEnabled4All(false);
        for(final Long plId : plIds) {
            setEnabled(plId, true);
        }
    }

    @Transaction
    public void enableAlsoPlaylists(final List<Long> plIds) {
        for(final Long plId : plIds) {
            setEnabled(plId, true);
        }
    }
}
