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

import java.util.List;

@Dao
public interface PlaylistInfoDao {
    @Query("SELECT * FROM playlist_info")
    List<PlaylistInfo> getAll();

    @Query("SELECT * FROM playlist_info WHERE _id = :id LIMIT 1")
    PlaylistInfo getById(long id);

    @Query("SELECT enabled FROM playlist_info WHERE _id = :id LIMIT 1")
    boolean isEnabled(long id);

    @Query("UPDATE playlist_info SET enabled = :enabled WHERE _id = :id")
    void setEnabled(long id, boolean enabled);

    @Query("SELECT * FROM playlist_info WHERE url LIKE :url LIMIT 1")
    PlaylistInfo findByUrl(String url);

    @Insert
    long insert(PlaylistInfo playlist);

    @Delete
    void delete(PlaylistInfo playlist);
}
