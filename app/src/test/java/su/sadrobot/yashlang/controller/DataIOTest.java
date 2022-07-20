package su.sadrobot.yashlang.controller;

import org.json.JSONException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.List;

import su.sadrobot.yashlang.model.PlaylistInfo;

public class DataIOTest {
    public static void main(String args[]) {
        loadPlaylistsFromJSON();
        checkPlaylistsAlive();
    }

    public static void loadPlaylistsFromJSON() {
        System.out.println("***** TEST: loadPlaylistsFromJSON *****");

        // !!!!!!!!!!!!!!!!!!!!!! ЧТОБЫ ЗАПУСТИТЬ СДЕЛАТЬ ВРУЧНУЮ ПРЕДВАРИТЕЛЬНО !!!!!!!!!!!!!!!!!!!!!
        // в build.gradle добавлена запись testImplementation 'org.json:json:20171018'
        // для запуска DataIOTest без андроида на десктопе, чтобы избежать ошибки
        // java.lang.RuntimeException: Stub!
        // at org.json.JSONObject.<init>(JSONObject.java:124)
        // т.к. там используем org.json.JSONObject из android-xx.jar, который за пределами андроида - заглушка
        // https://stackoverflow.com/questions/8982631/error-java-lang-runtimeexception-stub-in-android-with-fitnesse-testing
        // Но просто так всё равно запустить не получится, т.к. android-xx.jar в класпасе
        // будет всегда выше, чем любая библиотека отсюда (в build.gradle)
        // Чтобы запустить и не получить ошибку, нужно внутри файла .idea/modules/app/yashlang.app.iml переместить запись
        // <orderEntry type="jdk" jdkName="Android API 29 Platform" jdkType="Android SDK" />
        // ниже аналогичных записей, относящихся к тестам (конкретно, "org.json:json") вручную
        // и делать это каждый раз при перезапуске среды или при изменении build.gradle здесь
        // https://stackoverflow.com/questions/22863845/how-to-configure-the-order-of-libraries-in-the-classpath-for-android-studio
        // там же по ссылке предлгаюат написать код, который будет это делать автоматом (пока нах)
        // и еще здесь другое решение через какой-то App Engine, которого у меня, скорее всего, нет и нах он нужен
        // https://stackoverflow.com/questions/31698510/can-i-force-the-order-of-dependencies-in-my-classpath-with-gradle/

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

        try {
            final String loadedFileContent = DataIO.loadFromResource("/su/sadrobot/yashlang/data/recommended-playlists.json");

            final List<PlaylistInfo> loadedPlaylists = DataIO.loadPlaylistsFromJSON(loadedFileContent);
            for (final PlaylistInfo plInfo : loadedPlaylists) {
                System.out.println(plInfo.getName() + " " + plInfo.getUrl());

                try {
                    ContentLoader.getInstance().getPlaylistInfo(plInfo.getUrl());
                    System.out.println("..........OK");
                } catch (ExtractionException | IOException e) {
                    System.out.println("..........ERROR: " + e.getMessage());
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }
}
