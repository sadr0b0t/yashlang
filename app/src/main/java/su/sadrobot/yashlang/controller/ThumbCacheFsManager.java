package su.sadrobot.yashlang.controller;

/*
 * Copyright (C) Anton Moiseev 2023 <github.com/sadr0b0t>
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

import java.io.File;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoItem;

/**
 * Всё, что связано с хранением кэша иконок в файловой системе.
 */
public class ThumbCacheFsManager {


    public static String getFileNameForVideoItem(final VideoItem videoItem) {
        return "vid-" + videoItem.getId() + ".jpg";
    }

    public static String getFileNameForPlaylistInfo(final PlaylistInfo plInfo) {
        return "pl-" + plInfo.getId() + ".jpg";
    }

    public static File getThumbCacheFileForVideoItem(final Context context, final VideoItem videoItem) {
        final File cacheDir = getThumbCacheDir(context);
        final File cacheFile = new File(cacheDir, getFileNameForVideoItem(videoItem));
        return cacheFile;
    }

    public static File getThumbCacheFileForPlaylistInfo(final Context context, final PlaylistInfo plInfo) {
        final File cacheDir = getThumbCacheDir(context);
        final File cacheFile = new File(cacheDir, getFileNameForPlaylistInfo(plInfo));
        return cacheFile;
    }

    public static File getThumbCacheDir(final Context context) {
        return new File(context.getExternalFilesDir(null), ConfigOptions.THUMB_CACHE_DIR_NAME);
    }

    public static long getThumbCacheFsSize(final Context context) {
        final File cacheDir = getThumbCacheDir(context);

        // https://stackoverflow.com/questions/2149785/get-size-of-folder-or-file
        long cacheSize = 0;
        final File[] cacheFiles = cacheDir.listFiles();
        if (cacheFiles != null) {
            for (final File file : cacheFiles) {
                if (file.isFile()) {
                    cacheSize += file.length();
                }
            }
        }
        return  cacheSize;
    }

    /**
     * Удалить все файлы из каталога с кэшэм иконок
     * @param context
     * @return true, если удален хотя бы один файл
     */
    public static boolean clearThumbCache(final Context context) {
        boolean deletedSomething = false;
        final File cacheDir = getThumbCacheDir(context);
        final File[] cacheFiles = cacheDir.listFiles();
        if (cacheFiles != null) {
            for (final File file : cacheFiles) {
                if (file.isFile()) {
                    file.delete();
                    deletedSomething = true;
                }
            }
        }
        return deletedSomething;
    }
}
