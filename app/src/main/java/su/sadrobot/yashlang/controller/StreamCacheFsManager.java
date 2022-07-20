package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ContentLoader.java is part of YaShlang.
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;

/**
 * Всё, что связано с хранением кэша потоков в файловой системе.
 */
public class StreamCacheFsManager {

    private static final String PART_FILE_POSTFIX = ".part";

    public static String getPartFileNameForStream(final StreamCache streamCache) {
        return streamCache.getFileName() + PART_FILE_POSTFIX;
    }

    public static String getFileNameForStream(final StreamCache streamCache) {
        // Варианты имени файла для сохранения:
        // - использовать оригинальное имя потока (нечитаемый уникальный код)
        //   - плюсы: поток как на сервере, имя уже придумано, при необходимости можно их проассоциировать
        //   - минусы: по названию файла не ясно, что там внутри
        //   - главный минус: если имя файла на сервере PeerTube нормальное (не слишком длинный UUID),
        //       то URL потока YouTube - это не имя файла, а адов треш и без расширения файла - имя файла из него не получить
        // для пиртюба по этой схеме: url -> fileName
        // https://open.tube/download/videos/d20f1a66-8b56-491c-a319-c8930e3d0828-360.mp4
        // /download/videos/d20f1a66-8b56-491c-a319-c8930e3d0828-360.mp4
        // для ютюба жопа:
        // https://rr2---sn-wnj045oxu-bvwe.googlevideo.com/videoplayback?expire=1646183357&ei=XW8eYoWtBI2F0u8Po42rmAs&ip=93.157.255.228&id=o-AK1ejLpOvgqlvagv8SjIj3AmwqH-VeOUESg064K3vzbG&itag=248&source=youtube&requiressl=yes&mh=ZR&mm=31%2C29&mn=sn-wnj045oxu-bvwe%2Csn-n8v7znsr&ms=au%2Crdu&mv=m&mvi=2&pl=25&vprv=1&mime=video%2Fwebm&gir=yes&clen=49532134&dur=262.160&lmt=1606093805631587&mt=1646161023&fvip=12&keepalive=yes&fexp=24001373%2C24007246&c=ANDROID&txp=5535434&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cvprv%2Cmime%2Cgir%2Cclen%2Cdur%2Clmt&sig=AOq0QJ8wRgIhAMEUSNNjn_j_BC_RGqhUGMEVr5UGTqje5pffYle1PEmgAiEAiS5R_E6Muj7mZx16-NUtLA8rtEbmPbrkc6P61tHkVgc%3D&lsparams=mh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpl&lsig=AG3C_xAwRQIgbc58ePwQmdCxVMmgXzmRNpXbkP5PLgMXgJAPty8G0EoCIQDfFocruAURAhICWLapVDvnxV2DJB0MVs0oQQ6arS6UfQ%3D%3D
        // /videoplayback?expire=1646183357&ei=XW8eYoWtBI2F0u8Po42rmAs&ip=93.157.255.228&id=o-AK1ejLpOvgqlvagv8SjIj3AmwqH-VeOUESg064K3vzbG&itag=248&source=youtube&requiressl=yes&mh=ZR&mm=31%2C29&mn=sn-wnj045oxu-bvwe%2Csn-n8v7znsr&ms=au%2Crdu&mv=m&mvi=2&pl=25&vprv=1&mime=video%2Fwebm&gir=yes&clen=49532134&dur=262.160&lmt=1606093805631587&mt=1646161023&fvip=12&keepalive=yes&fexp=24001373%2C24007246&c=ANDROID&txp=5535434&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cvprv%2Cmime%2Cgir%2Cclen%2Cdur%2Clmt&sig=AOq0QJ8wRgIhAMEUSNNjn_j_BC_RGqhUGMEVr5UGTqje5pffYle1PEmgAiEAiS5R_E6Muj7mZx16-NUtLA8rtEbmPbrkc6P61tHkVgc%3D&lsparams=mh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpl&lsig=AG3C_xAwRQIgbc58ePwQmdCxVMmgXzmRNpXbkP5PLgMXgJAPty8G0EoCIQDfFocruAURAhICWLapVDvnxV2DJB0MVs0oQQ6arS6UfQ%3D%3D
        //
        // - сгенерировать имя файла, опираясь на название ролика
        //   - плюсы: по названию файла видно, что внутри
        //   - минусы: в названии могут быть любые спец-символы - от них нужно как-то избавляться, это проблема,
        //     сохраненный файл не проассоциировать с потоком на сервере по имени
        // - сгенировать имя через UUID
        //   - минусы: по названию файла не ясно, что там внутри
        //   - плюсы: всё под контролем на телефоне
        //
        // Итого решение: генерировать локальное имя через UUID. Проблема с произвольными символами в имени
        //   довольно заметный гемор без очевидной выгоды. Выгода от читабельных имен - иметь возможность
        //   потом скинуть кэш куда-нибудь за пределы плеера. Но это за пределами его функций. В крайнем случае
        //   можно сделать генератор скриптика, который их всех переименует (в окошке экспорта данных,
        //   например, кстати, да, хорошая идея).

        // Расширение из адреса потока мы тоже не получим - в адресе пока ютюб его просто нет.
        // В адресе потока PeerTube оно есть, но мы все равно решили не сохранять адрес потока в базе,
        // поэтому он здесь будет не доступен и нам все равно хочется иметь универсальный код

        // StreamFormatSuffix должно подойти в качестве расширения (судя по всему, это оно и есть)
        final String fileExt = streamCache.getStreamFormatSuffix().toLowerCase();

        // VideoItem id в начало все равно добавим, т.к. разные ролики могут встречаться в разных плейлистах
        // и, с одной стороны, это хорошо иметь общий кэш для разных роликов. С другой стороны,
        // для этого придется дублировать записи в базе данных как-то для одних и тех же роликов
        // из разных плейлистов и сейчас такая история не подразумевается.
        // Или же увязывать кэш и видео не по id видео, а по адресу ролика.
        final String filename = streamCache.getVideoId() + "-" +
                UUID.randomUUID().toString() + "-" +
                streamCache.getStreamFormat() + "-" +
                streamCache.getStreamRes() +
                "." + fileExt;

        return filename;
    }

