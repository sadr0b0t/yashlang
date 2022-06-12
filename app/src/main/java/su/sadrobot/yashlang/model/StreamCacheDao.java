package su.sadrobot.yashlang.model;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItemDao.java is part of YaShlang.
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
public abstract class StreamCacheDao {

    @Insert
    protected abstract long insert(final StreamCache cacheItem);

    @Delete
    protected abstract void delete(final StreamCache cacheItem);

    @Query("UPDATE stream_cache SET downloaded = :downloaded WHERE _id = :id")
    protected abstract void setDownloaded(long id, boolean downloaded);

    @Query("UPDATE stream_cache SET stream_size = :streamSize WHERE _id = :id")
    public abstract void setStreamSize(long id, long streamSize);

    @Query("SELECT * FROM stream_cache WHERE _id = :id LIMIT 1")
    public abstract StreamCache getById(long id);

    @Query("SELECT * FROM stream_cache WHERE downloaded ORDER BY video_id")
    public abstract DataSource.Factory<Integer, StreamCache> getFinishedDs();

    @Query("SELECT * FROM stream_cache WHERE NOT downloaded")
    public abstract List<StreamCache> getNotFinished();

    @Query("SELECT * FROM stream_cache WHERE NOT downloaded")
    public abstract DataSource.Factory<Integer, StreamCache> getNotFinishedDs();

    @Query("SELECT * FROM stream_cache WHERE video_id = :videoId AND downloaded")
    public abstract List<StreamCache> getFinishedForVideo(final long videoId);

    @Query("SELECT * FROM stream_cache WHERE file_name = :fileName")
    public abstract List<StreamCache> findStreamsForFile(final String fileName);

    @Query("UPDATE video_item SET has_offline = :hasOffline WHERE _id = :videoId")
    protected abstract void setVideoHasOffline(long videoId, boolean hasOffline);

    @Transaction
    public void insertStreamCache(final StreamCache cacheItem) {
        insert(cacheItem);
        if (cacheItem.isDownloaded()) {
            // если поток загружен, то этого достаточно, чтобы выставить ролику флаг
            setVideoHasOffline(cacheItem.getVideoId(), true);
        }
    }
    @Transaction
    public void deleteStreamCache(final StreamCache cacheItem) {
        delete(cacheItem);
        if (cacheItem.isDownloaded()) {
            // проверить, остались ли у ролика еще кэшированные потоки
            if (getFinishedForVideo(cacheItem.getVideoId()).isEmpty()) {
                setVideoHasOffline(cacheItem.getVideoId(), false);
            }
        }
    }

    @Transaction
    public void setDownloaded(final long streamCacheId, final long videoId, final boolean downloaded) {
        setDownloaded(streamCacheId, downloaded);

        // не будем проверять, что streamCacheId относится к videoId, мы сами себе не враги
        // (т.е. не будем отправлять неправильную комбинацию, а если отправили, то это баг в коде)
        if (downloaded) {
            // если поток загружен, то этого достаточно, чтобы выставить ролику флаг
            setVideoHasOffline(videoId, true);
        } else {
            // проверить, остались ли у ролика еще кэшированные потоки
            if (getFinishedForVideo(videoId).isEmpty()) {
                setVideoHasOffline(videoId, false);
            }
        }
    }
}
