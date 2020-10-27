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


@Database(entities = {VideoItem.class, PlaylistInfo.class}, version = 2)
public abstract class VideoDatabase extends RoomDatabase {
    public abstract VideoItemDao videoItemDao();
    public abstract PlaylistInfoDao playlistInfoDao();

    public static VideoDatabase getDb(Context context) {
        return Room.databaseBuilder(context,
                VideoDatabase.class, "video-db")
                .addMigrations(MIGRATION_1_2)
                //.fallbackToDestructiveMigration()
                //.allowMainThreadQueries()
                .build();
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
}
