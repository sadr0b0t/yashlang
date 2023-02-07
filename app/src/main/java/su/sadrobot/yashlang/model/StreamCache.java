package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
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
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "stream_cache",
        foreignKeys = @ForeignKey(entity = VideoItem.class,
                parentColumns = "_id",
                childColumns = "video_id",
                onDelete = ForeignKey.CASCADE),
        indices=@Index(value="video_id"))
public class StreamCache {

    public enum StreamType {
        VIDEO, AUDIO, BOTH
    }

    public static long STREAM_SIZE_UNKNOWN = -1;

    /**
     * Значение поля id для элементов, не добавленных в базу. Это же значение
     * должно быть устновлено для элементов, которые можно добавить в базу так,
     * чтобы сработал механизм autogenerate (поэтому ID_NONE=0, а не, например, -1).
     * Действующие id элементов в базе начинаются с 1.
     */
    public static long ID_NONE = 0;

    // Id должен быть только long, иначе из метода @Insert не получится получить id вновь созданной записи
    // https://developer.android.com/training/data-storage/room/accessing-data#convenience-insert
    // If the @Insert method receives only 1 parameter, it can return a long, which is the new rowId
    // for the inserted item. If the parameter is an array or a collection,
    // it should return long[] or List<Long> instead.

    // также _id должен быть равен нулю в тех случаях, когда мы добавляем объект в базу для того,
    // чтобы работал механизм autogenerate (id в базе начинаются с 1)

    @PrimaryKey(autoGenerate = true)
    private long _id;

    @ColumnInfo(name = "video_id")
    private long videoId;

    /**
     * StreamType: VIDEO, AUDIO, BOTH
     */
    @ColumnInfo(name = "stream_type")
    private String streamType;

    /**
     * Stream resolution: "1080p", "720p", "480p", "360p", "240p", "144p" etc
     */
    @ColumnInfo(name = "stream_res")
    private String streamRes;

    /**
     * Stream format: MP4, WEBM etc
     */
    @ColumnInfo(name = "stream_format")
    private String streamFormat;

    @ColumnInfo(name = "stream_mime_type")
    private String streamMimeType;

    @ColumnInfo(name = "stream_format_suffix")
    private String streamFormatSuffix;

    @ColumnInfo(name = "file_name")
    private String fileName;

    @ColumnInfo(name = "stream_size")
    private long streamSize = STREAM_SIZE_UNKNOWN;

    @ColumnInfo(name = "downloaded")
    private boolean downloaded;

    // кэш - объект видео ролик, для тех случаев, когда не хочется постоянно обращаться
    // в базу в фоновом потоке
    @Ignore
    private VideoItem videoItem;

    // кэш - объект плейлист, для тех случаев, когда не хочется постоянно обращаться
    // в базу в фоновом потоке
    @Ignore
    private PlaylistInfo playlistInfo;


    public long getId() {
        return _id;
    }

    public void setId(long _id) {
        this._id = _id;
    }

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public String getStreamType() {
        return streamType;
    }

    public StreamType getStreamTypeEnum() {
        return StreamType.valueOf(streamType);
    }

    public void setStreamType(String streamType) {
        this.streamType = streamType;
    }

    public void setStreamType(StreamType streamType) {
        this.streamType = streamType.name();
    }

    public String getStreamRes() {
        return streamRes;
    }

    public void setStreamRes(String streamRes) {
        this.streamRes = streamRes;
    }

    public String getStreamFormat() {
        return streamFormat;
    }

    public void setStreamFormat(String streamFormat) {
        this.streamFormat = streamFormat;
    }

    public String getStreamMimeType() {
        return streamMimeType;
    }

    public void setStreamMimeType(String streamMimeType) {
        this.streamMimeType = streamMimeType;
    }

    public String getStreamFormatSuffix() {
        return streamFormatSuffix;
    }

    public void setStreamFormatSuffix(String streamFormatSuffix) {
        this.streamFormatSuffix = streamFormatSuffix;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getStreamSize() {
        return streamSize;
    }

    public void setStreamSize(long streamSize) {
        this.streamSize = streamSize;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public VideoItem getVideoItem() {
        return videoItem;
    }

    public void setVideoItem(VideoItem videoItem) {
        this.videoItem = videoItem;
    }

    public PlaylistInfo getPlaylistInfo() {
        return playlistInfo;
    }

    public void setPlaylistInfo(PlaylistInfo playlistInfo) {
        this.playlistInfo = playlistInfo;
    }
}
