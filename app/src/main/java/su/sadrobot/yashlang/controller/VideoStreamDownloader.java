package su.sadrobot.yashlang.controller;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * ConfigurePlaylistActivity.java is part of YaShlang.
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

import org.schabi.newpipe.DownloaderTestImpl;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;

public class VideoStreamDownloader {

    /**
     * Начать загрузку потока в локальный файл.
     *
     * Не заботится о том, загружается ли этот же поток в этот же файл. Проверкой конфликтов
     * такого рода должен заниматься вызывающий код.
     *
     * @param context
     * @param streamCache
     * @param taskController
     */
    public static void downloadStream(final Context context, final StreamCache streamCache, final TaskController taskController) {
        // сохраняем в базе (инфа полученная онлайн, плюс что требуется для запросов):
        // - размер стрима
        //   - до обращения - "-1" - UNDEFINED
        //   - после первого обращения - размер стрима в байтах
        //   - обновлять при повторных обращения (продолжение загрзуки)
        //     - если изменилось, возможно, это повод скачать заново
        // - имя файла - финальное
        // - имя файла - промежуточное (.part) - не сохранять, а генировать
        // - статус: скачано/не скачано
        //   - требуется для запросов к б/д
        //   - можно было бы сверять с размером скачанного файла, но так не получится обращаться к базе
        //     брать недокачанные ролики или выбирать только те ролики, которые полностью выкачаны
        // не сохраняем (то, что можно получить из файловой системы, и не требуется для запросов):
        // - ссылку на стрим (на ютюбе она все равное меняется)
        // - размер скачанного файла
        //   - это не требуется в запросе к б/д, для запросов достаточно статус скачан/не скачан
        // - процент загрузки - вычисляем налету - размер скачанного файла как доля от размера стрима

        taskController.setRunning(true);

        if (!streamCache.isDownloaded()) {
            HttpURLConnection conn = null;
            BufferedInputStream in = null;
            FileOutputStream fos = null;
            BufferedOutputStream bout = null;

            boolean finishedWithError = false;
            Exception ex = null;

            // временный файл для загрузки
            final File cachePartFile = StreamCacheFsManager.getPartFileForStream(context, streamCache);

            // здесь нужно получить заново адрес потока по выбранным параметрам, т.к.
            // ютюбовские ссылки на потоки со временем перестают действовать
            // (один из параметров внутри ссылки так и называется - "expire")
            final String videoItemUrl;
            if (streamCache.getVideoItem() != null) {
                videoItemUrl = streamCache.getVideoItem().getItemUrl();
            } else {
                videoItemUrl = VideoDatabase.getDbInstance(context).videoItemDao().getById(streamCache.getVideoId()).getItemUrl();
            }

            try {
                // некоторые ролики ютюб имеют одинаковую комбинацию формат+разрешение. будем искать
                // по формат+разрешение+тип-потока (в тех роликах, которые видел, одинаковое разрешение и формат
                // встречаются у роликов, один из которых включает поток аудио, а другой не включает)
                final StreamHelper.StreamSources streamSources = ContentLoader.getInstance().extractStreams(videoItemUrl);
                final StreamHelper.StreamInfo streamInfo = StreamHelper.findStreamByParams(streamSources,
                        streamCache.getStreamTypeEnum(), streamCache.getStreamRes(), streamCache.getStreamFormat());

                final String url = streamInfo.getUrl();
                if (url == null) {
                    throw new Exception("Stream URL not found");
                }

                // Проверить: продолжаем загрузку или начинаем заново.
                // Будем считать, что загрузка продолжается, если:
                //   - временный файл с содержимым потока существует и его размер меньше, чем размер потока
                //   - размер потока, адрес которого только что получили, такой же, как был в прошлый раз
                //     (если он не был ранее сохранен, то это в любом случае новая загрузка)
                // Новая загрузка (скачанное содержимое затираем, даже если оно сохранилось), если:
                //   - временный файл с содержимым не существует или его размер равен 0
                //   - в базе не сохранился размер размер старого потока
                //   - размер потока, адрес которого только что получили,  не равен размеру потока, сохраненному в базе
                //   - временный файл существует, но его размер больше, чем размер потока
                // Досрочно завершаем закачку, если:
                //   - существует постоянный файл с содержимым потока, его размер в точности равен размеру потока
                //   - в этом случае приводим состояние бызы в порядок (в штатном режиме мы бы сюда не попали):
                //     - удаляем временный файл (если он есть)
                //     - выставляем статус записи DONE

                // сколько уже скачано
                long downloadedSize = cachePartFile.exists() ? cachePartFile.length() : 0;

                // нужно получить длину потока через отдельное подключение, т.к. если для подключения
                // выставить режим докачки, то длина потока будет не поток целиком, а оставшийся
                // размер с места, с которого продолжили
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(ConfigOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS);
                conn.setRequestMethod("HEAD");
                final long contentLength = conn.getContentLength();
                conn.disconnect();
                if (contentLength <= 0) {
                    // маловероятно, но для порядка проверим
                    throw new Exception("Stream content length <= 0: " + contentLength);
                }

                // подключаемся еще раз - уже для закачки
                conn = (HttpURLConnection) new URL(url).openConnection();

                //  настройки таймаутов
                // https://github.com/sadr0b0t/yashlang/issues/132
                // https://stackoverflow.com/questions/45199702/httpurlconnection-timeout-defaults
                // https://github.com/TeamNewPipe/NewPipe/blob/v0.23.1/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java
                conn.setConnectTimeout(ConfigOptions.DEFAULT_CONNECT_TIMEOUT_MILLIS);
                conn.setReadTimeout(ConfigOptions.DEFAULT_READ_TIMEOUT_MILLIS);

                // правильные настройки подключения для режима докачки (см в коде NewPipe):
                // https://github.com/TeamNewPipe/NewPipe/blob/v0.23.1/app/src/main/java/us/shandian/giga/get/DownloadMission.java
                // если что-то из этого не выставить, сервер будет плохо реагировать на параметр Range,
                // который необходим для докачки (будет возвращать размер потока 0)
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", DownloaderTestImpl.USER_AGENT);
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Encoding", "*");

                if (downloadedSize > 0) {
                    // режим докачки
                    conn.setRequestProperty("Range", "bytes=" + downloadedSize + "-");
                }

                // TODO: выводить статусы и отображать:
                // запрос размера, почему качаем заново (размер файла изменился на сервере, например) и т.п.
                // см. в коментах к ютюб-дл подвох с ютюбом - он иногда
                // любит менять размер файл на несколько байт - автор ютюбдл это как-то обходит, забивая на
                // несколько байт, мы, пока, пожалуй не будем это обрабатывать - до тех пор, пока не стало
                // проблемой по крайней мере.
                // в целом, глядя на исходники ютюбдл, там логика очень похожа на то, что я здесь
                // наизобретал (если размер файла изменился - качать заново, например)

                // проверка: если условие не выполнено, то уже всё скачано, но по какой-то причине
                // не переименовали временный файл и не обновили состояние в базе (такое не должно
                // было бы произойти, но технически это возможно)
                // в этом случае ничего не делаем - файл будет переименован и остояние в базе
                // обновится дальше
                if (!(streamCache.getStreamSize() != StreamCache.STREAM_SIZE_UNKNOWN &&
                        streamCache.getStreamSize() == contentLength &&
                        cachePartFile.exists() && cachePartFile.length() == contentLength)) {

                    // проверим: качаем заново или продолжаем закачку
                    final boolean startFromScratch;

                    if (streamCache.getStreamSize() != StreamCache.STREAM_SIZE_UNKNOWN &&
                            streamCache.getStreamSize() == contentLength &&
                            cachePartFile.exists() && cachePartFile.length() < contentLength) {
                        startFromScratch = false;
                    } else {
                        startFromScratch = true;
                    }

                    // обновим размер потока в базе
                    if (streamCache.getStreamSize() == StreamCache.STREAM_SIZE_UNKNOWN || streamCache.getStreamSize() != contentLength) {
                        streamCache.setStreamSize(contentLength);
                        VideoDatabase.getDbInstance(context).streamCacheDao().setStreamSize(streamCache.getId(), contentLength);
                    }

                    if (!cachePartFile.exists()) {
                        // Если файла нет, создать. Проверять на существование не будем, т.к. если
                        // не создался, то эксепшен вылетит в следующих строчках при попытке записаать.
                        // Размер файла обновлять тоже нет смысла - если файла не было выше, то он
                        // уже равен нулю.
                        cachePartFile.getParentFile().mkdirs();
                        cachePartFile.createNewFile();
                    }

                    taskController.setProgressMax(streamCache.getStreamSize());
                    taskController.setProgress(downloadedSize);

                    in = new BufferedInputStream(conn.getInputStream());
                    fos = startFromScratch ? new FileOutputStream(cachePartFile) : new FileOutputStream(cachePartFile, true);
                    bout = new BufferedOutputStream(fos, 1024);
                    final byte[] data = new byte[1024];
                    int x;
                    while (!taskController.isCanceled() && (x = in.read(data, 0, 1024)) >= 0) {
                        // todo: здесь можно следить за оставшимся свободным местом и завершать загрузку
                        // немного заранее, а не при ошибке записи на переполненный носитель
                        bout.write(data, 0, x);
                        downloadedSize += x;
                        // обновление прогресса будет происходить довольно часто, поэтому
                        // если обновлять графический интерфейс по слушателю этого события,
                        // при закачке во всей системе будут происходить адовы тормоза
                        taskController.setProgress(downloadedSize);
                    }
                    bout.flush();
                }
            } catch (MalformedURLException e) {
                finishedWithError = true;
                ex = e;
            } catch (IOException e) {
                finishedWithError = true;
                ex = e;
            } catch (ExtractionException e) {
                finishedWithError = true;
                ex = e;
            } catch (Exception e) {
                finishedWithError = true;
                ex = e;
            } finally {
                if (bout != null) {
                    try {
                        bout.close();
                    } catch (IOException e) {
                        // ошибка при закрытии стримов - какая-то история из ряда вон выходящая
                        // при этом файл уже скачали, значит статус закачки в ошибку можно не превращать,
                        // а если попали сюда из блока catch, то он уже и так стоит
                        e.printStackTrace();
                    }
                }
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        // ошибка при закрытии стримов - какая-то история из ряда вон выходящая
                        // при этом файл уже скачали, значит статус закачки в ошибку можно не превращать,
                        // а если попали сюда из блока catch, то он уже и так стоит
                        e.printStackTrace();
                    }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ошибка при закрытии стримов - какая-то история из ряда вон выходящая
                        // при этом файл уже скачали, значит статус закачки в ошибку можно не превращать,
                        // а если попали сюда из блока catch, то он уже и так стоит
                        e.printStackTrace();
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                    if (conn.getErrorStream() != null) {
                        // Сюда попадаем, если connection.getInputStream() вылетает с эксепшеном
                        // (на сервере нет видео, которое пытаемся скачать)
                        // см. комент в VideoThumbManager.loadBitmap
                        try {
                            conn.getErrorStream().close();
                        } catch (IOException e) {
                            // ошибка при закрытии стримов - какая-то история из ряда вон выходящая
                            // при этом файл уже скачали, значит статус закачки в ошибку можно не превращать,
                            // а если попали сюда из блока catch, то он уже и так стоит
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (finishedWithError) {
                taskController.setStatusMsg(ex.getMessage(), ex);
            } else {
                // штатно вышли из цикла while: завершили загрузку или пользователь прервал операцию
                // разместим этот код здесь, а не сразу после while, чтобы сначала закрыть
                // все стримы, в т.ч. стрим вывода в файл, а уже потом переименовывать файл
                if (!taskController.isCanceled()) {
                    if (cachePartFile.renameTo(StreamCacheFsManager.getFileForStream(context, streamCache))) {
                        VideoDatabase.getDbInstance(context).streamCacheDao().setDownloaded(
                                streamCache.getId(), streamCache.getVideoId(), true);
                        streamCache.setDownloaded(true);
                    } else {
                        // не получилось переименовать временный скачанный файл
                        // обычно такое не должно происходить
                        taskController.setStatusMsg("Could not rename cache file", new Exception("Could not rename cache file"));
                    }
                }
            }
        }
        taskController.setRunning(false);
    }
}
