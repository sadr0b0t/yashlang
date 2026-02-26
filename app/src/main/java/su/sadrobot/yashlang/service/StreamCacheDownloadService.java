package su.sadrobot.yashlang.service;

/*
 * Copyright (C) Anton Moiseev 2022 <github.com/sadr0b0t>
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
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.StreamCacheActivity;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoStreamDownloader;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.StringFormatUtil;

/**
 * Здесь активные на текущий момент закачки.
 *
 * В базе данных хранится флаг скачано/не скачано. Если в базе стоит флаг "не зкачано", то
 * такая закачка на текущий момент может скачиваться, а может не скачиваться.
 *
 * Для сиюминутного статуса АКТИВНА/НА ПАУЗЕ нужно обращаться к сервису, который этим занимается,
 * т.е. сюда.
 */
public class StreamCacheDownloadService extends Service {
    // https://developer.android.com/guide/components/services
    // https://developer.android.com/guide/components/bound-services
    // https://developer.android.com/reference/android/os/IBinder

    public enum StreamCacheDownloadServiceCmd {
        START_ITEM, START_ITEMS, START_ALL, PAUSE_ITEM, PAUSE_ALL
    }

    public static String PARAM_CMD = "PARAM_CMD";
    public static String PARAM_STREAM_CACHE_ITEM_ID = "PARAM_STREAM_CACHE_ITEM_ID";
    public static String PARAM_STREAM_CACHE_ITEM_IDS = "PARAM_STREAM_CACHE_ITEM_IDS";

    private static int NOTIFICATION_UPDATE_PERIOD_MS = 1000;

    public class StreamCacheDownloadServiceBinder extends android.os.Binder {
        public StreamCacheDownloadService getService() {
            return StreamCacheDownloadService.this;
        }
    }

    private IBinder serviceBinder;
    private final Map<Long, TaskController> taskControllerMap = new HashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(ConfigOptions.MAX_STREAM_CACHE_DOWNLOADS);

    // Счетчих задач - выполняемых или добавленных в очередь на выполнение.
    // Если живых задач нет и сервис не подключен к активити (т.е. не bound), можно завершать сервис.
    // В интернетах предлгают использовать Phaser или модификацию CountDownLatch
    // (но Phaser не доступен для текущей версии API здесь, значит придётся городить своё велосипед)
    // https://stackoverflow.com/questions/14535770/executors-how-to-synchronously-wait-until-all-tasks-have-finished-if-tasks-are
    // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Phaser.html
    // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadPoolExecutor.html
    private int submittedTaskCount = 0;

    private final Timer notificationUpdateTimer = new Timer();
    private final TimerTask notificationUpdateTask = new TimerTask() {
        @Override
        public void run() {
            updateNotification();
        }
    };

