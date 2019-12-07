package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoDatabase.java is part of YaShlang.
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

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


@Database(entities = {VideoItem.class, PlaylistInfo.class}, version = 1)
public abstract class VideoDatabase extends RoomDatabase {
    public abstract VideoItemDao videoItemDao();
    public abstract PlaylistInfoDao playlistInfoDao();

    public static VideoDatabase getDb(Context context) {
        return Room.databaseBuilder(context,
                VideoDatabase.class, "video-db")
                .fallbackToDestructiveMigration()
                .build();
    }
}
