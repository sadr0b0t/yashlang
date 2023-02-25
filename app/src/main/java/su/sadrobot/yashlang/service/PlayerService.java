package su.sadrobot.yashlang.service;

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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.WatchVideoActivity;
import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.StreamHelper;
import su.sadrobot.yashlang.controller.ThumbManager;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.player.RecommendationsProvider;
import su.sadrobot.yashlang.util.StringFormatUtil;
import su.sadrobot.yashlang.view.VideoItemArrayAdapter;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 * Механизм запуска сервиса:
 * - пользователь заходит в WatchVideoActivity - сервис запускается через механизм bind
 * - сервис становится перманентным, т.е. продолжит работу даже после того, как пользователь выходит
 *   из запустившей его активити после того, как сервис получает любую команду (например, играть) через startService
 * - если бы пользователь просто зашел в WatchVideoActivity, не отправил сервису никакую команду
 *   и вышел из активити (выполнив unbind), фоновый сервис после этого тоже бы завершился
 * - но прилажение работает таким образом, что после входа в WatchVideoActivity сервис сразу получит
 *   команду играть видео, т.е. он сразу становится перманентным
 *
 * Завершение работы
 * - После того, как сервис запущен и перешел в перманентное состояние (в нашем случае - сразу после запуска),
 *   он может только зам завершить свою работу через вызов stopSelf. Логика работы приложения в режиме
 *   фонового плеера не подразумевает ситуации, когда бы это могло произойти автоматически: если ролик
 *   не играет, значит он поставлен на паузу и кнопка "продолжить" просто будет ожидать действия
 *   пользователя. Таким образом, пользователь должен сам принять решение, что он хочет завершить приложение.
 *   Для этого должна быть специальная кнопка в плеере или пользователь должен иметь возможность
 *   "смахнуть" плеер из области уведомлений и в таком случае сервис должен завершить работу. Второй
 *   вариант предпочтительнее, но нужно проверить, что уведомление запускается в таком режиме, когда
 *   его можно "смахнуть".
 *
 * Дополнительно:
 * - сервис так же может быть запущен без входа в активити прямой отправкой команды через startService,
 *   но в текущей реализации приложения (верси 0.11.0) такой сценарий не предусмотрен. Не исключен вариант,
 *   что появится потом.
 */
public class PlayerService extends Service {
    // https://developer.android.com/guide/components/services
    // https://developer.android.com/guide/components/bound-services
    // https://developer.android.com/reference/android/os/IBinder

    /* Service things  */
    public enum PlayerServiceCmd {

        /**
         * Играть текущее видео
         */
        PLAY,

        /**
         * Поставить на паузу текущее видео
         */
        PAUSE,

        /**
         * Предыдущее видео
         */
        PREV,

        /**
         * Следующее видео
         */
        NEXT,

        /**
         * Остановить проигрывание, завершить работу сервиса
         */
        STOP,

        /**
         * Перезагрузить поток текущего видео
         */
        RELOAD
    }

    public static String PARAM_CMD = "PARAM_CMD";

    private static int NOTIFICATION_UPDATE_PERIOD_MS = 1000;

    // https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
    private static boolean _isRunning = false;
    public static boolean isRunning() {
        return _isRunning;
    }

    public class PlayerServiceBinder extends android.os.Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    /* Player things */
    public enum PlayerState {
        EMPTY, LOADING, ERROR, LOADED, NOTHING_TO_PLAY
    }

    public enum PlayerMode {
        VIDEO, AUDIO
    }

    public interface PlayerServiceListener {
        /**
         * Изменилось любое содержимое, которое может потребовать обновить значения элементов
         * управления. Всё, кроме PlayerState, изменение PlayerState обновляется отдельно через
         * onPlayerStateChange.
         */
        void onContentsStateChange();

        /**
         * Изменилось состояние плеера PlayerState
         */
        void onPlayerStateChange();

        /**
         * Изменилось текуущее видео в списке.
         */
        void currentVideoChange();
    }