    @Override
    public void onCreate() {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "StreamCacheDownloadService ON_CREATE", Toast.LENGTH_LONG).show();
        }

        // https://developer.android.com/guide/components/foreground-services
        // https://github.com/TeamNewPipe/NewPipe/blob/43e91ae4ae9878cdfce3f9c560612de41564962d/app/src/main/java/us/shandian/giga/service/DownloadManagerService.java
        // https://github.com/TeamNewPipe/NewPipe/blob/0039312a64e7271fec2e0a295db1d6979e83cf3f/app/src/main/java/org/schabi/newpipe/player/NotificationUtil.java
        createNotificationChannels();
        startForeground(ConfigOptions.NOTIFICATION_ID_DOWNLOAD_STREAM, buildNotificationWithProgress());

        notificationUpdateTimer.scheduleAtFixedRate(notificationUpdateTask, 0, NOTIFICATION_UPDATE_PERIOD_MS);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "StreamCacheDownloadService ON_START_COMMAND", Toast.LENGTH_LONG).show();
        }

        final StreamCacheDownloadServiceCmd cmd = StreamCacheDownloadServiceCmd.valueOf(intent.getStringExtra(PARAM_CMD));
        switch (cmd) {
            case START_ITEM: {
                final long streamCacheItemId = intent.getLongExtra(PARAM_STREAM_CACHE_ITEM_ID, StreamCache.ID_NONE);
                if (streamCacheItemId != StreamCache.ID_NONE) {
                    start(streamCacheItemId);
                }
                break;
            }
            case START_ITEMS: {
                final long[] streamCacheItemIds = intent.getLongArrayExtra(PARAM_STREAM_CACHE_ITEM_IDS);
                for (long streamCacheItemId : streamCacheItemIds) {
                    if (streamCacheItemId != StreamCache.ID_NONE) {
                        start(streamCacheItemId);
                    }
                }
                break;
            }
            case START_ALL: {
                startAll();
                break;
            }
            case PAUSE_ITEM: {
                final long streamCacheItemId = intent.getLongExtra(PARAM_STREAM_CACHE_ITEM_ID, StreamCache.ID_NONE);
                if (streamCacheItemId != StreamCache.ID_NONE) {
                    pause(streamCacheItemId);
                }
                break;
            }
            case PAUSE_ALL: {
                pauseAll();
                break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (serviceBinder == null) {
            serviceBinder = new StreamCacheDownloadServiceBinder();
        }
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "StreamCacheDownloadService ON_DESTROY", Toast.LENGTH_LONG).show();
        }
        pauseAll();
        notificationUpdateTask.cancel();
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannels() {
        // NotificationChannel - нужно добавить до того, как показывать уведомления
        // вот так не работает (требует высокий api):
        // https://developer.android.com/guide/topics/ui/notifiers/notifications#ManageChannels
        // так, как здесь, норм:
        // https://developer.android.com/training/notify-user/channels
        // https://github.com/TeamNewPipe/NewPipe/blob/53bf3420e76ef4a087c2ac33cc64c2aec094beba/app/src/main/java/org/schabi/newpipe/App.java

        final List<NotificationChannelCompat> notificationChannelCompats = new ArrayList<>();
        notificationChannelCompats.add(new NotificationChannelCompat
                .Builder(getString(R.string.notification_channel_download_stream_id),
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_download_stream_name))
                .setDescription(getString(R.string.notification_channel_download_stream_description))
                .build());

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.createNotificationChannelsCompat(notificationChannelCompats);
    }

    private void updateNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(ConfigOptions.NOTIFICATION_ID_DOWNLOAD_STREAM, buildNotificationWithProgress());
    }

    private Notification buildNotificationWithProgress() {
        // суммарный прогресс по всем активным закачкам
        long cumProgress = 0;
        long cumProgressMax = 0;

        int downloadsActive = 0;
        int downloadsTotal = 0;

        for (final TaskController taskController : taskControllerMap.values()) {
            if (taskController.getState() == TaskController.TaskState.ENQUEUED ||
                    taskController.getState() == TaskController.TaskState.ACTIVE) {
                downloadsTotal++;
            }

            if (taskController.getState() == TaskController.TaskState.ACTIVE) {
                downloadsActive++;

                // будем считать прогресс только по активным закачкам, т.к. для неактивных
                // до первого обращения может быть неизвестен размер
                cumProgress += taskController.getProgress();
                cumProgressMax += taskController.getProgressMax();
            }
        }
        final int cumProgressPercent = (int) ((double) cumProgress /
                (double) cumProgressMax * 100);

        final Intent gotoStreamCacheIntent = new Intent(this, StreamCacheActivity.class);
        final PendingIntent gotoStreamCachePendingIntent = PendingIntent.getActivity(this, 0,
                gotoStreamCacheIntent, PendingIntent.FLAG_IMMUTABLE);

        // https://developer.android.com/training/notify-user/build-notification
        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_download_stream_id))
                .setContentTitle(StringFormatUtil.formatFileSize(this, cumProgress) + " / " +
                        StringFormatUtil.formatFileSize(this, cumProgressMax))
                .setContentText(downloadsActive + " / " + downloadsTotal)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentIntent(gotoStreamCachePendingIntent)
                .setProgress(100, cumProgressPercent, false)
                .build();
    }

    public TaskController getTaskController(final long streamCacheItemId) {
        final TaskController taskController;
        if (taskControllerMap.containsKey(streamCacheItemId)) {
            taskController = taskControllerMap.get(streamCacheItemId);
        } else {
            taskController = new TaskController();
            taskControllerMap.put(streamCacheItemId, taskController);
        }

        return taskController;
    }

    /**
     * Вызвать stopSelf(), если нет активных закачек.
     *
     * Сервис завершится автоматически, если выполнено два условия:
     * - нет активного экрана, присоединенного к сервису в режиме bind
     * - все закачки завершились и сервис внутри себя вызвал метод stopSelf
     * Если закачки завершились (сервис вызвал stopSelf) в то время, как был открыт экран в режиме bind,
     * то сервис будет завершен после того, как экран будет закрыт (и при этом сделает unbind).
     * Но: если после вызова stopSelf и октрытом экране в режиме bind сервис получит любую команду
     * через startService -> onStartCommand (например, команду, которая не запускает новые закачки),
     * вызов stopService обнулится, сервис будет снова считаться запущенным и не завершит работу
     * даже в том случае, если активных закачек не будет (это значит, что не будет повода снова вызывать
     * stopSelf после остановки закачки), а экран сделает unbind и будет закрыт.
     * Таким образом, экрану, подключенному к сервису, следует перед закрытием не только делать
     * unbind, но и вызывать этот метод, который остановит сервис в том случае, если активных закачек
     * просто нет.
     */
    public void stopIfFinished() {
        if (submittedTaskCount == 0) {
            stopSelf();
        }
    }

    private void taskCountIncrement() {
        submittedTaskCount++;
    }

    /**
     * Уменьшить счетчих активных или ожидающих задач.
     *
     * Если задач больше нет, остановить сервис сразу здесь (если находимся за пределами приложения,
     * то сервис остнаовится и уведомление исчезнет, если находимся на экране закачки в состоянии bound,
     * то сервис остановится после выхода из экрана - после unbind).
     */
    private void taskCountDecrement() {
        submittedTaskCount--;
        if (submittedTaskCount == 0) {
            stopSelf();
        }
    }

    private void pause(final TaskController taskController) {
        if (taskController.getState() == TaskController.TaskState.ENQUEUED) {
            taskController.setState(TaskController.TaskState.WAIT);
        }
        // cancel делаем в любом случае на всякий случай
        // например: фоновый поток сначала проверил флаг ENQUEUED и решил запустить процесс закачки,
        // потом был вызыван этот участок кода, а уже потом фоновый поток переключил состояние на ACTIVE
        taskController.cancel();
    }

    private void pause(final long streamCacheItemId) {
        pause(getTaskController(streamCacheItemId));
    }

    private void pauseAll() {
        for(final TaskController taskController : taskControllerMap.values()) {
            pause(taskController);
        }
    }

    private void start(final long streamCacheItemId) {
        final TaskController taskController = getTaskController(streamCacheItemId);
        if (taskController.getState() == TaskController.TaskState.WAIT) {
            taskController.setState(TaskController.TaskState.ENQUEUED);
            taskCountIncrement();
            downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final StreamCache streamCacheItem = VideoDatabase.getDbInstance(StreamCacheDownloadService.this).
                            streamCacheDao().getById(streamCacheItemId);
                    // пока ждали очередь на закачку, состояние могло поменяться - например, на WAIT,
                    // в таком случае игнорировать первоначальный запрос на закачку
                    if (taskController.getState() == TaskController.TaskState.ENQUEUED) {
                        taskController.setState(TaskController.TaskState.ACTIVE);
                        VideoStreamDownloader.downloadStream(StreamCacheDownloadService.this, streamCacheItem, taskController);
                        taskController.setState(TaskController.TaskState.WAIT);
                    }

                    if (streamCacheItem.isDownloaded()) {
                        taskControllerMap.remove(streamCacheItemId);
                    }

                    taskCountDecrement();
                }
            });
        }
    }

    private void startAll() {
        // список роликов для загрузки
        for(final Long streamCacheItemId : taskControllerMap.keySet()) {
            start(streamCacheItemId);
        }
    }

    public static void startDownload(final Context context, final long streamCacheItemId) {
        // здесь запускаем закачку не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, StreamCacheDownloadService.class);
        intent.putExtra(StreamCacheDownloadService.PARAM_CMD, StreamCacheDownloadServiceCmd.START_ITEM.name());
        intent.putExtra(StreamCacheDownloadService.PARAM_STREAM_CACHE_ITEM_ID, streamCacheItemId);
        context.startService(intent);
    }

    public static void startDownload(final Context context, final List<Long> insertedIds) {
        final Intent intent = new Intent(context, StreamCacheDownloadService.class);
        // https://stackoverflow.com/questions/6175004/what-is-the-best-way-of-converting-listlong-object-to-long-array-in-java
        long[] longIds = insertedIds.stream().mapToLong(l -> l).toArray();
        intent.putExtra(StreamCacheDownloadService.PARAM_CMD, StreamCacheDownloadServiceCmd.START_ITEMS.name());
        intent.putExtra(StreamCacheDownloadService.PARAM_STREAM_CACHE_ITEM_IDS, longIds);
        context.startService(intent);
    }

    public static void startDownloads(final Context context) {
        // здесь запускаем закачку не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, StreamCacheDownloadService.class);
        intent.putExtra(StreamCacheDownloadService.PARAM_CMD, StreamCacheDownloadServiceCmd.START_ALL.name());
        context.startService(intent);
    }

    public static void pauseDownload(final Context context, final long streamCacheItemId) {
        final Intent intent = new Intent(context, StreamCacheDownloadService.class);
        intent.putExtra(StreamCacheDownloadService.PARAM_CMD, StreamCacheDownloadServiceCmd.PAUSE_ITEM.name());
        intent.putExtra(StreamCacheDownloadService.PARAM_STREAM_CACHE_ITEM_ID, streamCacheItemId);
        context.startService(intent);
    }

    public static void pauseDownloads(final Context context) {
        final Intent intent = new Intent(context, StreamCacheDownloadService.class);
        intent.putExtra(StreamCacheDownloadService.PARAM_CMD, StreamCacheDownloadServiceCmd.PAUSE_ALL.name());
        context.startService(intent);
    }
}
