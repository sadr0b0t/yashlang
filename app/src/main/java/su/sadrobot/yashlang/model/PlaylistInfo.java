package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * PlaylistInfo.java is part of YaShlang.
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

import android.graphics.Bitmap;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlist_info")
public class PlaylistInfo {

    public enum PlaylistType {
        YT_USER, YT_CHANNEL, YT_PLAYLIST, PT_USER, PT_CHANNEL, PT_PLAYLIST
    }

    /**
     * Значение поля id для элементов, не добавленных в базу. Это же значение
     * должно быть устновлено для элементов, которые можно добавить в базу так,
     * чтобы сработал механизм autogenerate (поэтому ID_NONE=0, а не, например, -1).
     * Действующие id элементов в базе начинаются с 1.
     */
    public static int ID_NONE = 0;

    // Id должен быть только long, иначе из метода @Insert не получится получить id вновь созданной записи
    // https://developer.android.com/training/data-storage/room/accessing-data#convenience-insert
    // If the @Insert method receives only 1 parameter, it can return a long, which is the new rowId
    // for the inserted item. If the parameter is an array or a collection,
    // it should return long[] or List<Long> instead.

    @PrimaryKey(autoGenerate = true)
    private long _id;

    @ColumnInfo(name = "name")
    private String name;

    /**
     * Url плейлиста на внешнем сервисе.
     */
    @ColumnInfo(name = "url")
    private String url;

    @ColumnInfo(name = "thumb_url")
    private String thumbUrl;

    @ColumnInfo(name = "type")
    private String type;

    @ColumnInfo(name = "enabled")
    private boolean enabled;


    // возможно, не очень красиво хранить здесь поля, не сохраняемые в базу,
    // но более удобного способа отправлять объекты в адаптер списка прямиком из
    // базы данных и при этом приклеплять к ним картинки, загружаемые по требованию
    // из интернета, найти не представляется возможным
    @Ignore
    private Bitmap thumbBitmap;


    public PlaylistInfo(final String name, final String url, final String thumbUrl, final String type) {
        this.name = name;
        this.url = url;
        this.thumbUrl = thumbUrl;
        this.type = type;
        this.enabled = true;
    }

    @Ignore
    public PlaylistInfo(final String name, final String url, final String thumbUrl, final PlaylistType type) {
        this.name = name;
        this.url = url;
        this.thumbUrl = thumbUrl;
        this.type = type.name();
        this.enabled = true;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setType(PlaylistType type) {
        this.type = type.name();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Bitmap getThumbBitmap() {
        return thumbBitmap;
    }

    public void setThumbBitmap(Bitmap thumbBitmap) {
        this.thumbBitmap = thumbBitmap;
    }


    public String toString() {
        return name;
    }
}
