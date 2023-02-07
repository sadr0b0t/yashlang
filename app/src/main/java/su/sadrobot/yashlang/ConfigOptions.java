package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigOptions.java is part of YaShlang.
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
import android.content.SharedPreferences;

public class ConfigOptions {
    private static final String SHARED_PREFERENCES_NAME = "yashlang.prefs";

    public static final String RECOMMENDED_PLAYLISTS_RES_PATH = "/su/sadrobot/yashlang/data/recommended-playlists.json";

    /**
     * File cache_dir = new File(context.getExternalFilesDir(null), ConfigOptions.STREAM_CACHE_DIR_NAME);
     */
    public static final String STREAM_CACHE_DIR_NAME = "stream_cache";

    /**
     * File cache_dir = new File(context.getExternalFilesDir(null), ConfigOptions.THUMB_CACHE_DIR_NAME);
     */
    public static final String THUMB_CACHE_DIR_NAME = "thumb_cache";

    // TIME_ADDED здесь, по сути, без сортировки, или сортировка по ID
    public enum SortBy {
        TIME_ADDED, NAME, URL, DURATION
    }

    /**
     * Стратегия выбора разрешения при проигрывании ролика:
     * MAX_RES - максимальное из доступных,
     * MIN_RES - минимальное из доступных,
     * CUSTOM_RES - выбранное в настройках,
     * LAST_CHOSEN - последнее выбранное на экране проигрывания ролика
     */
    public enum VideoStreamSelectStrategy {
        MAX_RES, MIN_RES, CUSTOM_RES, LAST_CHOSEN
    }

    /**
     * Предпочтения при автоматическом выборе разрешения: если не доступно указанное разрашение,
     * предпочитать:
     * HIGHER_RES - более высокое, чем указанное, из доступных,
     * LOWER_RES - более низкое, чем указанное, из доступных
     */
    public enum VideoStreamSelectPreferRes {
        HIGHER_RES, LOWER_RES
    }

    /**
     * Стратегия кэширования иконок для роликов:
     * NONE - не кэшировать
     * ALL - кэшировать все
     * WITH_OFFLINE_STREAMS - кэшировать только для роликов, у которых
     *   есть кэшированные оффлайн потоки
     */
    public enum VideoThumbCacheStrategy {
        NONE, ALL, WITH_OFFLINE_STREAMS
    }

    public static final boolean DEVEL_MODE_ON = false;

    public static final int RECOMMENDED_RANDOM_LIM = 200;

    public static final int LOAD_PAGE_RETRY_COUNT = 3;

    public static final int FAKE_TIMESTAMP_BLOCK_SIZE = 10000;

    public static final int ADD_RECOMMENDED_PLAYLISTS_DELAY_MS = 800;

    public static final int UPDATE_PLAYLISTS_DELAY_MS = 500;

    /** The default connection timeout, in milliseconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS =
            com.google.android.exoplayer2.upstream.DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /** The default read timeout, in milliseconds. */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS =
            com.google.android.exoplayer2.upstream.DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS;

    public static final String[] VIDEO_RESOLUTIONS = {
            "1080p", "720p", "480p", "360p", "240p", "144p"
    };

    /**
     * среднее качество, доступно почти всегда
     */
    public static final String DEFAULT_VIDEO_RESOLUTION = "480p";

    /**
     * максимальное количество параллельных закачек для кэша потоков
     */
    public static final int MAX_STREAM_CACHE_DOWNLOADS = 5;

    private static final String PREF_PLAYLISTS_SORT_BY = "PREF_PLAYLISTS_SORT_BY";

    /**
     * true: ascending
     * false: descending
     */
    private static final String PREF_PLAYLISTS_SORT_DIR = "PREF_PLAYLISTS_SORT_DIR";

    private static final String PREF_PLAYLIST_SORT_BY = "PREF_PLAYLIST_SORT_BY";

    /**
     * true: ascending
     * false: descending
     */
    private static final String PREF_PLAYLIST_SORT_DIR = "PREF_PLAYLIST_SORT_DIR";

    /**
     * VideoStreamSelectStrategy: MAX_RES, MIN_RES, CUSTOM_RES, LAST_CHOSEN
     */
    private static final String PREF_VIDEO_STREAM_SELECT_STRATEGY = "PREF_VIDEO_STREAM_SELECT_STRATEGY";

    private static final String PREF_VIDEO_STREAM_CUSTOM_RES = "PREF_VIDEO_STREAM_CUSTOM_RES";

    private static final String PREF_VIDEO_STREAM_LAST_SELECTED_RES = "PREF_VIDEO_STREAM_LAST_SELECTED_RES";

    /**
     * VideoStreamSelectPreferRes: HIGHER_RES, LOWER_RES
     */
    private static final String PREF_VIDEO_STREAM_SELECT_CUSTOM_PREFER_RES = "PREF_VIDEO_STREAM_SELECT_CUSTOM_PREFER_RES";

    /**
     * VideoStreamSelectPreferRes: HIGHER_RES, LOWER_RES
     */
    private static final String PREF_VIDEO_STREAM_SELECT_LAST_PREFER_RES = "PREF_VIDEO_STREAM_SELECT_LAST_PREFER_RES";

    /**
     * true: если у ролика есть любые потоки оффлайн, всегда по умолчанию выбирать поток видео оффлайн
     * наилучшего качества (из доступных оффлайн). Если потоков оффлайн нет, использовать обычную стратегию.
     * false: в любом случае использовать обычную стратегию выбора потока.
     */
    private static final String PREF_VIDEO_STREAM_SELECT_OFFLINE = "PREF_VIDEO_STREAM_SELECT_OFFLINE";

