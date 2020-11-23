package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * PlaylistInfoDao.java is part of YaShlang.
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

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class PlaylistInfoDao {
    @Query("SELECT * FROM playlist_info")
    public abstract List<PlaylistInfo> getAll();

    @Query("SELECT _id FROM playlist_info")
    public abstract List<Long> getAllIds();

    @Query("SELECT * FROM playlist_info WHERE enabled")
    public abstract List<PlaylistInfo> getEnabled();

    @Query("SELECT * FROM playlist_info WHERE _id = :id LIMIT 1")
    public abstract PlaylistInfo getById(long id);

    @Query("SELECT * FROM playlist_info WHERE url LIKE :url LIMIT 1")
    public abstract PlaylistInfo findByUrl(String url);

    @Insert
    public abstract long insert(PlaylistInfo playlist);

    @Delete
    public abstract void delete(PlaylistInfo playlist);

    @Query("SELECT enabled FROM playlist_info WHERE _id = :id LIMIT 1")
    public abstract boolean isEnabled(long id);

    //
    @Query("UPDATE video_item SET enabled = :enabled WHERE playlist_id = :playlistId")
    protected abstract void setVideoItemsEnabled4Playlist(long playlistId, boolean enabled);

    @Query("UPDATE video_item SET enabled = :enabled")
    protected abstract void setVideoItemsEnabled4All(boolean enabled);

    @Query("UPDATE video_item SET enabled = :enabled WHERE playlist_id IN " +
            "(SELECT _id FROM playlist_info WHERE  type = 'YT_USER' OR type = 'YT_CHANNEL' OR type = 'YT_PLAYLIST')")
    protected abstract void setVideoItemsEnabled4Yt(boolean enabled);
    ///

    @Query("UPDATE playlist_info SET enabled = :enabled WHERE _id = :id")
    protected abstract void setPlaylistEnabled(long id, boolean enabled);

    @Query("UPDATE playlist_info SET enabled = :enabled")
    protected abstract void setPlaylistsEnabled4All(boolean enabled);

    @Query("UPDATE playlist_info SET enabled = :enabled WHERE type = 'YT_USER' OR type = 'YT_CHANNEL' OR type = 'YT_PLAYLIST'")
    protected abstract void setPlaylistsEnabled4Yt(boolean enabled);

    @Transaction
    public void setEnabled(long id, boolean enabled) {
        setVideoItemsEnabled4Playlist(id, enabled);
        setPlaylistEnabled(id, enabled);
    }

    @Transaction
    public void setEnabled4All(boolean enabled) {
        setVideoItemsEnabled4All(enabled);
        setPlaylistsEnabled4All(enabled);
    }

    @Transaction
    public void setEnabled4Yt(boolean enabled) {
        setVideoItemsEnabled4Yt(enabled);
        setPlaylistsEnabled4Yt(enabled);
    }
}