    // будем загружать видео в фоне, но строго последовательно
    //private ExecutorService videoLoadingExecutor = Executors.newFixedThreadPool(1);
    //private ExecutorService videoLoadingExecutor = Executors.newSingleThreadExecutor();
    // Вообще, если мы начали загружать новое видео, а потом поставили еще одно видео в очередь на загрузку,
    // а потом после него еще одно, то загружать второе видео вообще не обязательно - загружать
    // нужно только самое последнее видео после того, как завершится загрузка первого видео,
    // раз уж она началась (по-хорошему, ее тоже можно прервать, но это отдельная история).
    // По этой причине у нас ThreadPool будет содержать в очереди на выполнение всегда только один элемент:
    // один поток выполняется (должен быть уже извлечен из очереди), еще одно задание ожидает,
    // если добавляется новое задание, то оно заменяет ожидающее.
    // Как ни странно, в стандартных реализациях BlockingQueue такого варианта поведения не нашлось,
    // поэтому придется переопределить добавление элементов в очередь самостоятельно.
    // по мотивам:
    // https://www.javamex.com/tutorials/threads/thread_pools_queues.shtml
    // https://askdev.ru/q/ogranichennaya-po-razmeru-ochered-soderzhaschaya-poslednie-n-elementov-v-java-10569/
    // (похоже на автоматический перевод с английского, но там нет ссылки на источник)
    // здесь:
    //   - конструктор ThreadPoolExecutor с LinkedBlockingQueue взял из Executors.newSingleThreadExecutor
    //   - ThreadPoolExecutor.execute вызывает queue.offer, а не queue.add, поэтому переопределяем его
    private final ExecutorService videoLoaderExecutor =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>() {
                        @Override
                        public boolean offer(Runnable o) {
                            super.clear();
                            return super.offer(o);
                        }
                    });

    // пул потоков для загрузки иконок видео, логика аналогичная videoLoadingExecutor
    private final ExecutorService videoThumbLoaderExecutor =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>() {
                        @Override
                        public boolean offer(Runnable o) {
                            super.clear();
                            return super.offer(o);
                        }
                    });

    private SimpleExoPlayer exoPlayer;
    private com.google.android.exoplayer2.upstream.DataSource.Factory videoDataSourceFactory;

    private PlayerState playerState = PlayerState.EMPTY;
    private PlayerMode playerMode = PlayerMode.VIDEO;
    private String videoLoadErrorMsg = "";

    // Флаг - ставить ли загружаемый в текущий момент ролик на паузу сразу после загрузки.
    // Если активити была скрыта в момент, когда начали грузить новое видео,
    // ставить на паузу текущий плеер бесполезно - новое видео начнет играть после загрузки,
    // при этом окно плеера будет скрато. Поэтому при загрузке нового видео проверяем этот флаг
    // и если он включен (т.е. экран плеера скрыт) и в настройках включен режим "ставить на паузу"
    // при скрытии плеера, новое загруженное видео будет на паузе сразу после загрузки.
    // Вариант: заменить флаг на проверку, подключен ли (bound) сервис к активити.
    // Но: если плеер уже в фоне и переходит на следующий ролик после текущего, новый ролик
    // при этом на паузу ставить не следует.
    private boolean pauseOnLoad = false;

    //
    private VideoItem currentVideo;
    private RecommendationsProvider recommendationsProvider;

    // для функции перехода на следующее видео
    private int videoListCurrentPosition = -1;
    private final Map<Long, Integer> posMap = new HashMap<>();
    private final Stack<VideoItem> playbackHistory = new Stack<>();

    // уведомления
    private RemoteViews notificationPlayerControlsView;

    // внешнее подключение
    private IBinder serviceBinder;
    private boolean isBound = false;
    private PlayerServiceListener serviceListener;

    //
    private final Timer notificationUpdateTimer = new Timer();
    private TimerTask notificationUpdateTask;

    //
    private final Handler handler = new Handler();

    @Override
    public void onCreate() {
        _isRunning = true;

        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlayerService onCreate", Toast.LENGTH_LONG).show();
        }

        // Плеер
        exoPlayer = new SimpleExoPlayer.Builder(this).build();
        videoDataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "yashlang"));

        // https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/Player.Listener.html
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
                // сохранить текущее состояние, если плеер встал на паузу
                if (!playWhenReady) {
                    saveVideoCurrPos();
                }

                updateNotification();
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, @Player.DiscontinuityReason int reason) {
                // сохранить текущее состояние, если текущее место проигрывания поменялось
                // по внешней причине, а не в процессе проигрывания
                // (например, пользователь кликнул на ползунке плеера)
                if(reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Сохранять только в том случае, если переход по действию пользователя (DISCONTINUITY_REASON_SEEK).
                    // При переключении ролика на новый будет вызвано событие DISCONTINUITY_REASON_REMOVE
                    // с новой позицией newPosition равной 0 (её точно нельзя сохранять).
                    // Насчет других возможных вариантов - с ними пока не встречался, если потребуется, добавим.
                    saveVideoCurrPos();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    setPlayerState(PlayerState.LOADED, null);
                } else if (playbackState == Player.STATE_ENDED) {
                    // ролик завершился - переходим к следующему
                    // TODO: сделайть экран с таймаутом секунд на 10, прогрессбаром и кнопкой
                    // перейти сейчас, отменить, играть заново текущий.

                    if (recommendationsProvider != null && recommendationsProvider.getVideoListAdapter().getItemCount() > 1) {
                        // переходим на следующее видео по списку рекомендаций
                        final int nextVideoPosition = videoListCurrentPosition >= recommendationsProvider.getVideoListAdapter().getItemCount() - 1 ?
                                0 : videoListCurrentPosition + 1;
                        final VideoItem item;
                        if (recommendationsProvider.getVideoListAdapter() instanceof VideoItemPagedListAdapter) {
                            // здесь не случайные рекомендации, а, например, список выдачи по поисковому запросу
                            item = ((VideoItemPagedListAdapter) recommendationsProvider.getVideoListAdapter()).getItem(nextVideoPosition);
                        } else if (recommendationsProvider.getVideoListAdapter() instanceof VideoItemArrayAdapter) {
                            // здесь скорее всего случайные рекомендации
                            item = ((VideoItemArrayAdapter) recommendationsProvider.getVideoListAdapter()).getItem(nextVideoPosition);
                        } else {
                            item = null;
                        }
                        if (item != null) {
                            posMap.put(item.getId(), nextVideoPosition);
                            // перед загрузкой нового видео обнулим текущую позицию
                            playVideoItem(item, true);
                        }
                    }
                }
            }

            @Override
            public void onPlayerError(final PlaybackException error) {
                //  здесь для предотвращения деградации после вот этого коммита
                //  https://github.com/sadr0b0t/yashlang/commit/b89c415ba3d71a0ac81c40f5d54b7fad249eac27
                //  применим логику:
                // - если произошла ошибка при загрузке плеером потока и при этом этот поток -
                //   поток высокого качества, то попробовать загрузить другой поток
                //   (стратегия выбора потока указана в настройках)
                // - если все потоки в списке перепробованы, то показать ошибку
                // пока переключаемся от потока к потоку, плеер будет показывать прогресс
                setPlayerState(PlayerState.LOADING, null);
                boolean tryAnotherStream = false;
                try {
                    final StreamHelper.StreamPair nextPlaybackStreams = StreamHelper.getNextPlaybackStreamPair(
                            PlayerService.this,
                            currentVideo.getStreamSources().getVideoStreams(),
                            currentVideo.getStreamSources().getAudioStreams(),
                            currentVideo.getPlaybackStreams().getVideoStream());
                    if (currentVideo.getPlaybackStreams().getVideoStream() != null &&
                            nextPlaybackStreams.getVideoStream() != null &&
                            !nextPlaybackStreams.getVideoStream().getUrl().equals(currentVideo.getPlaybackStreams().getVideoStream().getUrl())) {
                        currentVideo.setPlaybackStreams(nextPlaybackStreams);
                        // перерисовать информацию о текущих потоках
                        if (serviceListener != null) {
                            serviceListener.onContentsStateChange();
                        }
                        tryAnotherStream = true;
                        playVideoStream(
                                currentVideo.getPlaybackStreams().getVideoStream().getUrl(),
                                (currentVideo.getPlaybackStreams().getAudioStream() != null ? currentVideo.getPlaybackStreams().getAudioStream().getUrl() : null),
                                currentVideo.getPausedAt(),
                                pauseOnLoad);
                    }
                } catch (Exception e) {
                }

                if (!tryAnotherStream) {
                    setPlayerState(PlayerState.ERROR, error.getMessage());
                }
            }
        });

        // https://developer.android.com/guide/components/foreground-services
        // https://github.com/TeamNewPipe/NewPipe/blob/43e91ae4ae9878cdfce3f9c560612de41564962d/app/src/main/java/us/shandian/giga/service/DownloadManagerService.java
        // https://github.com/TeamNewPipe/NewPipe/blob/0039312a64e7271fec2e0a295db1d6979e83cf3f/app/src/main/java/org/schabi/newpipe/player/NotificationUtil.java
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlayerService onStart: PARAM_CMD=" + intent.getStringExtra(PARAM_CMD), Toast.LENGTH_LONG).show();
        }

        // Если команды нет, то специальных действий делать не нужно, - достаточно того, что сервис
        // её принял через механизм startService+intent, сервис перейдет в "перманентный" режим
        if (intent.hasExtra(PARAM_CMD)) {
            final PlayerServiceCmd cmd = PlayerServiceCmd.valueOf(intent.getStringExtra(PARAM_CMD));
            switch (cmd) {
                case PLAY: {
                    exoPlayer.setPlayWhenReady(true);
                    break;
                }
                case PAUSE: {
                    exoPlayer.setPlayWhenReady(false);
                    break;
                }
                case PREV: {
                    gotoPrevVideo();
                    break;
                }
                case NEXT: {
                    gotoNextVideo();
                    break;
                }
                case STOP: {
                    this.stopSelf();
                }
                case RELOAD: {
                    reloadCurrentVideo();
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        isBound = true;

        // экран плеера при выходе на передний план подлкючается к сервису
        onPlayerActivityVisibilityChange(false);

        // скрыть уведомление, когда экран плеера активен
        if (notificationUpdateTask != null) {
            notificationUpdateTask.cancel();
            notificationUpdateTask = null;
        }
        stopForeground(true);

        if (serviceBinder == null) {
            serviceBinder = new PlayerServiceBinder();
        }
        return serviceBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        isBound = true;

        // экран плеера при выходе на передний план подлкючается к сервису
        onPlayerActivityVisibilityChange(false);

        // скрыть уведомление, когда экран плеера активен
        if (notificationUpdateTask != null) {
            notificationUpdateTask.cancel();
            notificationUpdateTask = null;
        }
        stopForeground(true);
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        isBound = false;

        // экран ушел на задний план
        if (!ConfigOptions.getBackgroundPlaybackOn(this) || ConfigOptions.getPauseOnHide(this)) {
            // поставить на паузу
            // (текущая позиция будет сохранена в обработчике события постановки на паузу)
            exoPlayer.setPlayWhenReady(false);
        }

        // для таймера почему-то обязательно каждый раз создавать новую таску
        notificationUpdateTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateNotification();
                    }
                });
            }
        };
        notificationUpdateTimer.scheduleAtFixedRate(notificationUpdateTask, 0, NOTIFICATION_UPDATE_PERIOD_MS);

        // экран плеера при скрытии отключается от сервиса
        onPlayerActivityVisibilityChange(true);

        // показать уведомление, когда экран плеера скрыт
        startForeground(ConfigOptions.NOTIFICATION_ID_PLAYER, buildNotification());

        // true, чтобы при повторном подключении был вызван onRebind
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        _isRunning = false;
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlayerService onDestroy", Toast.LENGTH_LONG).show();
        }

        exoPlayer.setPlayWhenReady(false);
        exoPlayer.release();
        if (notificationUpdateTask != null) {
            notificationUpdateTask.cancel();
            notificationUpdateTask = null;
        }
        stopForeground(true);
    }

    private void createNotificationChannels() {
        // NotificationChannel - нужно добавить до того, как показывать уведомления
        // вот так не работает (требует высокий api)
        // https://developer.android.com/guide/topics/ui/notifiers/notifications#ManageChannels
        // так, как здесь, норм:
        // https://developer.android.com/training/notify-user/channels
        // https://github.com/TeamNewPipe/NewPipe/blob/53bf3420e76ef4a087c2ac33cc64c2aec094beba/app/src/main/java/org/schabi/newpipe/App.java

        final List<NotificationChannelCompat> notificationChannelCompats = new ArrayList<>();
        notificationChannelCompats.add(new NotificationChannelCompat
                .Builder(getString(R.string.notification_channel_player_id), NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_player_name))
                .setDescription(getString(R.string.notification_channel_player_description))
                .build());

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.createNotificationChannelsCompat(notificationChannelCompats);
    }

    private Notification buildNotification() {

        // https://developer.android.com/develop/ui/views/notifications
        // https://developer.android.com/guide/topics/media-apps/audio-app/building-a-mediabrowserservice#mediastyle-notifications
        // https://developer.android.com/develop/ui/views/notifications/expanded
        // https://developer.android.com/develop/ui/views/notifications#ApplyStyle

        // уведомление со своим лэйаут-файлом
        // https://developer.android.com/training/notify-user/custom-notification
        // https://developer.android.com/reference/android/app/Notification.html#contentView
        // https://stackoverflow.com/questions/10516722/android-notification-with-buttons-on-it

        // https://stackoverflow.com/questions/42997350/extra-values-not-updating-in-pendingintent
        // если менять значения интентов во время разработки, нужно добавлять флаг PendingIntent.FLAG_UPDATE_CURRENT,
        // иначе андроид кэширует где-то у себя первую версию интента и дальше использует ее даже
        // после переустановки приложения

        final Intent gotoPlayerIntent = new Intent(this, WatchVideoActivity.class);
        gotoPlayerIntent.putExtra(WatchVideoActivity.PARAM_INIT_FROM_SERVICE, true);
        gotoPlayerIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        final PendingIntent gotoPlayerPendingIntent = PendingIntent.getActivity(this, 0,
                gotoPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final Intent playIntent = new Intent(this, PlayerService.class);
        playIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.PLAY.name());
        final PendingIntent playPendingIntent = PendingIntent.getService(this, 1,
                playIntent, PendingIntent.FLAG_IMMUTABLE);

        final Intent pauseIntent = new Intent(this, PlayerService.class);
        pauseIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.PAUSE.name());
        final PendingIntent pausePendingIntent = PendingIntent.getService(this, 2,
                pauseIntent, PendingIntent.FLAG_IMMUTABLE);

        final Intent prevIntent = new Intent(this, PlayerService.class);
        prevIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.PREV.name());
        final PendingIntent prevPendingIntent = PendingIntent.getService(this, 3,
                prevIntent, PendingIntent.FLAG_IMMUTABLE);

        final Intent nextIntent = new Intent(this, PlayerService.class);
        nextIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.NEXT.name());
        final PendingIntent nextPendingIntent = PendingIntent.getService(this, 4,
                nextIntent, PendingIntent.FLAG_IMMUTABLE);

        final Intent stopIntent = new Intent(this, PlayerService.class);
        stopIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.STOP.name());
        final PendingIntent stopPendingIntent = PendingIntent.getService(this, 5,
                stopIntent, PendingIntent.FLAG_IMMUTABLE);

        final Intent reloadIntent = new Intent(this, PlayerService.class);
        reloadIntent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.RELOAD.name());
        final PendingIntent reloadPendingIntent = PendingIntent.getService(this, 6,
                reloadIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationPlayerControlsView = new RemoteViews(getApplicationContext().getPackageName(),
                R.layout.notification_player_controls);

        notificationPlayerControlsView.setProgressBar(R.id.video_progress, 100, 10, false);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.play_btn, playPendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.pause_btn, pausePendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.prev_video_btn, prevPendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.next_video_btn, nextPendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.stop_btn, stopPendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.stop_btn, stopPendingIntent);
        notificationPlayerControlsView.setOnClickPendingIntent(R.id.video_load_error_txt, reloadPendingIntent);

        if (currentVideo != null) {
            notificationPlayerControlsView.setBitmap(R.id.video_thumb_img, "setImageBitmap", currentVideo.getThumbBitmap());
            notificationPlayerControlsView.setCharSequence(R.id.video_name_txt, "setText", currentVideo.getName());

            if (recommendationsProvider == null || recommendationsProvider.getVideoListAdapter() == null || recommendationsProvider.getVideoListAdapter().getItemCount() < 2) {
                notificationPlayerControlsView.setInt(R.id.prev_video_btn, "setVisibility", View.INVISIBLE);
                notificationPlayerControlsView.setInt(R.id.next_video_btn, "setVisibility", View.INVISIBLE);
            } else {
                notificationPlayerControlsView.setInt(R.id.prev_video_btn, "setVisibility", getPlaybackHistory().size() > 1 ? View.VISIBLE : View.INVISIBLE);
                notificationPlayerControlsView.setInt(R.id.next_video_btn, "setVisibility", View.VISIBLE);
            }

            if (playerState == PlayerState.LOADED) {
                if (exoPlayer.isPlaying()) {
                    notificationPlayerControlsView.setInt(R.id.play_btn, "setVisibility", View.GONE);
                    notificationPlayerControlsView.setInt(R.id.pause_btn, "setVisibility", View.VISIBLE);
                } else {
                    notificationPlayerControlsView.setInt(R.id.play_btn, "setVisibility", View.VISIBLE);
                    notificationPlayerControlsView.setInt(R.id.pause_btn, "setVisibility", View.GONE);
                }
                notificationPlayerControlsView.setInt(R.id.video_load_progress, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_load_error_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_duration_txt, "setVisibility", View.VISIBLE);
                notificationPlayerControlsView.setInt(R.id.video_curr_time_txt, "setVisibility", View.VISIBLE);
                notificationPlayerControlsView.setInt(R.id.video_progress, "setVisibility", View.VISIBLE);

                notificationPlayerControlsView.setCharSequence(R.id.video_duration_txt, "setText", StringFormatUtil.formatDuration(this, currentVideo.getDuration()));
                notificationPlayerControlsView.setCharSequence(R.id.video_curr_time_txt, "setText", StringFormatUtil.formatCurrTime(exoPlayer.getContentPosition() / 1000));
                notificationPlayerControlsView.setInt(R.id.video_progress, "setMax", (int) exoPlayer.getDuration() / 1000);
                notificationPlayerControlsView.setInt(R.id.video_progress, "setProgress", (int) exoPlayer.getContentPosition() / 1000);
            } else if (playerState == PlayerState.LOADING) {
                notificationPlayerControlsView.setInt(R.id.play_btn, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.pause_btn, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_load_progress, "setVisibility", View.VISIBLE);
                notificationPlayerControlsView.setInt(R.id.video_load_error_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_duration_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_curr_time_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_progress, "setVisibility", View.GONE);
            } else if (playerState == PlayerState.ERROR) {
                notificationPlayerControlsView.setInt(R.id.play_btn, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.pause_btn, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_load_progress, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_load_error_txt, "setVisibility", View.VISIBLE);
                notificationPlayerControlsView.setInt(R.id.video_duration_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_curr_time_txt, "setVisibility", View.GONE);
                notificationPlayerControlsView.setInt(R.id.video_progress, "setVisibility", View.GONE);
            }
        }

        // https://developer.android.com/training/notify-user/build-notification
        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_player_id))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContent(notificationPlayerControlsView)
                .setContentIntent(gotoPlayerPendingIntent)
                .build();
    }

    private void updateNotification() {
        if (!isBound && _isRunning) {
            // проверяем _isRunning тоже, т.к. может произойти ситуация, когда пльзователь
            // оставновил сервис командой stoSelf, но команда добавить обновление уже отправлена
            // и уведомление добавляется после того, как сервис остановлен
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(ConfigOptions.NOTIFICATION_ID_PLAYER, buildNotification());
        }
    }

    private void setPlayerState(final PlayerState playerState, final String errorMsg) {
        this.playerState = playerState;
        this.videoLoadErrorMsg = playerState == PlayerState.ERROR ? errorMsg : "";

        if (playerState != PlayerState.LOADING) {
            // cбросим флаг "ставить на паузу после загрузки" при любом состоянии, кроме LOADING.
            // флаг pauseOnLoad может стать true, если видео было в состоянии LOADING в момент
            // скрытия экрана плеера. Если процесс загрузки завершился с любым результатом
            // (успех или ошибка), то новый ролик не нужно ставить на паузу, даже если
            // экран плеера всё еще скрыт, поэтому этот флаг нужно сбросить.
            pauseOnLoad = false;
        }

        if (serviceListener != null) {
            serviceListener.onPlayerStateChange();
        }

        updateNotification();
    }

    private void onPlayerActivityVisibilityChange(final boolean hidden) {
        if (hidden && (playerState == PlayerState.LOADING) && ConfigOptions.getPauseOnHide(this)) {
            // текущий экран плеера скрылася в момент, когда загружалось видео, при этом в настройках
            // выставлен режим ставить ролик на паузу при скрытии экрана плеера - поставим загружаемый
            // ролик на паузу сразу после загрузки
            pauseOnLoad = true;
        } else {
            // не ставить на паузу загруженное видео во всех прочих случаях
            pauseOnLoad = false;
        }
    }

    public void setServiceListener(final PlayerServiceListener serviceListener) {
        this.serviceListener = serviceListener;
    }

    public void removeServiceListener() {
        this.serviceListener = null;
    }

    public ExoPlayer getPlayer() {
        return exoPlayer;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public PlayerMode getPlayerMode() {
        return playerMode;
    }

    public VideoItem getCurrentVideo() {
        return currentVideo;
    }

    public String getVideoLoadErrorMsg() {
        return videoLoadErrorMsg;
    }

    public Stack<VideoItem> getPlaybackHistory() {
        return playbackHistory;
    }

    public Map<Long, Integer> getVideoListPosMap() {
        return posMap;
    }

    public void setVideoListCurrentPosition(int videoListCurrentPosition) {
        this.videoListCurrentPosition = videoListCurrentPosition;
    }

    public int getVideoListCurrentPosition() {
        return videoListCurrentPosition;
    }

    public void setRecommendationsProvider(final RecommendationsProvider recommendationsProvider) {
        this.recommendationsProvider = recommendationsProvider;
    }

    public RecommendationsProvider getRecommendationsProvider() {
        return recommendationsProvider;
    }

    public void gotoPrevVideo() {
        if (playbackHistory.size() > 1) {
            playbackHistory.pop();
            // здесь снимаем ролик с вершины, но он там снова сразу окажется в playVideoItem
            playVideoItem(playbackHistory.pop(), false);
        }
    }

    public void gotoNextVideo() {
        if (recommendationsProvider == null || recommendationsProvider.getVideoListAdapter() == null) {
            // по-хорошему, мы не должны сюда попасть, т.к. в этом случае кнопка будет скрыта
            return;
        }

        // переходим на следующее видео по списку рекомендаций
        // если мы на последней рекомендации, начинаем с начала
        final int nextVideoPosition = videoListCurrentPosition >= recommendationsProvider.getVideoListAdapter().getItemCount() - 1 ?
                0 : videoListCurrentPosition + 1;
        final VideoItem item;
        if (recommendationsProvider.getVideoListAdapter().getItemCount() > 0) {
            if (recommendationsProvider.getVideoListAdapter() instanceof VideoItemPagedListAdapter) {
                // здесь не случайные рекомендации, а, например, список выдачи по поисковому запросу
                item = ((VideoItemPagedListAdapter) recommendationsProvider.getVideoListAdapter()).getItem(nextVideoPosition);
            } else if (recommendationsProvider.getVideoListAdapter() instanceof VideoItemArrayAdapter) {
                // здесь скорее всего случайные рекомендации
                item = ((VideoItemArrayAdapter) recommendationsProvider.getVideoListAdapter()).getItem(nextVideoPosition);
            } else {
                // сюда не попадём
                item = null;
            }
        } else {
            item = null;
        }
        if (item != null) {
            posMap.put(item.getId(), nextVideoPosition);
            playVideoItem(item, false);
        }
    }

    /**
     * Загрузить заново видеопоток для текущего ролика
     */
    public void reloadCurrentVideo() {
        if (currentVideo != null) {
            if (currentVideo.getId() != VideoItem.ID_NONE) {
                // загрузить поток видео заново (иногда после разрыва соединения
                // видео может перестать загружаться и появление соединения процесс
                // не возобновляет)

                // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
                // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
                final VideoItem _currentVideo = currentVideo;
                final long _currentPos = exoPlayer.getCurrentPosition();
                // для текущего кэша, да
                if (currentVideo != null && playerState == PlayerState.LOADED) {
                    currentVideo.setPausedAt(_currentPos);
                }
                // сохраним текущую позицию (если она больше нуля) в б/д и загрузим
                // видео заново - обе операции в фоновом потоке
                videoLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // если за время запуска потока видео успели переключить, всё отменяем
                        if (_currentVideo != null && _currentVideo == currentVideo) {
                            if (playerState == PlayerState.LOADED) {
                                // сохраним текущую позицию только в том случае, если ролик был загружен
                                // (может быть ситуация, когда мы переключились на видео с ранее
                                // сохраненной позицией, а оно не загрузилось, тогда бы у нас
                                // сбросилась старая сохраненная позиция, а это не хорошо)
                                VideoDatabase.getDbInstance(PlayerService.this).
                                        videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                            }

                            loadVideoItem(currentVideo);
                        }
                    }
                });
            } else {
                // если видео нет в базе
                final VideoItem _currentVideo = currentVideo;
                final long _currentPos = exoPlayer.getCurrentPosition();
                // для текущего кэша, да
                if (currentVideo != null && playerState == PlayerState.LOADED) {
                    currentVideo.setPausedAt(_currentPos);
                }
                videoLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // если за время запуска потока видео успели переключить, всё отменяем
                        if (_currentVideo != null && _currentVideo == currentVideo) {
                            loadVideoItem(currentVideo);
                        }
                    }
                });
            }
        }
    }

    /**
     * Сохраним текущую позицию видео в базу
     */
    private void saveVideoCurrPos() {
        if (currentVideo != null && playerState == PlayerState.LOADED) {
            // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
            // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
            final VideoItem _currentVideo = currentVideo;
            final long _currentPos = exoPlayer.getCurrentPosition();
            // для текущего кэша, да
            _currentVideo.setPausedAt(_currentPos);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    VideoDatabase.getDbInstance(PlayerService.this).
                            videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                }
            }).start();
        }
    }

    /**
     * Обнулить текущую позицию видео в базе
     */
    private void resetVideoCurrPos() {
        if (currentVideo != null) {
            // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
            // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
            final VideoItem _currentVideo = currentVideo;
            // для текущего кэша, да
            _currentVideo.setPausedAt(0);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    VideoDatabase.getDbInstance(PlayerService.this).
                            videoItemDao().setPausedAt(_currentVideo.getId(), 0);
                }
            }).start();
        }
    }

    public void playVideoItem(final long videoId, final boolean resetCurrPos) {
        if (videoId != VideoItem.ID_NONE) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final VideoItem videoItem = VideoDatabase.getDbInstance(
                            PlayerService.this).videoItemDao().getById(videoId);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem, resetCurrPos);
                        }
                    });
                }
            }).start();
        }
    }

    public void playVideoItem(final String videoItemUrl, final boolean resetCurrPos) {
        if (videoItemUrl != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // грузим информацию из онлайна
                    VideoItem _videoItem;
                    try {
                        _videoItem = ContentLoader.getInstance().fetchVideoItem(videoItemUrl);
                    } catch (ExtractionException | IOException e) {
                        _videoItem = null;
                        //e.printStackTrace();
                    }

                    final VideoItem videoItem = _videoItem;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playVideoItem(videoItem, resetCurrPos);
                        }
                    });
                }
            }).start();
        }
    }

    /**
     * Начать проигрывание нового ролика - показать информацию о видео, решить вопросы
     * с сохранением позиций предыдущего видео, стеком истории проигрывания и т.п.
     */
    public void playVideoItem(final VideoItem videoItem, final boolean resetCurrPos) {
        // сбросим или сохраним текущую позицию предыдущего видео
        if (resetCurrPos) {
            resetVideoCurrPos();
        } else {
            saveVideoCurrPos();
        }
        // остановим старое видео, если оно играло
        playVideoStream(null, null, 0, true);

        // загружаем новое видео
        setPlayerState(PlayerState.LOADING, null);

        currentVideo = videoItem;
        videoListCurrentPosition = posMap.containsKey(videoItem.getId()) ? posMap.get(videoItem.getId()) : -1;

        // текущее видео поменялось - здесь можно промотать к нему список рекомендаций
        if (serviceListener != null) {
            serviceListener.currentVideoChange();
        }
        if (videoItem != null) {
            playbackHistory.push(videoItem);

            // обновить меню
            if (serviceListener != null) {
                serviceListener.onContentsStateChange();
            }

            // теперь то, что в фоне
            videoLoaderExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // посчитать просмотр (для ролика, загруженного из базы)
                    if (videoItem.getId() != VideoItem.ID_NONE) {
                        VideoDatabase.getDbInstance(PlayerService.this).
                                videoItemDao().countView(videoItem.getId());
                    }

                    loadVideoItem(videoItem);
                }
            });

            // если иконка видео не загружена, загрузим её здесь на всякий случай отдельным потоком,
            // это может пригодиться в режиме проигрывания потока аудио без видео.
            // неудачная загрузка иконки не является критичной проблемой, поэтому не будем
            // вставлять ее в основной поток загрузки
            if (videoItem.getThumbBitmap() == null) {
                videoThumbLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap thumb =
                                ThumbManager.getInstance().loadVideoThumb(PlayerService.this, videoItem);
                        videoItem.setThumbBitmap(thumb);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (serviceListener != null) {
                                    serviceListener.onContentsStateChange();
                                }
                            }
                        });

                    }
                });
            }
        }

        // новый текущий ролик - обновить состояние элементов управления
        // показать информацию о ролике
        if (serviceListener != null) {
            serviceListener.onContentsStateChange();
        }
    }

    /**
     * Загрузка контента видео - выбранного ролика, здесь касается только области проигрывания, т.е. виджет плеера.
     * Время выполнения не определено, т.к. выполняет сетевые операции, поэтому запускать нужно в фоновом потоке.
     *
     * @param videoItem
     */
    private void loadVideoItem(final VideoItem videoItem) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // для начала остановим проигрывание текущего видео,
                // чтобы оно не играло в фоне во время загрузки
                // (если его не остановили перед этим)
                playVideoStream(null, null, 0, true);

                // теперь покажем экран загрузки
                setPlayerState(PlayerState.LOADING, null);
            }
        });

        // Вообще, наверное, хорошо делать эту операцию после того, как плеер перейдет в состояние
        // "загружаем", т.е. создать новый фоновый поток внутри handler.post выше. Но, если handler.post
        // отправляет задачи в синхронную очередь для выполнения одна за одной, то задача в handler.post
        // ниже будет выполнена в любом случае после задачи handler.post выше, поэтому проблемы

        // загрузить поток видео
        final StreamHelper.StreamSources streamSources = StreamHelper.fetchStreams(this, videoItem);
        if (streamSources.getVideoStreams().size() > 0 || streamSources.getAudioStreams().size() > 0) {
            StreamHelper.sortVideoStreamsDefault(streamSources.getVideoStreams());
            StreamHelper.sortAudioStreamsDefault(streamSources.getAudioStreams());
            final StreamHelper.StreamPair playbackStreams = StreamHelper.getNextPlaybackStreamPair(
                    this, streamSources.getVideoStreams(), streamSources.getAudioStreams(), null);
            videoItem.setStreamSources(streamSources);
            videoItem.setPlaybackStreams(playbackStreams);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    // пока загружали информацию о видео, пользователь мог кликнуть на загрузку нового ролика,
                    // в этом случае уже нет смысла загружать в плеер этот ролик на долю секунды
                    // или на то время, пока загружается новый ролик, поэтому здесь просто ничего не делаем,
                    // а плеер останется в статусе "LOADING" до тех пор, пока не будет загружен новый ролик
                    if (videoItem == currentVideo) {

                        // т.к. загрузка видео осуществляется в фононовом потоке, мы можем сюда попасть
                        // в такой ситуации, когда пользователь кликнул на загрузку видео, а потом
                        // сразу свернул приложение - в этом случае ролик начнет проигрывание в фоне,
                        // а пользователь услышит его звук и ему придется вернуться в приложение, чтобы
                        // поставить плеер на паузу.
                        // по этой причине мы здесь проверяем, является ли экран с плеером активным
                        // (см: https://stackoverflow.com/questions/5446565/android-how-do-i-check-if-activity-is-running/25722319 )
                        // и если не является, то загружать видео, но не начинать его проигрывание
                        // сразу после загрузки.
                        // https://github.com/sadr0b0t/yashlang/issues/4

                        if (serviceListener != null) {
                            serviceListener.onContentsStateChange();
                        }

                        if (videoItem.getPlaybackStreams().getVideoStream() == null && videoItem.getPlaybackStreams().getAudioStream() == null) {
                            // здесь нас тоже скорее всего не будет, т.к. в автоматическом режиме
                            // если есть потоки видео или адио, что-то из них будет выбрано
                            setPlayerState(PlayerState.NOTHING_TO_PLAY, null);
                        } else {
                            try {
                                playVideoStream(
                                        (videoItem.getPlaybackStreams().getVideoStream() != null ? videoItem.getPlaybackStreams().getVideoStream().getUrl() : null),
                                        (videoItem.getPlaybackStreams().getAudioStream() != null ? videoItem.getPlaybackStreams().getAudioStream().getUrl() : null),
                                        videoItem.getPausedAt(),
                                        pauseOnLoad);
                            } catch (Exception ex) {
                                // в принципе, мы сюда не должны попасть никогда. Возможно, был повод
                                // поймать RuntimeException в плеере и не упасть.
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            });
        } else {
            if (videoItem == currentVideo) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        setPlayerState(
                                PlayerState.ERROR,
                                PlayerService.this.getString(R.string.no_playback_streams_for_video) +
                                        (streamSources.problems.size() > 0 ? "\n" + streamSources.problems.get(0).getMessage() : ""));
                    }
                });
            }
        }
    }

    public void playVideoItemStreams(final StreamHelper.StreamInfo videoStream, final StreamHelper.StreamInfo audioStream) {
        // сохраним переменные здесь, чтобы потом спокойно их использовать внутри потока
        // и не бояться, что текущее видео будет переключено до того, как состояние сохранится
        final VideoItem _currentVideo = currentVideo;
        final long _currentPos = exoPlayer.getCurrentPosition();
        // для текущего кэша, да
        if (currentVideo != null && playerState == PlayerService.PlayerState.LOADED) {
            currentVideo.setPausedAt(_currentPos);
        }

        setPlayerState(PlayerService.PlayerState.LOADING, null);
        // сохраним текущую позицию (если она больше нуля) в б/д и загрузим
        // видео заново - обе операции в фоновом потоке
        videoLoaderExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // если за время запуска потока видео успели переключить, всё отменяем
                if (_currentVideo == currentVideo) {
                    if (_currentVideo.getId() != VideoItem.ID_NONE) {
                        if (playerState == PlayerService.PlayerState.LOADED) {
                            // сохраним текущую позицию только в том случае, если ролик был загружен
                            // (может быть ситуация, когда мы переключились на видео с ранее
                            // сохраненной позицией, а оно не загрузилось, тогда бы у нас
                            // сбросилась старая сохраненная позиция, а это не хорошо)
                            VideoDatabase.getDbInstance(PlayerService.this).
                                    videoItemDao().setPausedAt(_currentVideo.getId(), _currentPos);
                        }
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            // сохраним выбранное вручную качество в настройки
                            if (videoStream != null) {
                                ConfigOptions.setVideoStreamLastSelectedRes(PlayerService.this, videoStream.getResolution());
                            }
                            _currentVideo.setPlaybackStreams(new StreamHelper.StreamPair(videoStream, audioStream));
                            if (videoStream == null && audioStream == null) {
                                setPlayerState(PlayerService.PlayerState.NOTHING_TO_PLAY, null);
                            } else {
                                // перерисовать информацию о текущих потоках
                                if (serviceListener != null) {
                                    serviceListener.onContentsStateChange();
                                }
                                playVideoStream(
                                        (videoStream != null ? videoStream.getUrl() : null),
                                        (audioStream != null ? audioStream.getUrl() : null),
                                        _currentVideo.getPausedAt(),
                                        pauseOnLoad);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Собственно, запустить на проигрывание видеопоток по известному адресу
     *
     * @param streamUrl адрес потока видео (должен быть с форматом ExoPlayer),
     *                  может содержать или не содержать дорожку аудио
     *                  если null, остановить проигрывание текущего ролика, если он уже был загружен
     * @param audioStreamUrl адрес потока аудио. Нужен, если у потока видео нет дорожки аудио.
     *                  Отправить null, если у потока видео есть совмещенная дорожка аудио.
     * @param seekTo    начать проигрывание с указанной позиции
     * @param paused    false: начать проигрывание сразу после загрузки;
     *                  true: загрузить поток и поставить на паузу
     */
    private void playVideoStream(final String streamUrl, final String audioStreamUrl, final long seekTo, final boolean paused) {
        if (streamUrl == null && audioStreamUrl == null) {
            // остановить проигрывание текущего ролика, если был загружен
            exoPlayer.stop();
        } else {
            // https://exoplayer.dev/
            // https://github.com/google/ExoPlayer

            // датасорсы к видео в плеере NewPipe:
            // - про продолжение с установленной позиции в коде не вижу или не нашел
            // - (как играть видео конкретно с ютюба не вижу тоже, там ацкий ООП)
            // - короче, толку от них ноль, пусть будут пока ссылки для справки
            // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java
            // https://github.com/TeamNewPipe/NewPipe/blob/master/app/src/main/java/org/schabi/newpipe/player/resolver/PlaybackResolver.java

            final MediaSource mediaSource;
            final MediaSource videoSource;
            final MediaSource audioSource;

            if (streamUrl != null) {
                final Uri mp4VideoUri = Uri.parse(streamUrl);
                videoSource = new ProgressiveMediaSource.Factory(videoDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(mp4VideoUri));
            } else {
                videoSource = null;
            }
            if (audioStreamUrl != null) {
                final Uri mp3AudioUri = Uri.parse(audioStreamUrl);
                audioSource = new ProgressiveMediaSource.Factory(videoDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(mp3AudioUri));
            } else {
                audioSource = null;
            }

            if (videoSource != null && audioSource == null) {
                mediaSource = videoSource;
                playerMode = PlayerMode.VIDEO;
            } else if (videoSource == null && audioSource != null) {
                mediaSource = audioSource;
                playerMode = PlayerMode.AUDIO;
            } else {
                // videoSource != null && audioSource != null
                // (оба null буть не могут, т.к. этот случай отсекли еще выше)
                // совместить дорожку аудио и видео
                // https://stackoverflow.com/questions/58404056/exoplayer-play-an-audio-stream-and-a-video-stream-synchronously

                mediaSource = new MergingMediaSource(videoSource, audioSource);
                playerMode = PlayerMode.VIDEO;
            }

            // Поставим на паузу старое видео, пока готовим новое
            if (exoPlayer.getPlaybackState() != Player.STATE_ENDED) {
                // Если ставить на паузу здесь после того, как плеер встал на паузу сам, закончив
                // играть видео, получим здесь второе событие STATE_ENDED, поэтому нам нужна здесь
                // специальная проверка.
                // При этом значение getPlayWhenReady() останется true, поэтому проверяем именно состояние.
                // https://github.com/google/ExoPlayer/issues/2272
                exoPlayer.setPlayWhenReady(false);
            }

            // Prepare the player with the source
            ((SimpleExoPlayer) exoPlayer).setMediaSource(mediaSource);
            exoPlayer.prepare();

            // Укажем текущую позицию сразу при загрузке видео
            // (в коментах что-то пишут что-то про датасорсы, которые поддерживают или не поддерживают
            // переходы seek при загрузке, похоже, что это фигня - просто делаем seek сразу после загрузки)
            // Exoplayer plays new Playlist from the beginning instead of provided position
            // https://github.com/google/ExoPlayer/issues/4375
            // How to load stream in the desired position? #2197
            // https://github.com/google/ExoPlayer/issues/2197
            // в этом месте нормлаьный duration еще не доступен, поэтому его не проверяем
            //if(seekTo > 0 && seekTo < videoPlayerView.getPlayer().getDuration()) {
            if (seekTo > 0) {
                // на 5 секунд раньше
                exoPlayer.seekTo(seekTo - 5000 > 0 ? seekTo - 5000 : 0);
            }
            exoPlayer.setPlayWhenReady(!paused);

            // статус LOADED будем выставлять после загрузки потока перед началом проигрывания
            // (произошло событие onPlaybackStateChanged:Player.STATE_READY)
            //setPlayerState(PlayerState.LOADED, null);
        }
    }

    /**
     * Запустить сервис или перевести запущенный сервис в состояние фонового "перманентного"
     * существования, в котором он завершит работу по действию пользователя, а не после
     * закрытия экрана плеера.
     */
    public static void cmdStartOrStick(final Context context) {
        final Intent intent = new Intent(context, PlayerService.class);
        context.startService(intent);
    }

    public static void cmdStop(final Context context) {
        final Intent intent = new Intent(context, PlayerService.class);
        intent.putExtra(PlayerService.PARAM_CMD, PlayerServiceCmd.STOP.name());
        context.startService(intent);
    }
}
