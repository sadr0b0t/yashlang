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

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "profile")
public class Profile {

    /**
     * Значение поля id для элементов, не добавленных в базу. Это же значение
     * должно быть устновлено для элементов, которые можно добавить в базу так,
     * чтобы сработал механизм autogenerate (поэтому ID_NONE=0, а не, например, -1).
     * Действующие id элементов в базе начинаются с 1.
     */
    public static long ID_NONE = 0;

    public static long ID_ENABLE_ALL = -1;
    public static long ID_DISABLE_ALL = -2;
    public static long ID_DISABLE_YT = -3;

    // Id должен быть только long, иначе из метода @Insert не получится получить id вновь созданной записи
    // https://developer.android.com/training/data-storage/room/accessing-data#convenience-insert
    // If the @Insert method receives only 1 parameter, it can return a long, which is the new rowId
    // for the inserted item. If the parameter is an array or a collection,
    // it should return long[] or List<Long> instead.

    @PrimaryKey(autoGenerate = true)
    private long _id;

    @ColumnInfo(name = "name")
    private String name;

    public Profile(final String name) {
        this.name = name;
    }

    @Ignore
    public Profile(final long _id, final String name) {
        this._id = _id;
        this.name = name;
    }

    public long getId() {
        return _id;
    }

    public void setId(long _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
