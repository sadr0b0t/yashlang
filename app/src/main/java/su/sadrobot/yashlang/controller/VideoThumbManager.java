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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.R;
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

    //private static final String THUMB_URL_TEMPLATE = "https://img.youtube.com/vi/%id%/sddefault.jpg";
    private static final String THUMB_URL_TEMPLATE = "https://img.youtube.com/vi/%id%/default.jpg";

    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/default.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/hqdefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/mqdefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/sddefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/maxresdefault.jpg


    public Bitmap loadBitmap(final String url) throws IOException {

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
            if(input != null) {
                input.close();
            }
            if(conn.getErrorStream() != null) {
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

    /**
     * Load thumbnail for youtube video
     * @param ytId youtube video id
     * @return video thumbnail as bitmap
     * @throws IOException
     */
    public Bitmap loadVideoThumb(final String ytId) throws IOException {
        // 1) YouTube data api (Android):
        // https://github.com/youtube/api-samples/tree/master/java
        // (похоже, хотят обращаться к ютюб-сервисам, установленным на телефоне;
        // скорее всего выберем данунах, проще тягать иконки по шаблону URL и парсить страничку с поиском)
        // https://developers.google.com/youtube/v3/quickstart/android
        // build.gradle:
        //     compile('com.google.apis:google-api-services-youtube:v3-rev209-1.25.0') {
        //        exclude group: 'org.apache.httpcomponents'
        //    }
        // AndroidManifest.xml
        //     <uses-permission android:name="android.permission.INTERNET" />
        //    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

        // 2) Вручную по шаблону
        // https://www.thewebtaylor.com/articles/how-to-get-a-youtube-videos-thumbnail-image-in-high-quality
        // http://img.youtube.com/vi/<insert-youtube-video-id-here>/hqdefault.jpg
        // https://img.youtube.com/vi/KguUtafZtN8/sddefault.jpg

        return loadBitmap(THUMB_URL_TEMPLATE.replace("%id%", ytId));
    }


    /**
     * TODO: пожалуй, если картинка не грузится, лучше возвращать null,
     * чтобы было понятно, что произошло.
     *
     * Загрузить картинку с превью видео или вернуть картинку по умолчанию.
     * @param context
     * @param vid
     * @return
     */
    public Bitmap loadVideoThumb(final Context context, final VideoItem vid) {
        Bitmap thumb = null;
        try {
            // Будем грузить для роликов YouTube иконку большего размера (так будет лучше на планшетах),
            // для PeerTube ссылка останется без изменений
            thumb = loadBitmap(PlaylistUrlUtil.fixYtVideoThumbSize(vid.getThumbUrl()));
        } catch (IOException e) {
            //thumb = defaultThumb; // default thumb
            //e.printStackTrace();
        }
        if(thumb == null) {
            thumb = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_yashlang_thumb);
        }
        return thumb;
    }


    public Bitmap loadPlaylistThumb(final Context context, final String thumbUrl) {
        Bitmap thumb = null;
        try {
            // в базе данных и так сохраняется ссылка на большую иконку (=240-),
            // но это может быть пригодится, если придется взять картинку еще больше
            // (или, наоборот, поэкономит трафик и взять меньше) - сюда можно будет
            // передавать нужный размер и подставлять его в url
            thumb = loadBitmap(PlaylistUrlUtil.fixYtChannelAvatarSize(thumbUrl));
        } catch (IOException e) {
            //thumb = defaultThumb; // default thumb
        }
        if(thumb == null) {
            thumb = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_yashlang_thumb);
        }
        return thumb;
    }

    public void loadThumbs(final Context context, final List<VideoItem> videoItems) {
        for (VideoItem videoItem : videoItems) {
            final Bitmap thumb =
                    VideoThumbManager.getInstance().loadVideoThumb(context, videoItem);
            videoItem.setThumbBitmap(thumb);
        }
    }
}
