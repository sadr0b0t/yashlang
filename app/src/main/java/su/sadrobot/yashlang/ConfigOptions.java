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

    // TIME_ADDED здесь, по сути, без сортировки, или сортировка по ID
    public enum SortBy {
        TIME_ADDED, NAME, URL, DURATION
    }

    public static final boolean DEVEL_MODE_ON = false;

    public static final int RECOMMENDED_RANDOM_LIM = 200;

    public static final int LOAD_PAGE_RETRY_COUNT = 3;

    public static final int FAKE_TIMESTAMP_BLOCK_SIZE = 10000;

    public static final int ADD_RECOMMENDED_PLAYLISTS_DELAY_MS = 800;

    public static final int UPDATE_PLAYLISTS_DELAY_MS = 500;


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
}
