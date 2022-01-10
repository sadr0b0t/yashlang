package su.sadrobot.yashlang.controller;


/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
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
import android.database.SQLException;

import org.schabi.newpipe.DownloaderTestImpl;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;

import static org.schabi.newpipe.extractor.ServiceList.PeerTube;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;


public class ContentLoader {

    private static ContentLoader _instance;

    static {
        _instance = new ContentLoader();
    }

    public static ContentLoader getInstance() {
        return _instance;
    }

    private ContentLoader() {
    }

    public interface TaskListener {
        void onStart();

        void onFinish();

        void onStatusChange(final String status, final Exception e);
    }

    public static class TaskController {
        private boolean running = false;
        private boolean canceled = false;
        private String statusMsg = "";
        private Exception exception;

        private TaskListener taskListener;

        public void setTaskListener(TaskListener taskListener) {
            this.taskListener = taskListener;
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            boolean changed = this.running != running;
            this.running = running;
            if (this.taskListener != null && changed) {
                if (running) {
                    this.taskListener.onStart();
                } else {
                    this.taskListener.onFinish();
                }
            }
        }

        public boolean isCanceled() {
            return canceled;
        }

        public void cancel() {
            canceled = true;
        }

        public void setStatusMsg(final String msg) {
            setStatusMsg(msg, null);
        }

        public void setStatusMsg(final String msg, final Exception e) {
            this.statusMsg = msg;
            this.exception = e;

            if (this.taskListener != null) {
                taskListener.onStatusChange(msg, e);
            }
        }

        public String getStatusMsg() {
            return statusMsg;
        }

        public Exception getException() {
            return exception;
        }
    }

    /**
     * Ищем каналы и плейлисты по имени. В выдачу сначала добавляем каналы, потом плейлисты.
     *
     * @param sstr
     * @return
     * @throws ExtractionException
     * @throws IOException
     */
    public List<PlaylistInfo> searchYtPlaylists(final String sstr) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java


        final List<PlaylistInfo> playlists = new ArrayList<PlaylistInfo>();
        final List<InfoItem> pageItems = new ArrayList<InfoItem>();

        NewPipe.init(DownloaderTestImpl.getInstance());

        // с YouTube работает только 1й элемент (см YoutubeSearchQueryHandlerFactory.getUrl)
        final List<String> contentFilters = new ArrayList<String>();
        contentFilters.add(YoutubeSearchQueryHandlerFactory.CHANNELS);
        SearchExtractor extractor = YouTube.getSearchExtractor(sstr, contentFilters, "");
        extractor.fetchPage();
        pageItems.addAll(extractor.getInitialPage().getItems());

        // теперь плейлисты
        contentFilters.clear();
        contentFilters.add(YoutubeSearchQueryHandlerFactory.PLAYLISTS);
        extractor = YouTube.getSearchExtractor(sstr, contentFilters, "");
        extractor.fetchPage();
        pageItems.addAll(extractor.getInitialPage().getItems());


        for (final InfoItem infoItem : pageItems) {
            if (infoItem.getInfoType() == InfoItem.InfoType.CHANNEL ||
                    infoItem.getInfoType() == InfoItem.InfoType.PLAYLIST) {
                final String plName = infoItem.getName();
                final String plUrl = infoItem.getUrl();
                final String plThumbUrl = infoItem.getThumbnailUrl();
                final PlaylistInfo.PlaylistType plType;
                if (infoItem.getInfoType() == InfoItem.InfoType.CHANNEL) {
                    plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
                } else {// if(infoItem.getInfoType() == InfoItem.InfoType.PLAYLIST) {
                    plType = PlaylistInfo.PlaylistType.YT_PLAYLIST;
                }

                final PlaylistInfo pl = new PlaylistInfo(plName, plUrl, plThumbUrl, plType);
                playlists.add(pl);
            }
        }

