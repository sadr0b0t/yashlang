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
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;


@Database(entities = {VideoItem.class, PlaylistInfo.class, Profile.class, ProfilePlaylists.class}, version = 3)
public abstract class VideoDatabase extends RoomDatabase {
    private static volatile VideoDatabase INSTANCE;


    public abstract VideoItemDao videoItemDao();
    public abstract PlaylistInfoDao playlistInfoDao();
    public abstract ProfileDao profileDao();

    public static VideoDatabase getDbInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (VideoDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            VideoDatabase.class, "video-db")
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            //.fallbackToDestructiveMigration()
                            //.allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // Миграция при изменении структуры базы:
    // https://startandroid.ru/ru/courses/architecture-components/27-course/architecture-components/540-urok-12-migracija-versij-bazy-dannyh.html
    // Посмотреть текущую структуру бызы:
    //   - файл с базой /data/data/su.sadrobot.yashlang/databases/video-db
    //   - выкачать на компьютер
    //   - из эмулятора в Android Studio: справа внизу вкладка Device File Explorer
    //   - на компьютере - открыть в любом редакторе файлов баз SQLite
    //   - например: "DB Browser for SQLite" или Sqliteman есть в репозитории Убунты
    //   - можно переименовать файл - добавить расширение ".db" video-db.db
    //       (нужно было сразу так назвать, сейчас уже поздно)
    //   - "DB Browser for SQLite" лучше, позволяет смотреть структуру базы, в т.ч.
    //       типы данных и настройки колонок (показывает команду создания базы) и выполнять запросы SQL
    private static Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // Здесь добавляем новую колонку item_url - это полная ссылка на страницу ролика,
            // колонка yt_id теперь будет deprecated

            // Хороший вариант был бы переименовать yt_id-> item_url с правкой значений
            // но RENAME COLUMN появилась в SQLite с версии 3.25, а в Андроиде API 27 SQLite 3.19
            // https://www.sqlitetutorial.net/sqlite-rename-column/
            // https://developer.android.com/reference/android/database/sqlite/package-summary
            // ALTER TABLE video_item RENAME COLUMN yt_id TO item_url
            // UPDATE video_item SET item_url='https://youtube.com/watch?v=' || item_url

            // Удалить тоже не получится
            // https://stackoverflow.com/questions/5938048/delete-column-from-sqlite-table/5987838
            // ALTER TABLE video_item DROP COLUMN yt_id

            database.execSQL("ALTER TABLE video_item ADD COLUMN item_url TEXT");
            // https://www.sqlitetutorial.net/sqlite-string-functions/sqlite-concat/
            database.execSQL("UPDATE video_item SET item_url='https://www.youtube.com/watch?v=' || yt_id");
        }
    };

    private static Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // CREATE TABLE profile (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT)
            // CREATE TABLE profile_playlists (
            //   _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            //   profile_id INTEGER NOT NULL, playlist_id INTEGER NOT NULL,
            //   FOREIGN KEY(profile_id) REFERENCES profile(_id) ON UPDATE NO ACTION ON DELETE CASCADE,
            //   FOREIGN KEY(playlist_id) REFERENCES playlist_info(_id) ON UPDATE NO ACTION ON DELETE CASCADE )
            // CREATE INDEX index_profile_playlists_profile_id ON profile_playlists (profile_id)
            // CREATE INDEX index_profile_playlists_playlist_id ON profile_playlists (playlist_id)

            database.execSQL("CREATE TABLE profile (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT)");
            database.execSQL("CREATE TABLE profile_playlists (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "profile_id INTEGER NOT NULL, playlist_id INTEGER NOT NULL, " +
                    "FOREIGN KEY(profile_id) REFERENCES profile(_id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(playlist_id) REFERENCES playlist_info(_id) ON UPDATE NO ACTION ON DELETE CASCADE)");

            database.execSQL("CREATE INDEX index_profile_playlists_profile_id ON profile_playlists (profile_id)");
            database.execSQL("CREATE INDEX index_profile_playlists_playlist_id ON profile_playlists (playlist_id)");
        }
    };
}
