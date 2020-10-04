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
import java.util.HashMap;
import java.util.Map;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.VideoItem;

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

    private Map<String, Bitmap> thumbCache = new HashMap<String, Bitmap>();

    //private static final String THUMB_URL_TEMPLATE = "https://img.youtube.com/vi/%id%/sddefault.jpg";
    private static final String THUMB_URL_TEMPLATE = "https://img.youtube.com/vi/%id%/default.jpg";

    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/default.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/hqdefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/mqdefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/sddefault.jpg
    // http://img.youtube.com/vi/<insert-youtube-video-id-here>/maxresdefault.jpg


    private Bitmap defaultThumb = null;//BitmapFactory.decodeResource(R.drawable.bug1);

    public Bitmap loadBitmap(final String url) throws IOException {

        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.connect();

        InputStream input = null;
        Bitmap bm;

        try {
            input = connection.getInputStream();
            bm = BitmapFactory.decodeStream(input);
        } finally {
            if(input != null) {
                input.close();
            }
            if(connection.getErrorStream() != null) {
                // Сюда попадаем, если connection.getInputStream() вылетает с эксепшеном
                // (на сервере нет иконки, которую пытаемся скачать)
                // Это очень важное место:
                // 1. Без него будет регулярно сыпаться ворнинг при прокрутке роликов в разных списках:
                // 2020-10-03 22:14:23.911 27270-27314/su.sadrobot.yashlang W/OkHttpClient: A connection to https://i.ytimg.com/ was leaked. Did you forget to close a response body?
                // 2. Хуже того, приложение может вылететь при быстрой прокрутке списка с большим
                // количеством роликов, для которых удалены аналоги на сервере (иконка недоступна на сервере),
                // если в этом же списке происходит обращение к б/д - вылетает ошибка:
                // SQLiteCantOpenDatabaseException: unable to open database file
                connection.getErrorStream().close();
            }
            connection.disconnect();
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
     * @param vid
     * @return
     */
    public Bitmap loadVideoThumb(final Context context, final VideoItem vid) {
        Bitmap thumb = null;
        try {
            //thumb = loadVideoThumb(vid.getYtId());
            thumb = loadBitmap(vid.getThumbUrl());
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
            thumb = loadBitmap(thumbUrl);
        } catch (IOException e) {
            //thumb = defaultThumb; // default thumb
        }
        if(thumb == null) {
            thumb = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_yashlang_thumb);
        }
        return thumb;
    }
}
