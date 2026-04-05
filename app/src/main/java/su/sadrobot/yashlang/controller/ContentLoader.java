package su.sadrobot.yashlang.controller;


/*
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
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
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.services.peertube.linkHandler.PeertubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
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
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.util.PlaylistUrlUtil;

import static org.schabi.newpipe.extractor.ServiceList.PeerTube;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;


public class ContentLoader {

    private static final ContentLoader _instance;

    static {
        _instance = new ContentLoader();
    }

    public static ContentLoader getInstance() {
        return _instance;
    }

    private ContentLoader() {
    }

    public static final String TASK_CONTROLLER_ATTR_PLAYLIST_ID = "TASK_CONTROLLER_ATTR_PLAYLIST_ID";

    public class InterruptTransactionException extends RuntimeException {
        public InterruptTransactionException(final String message, final Exception cause) {
            super(message, cause);
        }

        public InterruptTransactionException(final String message) {
            super(message);
        }

        public InterruptTransactionException(final Exception cause) {
            super(cause);
        }
    }

    public class PlaylistExtractorBundle {
        private ChannelExtractor channelExtractor;
        private PlaylistExtractor playlistExtractor;
        private ListExtractor<StreamInfoItem> streamInfoItemListExtractor;

        PlaylistExtractorBundle(ChannelExtractor channelExtractor,
                            ListExtractor<StreamInfoItem> streamInfoItemListExtractor) {
            this.channelExtractor = channelExtractor;
            this.streamInfoItemListExtractor = streamInfoItemListExtractor;
        }

        PlaylistExtractorBundle(PlaylistExtractor playlistExtractor) {
            this.playlistExtractor = playlistExtractor;
            this.streamInfoItemListExtractor = playlistExtractor;
        }

        public ChannelExtractor getChannelExtractor() {
            return channelExtractor;
        }

        public PlaylistExtractor getPlaylistExtractor() {
            return playlistExtractor;
        }
        public ListExtractor<StreamInfoItem> getStreamInfoItemListExtractor() {
            return streamInfoItemListExtractor;
        }
    }

    /**
     * Выбрать иконку с максимальным размером (будем ориентироваться на ширину)
     * @param thumbs
     * @return
     */
    private Image findBestThumb(final List<Image> thumbs) {
        Image plThumb = null;
        for (final Image thumb : thumbs) {
            if (plThumb == null) {
                plThumb = thumb;
            } else if (plThumb.getWidth() < thumb.getWidth()) {
                plThumb = thumb;
            }
        }
        return plThumb;
    }

    /**
     * Выбрать иконку с максимальным размером (будем ориентироваться на ширину)
     * @param thumbs
     * @return
     */
    private String findBestThumbUrl(final List<Image> thumbs) {
        final Image plThumb = findBestThumb(thumbs);
        return plThumb != null ? plThumb.getUrl() : "";
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


        final List<PlaylistInfo> playlists = new ArrayList<>();
        final List<InfoItem> pageItems = new ArrayList<>();

        NewPipe.init(DownloaderTestImpl.getInstance());

        // с YouTube работает только 1й элемент (см YoutubeSearchQueryHandlerFactory.getUrl)
        final List<String> contentFilters = new ArrayList<>();
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
                final String plThumbUrl = findBestThumb(infoItem.getThumbnails()).getUrl();
                final PlaylistInfo.PlaylistType plType;
                if (infoItem.getInfoType() == InfoItem.InfoType.CHANNEL) {
                    plType = PlaylistInfo.PlaylistType.YT_CHANNEL;
                } else {// if (infoItem.getInfoType() == InfoItem.InfoType.PLAYLIST) {
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
        final PlaylistExtractorBundle extractorBundle = getListExtractor(plUrl);

        // заберем со страницы то, что нам нужно
        final String plName;
        final String plThumbUrl;
        final List<Image> plThumbs;

        if (extractorBundle.getChannelExtractor() != null) {
            // грузим реально страницу здесь
            extractorBundle.getChannelExtractor().fetchPage();

            plName = extractorBundle.getChannelExtractor().getName();
            plThumbs = extractorBundle.getChannelExtractor().getAvatars();
        } else if (extractorBundle.getPlaylistExtractor() != null) {
            // грузим реально страницу здесь
            extractorBundle.getPlaylistExtractor().fetchPage();

            plName = extractorBundle.getPlaylistExtractor().getName();
            plThumbs = extractorBundle.getPlaylistExtractor().getThumbnails();
        } else {
            // мы сюда никогда не попадем, но ладно
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        // Выбрать иконку с максимальным размером (будем ориентироваться на ширину)
        plThumbUrl = findBestThumbUrl(plThumbs);

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

        if (taskController.isCanceled()) {
            // отменили задачу до того, как начали
            return PlaylistInfo.ID_NONE;
        }

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
                    final PlaylistExtractorBundle extractorBundle;
                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_init_playlist_loader));
                    try {
                        plType = getPlaylistType(plUrl);
                        extractorBundle = getListExtractor(plUrl);
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                        throw new InterruptTransactionException(e);
                    }

                    // дадим возможность другим потокам время от времени тоже обращаться к базе
                    videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n)
                            .replace("%s", "1"));

                    // Сначала нужно загрузить главную страницу канала,
                    // чтобы получить из нее имя канала и иконку
                    // Это может занять некоторое время
                    final String plName;
                    final String plThumbUrl;
                    final List<Image> plThumbs;
                    try {
                        if (extractorBundle.getChannelExtractor() != null) {
                            // грузим реально страницу канала здесь - здесь может быть долго
                            extractorBundle.getChannelExtractor().fetchPage();

                            plName = extractorBundle.getChannelExtractor().getName();
                            plThumbs = extractorBundle.getChannelExtractor().getAvatars();
                        } else if (extractorBundle.getPlaylistExtractor() != null) {
                            // грузим реально страницу канала здесь - здесь может быть долго
                            extractorBundle.getPlaylistExtractor().fetchPage();

                            plName = extractorBundle.getPlaylistExtractor().getName();
                            plThumbs = extractorBundle.getPlaylistExtractor().getThumbnails();
                        } else {
                            // мы сюда никогда не попадем, но ладно
                            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
                        }
                    } catch (IOException | ExtractionException e) {
                        // если в процессе выкачивания пользователь нажал отменить задание,
                        // статус отмены задания приоритетнее произошедшей ошибки
                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        } else {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                            throw new InterruptTransactionException(e);
                        }
                    }

                    // Выбрать иконку с максимальным размером (будем ориентироваться на ширину)
                    plThumbUrl = findBestThumbUrl(plThumbs);

                    // создадим запись в таблице плейлистов, чтобы был id
                    final long _plId = videodb.playlistInfoDao().insert(
                            new PlaylistInfo(plName, plUrl, plThumbUrl, plType));

                    // выкачивали страницу некоторое время, в течение которого
                    // могли успеть отменить задачу
                    if (taskController.isCanceled()) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                        throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                    }

                    // дадим возможность другим потокам время от времени тоже обращаться к базе
                    videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                    // Теперь загрузим первую страницу со списком роликов - вот здесь может быть долго
                    // Для каналов список страница с информацией о канале и страница со списком роликов - разные страницы,
                    // поэтому страницу со списком роликов нужно загрузить
                    // Для плейлистов страница с информацией о плейлисте и страница со списком роликов - одна и та же страница,
                    // поэтому для плейлиста первую страницу со списком роликов еще раз не грузим
                    if (extractorBundle.getChannelExtractor() != null) {
                        try {
                            extractorBundle.getStreamInfoItemListExtractor().fetchPage();
                        } catch (IOException | ExtractionException e) {
                            // если в процессе выкачивания пользователь нажал отменить задание,
                            // статус отмены задания приоритетнее произошедшей ошибки
                            if (taskController.isCanceled()) {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                                throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                            } else {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                                throw new InterruptTransactionException(e);
                            }
                        }

                        // выкачивали страницу некоторое время, в течение которого
                        // могли успеть отменить задачу
                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        }

                        // дадим возможность другим потокам время от времени тоже обращаться к базе
                        videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();
                    }

                    // Теперь качаем список видео и добавляем в базу страница за страницей
                    // начинаем с первой страницы (она уже выкачана выше)
                    ListExtractor.InfoItemsPage<? extends InfoItem> nextPage;
                    try {
                        nextPage = extractorBundle.getStreamInfoItemListExtractor().getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                        throw new InterruptTransactionException(e);
                    }

                    int videoItemCount = 0;
                    int fakeTimestamp = ConfigOptions.FAKE_TIMESTAMP_BLOCK_SIZE;

                    final List<InfoItem> pageItems = new ArrayList<>();
                    pageItems.addAll(nextPage.getItems());
                    final List<VideoItem> videoItems = new ArrayList<>();

                    // теперь загружаем все ролики - здесь 1-я страница
                    // Пропустить несколько первых роликов - для ConfigOptions.DEVEL_MODE_ON
                    int skipItems = 25;
                    for (final InfoItem item : pageItems) {
                        if (ConfigOptions.DEVEL_MODE_ON && skipItems > 0) {
                            // пропустим несколько первых, чтобы потестить loadNewPlaylistItems
                            skipItems--;
                        } else {
                            videoItems.add(extractVideoItem((StreamInfoItem) item, _plId, true, fakeTimestamp));
                            fakeTimestamp--;

                            videoItemCount++;
                        }
                    }
                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_ok)
                            .replace("%s", "1"));

                    //System.out.println(nextPage.hasNextPage() + ": " + nextPage.getNextPageUrl());
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся
                    int page_n = 1;
                    while (nextPage.hasNextPage()) {
                        // дадим возможность другим потокам время от времени тоже обращаться к базе
                        videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        }

                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n)
                                .replace("%s", String.valueOf(page_n)));

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = ConfigOptions.LOAD_PAGE_RETRY_COUNT;
                        Exception retryEx = null;
                        while (!done && retryCount > 0) {
                            if (taskController.isCanceled()) {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                                throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                            }

                            // дадим возможность другим потокам время от времени тоже обращаться к базе
                            videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                            try {
                                // здесь тоже долго
                                nextPage = extractorBundle.getStreamInfoItemListExtractor().getPage(nextPage.getNextPage());
                                done = true;
                            } catch (Exception e) {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_retry_c)
                                        .replace("%s", String.valueOf(page_n))
                                        .replace("%c", String.valueOf(ConfigOptions.LOAD_PAGE_RETRY_COUNT - retryCount + 1)));
                                retryEx = e;
                                retryCount--;

                                // сделаем паузу перед следующей попыткой
                                // (но сначала еще раз проверим, не было ли отмены)
                                if (taskController.isCanceled()) {
                                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                                    throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                                }
                                try {
                                    Thread.sleep(ConfigOptions.LOAD_PAGE_RETRY_TIMEOUT_MILLIS);
                                } catch (InterruptedException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }

                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        }

                        if (done) {
                            // загрузили страницу
                            pageItems.addAll(nextPage.getItems());

                            for (final InfoItem item : pageItems) {
                                videoItems.add(extractVideoItem((StreamInfoItem)item, _plId, true, fakeTimestamp));
                                fakeTimestamp--;
                                videoItemCount++;
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_ok)
                                    .replace("%s", String.valueOf(page_n)));
                        } else {
                            // страница так и не загрузилась, обрываем транзакцию с ошибкой
                            final Exception e = new IOException(context.getString(R.string.task_status_msg_error_loading_playlist_page_retry_count_exceeded), retryEx);
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_error)
                                            .replace("%s", String.valueOf(page_n)), e);
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_error_loading_playlist_page_retry_count_exceeded), e);
                        }
                    }

                    // ставим plId здесь, т.к. здесь уже точно все в порядке
                    plId.set(_plId);
                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_playlist_added_n_items)
                            .replace("%s", String.valueOf(videoItemCount)));
                }
            });
        } catch (final InterruptTransactionException e) {
            // штатное прерывание транзакции из-за ошибки в процессе выполнения или отмены
            // (если была ошибка, то она уже поймана внутри)
            // статус taskController уже выставлен внутри
        } catch (final SQLException e) {
            // нештатная ошибка обращения к базе данных внутри транзакции
            taskController.setStatusMsg(context.getString(R.string.task_status_msg_unexpected_db_problem), e);
            e.printStackTrace();
        }  catch (final Exception e) {
            // нештатная ошибка внутри транзакции
            taskController.setStatusMsg(context.getString(R.string.task_status_msg_unexpected_import_playlist_problem), e);
            e.printStackTrace();
        }
        taskController.setAttr(TASK_CONTROLLER_ATTR_PLAYLIST_ID, plId.get());
        taskController.setRunning(false);
        return plId.get();
    }

    /**
     *
     * @param context
     * @param playlistId
     * @param taskController
     * @return количество добавленных элементов,
     *         0 - если нет новых элементов,
     *        -1 - если ошибка во время проверки или добавления
     */
    public int addPlaylistNewItems(final Context context, final long playlistId,
                                      final TaskController taskController) {
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

        if (taskController.isCanceled()) {
            // отменили задачу до того, как начали
            return -1;
        }

        taskController.setRunning(true);

        // здесь небольшой хак, чтобы получить количество добавленных элементов
        // из транзакции в переменную за пределами транзакции
        final int[] videoItemCount = {0};


        final VideoDatabase videodb = VideoDatabase.getDbInstance(context);
        // здесь false
        // System.out.println("BEFORE TRANSACTION: isDbLockedByCurrentThread=" + videodb.getOpenHelper().getWritableDatabase().isDbLockedByCurrentThread());
        try {
            videodb.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    // здесь true
                    //System.out.println("INSIDE TRANSACTION: isDbLockedByCurrentThread=" + videodb.getOpenHelper().getWritableDatabase().isDbLockedByCurrentThread());

                    final PlaylistInfo playlistInfo = videodb.playlistInfoDao().getById(playlistId);

                    final List<VideoItem> videoItems = new ArrayList<>();

                    NewPipe.init(DownloaderTestImpl.getInstance());
                    final PlaylistExtractorBundle extractorBundle;
                    try {
                        extractorBundle = getListExtractor(playlistInfo.getUrl());
                    } catch (ExtractionException e) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                        throw new InterruptTransactionException(e);
                    }

                    // дадим возможность другим потокам время от времени тоже обращаться к базе
                    videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                    // список видео страница за страницей
                    // начинаем с первой страницы
                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n)
                            .replace("%s", "1"));
                    // загрузить первую страницу - вот здесь может быть долго
                    try {
                        extractorBundle.getStreamInfoItemListExtractor().fetchPage();
                    } catch (IOException | ExtractionException e) {
                        // если в процессе выкачивания пользователь нажал отменить задание,
                        // статус отмены задания приоритетнее произошедшей ошибки
                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        } else {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                            throw new InterruptTransactionException(e);
                        }
                    }

                    // выкачивали страницу некоторое время, в течение которого
                    // могли успеть отменить задачу
                    if (taskController.isCanceled()) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                        throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                    }

                    // дадим возможность другим потокам время от времени тоже обращаться к базе
                    videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                    ListExtractor.InfoItemsPage<StreamInfoItem> nextPage;
                    try {
                        nextPage = extractorBundle.getStreamInfoItemListExtractor().getInitialPage();
                    } catch (IOException | ExtractionException e) {
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_error_loading_playlist), e);
                        throw new InterruptTransactionException(e);
                    }

                    long fakeTimestamp = videodb.videoItemDao().getMaxFakeTimestamp(playlistId) + ConfigOptions.FAKE_TIMESTAMP_BLOCK_SIZE;
                    final boolean plEnabled = videodb.playlistInfoDao().isEnabled(playlistId);

                    final List<StreamInfoItem> pageItems = new ArrayList<>();
                    pageItems.addAll(nextPage.getItems());

                    boolean foundOld = false;
                    for (final StreamInfoItem item : pageItems) {
                        // дадим возможность другим потокам время от времени тоже обращаться к базе
                        videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();
                        if (videodb.videoItemDao().getByItemUrl(playlistId, item.getUrl()) == null) {
                            videoItems.add(extractVideoItem(item, playlistId, plEnabled, fakeTimestamp));
                            fakeTimestamp--;
                            videoItemCount[0]++;
                        } else {
                            foundOld = true;
                            break;
                        }
                    }

                    // сохраняем страницу в базу
                    videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));
                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_ok)
                            .replace("%s", "1"));

                    // если на первой странице все ролики оказались новыми, продолжаем с остальными страницами
                    // загружать по порядку остальные страницы до тех пор, пока не закончатся страницы
                    // или не встретим ролик, который уже был добавлен в базу
                    int page_n = 1;
                    while (!foundOld && nextPage.hasNextPage()) {
                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        }

                        // дадим возможность другим потокам время от времени тоже обращаться к базе
                        videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                        pageItems.clear();
                        videoItems.clear();

                        page_n++;
                        taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n)
                                .replace("%s", String.valueOf(page_n)));

                        boolean done = false;
                        // количество повторных попыток, т.к. гугл может (и будет) время от времени возвращать
                        // ошибку вместо страницы
                        int retryCount = ConfigOptions.LOAD_PAGE_RETRY_COUNT;
                        Exception retryEx = null;
                        while (!done && retryCount > 0) {
                            if (taskController.isCanceled()) {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                                throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                            }

                            // дадим возможность другим потокам время от времени тоже обращаться к базе
                            videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                            // дадим возможность другим потокам время от времени тоже обращаться к базе
                            videodb.getOpenHelper().getWritableDatabase().yieldIfContendedSafely();

                            try {
                                nextPage = extractorBundle.getStreamInfoItemListExtractor().getPage(nextPage.getNextPage());
                                done = true;
                            } catch (IOException | ExtractionException e) {
                                taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_retry_c)
                                        .replace("%s", String.valueOf(page_n))
                                        .replace("%c", String.valueOf(ConfigOptions.LOAD_PAGE_RETRY_COUNT - retryCount + 1)));

                                retryEx = e;
                                retryCount--;

                                // сделаем паузу перед следующей попыткой
                                // (но сначала еще раз проверим, не было ли отмены)
                                if (taskController.isCanceled()) {
                                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                                    throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                                }
                                try {
                                    Thread.sleep(ConfigOptions.LOAD_PAGE_RETRY_TIMEOUT_MILLIS);
                                } catch (InterruptedException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        }

                        if (taskController.isCanceled()) {
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_task_canceled));
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_task_canceled));
                        }

                        if (done) {
                            // загрузили страницу - проверяем ролики
                            pageItems.addAll(nextPage.getItems());
                            for (final StreamInfoItem item : pageItems) {
                                if (videodb.videoItemDao().getByItemUrl(playlistId, item.getUrl()) == null) {
                                    videoItems.add(extractVideoItem(item, playlistId, plEnabled, fakeTimestamp));
                                    fakeTimestamp--;
                                    videoItemCount[0]++;
                                } else {
                                    foundOld = true;
                                    break;
                                }
                            }
                            // сохраняем страницу в базу
                            videodb.videoItemDao().insertAll(videoItems.toArray(new VideoItem[videoItems.size()]));

                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_ok)
                                    .replace("%s", String.valueOf(page_n)));
                        } else {
                            // страница так и не загрузилась,
                            // обрываем транзакцию с ошибкой
                            final Exception e = new IOException(context.getString(R.string.task_status_msg_error_loading_playlist_page_retry_count_exceeded), retryEx);
                            taskController.setStatusMsg(context.getString(R.string.task_status_msg_loading_playlist_page_n_error)
                                    .replace("%s", String.valueOf(page_n)), e);
                            throw new InterruptTransactionException(context.getString(R.string.task_status_msg_error_loading_playlist_page_retry_count_exceeded));
                        }
                    }

                    taskController.setStatusMsg(context.getString(R.string.task_status_msg_playlist_added_n_items)
                            .replace("%s", String.valueOf(videoItemCount[0])));
                }
            });
        } catch (final InterruptTransactionException e) {
            videoItemCount[0] = -1;
            // штатное прерывание транзакции из-за ошибки в процессе выполнения или отмены
            // (если была ошибка, то она уже поймана внутри)
            // статус taskController уже выставлен внутри
        } catch (final SQLException e) {
            videoItemCount[0] = -1;
            // нештатная ошибка обращения к базе данных внутри транзакции
            taskController.setStatusMsg(context.getString(R.string.task_status_msg_unexpected_db_problem), e);
            e.printStackTrace();
        }  catch (final Exception e) {
            videoItemCount[0] = -1;
            // нештатная ошибка внутри транзакции
            taskController.setStatusMsg(context.getString(R.string.task_status_msg_unexpected_import_playlist_problem), e);
            e.printStackTrace();
        }

        // здесь false
        //System.out.println("AFTER TRANSACTION: isDbLockedByCurrentThread=" + videodb.getOpenHelper().getWritableDatabase().isDbLockedByCurrentThread());

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
                // отсортируем по качеству (численно) по убыванию
                // (для порядка можно еще и по формату - по алфавиту, но пока вроде незачем)
                return Integer.valueOf(vs1.getResolution().replaceAll("p.*", "")).compareTo(
                        Integer.valueOf(vs2.getResolution().replaceAll("p.*", "")));
            }
        });
        Collections.reverse(_vidStreams);

        return new StreamHelper.StreamSources(
                StreamHelper.toStreamInfoListFromVideoList(_vidStreams),
                StreamHelper.toStreamInfoListFromAudioList(extractor.getAudioStreams()));
    }

    public PlaylistExtractorBundle getListExtractor(final String plUrl) throws ExtractionException {
        // В NewPipeExtractor v0.23.0 ролики в канале нужно получать не через
        // ChannelExtractor, а через ChannedTabExtractor, который возвращает не
        // ListExtractor<StreamInfoItem>, а ListExtractor<InfoItem>
        // https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.23.0
        // https://github.com/TeamNewPipe/NewPipeExtractor/pull/1082
        final PlaylistExtractorBundle extratorBundle;

        if (PlaylistUrlUtil.isYtUser(plUrl) || PlaylistUrlUtil.isYtChannel(plUrl)) {
            final ListLinkHandler channelListLinkHandler = YoutubeChannelLinkHandlerFactory.getInstance().fromUrl(plUrl);
            final ChannelExtractor channelExtractor = YouTube.getChannelExtractor(channelListLinkHandler.getUrl());

            // это выглядит логично, но не работает:
            //final ListLinkHandler channelTabListLinkHandler = YoutubeChannelTabLinkHandlerFactory.getInstance().fromUrl(plUrl);
            //final ListExtractor<? extends InfoItem> listExtractor = YouTube.getChannelTabExtractor(channelTabListLinkHandler);
            // вот так работает:
            final ListExtractor<? extends InfoItem> listExtractor = YouTube.getChannelTabExtractorFromId(channelListLinkHandler.getId(), "videos");

            extratorBundle = new PlaylistExtractorBundle(
                    channelExtractor, (ListExtractor<StreamInfoItem>) listExtractor);
        } else if (PlaylistUrlUtil.isYtPlaylist(plUrl)) {
            extratorBundle = new PlaylistExtractorBundle(YouTube.getPlaylistExtractor(plUrl));
        } else if (PlaylistUrlUtil.isPtUser(plUrl) || PlaylistUrlUtil.isPtChannel(plUrl)) {
            final ListLinkHandler channelListLinkHandler = PeertubeChannelLinkHandlerFactory.getInstance().fromUrl(plUrl);
            final ChannelExtractor channelExtractor = PeerTube.getChannelExtractor(channelListLinkHandler.getUrl());

            // это выглядит логично, но не работает:
            //final ListLinkHandler channelTabListLinkHandler = PeertubeChannelTabLinkHandlerFactory.getInstance().fromUrl(plUrl);
            //final ListExtractor<? extends InfoItem> listExtractor = PeerTube.getChannelTabExtractor(channelTabListLinkHandler);
            // вот так работает:
            final ListExtractor<? extends InfoItem> listExtractor = PeerTube.getChannelTabExtractorFromIdAndBaseUrl(
                    channelListLinkHandler.getId(), "videos", channelListLinkHandler.getBaseUrl());

            extratorBundle = new PlaylistExtractorBundle(
                    channelExtractor, (ListExtractor<StreamInfoItem>) listExtractor);
        } else if (PlaylistUrlUtil.isPtPlaylist(plUrl)) {
            extratorBundle = new PlaylistExtractorBundle(PeerTube.getPlaylistExtractor(plUrl));
        } else {
            throw new ExtractionException("Unrecognized playlist URL: " + plUrl);
        }

        return extratorBundle;
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
        final String thumbUrl = findBestThumbUrl(item.getThumbnails());

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
        final String thumbUrl = findBestThumbUrl(item.getThumbnails());
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
        final String thumbUrl = findBestThumbUrl(item.getThumbnails());

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

    /**
     * Для отладки.
     * Для продолжительная задача, которая ничего не делает. Управляется через taskController.
     * @param taskDurationMls продолжительность задачи в миллисекундах,
     *                        -1 - бесконечная задача
     * @param finishWithError завершить выполнение с ошибкой (если задача не бесконечная)
     * @param taskController
     */
    public void develModeFakeTask(final long taskDurationMls, final boolean finishWithError, final TaskController taskController) {
        taskController.setRunning(true);
        // выполняем пробежками по, пусть будет, 1000 млс.
        final long taskPartDurMls = 1000;

        // заодно в это же время будем менять статус,
        // как будто грузим разные страницы
        int fakeCurrentPage = 0;

        long timeLeftMls = taskDurationMls;
        while (timeLeftMls > 0 && !taskController.isCanceled()) {
            timeLeftMls -= taskPartDurMls;

            taskController.setStatusMsg("loading page-" + fakeCurrentPage);
            fakeCurrentPage++;

            try {
                Thread.sleep(taskPartDurMls);
            } catch (InterruptedException e) {
            }
        }

        if (!taskController.isCanceled()) {
            if (finishWithError) {
                taskController.setStatusMsg("Fake task finished with fake problem",
                        new RuntimeException("Fake task finished with fake problem"));
            } else {
                taskController.setStatusMsg("Loaded " + fakeCurrentPage + " fake pages");
            }
        }
        taskController.setAttr(TASK_CONTROLLER_ATTR_PLAYLIST_ID, PlaylistInfo.ID_NONE);
        taskController.setRunning(false);
    }
}
