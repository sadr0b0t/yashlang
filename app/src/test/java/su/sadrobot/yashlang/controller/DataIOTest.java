package su.sadrobot.yashlang.controller;

import org.junit.Test;

import org.json.JSONException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;

public class DataIOTest {

    @Test
    public void runAll() {
        loadPlaylistsFromJSON();
        checkPlaylistsAlive();
    }

    public static void loadPlaylistsFromJSON() {
        System.out.println("***** TEST: loadPlaylistsFromJSON *****");

        // src/main/res/raw vs src/main/assets
        // https://medium.com/mobile-app-development-publication/assets-or-resource-raw-folder-of-android-5bdc042570e0

        // ресурсы из res отсюда можно заггружать просто так
        // ресурсы из assets просто так уже не зарзужатся (прилетает null)
        // чтобы загружал, app/build.gradle добавили запись
        //    android {
        //        sourceSets {
        //            main {
        //                resources {
        //                    srcDirs 'src/main/assets'
        //                }
        //            }
        //        }
        //    }
        // (по ссылке немного не так настроено, но хоть какая подсказка, жесть, пипец, часа 4 просрал)
        // https://blog.codetitans.pl/post/howto-assets-android-unittest/

        // (для того, чтобы ресурс загружался внутри приложения, такая конструкция, возможно, не нужна,
        // но это не точно)

        // пути для ресурсов из res будут в духе:
        // /home/user/Android/Sdk/platforms/android-29/data/res/values/strings.xml
        // пути для рекурсов assets будут в духе:
        // /home/user/devel/yashlang/app/build/intermediates/java_res/debug/out/abc.json

        // интересно, что в первом случае в начале нужен слеш, а во втором - не нужен:
        //System.out.println(DataIOTest.class.getResource("/values/strings.xml"));
        //System.out.println(DataIOTest.class.getClassLoader().getResource("values/strings.xml"));
        //System.out.println(DataIOTest.class.getResource("/abc.json"));
        //System.out.println(DataIOTest.class.getClassLoader().getResource("abc.json"));

        try {
            final String loadedFileContent = DataIO.loadFromResource("/su/sadrobot/yashlang/data/recommended-playlists.json");

            final List<PlaylistInfo> loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);
            for (final PlaylistInfo plInfo : loadedPlaylists) {
                System.out.println(plInfo.getName() + " " + plInfo.getUrl() + " " + PlaylistInfo.PlaylistType.valueOf(plInfo.getType()).name());
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkPlaylistsAlive() {
        System.out.println("***** TEST: checkPlaylistsAlive *****");

        boolean allPlaylistsOk = true;

        try {
            final String loadedFileContent = DataIO.loadFromResource("/su/sadrobot/yashlang/data/recommended-playlists.json");

            final List<PlaylistInfo> loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);
            for (final PlaylistInfo plInfo : loadedPlaylists) {
                System.out.println(plInfo.getName() + " " + plInfo.getUrl());

                try {
                    ContentLoader.getInstance().getPlaylistInfo(plInfo.getUrl());
                    System.out.println("..........OK");
                } catch (ExtractionException | IOException e) {
                    allPlaylistsOk = false;
                    System.out.println("..........ERROR: " + e.getMessage());
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }

        System.out.println("--------");
        if (allPlaylistsOk) {
            System.out.println("ОК: ВСЕ плейлисты в порядке");
        } else {
            System.out.println("ЕСТЬ ОШИБКИ: с некоторыми плейлистами есть проблемы (см. лог выше)");
        }
        System.out.println("--------");
    }
}
