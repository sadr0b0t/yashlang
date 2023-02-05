package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoThumbManager.java is part of YaShlang.
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;

public class VideoThumbManager {
    private static VideoThumbManager _instance;
    static {
        _instance = new VideoThumbManager();
    }

    public static VideoThumbManager getInstance() {
        return _instance;
    }
    private VideoThumbManager() {
    }

    private Bitmap defaultVideoItemThumb;
    private Bitmap defaultPlaylistInfoThumb;

    private Bitmap getDefaultVideoItemThumb(final Context context) {
        if (defaultVideoItemThumb == null) {
            defaultVideoItemThumb = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_yashlang_thumb);
        }
        return defaultVideoItemThumb;
    }

    private Bitmap getDefaultPlaylistInfoThumb(final Context context) {
        if (defaultPlaylistInfoThumb == null) {
            defaultPlaylistInfoThumb = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_yashlang_thumb);
        }
        return defaultPlaylistInfoThumb;
    }

    private Bitmap loadBitmap(final String url) throws IOException {

        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        //  настройки таймаутов
        // https://github.com/sadr0b0t/yashlang/issues/132
        // https://stackoverflow.com/questions/45199702/httpurlconnection-timeout-defaults
        // https://github.com/TeamNewPipe/NewPipe/blob/v0.23.1/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java
        conn.setConnectTimeout(ConfigOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        conn.setReadTimeout(ConfigOptions.DEFAULT_READ_TIMEOUT_MILLIS);
        conn.connect();

        InputStream input = null;
        Bitmap bm;

        try {
            input = conn.getInputStream();
            bm = BitmapFactory.decodeStream(input);
        } finally {
            if (input != null) {
                input.close();
            }
            if (conn.getErrorStream() != null) {
                // Сюда попадаем, если connection.getInputStream() вылетает с эксепшеном
                // (на сервере нет иконки, которую пытаемся скачать)
                // Это очень важное место:
                // 1. Без него будет регулярно сыпаться ворнинг при прокрутке роликов в разных списках:
                // 2020-10-03 22:14:23.911 27270-27314/su.sadrobot.yashlang W/OkHttpClient: A connection to https://i.ytimg.com/ was leaked. Did you forget to close a response body?
                // 2. Хуже того, приложение может вылететь при быстрой прокрутке списка с большим
                // количеством роликов, для которых удалены аналоги на сервере (иконка недоступна на сервере),
                // если в этом же списке происходит обращение к б/д - вылетает ошибка:
                // SQLiteCantOpenDatabaseException: unable to open database file
                conn.getErrorStream().close();
            }
            conn.disconnect();
        }

        return bm;
    }

    private Bitmap loadBitmap(final File file) {
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private boolean saveBitmap(final Bitmap bm, final File file) throws IOException {
        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            file.createNewFile();
        }
        return bm.compress(Bitmap.CompressFormat.JPEG, 90, new FileOutputStream(file));
    }

    /**
     * TODO: пожалуй, если картинка не грузится, лучше возвращать null,
     *   чтобы было понятно, что произошло.
     *   updt: не факт, что следует так делать, т.к. сейчас во всех местах, где грузятся иконки,
     *   если иконка null, то происходит попытка её загрузить. Если при неудачной попытке загрузить
     *   будет оставаться null, то попытка загрузить иконку будет происходить снова и снова, что
     *   при плохом или выключенном интернете ни к чему хорошему не приведет
     *
     * Загрузить картинку с превью видео или вернуть картинку по умолчанию.
     *
     * @param context
     * @param videoItem
     * @return
     */
    public Bitmap loadVideoThumb(final Context context, final VideoItem videoItem) {
        // сначала пробуем загрузить из кэша
        final File cacheFile = ThumbCacheFsManager.getThumbCacheFileForVideoItem(context, videoItem);
        Bitmap thumb = loadBitmap(cacheFile);

        // если в кэше нет, грузим онлайн
        if (thumb == null) {
            try {
                // Будем грузить для роликов YouTube иконку большего размера (так будет лучше на планшетах),
                // для PeerTube ссылка останется без изменений
                thumb = loadBitmap(PlaylistUrlUtil.fixYtVideoThumbSize(videoItem.getThumbUrl()));

                // сохраним в кэш, если выставлена настройка
                final ConfigOptions.VideoThumbCacheStrategy cacheStrategy = ConfigOptions.getVideoThumbCacheStrategy(context);
                if (cacheStrategy == ConfigOptions.VideoThumbCacheStrategy.ALL ||
                        (cacheStrategy == ConfigOptions.VideoThumbCacheStrategy.WITH_OFFLINE_STREAMS &&
                                videoItem.isHasOffline())) {
                    saveBitmap(thumb, cacheFile);
                }
            } catch (IOException e) {
                // если произошла ошибка при загрузке или сохранении, знать о ней не обязтельно -
                // приложение просто оставит иконку по умолчанию
                //e.printStackTrace();
            }

            // онлайн не загрузили - грузим иконку из ресурсов
            if (thumb == null) {
                thumb = getDefaultVideoItemThumb(context);
            }
        }
        return thumb;
    }

    public Bitmap loadPlaylistThumb(final Context context, final PlaylistInfo plInfo) {
        // сначала пробуем загрузить из кэша
        final File cacheFile = ThumbCacheFsManager.getThumbCacheFileForPlaylistInfo(context, plInfo);
        Bitmap thumb = loadBitmap(cacheFile);

        // если в кэше нет, грузим онлайн
        if (thumb == null) {
            try {
                // в базе данных и так сохраняется ссылка на большую иконку (=240-),
                // но это может быть пригодится, если придется взять картинку еще больше
                // (или, наоборот, поэкономит трафик и взять меньше) - сюда можно будет
                // передавать нужный размер и подставлять его в url
                thumb = loadBitmap(PlaylistUrlUtil.fixYtChannelAvatarSize(plInfo.getThumbUrl()));

                // сохраним в кэш
                saveBitmap(thumb, cacheFile);
            } catch (IOException e) {
                // если произошла ошибка при загрузке или сохранении, знать о ней не обязтельно -
                // приложение просто оставит иконку по умолчанию
                //e.printStackTrace();
            }

            // онлайн не загрузили - грузим иконку из ресурсов
            if (thumb == null) {
                thumb = getDefaultPlaylistInfoThumb(context);
            }
        }
        return thumb;
    }

    public void loadThumbs(final Context context, final List<VideoItem> videoItems) {
        for (VideoItem videoItem : videoItems) {
            final Bitmap thumb = loadVideoThumb(context, videoItem);
            videoItem.setThumbBitmap(thumb);
        }
    }
}