    public static File getPartFileForStream(final Context context, final StreamCache streamCache) {
        final File cacheDir = getStreamCacheDir(context);
        final File cacheFile = new File(cacheDir, getPartFileNameForStream(streamCache));
        return cacheFile;
    }

    public static File getFileForStream(final Context context, final StreamCache streamCache) {
        final File cacheDir = getStreamCacheDir(context);
        final File cacheFile = new File(cacheDir, streamCache.getFileName());
        return cacheFile;
    }

    public static long getDownloadedFileSize(final Context context, final StreamCache streamCache) {
        final File cacheFile;

        if(streamCache.isDownloaded()) {
            cacheFile = getFileForStream(context, streamCache);
        } else {
            cacheFile = getPartFileForStream(context, streamCache);
        }

        return cacheFile.exists() ? cacheFile.length() : 0;
    }

    public static File getStreamCacheDir(final Context context) {
        return new File(context.getExternalFilesDir(null), ConfigOptions.STREAM_CACHE_DIR_NAME);
    }

    /**
     * Файлы в каталоге кэша, не ассоциированные ни с одной записью в базе данных.
     * @param context
     * @return
     */
    public static List<File> getUnmanagedFiles(final Context context) {
        final List<File> unmanagedFiles = new ArrayList<>();
        final File cacheDir = getStreamCacheDir(context);
        final File[] allFiles = cacheDir.listFiles();
        for (final File file: allFiles) {
            final String fileName = file.getName();
            if (VideoDatabase.getDbInstance(context).streamCacheDao().findStreamsForFile(fileName).isEmpty()) {
                // записей в базе на этот файл нет
                // проверим еще, может это временный файл
                if (fileName.endsWith(PART_FILE_POSTFIX)) {
                    if (VideoDatabase.getDbInstance(context).streamCacheDao().findStreamsForFile(
                            fileName.substring(0, fileName.lastIndexOf(PART_FILE_POSTFIX))).isEmpty()) {
                        unmanagedFiles.add(file);
                    }
                } else {
                    unmanagedFiles.add(file);
                }
            }
        }
        return unmanagedFiles;
    }

    public static long getStreamCacheFsSize(final Context context) {
        final File cacheDir = getStreamCacheDir(context);

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

    public static long getStreamCacheUnfinishedPartsFsSize(final Context context) {
        final File cacheDir = getStreamCacheDir(context);

        // https://stackoverflow.com/questions/2149785/get-size-of-folder-or-file
        long cachePartSize = 0;
        final File[] cacheFiles = cacheDir.listFiles();
        if (cacheFiles != null) {
            for (final File file : cacheFiles) {
                if (file.isFile() && file.getName().endsWith(PART_FILE_POSTFIX)) {
                    cachePartSize += file.length();
                }
            }
        }
        return  cachePartSize;
    }

    public static long getUnmanagedFilesFsSize(final Context context) {
        final List<File> unmanagedFiles = StreamCacheFsManager.getUnmanagedFiles(context);
        long totalSize = 0;
        for (final File file : unmanagedFiles) {
            totalSize += file.length();
        }
        return totalSize;
    }
}