    /**
     * VideoThumbCacheStrategy
     */
    private static final String PREF_VIDEO_THUMB_CACHE_STRATEGY = "PREF_VIDEO_THUMB_CACHE_STRATEGY";

    /**
     * true: включить режим оффлайн (показывать в рекомендациях только такие ролики, у которых
     * есть сохраненные оффлайн потоки)
     */
    private static final String PREF_OFFLINE_MODE_ON = "PREF_OFFLINE_MODE_ON";


    public static SortBy getPlaylistsSortBy(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: TIME_ADDED (чтобы сохранить старое поведение)
        return SortBy.valueOf(sp.getString(PREF_PLAYLISTS_SORT_BY, SortBy.TIME_ADDED.name()));
    }

    public static void setPlaylistsSortBy(final Context context, final SortBy sortBy) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_PLAYLISTS_SORT_BY, sortBy.name());
        editor.commit();
    }

    public static boolean getPlaylistsSortDir(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: TIME_ADDED + asc (чтобы сохранить старое поведение)
        return sp.getBoolean(PREF_PLAYLISTS_SORT_DIR, true);
    }

    public static void setPlaylistsSortDir(final Context context, final boolean asc) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putBoolean(PREF_PLAYLISTS_SORT_DIR, asc);
        editor.commit();
    }

    public static SortBy getPlaylistSortBy(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: TIME_ADDED+desc (чтобы сохранить старое поведение)
        return SortBy.valueOf(sp.getString(PREF_PLAYLIST_SORT_BY, SortBy.TIME_ADDED.name()));
    }

    public static void setPlaylistSortBy(final Context context, final SortBy sortBy) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_PLAYLIST_SORT_BY, sortBy.name());
        editor.commit();
    }

    public static boolean getPlaylistSortDir(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: TIME_ADDED + desc (чтобы сохранить старое поведение)
        return sp.getBoolean(PREF_PLAYLIST_SORT_DIR, false);
    }

    public static void setPlaylistSortDir(final Context context, final boolean asc) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putBoolean(PREF_PLAYLIST_SORT_DIR, asc);
        editor.commit();
    }

    public static VideoStreamSelectStrategy getVideoStreamSelectStrategy(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: MAX_RES
        return VideoStreamSelectStrategy.valueOf(sp.getString(PREF_VIDEO_STREAM_SELECT_STRATEGY, VideoStreamSelectStrategy.MAX_RES.name()));
    }

    public static void setVideoStreamSelectStrategy(final Context context, final VideoStreamSelectStrategy strategy) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_STREAM_SELECT_STRATEGY, strategy.name());
        editor.commit();
    }

    public static String getVideoStreamCustomRes(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: среднее качество, доступно почти всегда
        return sp.getString(PREF_VIDEO_STREAM_CUSTOM_RES, DEFAULT_VIDEO_RESOLUTION);
    }

    public static void setVideoStreamCustomRes(final Context context, final String resolution) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_STREAM_CUSTOM_RES, resolution);
        editor.commit();
    }

    public static String getVideoStreamLastSelectedRes(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: среднее качество, доступно почти всегда
        return sp.getString(PREF_VIDEO_STREAM_LAST_SELECTED_RES, DEFAULT_VIDEO_RESOLUTION);
    }

    public static void setVideoStreamLastSelectedRes(final Context context, final String resolution) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_STREAM_LAST_SELECTED_RES, resolution);
        editor.commit();
    }

    public static VideoStreamSelectPreferRes getVideoStreamSelectCustomPreferRes(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        return VideoStreamSelectPreferRes.valueOf(sp.getString(PREF_VIDEO_STREAM_SELECT_CUSTOM_PREFER_RES, VideoStreamSelectPreferRes.HIGHER_RES.name()));
    }

    public static void setVideoStreamSelectCustomPreferRes(final Context context, final VideoStreamSelectPreferRes preferRes) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_STREAM_SELECT_CUSTOM_PREFER_RES, preferRes.name());
        editor.commit();
    }

    public static VideoStreamSelectPreferRes getVideoStreamSelectLastPreferRes(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        return VideoStreamSelectPreferRes.valueOf(sp.getString(PREF_VIDEO_STREAM_SELECT_LAST_PREFER_RES, VideoStreamSelectPreferRes.HIGHER_RES.name()));
    }

    public static void setVideoStreamSelectLastPreferRes(final Context context, final VideoStreamSelectPreferRes preferRes) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_STREAM_SELECT_LAST_PREFER_RES, preferRes.name());
        editor.commit();
    }

    public static boolean getVideoStreamSelectOffline(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: true
        return sp.getBoolean(PREF_VIDEO_STREAM_SELECT_OFFLINE, true);
    }

    public static void setVideoStreamSelectOffline(final Context context, final boolean selectOffline) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putBoolean(PREF_VIDEO_STREAM_SELECT_OFFLINE, selectOffline);
        editor.commit();
    }

    public static VideoThumbCacheStrategy getVideoThumbCacheStrategy(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        return VideoThumbCacheStrategy.valueOf(sp.getString(PREF_VIDEO_THUMB_CACHE_STRATEGY, VideoThumbCacheStrategy.ALL.name()));
    }

    public static void setVideoThumbCacheStrategy(final Context context, final VideoThumbCacheStrategy strategy) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putString(PREF_VIDEO_THUMB_CACHE_STRATEGY, strategy.name());
        editor.commit();
    }

    public static void setOfflineModeOn(final Context context, final boolean offlineModeOn) {
        final SharedPreferences.Editor editor = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0).edit();
        editor.putBoolean(PREF_OFFLINE_MODE_ON, offlineModeOn);
        editor.commit();
    }

    public static boolean getOfflineModeOn(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,0);
        // по умолчанию: false
        return sp.getBoolean(PREF_OFFLINE_MODE_ON, false);
    }
}