        return playlists;
    }

    public PlaylistInfo getPlaylistInfo(final String plUrl) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java

        // Выкачать список всех видео в канале
        NewPipe.init(DownloaderTestImpl.getInstance());

        final PlaylistInfo.PlaylistType plType = getPlaylistType(plUrl);
        final ListExtractor<StreamInfoItem> extractor = getListExtractor(plUrl);

        // грузим реально страницу здесь
        extractor.fetchPage();

        // заберем со страницы то, что нам нужно
        final String plName = extractor.getName();
        final String plThumbUrl;

        if (extractor instanceof ChannelExtractor) {
            // Хак: выбрать разрмер побольше для иконки YouTube
            // Для PeerTube это просто ничего не сделает
            plThumbUrl = PlaylistUrlUtil.fixYtChannelAvatarSize(((ChannelExtractor) extractor).getAvatarUrl());
        } else if (extractor instanceof PlaylistExtractor) {
            plThumbUrl = ((PlaylistExtractor) extractor).getThumbnailUrl();
        } else {
            // мы сюда никогда не попадем, но ладно
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        return new PlaylistInfo(plName, plUrl, plThumbUrl, plType);
    }

    public long addPlaylist(final Context context, final String plUrl, final TaskController taskController) {
        // https://github.com/TeamNewPipe/NewPipeExtractor
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeChannelExtractorTest.java
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubePlaylistExtractorTest.java


        // 1. Не выбрасываем эксепшены,ошибки ловим через TaskController (иначе не сможем задать корректный статус)
        // 2. Добавляем записи в базу данных по мере загрузки страниц (так должно быть правильнее
        // с точки зрения использоания памяти, хотя кто ее сейчас экономит)
        // 3. Все делаем внутри одной транзакции, в случае неудачной загрузки очередной страницы
        // отменяем всю транзакцию
        // 4. Недостаток такого подхода: код работы с базой данных перемешан с кодом выкачивания
        // информации из сети, вариант "выкачать все записи со всех страниц, а потом все их
        // добавить в базу" выглядит красивее

        // YouTube
        // канал
        //https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA
        // пользователь
        //https://www.youtube.com/user/ClassicCartoonsMedia
        // плейлист
        //https://www.youtube.com/playlist?list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388
        //https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388

        // PeerTube
        // канал
        // https://peer.tube/video-channels/cartoons@vidcommons.org/videos
        // пользователь
        // https://peer.tube/accounts/animation@vidcommons.org/video-channels

        taskController.setRunning(true);

        final AtomicLong plId = new AtomicLong(PlaylistInfo.ID_NONE);

        final VideoDatabase videodb = VideoDatabase.getDbInstance(context);
        try {
            videodb.runInTransaction(new Runnable() {
                @Override
                public void run() {

                    // Выкачать список всех видео в канале

                    // для тестов:
                    // YouTube
                    // канал
                    //final String plUrl = "https://www.youtube.com/channel/UCrlFHstLFNA_HsIV7AveNzA";
                    // пользователь
                    //final String plUrl = "https://www.youtube.com/user/ClassicCartoonsMedia";
                    // плейлист
                    //final String plUrl = "https://www.youtube.com/playlist?list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388";
                    //final String plUrl = "https://www.youtube.com/watch?v=5NXpdxG4j5k&list=PLt6kNtUbjfc_YA0YmmyQP6ByXKa3u4388";

                    // PeerTube
                    // канал
                    // final String plUrl = "https://peer.tube/video-channels/cartoons@vidcommons.org/videos"


                    NewPipe.init(DownloaderTestImpl.getInstance());

                    final PlaylistInfo.PlaylistType plType;
                    final ListExtractor<StreamInfoItem> extractor;
                    try {
                        plType = getPlaylistType(plUrl);
                        extractor = getListExtractor(plUrl);
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    taskController.setStatusMsg("Loading page-1...");
                    // загрузить первую страницу - вот здесь может быть долго
                    try {
                        extractor.fetchPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // выкачивали страницу некоторое время, в течение которого могли успеть
                    // отменить задачу (кнопкой или закрыв экран)
                    if(taskController.isCanceled()) {
                        final RuntimeException e = new RuntimeException("Task canceled");
                        taskController.setStatusMsg("Task canceled", e);
                        throw e;
                    }

                    // имя канала
                    final String plName;
                    final String plThumbUrl;
                    try {
                        plName = extractor.getName();
                        if (extractor instanceof ChannelExtractor) {
                            // Хак: выбрать разрмер побольше для иконки YouTube
                            // Для PeerTube это просто ничего не сделает
                            plThumbUrl = PlaylistUrlUtil.fixYtChannelAvatarSize(((ChannelExtractor) extractor).getAvatarUrl());
                        } else if (extractor instanceof PlaylistExtractor) {
                            plThumbUrl = ((PlaylistExtractor) extractor).getThumbnailUrl();
                        } else {
                            plThumbUrl = "";
                        }
                    } catch (ParsingException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // создадим запись в таблице плейлистов, чтобы был id
                    final long _plId = videodb.playlistInfoDao().insert(
                            new PlaylistInfo(plName, plUrl, plThumbUrl, plType));

                    // качаем список видео страница за страницей
                    // начинаем с первой страницы
                    ListExtractor.InfoItemsPage<StreamInfoItem> nextPage;
                    try {
                        nextPage = extractor.getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    int videoItemCount = 0;
                    int fakeTimestamp = ConfigOptions.FAKE_TIMESTAMP_BLOCK_SIZE;

                    final List<StreamInfoItem> pageItems = new ArrayList<StreamInfoItem>();
                    pageItems.addAll(nextPage.getItems());
                    final List<VideoItem> videoItems = new ArrayList<VideoItem>();

                    // теперь загружаем все ролики - здесь 1я страница
                    // Пропустить несколько первых роликов - для ConfigOptions.DEVEL_MODE_ON
                    int skipItems = 25;
                    for (StreamInfoItem item : pageItems) {
                        if (ConfigOptions.DEVEL_MODE_ON && skipItems > 0) {
                            // пропустим несколько первых, чтобы потестить loadNewPlaylistItems
                            skipItems--;
                        } else {
                            videoItems.add(extractVideoItem(item, _plId, true, fakeTimestamp));
                            fakeTimestamp--;

                            videoItemCount++;
                        }
                    }
                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg("Loading page-1...ok");

                    //System.out.println(nextPage.hasNextPage() + ": " + nextPage.getNextPageUrl());
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся
                    int page_n = 1;
                    while (nextPage.hasNextPage()) {
                        if(taskController.isCanceled()) {
                            final RuntimeException e = new RuntimeException("Task canceled");
                            taskController.setStatusMsg("Task canceled", e);
                            throw e;
                        }

                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg("Loading page-" + page_n + "...");

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = ConfigOptions.LOAD_PAGE_RETRY_COUNT;
                        Exception retryEx = null;
                        while (!done && retryCount > 0) {
                            if(taskController.isCanceled()) {
                                final RuntimeException e = new RuntimeException("Task canceled");
                                taskController.setStatusMsg("Task canceled", e);
                                throw e;
                            }
                            try {
                                // здесь тоже долго
                                nextPage = extractor.getPage(nextPage.getNextPage());
                                done = true;
                            } catch (Exception e) {
                                taskController.setStatusMsg("Loading page-" + page_n + "..." +
                                        "[retry: " + (ConfigOptions.LOAD_PAGE_RETRY_COUNT - retryCount + 1) + "]");
                                retryEx = e;
                                retryCount--;
                            }
                        }

                        if(taskController.isCanceled()) {
                            final RuntimeException e = new RuntimeException("Task canceled");
                            taskController.setStatusMsg("Task canceled", e);
                            throw e;
                        }

                        if (done) {
                            // загрузили страницу
                            pageItems.addAll(nextPage.getItems());

                            for (StreamInfoItem item : pageItems) {
                                videoItems.add(extractVideoItem(item, _plId, true, fakeTimestamp));
                                fakeTimestamp--;
                                videoItemCount++;
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                            taskController.setStatusMsg("Loading page-" + page_n + "...ok");
                        } else {
                            // страница так и не загрузилась, обрываем транзакцию с ошибкой
                            final Exception e = new IOException("Error loading page, retry count exceeded", retryEx);
                            taskController.setStatusMsg("Loading page-" + page_n + "...ERROR", e);
                            throw new RuntimeException("Error loading page, retry count exceeded", e);
                        }
                    }

                    // ставим pId здесь, т.к. здесь уже точно все в порядке
                    plId.set(_plId);
                    taskController.setStatusMsg("Added " + videoItemCount + " items");
                }
            });
        } catch (SQLException e) {
            taskController.setStatusMsg("UNEXPECTED DB problem", e);
            e.printStackTrace();
        } catch (Exception e) {
            // нам все-таки нужно поймать здесь RuntimeException,
            // статус taskController уже выставлен внутри
        }
        taskController.setRunning(false);
        return plId.get();
    }

    /**
     *
     * @param context
     * @param plId
     * @param plUrl
     * @param taskController
     * @return количество добавленных элементов,
     *         0 - если нет новых элементов,
     *        -1 - если ошибка во время проверки или добавления
     */
    public int addPlaylistNewItems(final Context context, final long plId,
                                      final String plUrl, final TaskController taskController) {
        // Загрузить новые видео в плейлисте - видео, добавленные после того,
        // как плейлист был сохранен локально или последний раз обновлялся
        // Алгоритм такой:
        // 1) Мы считаем, что самые новые видео появляются первыми начиная с первой страницы
        // 2) Загружаем все видео страница за страницей до тех пор, пока не встретится
        // видео, которое уже было добавлено в локальную базу для этого плейлиста
        // 3) У видео есть дата, но ее использовать сложнее и накладнее, чем предложенный
        // алгоритм: во-первых, при загрузке информации о видео со страницы плейлиста
        // вместо даты загрузки приходит бесполезная поебень вида "1 год назад", "4 месяца назад" и т.п.;
        // на странице видео есть дата получше (например: "12 авг. 2008 г."),
        // но ее парсить тоже не очень удобно с учетом локализации и сокращений.

        taskController.setRunning(true);

        // здесь небольшой хак, чтобы получить количество добавленных элементов
        // из транзакции в переменную за пределами транзакции
        final int[] videoItemCount = {0};


        final VideoDatabase videodb = VideoDatabase.getDbInstance(context);
        try {
            videodb.runInTransaction(new Runnable() {
                @Override
                public void run() {

                    final List<VideoItem> videoItems = new ArrayList<VideoItem>();

                    NewPipe.init(DownloaderTestImpl.getInstance());
                    final ListExtractor<StreamInfoItem> extractor;
                    try {
                        extractor = getListExtractor(plUrl);
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // список видео страница за страницей

                    // начинаем с первой страницы
                    taskController.setStatusMsg("Loading page-1...");
                    // загрузить первую страницу - вот здесь может быть долго
                    try {
                        extractor.fetchPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error loading playlist", e);
                        throw new RuntimeException(e);
                    }

                    // выкачивали страницу некоторое время, в течение которого могли успеть
                    // отменить задачу (кнопкой или закрыв экран)
                    if(taskController.isCanceled()) {
                        final RuntimeException e = new RuntimeException("Task canceled");
                        taskController.setStatusMsg("Task canceled", e);
                        throw e;
                    }

                    ListExtractor.InfoItemsPage<StreamInfoItem> nextPage;
                    try {
                        nextPage = extractor.getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg("Error", e);
                        throw new RuntimeException(e);
                    }

                    long fakeTimestamp = videodb.videoItemDao().getMaxFakeTimestamp(plId) + ConfigOptions.FAKE_TIMESTAMP_BLOCK_SIZE;
                    final boolean plEnabled = videodb.playlistInfoDao().isEnabled(plId);

                    final List<StreamInfoItem> pageItems = new ArrayList<StreamInfoItem>();
                    pageItems.addAll(nextPage.getItems());


                    boolean foundOld = false;
                    for (StreamInfoItem item : pageItems) {
                        if (videodb.videoItemDao().getByItemUrl(plId, item.getUrl()) == null) {
                            videoItems.add(extractVideoItem(item, plId, plEnabled, fakeTimestamp));
                            fakeTimestamp--;
                            videoItemCount[0]++;
                        } else {
                            foundOld = true;
                            break;
                        }
                    }

                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg("Loading page-1...ok");

                    // если на первой странице все ролики оказались новыми, продолжаем с остальными страницами
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся страницы
                    // или не встретим ролик, который уже был добавлен в базу
                    int page_n = 1;
                    while (!foundOld && nextPage.hasNextPage()) {
                        if(taskController.isCanceled()) {
                            final RuntimeException e = new RuntimeException("Task canceled");
                            taskController.setStatusMsg("Task canceled", e);
                            throw e;
                        }

                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg("Loading page-" + page_n + "...");

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = ConfigOptions.LOAD_PAGE_RETRY_COUNT;
                        while (!done && retryCount > 0) {
                            if(taskController.isCanceled()) {
                                final RuntimeException e = new RuntimeException("Task canceled");
                                taskController.setStatusMsg("Task canceled", e);
                                throw e;
                            }
                            try {
                                nextPage = extractor.getPage(nextPage.getNextPage());
                                done = true;
                            } catch (IOException | ExtractionException e) {
                                retryCount--;
                            }
                        }

                        if(taskController.isCanceled()) {
                            final RuntimeException e = new RuntimeException("Task canceled");
                            taskController.setStatusMsg("Task canceled", e);
                            throw e;
                        }

                        if (done) {
                            // загрузили страницу - проверяем ролики
                            pageItems.addAll(nextPage.getItems());
                            for (StreamInfoItem item : pageItems) {
                                if (videodb.videoItemDao().getByItemUrl(plId, item.getUrl()) == null) {
                                    videoItems.add(extractVideoItem(item, plId, plEnabled, fakeTimestamp));
                                    fakeTimestamp--;
                                    videoItemCount[0]++;
                                } else {
                                    foundOld = true;
                                    break;
                                }
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));

                            taskController.setStatusMsg("Loading page-" + page_n + "...ok");
                        } else {
                            // страница так и не загрузилась,
                            // обрываем транзакцию с ошибкой
                            final Exception e = new IOException("Error loading page, retry count exceeded");
                            taskController.setStatusMsg("Loading page-" + page_n + "...ERROR", e);
                            throw new RuntimeException("Error loading page, retry count exceeded");
                        }
                    }

                    taskController.setStatusMsg("Added " + videoItemCount[0] + " items");
                }
            });
        } catch (SQLException e) {
            videoItemCount[0] = -1;
            taskController.setStatusMsg("UNEXPECTED DB problem", e);
            e.printStackTrace();
        } catch (Exception e) {
            videoItemCount[0] = -1;
            // нам все-таки нужно поймать здесь RuntimeException,
            // статус taskController уже выставлен внутри
        }

        taskController.setRunning(false);
        return videoItemCount[0];
    }

    public VideoItem fetchVideoItem(final String itemUrl) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamExtractorDefaultTest.java
        NewPipe.init(DownloaderTestImpl.getInstance());
        final StreamExtractor extractor = getStreamExtractor(itemUrl);
        extractor.fetchPage();
        return extractVideoItem(extractor);
    }

    /**
     * Потоки видео и аудио для ролика.
     * Потоки видео отсортированы по качеству от лучших к худшим.
     *
     * @param itemUrl
     * @return
     * @throws ExtractionException
     * @throws IOException
     */
    public StreamHelper.StreamSources extractStreams(final String itemUrl) throws ExtractionException, IOException {
        // https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/test/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamExtractorDefaultTest.java
        NewPipe.init(DownloaderTestImpl.getInstance());
        final StreamExtractor extractor = getStreamExtractor(itemUrl);
        extractor.fetchPage();

        final List<VideoStream> _vidStreams = new ArrayList<>();
        // обычно getVideoStreams возвращает всего два варианта потоков: mp4 360p и mp4 720p,
        // иногда еще 144p, в любом случае низкое или среднее качество
        // другие варианты качества (в т.ч. hd - 1080p mp4 или webm) нужно извлекать через getVideoOnlyStreams,
        // а аудио, судя по всему, гнать отдельным потоком
        _vidStreams.addAll(extractor.getVideoStreams());
        _vidStreams.addAll(extractor.getVideoOnlyStreams());

        Collections.sort(_vidStreams, new Comparator<VideoStream>() {
            @Override
            public int compare(final VideoStream vs1, final VideoStream vs2) {
                return Integer.valueOf(vs1.getResolution().replace("p", "")).compareTo(
                        Integer.valueOf(vs2.getResolution().replace("p", "")));
            }
        });
        Collections.reverse(_vidStreams);

        return new StreamHelper.StreamSources(_vidStreams, extractor.getAudioStreams());
    }

    public ListExtractor<StreamInfoItem> getListExtractor(final String plUrl) throws ExtractionException {
        final ListExtractor<StreamInfoItem> extractor;

        if (PlaylistUrlUtil.isYtUser(plUrl) || PlaylistUrlUtil.isYtChannel(plUrl)) {
            extractor = YouTube.getChannelExtractor(plUrl);
        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
            extractor = YouTube.getPlaylistExtractor(plUrl);
        } else if (PlaylistUrlUtil.isPtUser(plUrl) || PlaylistUrlUtil.isPtChannel(plUrl)) {
            extractor = PeerTube.getChannelExtractor(plUrl);
        } else if (PlaylistUrlUtil.isPtPlaylist(plUrl)) {
            extractor = PeerTube.getPlaylistExtractor(plUrl);
        } else {
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        return extractor;
    }

    public PlaylistInfo.PlaylistType getPlaylistType(final String plUrl) throws ExtractionException {
        final PlaylistInfo.PlaylistType plType;

        if (PlaylistUrlUtil.isYtUser(plUrl)) {
            plType = PlaylistInfo.PlaylistType.YT_USER;
        } else if (PlaylistUrlUtil.isYtChannel(plUrl)) {
            plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
            plType = PlaylistInfo.PlaylistType.YT_PLAYLIST;
        } else if (PlaylistUrlUtil.isPtUser(plUrl)) {
            plType = PlaylistInfo.PlaylistType.PT_USER;
        } else if (PlaylistUrlUtil.isPtChannel(plUrl)) {
            plType = PlaylistInfo.PlaylistType.PT_CHANNEL;
        } else if (PlaylistUrlUtil.isPtPlaylist(plUrl)) {
            plType = PlaylistInfo.PlaylistType.PT_PLAYLIST;
        } else {
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        return plType;
    }

    public StreamExtractor getStreamExtractor(final String itemUrl) throws ExtractionException {
        final StreamExtractor extractor;
        if (PlaylistUrlUtil.isYtVideo(itemUrl)) {
            extractor = YouTube.getStreamExtractor(itemUrl);
        } else if (PlaylistUrlUtil.isPtVideo(itemUrl)) {
            extractor = PeerTube.getStreamExtractor(itemUrl);
        } else {
            throw new ExtractionException("Unrecognized video URL: " + itemUrl);
        }
        return extractor;
    }

    /**
     * Создать объект VideoItem из объекта StreamExtractor - на странице видео
     * @param item
     * @return
     * @throws ParsingException
     */
    public VideoItem extractVideoItem(final StreamExtractor item) throws ParsingException {
        final String itemUrl = item.getUrl();
        final String name = item.getName();
        final String uploader = item.getUploaderName();
        //final String date = item.getUploadDate();
        final long viewCount = 0;
        final long viewCountExt = item.getViewCount();
        // поймаем здесь
        // org.schabi.newpipe.extractor.exceptions.ParsingException: Could not get video length
        //final long duration = item.getLength();
        final long duration = 0;
        final String thumbUrl = item.getThumbnailUrl();

        return new VideoItem(PlaylistInfo.ID_NONE, itemUrl, name, uploader, viewCount, viewCountExt, duration, thumbUrl);
    }

    /**
     * @param item
     * @param playlistId    playlist_id value to set
     * @param enabled       enabled flag to set
     * @param fakeTimestamp fake_timestamp value to set
     * @return
     */
    public VideoItem extractVideoItem(final StreamInfoItem item, final long playlistId,
                                       final boolean enabled, final long fakeTimestamp) {
        //final long _playlistId = playlistId;
        final String itemUrl = item.getUrl();
        final String name = item.getName();
        final String uploader = item.getUploaderName();
        //final String date = item.getUploadDate();
        final long viewCount = 0;
        final long viewCountExt = item.getViewCount();
        final long duration = item.getDuration();
        final String thumbUrl = item.getThumbnailUrl();
        //final long _fakeTimestamp = fakeTimestamp;

        return new VideoItem(playlistId, itemUrl, name, uploader, viewCount, viewCountExt, duration, thumbUrl, enabled, fakeTimestamp);
    }

    /**
     * Создать объект VideoItem из объекта StreamInfoItem - ролик на странице списка роликов
     * @param item
     * @return
     */
    public VideoItem extractVideoItem(final StreamInfoItem item) {
        final long playlistId = PlaylistInfo.ID_NONE;
        final String itemUrl = item.getUrl();
        final String name = item.getName();
        final String uploader = item.getUploaderName();
        //final String date = item.getUploadDate();
        final long viewCount = 0;
        final long viewCountExt = item.getViewCount();
        final long duration = item.getDuration();
        final String thumbUrl = item.getThumbnailUrl();

        return new VideoItem(playlistId, itemUrl, name, uploader, viewCount, viewCountExt, duration, thumbUrl);
    }

    public List<VideoItem> extractVideoItems(final List<StreamInfoItem> pageItems) {
        final List<VideoItem> videos = new ArrayList<>();

        // конвертировать в объекты VideoItem
        for (StreamInfoItem item : pageItems) {
            videos.add(extractVideoItem(item));
        }
        return videos;
    }

    public List<VideoItem> extractVideoItems(final List<StreamInfoItem> pageItems, long playlistId) {
        final List<VideoItem> videos = new ArrayList<>();

        // конвертировать в объекты VideoItem
        for (StreamInfoItem item : pageItems) {
            VideoItem videoItem = extractVideoItem(item);
            videoItem.setPlaylistId(playlistId);
            videos.add(videoItem);
        }
        return videos;
    }
}
