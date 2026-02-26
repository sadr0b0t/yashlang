package su.sadrobot.yashlang.service;

/*
 * Copyright (C) Anton Moiseev 2026 <github.com/sadr0b0t>
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
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.ImportPlaylistsProgressActivity;
import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.controller.ContentLoader;
import su.sadrobot.yashlang.controller.PlaylistImportTask;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;

/**
 */
public class PlaylistsImportService extends Service {
    // https://developer.android.com/guide/components/services
    // https://developer.android.com/guide/components/bound-services
    // https://developer.android.com/reference/android/os/IBinder

    public enum PlaylistsImportServiceCmd {
        ADD_PLAYLIST,
        ADD_PLAYLIST_NEW_ITEMS,
        DEVEL_MODE_START_FAKE_TASK,
        CANCEL_TASK,
        CANCEL_ALL_LIVE_TASKS,
        RETRY_TASK,
        DISMISS_TASK,
        DISMISS_ALL_FINISHED_TASKS
    }

    public static final String PARAM_CMD = "PARAM_CMD";
    public static final String PARAM_PLAYLIST_URL = "PARAM_PLAYLIST_URL";
    public static final String PARAM_PLAYLIST_ID = "PARAM_PLAYLIST_ID";
    public static final String PARAM_PLAYLIST_INFO_CACHE_NAME = "PARAM_PLAYLIST_INFO_CACHE_NAME";
    public static final String PARAM_PLAYLIST_INFO_CACHE_THUMB_URL = "PARAM_PLAYLIST_INFO_CACHE_THUMB_URL";
    public static final String PARAM_PLAYLIST_INFO_CACHE_TYPE = "PARAM_PLAYLIST_INFO_CACHE_TYPE";

    public static final String PARAM_IMPORT_TASK_ID = "PARAM_IMPORT_TASK_ID";
    public static final String PARAM_IMPORT_TASK_TYPE = "PARAM_IMPORT_TASK_TYPE_ID";

    // для режима разработки
    public static final String PARAM_DEVEL_MODE_START_FAKE_TASK_DURATION_MLS = "PARAM_DEVEL_MODE_START_FAKE_TASK_DURATION_MLS";
    public static final String PARAM_DEVEL_MODE_START_FAKE_TASK_FINISH_WITH_ERROR = "PARAM_DEVEL_MODE_START_FAKE_TASK_FINISH_WITH_ERROR";

    private static final int NOTIFICATION_UPDATE_PERIOD_MS = 1000;

    public class PlaylistsImportServiceBinder extends android.os.Binder {
        public PlaylistsImportService getService() {
            return PlaylistsImportService.this;
        }
    }

    private static long importTaskIdCounter = 0;
    private static long nextImportTaskId() {
        importTaskIdCounter++;
        return importTaskIdCounter;
    }

    private IBinder serviceBinder;
    private PlaylistsImportServiceListener serviceListener;
    // список всех задач, в т.ч. текущая выполняемая и завершенные.
    // Выполняемая будет оставаться в этом списке до перезапуска
    // сервиса или пока пользователь или логика интерфейса ее не удалит
    // (здесь важно иметь ConcurrentHashMap, т.к. с обычной HashMap можно в некоторых
    // местах поймать ConcurrentModificationException при отправке плейлистов для импорта
    // и одновременным доступом к сервису для получения информации о плейлистах)
    private final Map<Long, PlaylistImportTask> importTaskMap = new ConcurrentHashMap<>();
    private PlaylistImportTask activeImportTask = null;

    // Импортировать - скачивать и добавлять в  базу, - плейлисты последовательно в одном потоке.
    // Если скачивать параллельно еще ок, параллельные длинные транзакции уже не получится сделать
    private final ExecutorService importTaskExecutor = Executors.newSingleThreadExecutor();

    // Для коротких запросов в базу, но не для импорта с длинными транзакциями
    // (есть приём, который позволяет обращаться в базу во время длинной транзакции)
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    //
    private final Handler handler = new Handler();

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

    public interface PlaylistsImportServiceListener {
        void onImportPlaylistTaskAdded(final PlaylistImportTask importTask);
        void onImportPlaylistTaskRemoved(final PlaylistImportTask importTask);
    }

    @Override
    public void onCreate() {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlaylistsImportService ON_CREATE", Toast.LENGTH_LONG).show();
        }

        // https://developer.android.com/guide/components/foreground-services
        // https://github.com/TeamNewPipe/NewPipe/blob/43e91ae4ae9878cdfce3f9c560612de41564962d/app/src/main/java/us/shandian/giga/service/DownloadManagerService.java
        // https://github.com/TeamNewPipe/NewPipe/blob/0039312a64e7271fec2e0a295db1d6979e83cf3f/app/src/main/java/org/schabi/newpipe/player/NotificationUtil.java
        createNotificationChannels();
        startForeground(ConfigOptions.NOTIFICATION_ID_IMPORT_PLAYLIST, buildNotificationWithProgress());

        notificationUpdateTimer.scheduleAtFixedRate(notificationUpdateTask, 0, NOTIFICATION_UPDATE_PERIOD_MS);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlaylistsImportService ON_START_COMMAND", Toast.LENGTH_LONG).show();
        }

        final PlaylistsImportServiceCmd cmd = PlaylistsImportServiceCmd.valueOf(intent.getStringExtra(PARAM_CMD));
        final long importTaskId = intent.getLongExtra(PARAM_IMPORT_TASK_ID, 0);
        switch (cmd) {
            case ADD_PLAYLIST: {
                final String playlistUrl = intent.getStringExtra(PARAM_PLAYLIST_URL);

                final String playlistInfoDisplayCacheName = intent.getStringExtra(PARAM_PLAYLIST_INFO_CACHE_NAME);
                final String playlistInfoDisplayCacheTbumbUrl = intent.getStringExtra(PARAM_PLAYLIST_INFO_CACHE_THUMB_URL);
                final PlaylistInfo.PlaylistType playlistInfoDisplayCacheType =
                        PlaylistInfo.PlaylistType.valueOf(intent.getStringExtra(PARAM_PLAYLIST_INFO_CACHE_TYPE));
                if (playlistUrl != null) {
                    queueTaskAddPlaylist(importTaskId, playlistUrl,
                            playlistInfoDisplayCacheName,
                            playlistInfoDisplayCacheTbumbUrl,
                            playlistInfoDisplayCacheType);
                }
                break;
            }
            case ADD_PLAYLIST_NEW_ITEMS: {
                final long playlistId = intent.getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
                if (playlistId != PlaylistInfo.ID_NONE) {
                    queueTaskAddPlaylistNewItems(importTaskId, playlistId);
                }
                break;
            }
            case DEVEL_MODE_START_FAKE_TASK: {
                final long playlistId = intent.getLongExtra(PARAM_PLAYLIST_ID, PlaylistInfo.ID_NONE);
                final PlaylistImportTask.PlaylistImportTaskType importTaskType =
                        PlaylistImportTask.PlaylistImportTaskType.valueOf(intent.getStringExtra(PARAM_IMPORT_TASK_TYPE));
                final long taskDurationMls = intent.getLongExtra(PARAM_DEVEL_MODE_START_FAKE_TASK_DURATION_MLS, -1);
                final boolean finishWithError = intent.getBooleanExtra(PARAM_DEVEL_MODE_START_FAKE_TASK_FINISH_WITH_ERROR, false);
                queueTaskDevelModeFakeTask(importTaskId, importTaskType, playlistId, taskDurationMls, finishWithError);
                break;
            }
            case CANCEL_TASK: {
                if (importTaskId != PlaylistImportTask.ID_NONE) {
                    cancel(importTaskId);
                }
                break;
            }
            case CANCEL_ALL_LIVE_TASKS: {
                cancelAllLive();
                break;
            }
            case RETRY_TASK: {
                if (importTaskId != PlaylistImportTask.ID_NONE) {
                    retry(importTaskId);
                }
                break;
            }
            case DISMISS_TASK: {
                if (importTaskId != PlaylistImportTask.ID_NONE) {
                    dismiss(importTaskId);
                }
                break;
            }
            case DISMISS_ALL_FINISHED_TASKS: {
                dismissAllFinished();
                break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (serviceBinder == null) {
            serviceBinder = new PlaylistsImportServiceBinder();
        }
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "PlaylistsImportService ON_DESTROY", Toast.LENGTH_LONG).show();
        }
        cancelAllLive();
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
                .Builder(getString(R.string.notification_channel_import_playlists_id),
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_import_playlists_name))
                .setDescription(getString(R.string.notification_channel_import_playlists_description))
                .build());

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.createNotificationChannelsCompat(notificationChannelCompats);
    }

    private void updateNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(ConfigOptions.NOTIFICATION_ID_IMPORT_PLAYLIST, buildNotificationWithProgress());
    }

    private Notification buildNotificationWithProgress() {
        final Intent gotoImportStatusIntent = new Intent(this, ImportPlaylistsProgressActivity.class);
        final PendingIntent gotoImportStatusPendingIntent = PendingIntent.getActivity(this, 0,
                gotoImportStatusIntent, PendingIntent.FLAG_IMMUTABLE);

        final List<PlaylistImportTask> importTasks = new ArrayList<>(importTaskMap.values());
        int numImportTasks = importTaskMap.size();
        int numEnqueuedOrRunningTaks = 0;
        int numCanceledTasks = 0;
        int numTasksWithErrors = 0;
        int numDoneTasks = 0;

        for (final PlaylistImportTask importTask : importTasks) {
            if (importTask.getTaskController().getState() == TaskController.TaskState.ENQUEUED ||
                    importTask.getTaskController().getState() == TaskController.TaskState.ACTIVE) {
                numEnqueuedOrRunningTaks++;
            } else if (importTask.getTaskController().getState() == TaskController.TaskState.WAIT) {
                if (importTask.getTaskController().isCanceled()) {
                    numCanceledTasks++;
                } else if (importTask.getTaskController().getException() != null) {
                    numTasksWithErrors++;
                } else {
                    numDoneTasks++;
                }
            }
        }

        if (numImportTasks > 0) {
            final PlaylistImportTask _activeImportTask = activeImportTask;
            if (_activeImportTask != null) {
                final String contentTitle = getString(R.string.import_playlists_task_m_of_n)
                        .replace("%s", String.valueOf(numImportTasks - numEnqueuedOrRunningTaks + 1))
                        .replace("%n", String.valueOf(numImportTasks));
                final String bigContentText = getString(R.string.import_playlists_tasks_running_done_canceled_with_error)
                        .replace("%r", String.valueOf(numEnqueuedOrRunningTaks))
                        .replace("%d", String.valueOf(numDoneTasks))
                        .replace("%c", String.valueOf(numCanceledTasks))
                        .replace("%e", String.valueOf(numTasksWithErrors));

                // https://developer.android.com/training/notify-user/build-notification
                return new NotificationCompat.Builder(this, getString(R.string.notification_channel_import_playlists_id))
                        //.setContentTitle(contentTitle)
                        .setContentTitle(contentTitle)
                        // Если показывать полоску прогресса, contentText ужимается на половину верхней строки.
                        .setContentText(_activeImportTask.getTaskController().getStatusMsg())
                        // Большой текст разворачивается кликом из исходного свернутого состояния,
                        // при этом кусочек с маленьким фрагментом текста справа contentTitle исчезнет,
                        // поэтому здесь его дублируем
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(_activeImportTask.getTaskController().getStatusMsg() + "\n" + bigContentText))
                        .setSmallIcon(android.R.drawable.arrow_down_float)
                        .setContentIntent(gotoImportStatusPendingIntent)
                        // Здесь можно было бы показать прогресс импорта текущего плейлиста, но точное
                        // значение прогресса для плейлиста не доступно, т.к. мы не знаем изначально,
                        // какого размера плейлист, а качаем его страница за страницей
                        .setProgress(0, 0, true)
                        .build();
            } else {
                final String contentTitle = getString(R.string.import_playlists_n_tasks)
                        .replace("%s", String.valueOf(numImportTasks));
                final String contentText = getString(R.string.import_playlists_tasks_running_done_canceled_with_error)
                        .replace("%r", String.valueOf(numEnqueuedOrRunningTaks))
                        .replace("%d", String.valueOf(numDoneTasks))
                        .replace("%c", String.valueOf(numCanceledTasks))
                        .replace("%e", String.valueOf(numTasksWithErrors));

                // https://developer.android.com/training/notify-user/build-notification
                return new NotificationCompat.Builder(this, getString(R.string.notification_channel_import_playlists_id))
                        .setContentTitle(contentTitle)
                        // если не показывать прогресс-бар, этот текст будет сразу показан отдельной стокой целиком
                        .setContentText(contentText)
                        // на случай, если весь текст не уместится в одну строку
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                        .setSmallIcon(android.R.drawable.arrow_down_float)
                        .setContentIntent(gotoImportStatusPendingIntent)
                        .build();
            }
        } else {
            // https://developer.android.com/training/notify-user/build-notification
            return new NotificationCompat.Builder(this, getString(R.string.notification_channel_import_playlists_id))
                    .setContentTitle(getString(R.string.import_playlists_no_tasks))
                    .setSmallIcon(android.R.drawable.arrow_down_float)
                    .setContentIntent(gotoImportStatusPendingIntent)
                    .build();
        }
    }

    public void setServiceListener(final PlaylistsImportServiceListener listener) {
        this.serviceListener = listener;
    }

    public void removeServiceListener() {
        this.serviceListener = null;
    }

    public List<PlaylistImportTask> getPlaylistImportTasks() {
        return new ArrayList<>(importTaskMap.values());
    }

    public List<PlaylistImportTask> getPlaylistImportTasks(final PlaylistImportTask.PlaylistImportTaskType importTaskType) {
        List<PlaylistImportTask> tasksByType = new ArrayList<>();
        for (final PlaylistImportTask importTask : importTaskMap.values()) {
            if (importTask.getTaskType() == importTaskType) {
                tasksByType.add(importTask);
            }
        }
        return tasksByType;
    }

    public PlaylistImportTask getImportTask(final long importTaskId) {
        return importTaskMap.get(importTaskId);
    }

    public long getImportTaskIdForPlaylist(final long playlistId) {
        long importTaskId = PlaylistImportTask.ID_NONE;
        for (final Map.Entry<Long, PlaylistImportTask> taskEntry : importTaskMap.entrySet()) {
            if (taskEntry.getValue().getPlaylistInfo().getId() != PlaylistInfo.ID_NONE &&
                    taskEntry.getValue().getPlaylistInfo().getId() == playlistId) {
                importTaskId = taskEntry.getKey();
                break;
            }
        }
        return importTaskId;
    }

    public PlaylistImportTask getImportTaskForPlaylist(final long playlistId) {
        return getImportTask(getImportTaskIdForPlaylist(playlistId));
    }

    public long getImportTaskIdForPlaylistUrl(final String playlistUrl) {
        long importTaskId = PlaylistImportTask.ID_NONE;
        for (final Map.Entry<Long, PlaylistImportTask> taskEntry : importTaskMap.entrySet()) {
            if (taskEntry.getValue().getPlaylistInfo().getUrl() != null &
                    taskEntry.getValue().getPlaylistInfo().getUrl().equals(playlistUrl)) {
                importTaskId = taskEntry.getKey();
                break;
            }
        }
        return importTaskId;
    }

    public PlaylistImportTask getImportTaskForPlaylistUrl(final String playlistUrl) {
        return getImportTask(getImportTaskIdForPlaylistUrl(playlistUrl));
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

    private boolean canCancel(final TaskController taskController) {
        final boolean canCancel;
        switch (taskController.getState()) {
            case WAIT:
                // ожидаение действия пользователя
                if (!taskController.isCanceled() && taskController.getException() != null) {
                    // задача ожидает действия, при этом не отменена и есть ошибка -
                    // можно отменять
                    canCancel = true;
                } else {
                    // задача ожидает действия, при этом или уже отменена до выоплнения или
                    // не отменена и нет ошибки (значит задача выполнена)
                    // в обоих случаях отменять нельзя
                    canCancel = false;
                }
                break;
            case ENQUEUED:
                // в очереди на выполнение
                // можем отменить
                canCancel = true;
                break;
            case ACTIVE:
                // выполняется
                // можем отменить
                canCancel = true;
                break;
            default:
                // cюда никогда не попадем
                canCancel = false;
                break;
        }
        return canCancel;
    }


    private boolean canDismiss(final TaskController taskController) {
        final boolean canDismiss;
        switch (taskController.getState()) {
            case WAIT:
                // ожидаение действия пользователя
                if (taskController.isCanceled() ||
                        (!taskController.isCanceled() && taskController.getException() == null)) {
                    // задача ожидает действия, при этом задача отменена или
                    // не отменена и нет ошибки (значит задача выполнена)
                    // в обоих случаях можем скрыть
                    canDismiss = true;
                } else {
                    // задача ожидает действия, не отменена и есть ошибка
                    // скрывать нельзя
                    canDismiss = false;
                }
                break;
            case ENQUEUED:
                // в очереди на выполнение
                // скрыть нельзя (нужно сначала отменить)
                canDismiss = false;
                break;
            case ACTIVE:
                // выполняется
                // скрыть нельзя (нужно сначала отменить)
                canDismiss = false;
                break;
            default:
                // сюда никогда не попадем
                canDismiss = false;
        }
        return canDismiss;
    }

    private boolean cancel(final long importTaskId) {
        final PlaylistImportTask importTask = getImportTask(importTaskId);
        if (importTask != null && canCancel(importTask.getTaskController())) {
            // удалить задачу из importTaskExecutor не можем,
            // но выставление флага через taskController.cancel() должно сделаеть
            // так, что задача завершит выполнение сразу при старте

            if (importTask.getTaskController().isRunning()) {
                importTask.getTaskController().cancel(this.getString(R.string.task_status_msg_canceling));
            } else {
                importTask.getTaskController().cancel(this.getString(R.string.task_status_msg_task_canceled));

                // Если задача уже выполняется, здесь еще рано переводить её в состояние ожидания WAIT,
                // т.к. по факту задача еще будет продолжать выполняться какое-то время после отмены
                // и будет переведена в состояние WAIT после завершения.
                // Но если задача стоит в очереди на выполнение, она фактически еще не запущена
                // и код выполнения не будет запущен, когда до него дойдет очередь, поэтому
                // в состояние WAIT её можно переместить уже здесь, чтобы иметь возможность
                // учитывать ее как отмененную,
                importTask.getTaskController().setState(TaskController.TaskState.WAIT);
            }
            return true;
        } else {
            return false;
        }
    }

    private void cancelAllLive() {
        for (final PlaylistImportTask importTask : importTaskMap.values()) {
            if (canCancel(importTask.getTaskController())) {
                if (importTask.getTaskController().isRunning()) {
                    importTask.getTaskController().cancel(this.getString(R.string.task_status_msg_canceling));
                } else {
                    importTask.getTaskController().cancel(this.getString(R.string.task_status_msg_task_canceled));

                    // Если задача уже выполняется, здесь еще рано переводить её в состояние ожидания WAIT,
                    // т.к. по факту задача еще будет продолжать выполняться какое-то время после отмены
                    // и будет переведена в состояние WAIT после завершения.
                    // Но если задача стоит в очереди на выполнение, она фактически еще не запущена
                    // и код выполнения не будет запущен, когда до него дойдет очередь, поэтому
                    // в состояние WAIT её можно переместить уже здесь, чтобы иметь возможность
                    // учитывать ее как отмененную.
                    importTask.getTaskController().setState(TaskController.TaskState.WAIT);

                    // здесь задачу из списка не удаляем - пользователь сам решит, когда ее скрыть
                    //removeImportTask(importTask.getId());
                }
            }
        }
    }

    private void retry(final long importTaskId) {
        final PlaylistImportTask importTask = getImportTask(importTaskId);
        if (importTask != null) {
            importTask.getTaskController().reset();
            queueImportTask(importTask);
        }
    }

    private boolean dismiss(final long importTaskId) {
        final PlaylistImportTask importTask = getImportTask(importTaskId);
        if (importTask != null && canDismiss(importTask.getTaskController())) {
            removeImportTask(importTaskId);
            return true;
        } else {
            return false;
        }
    }

    private void dismissAllFinished() {
        for (final PlaylistImportTask importTask : new ArrayList<>(importTaskMap.values())) {
            if (canDismiss(importTask.getTaskController())) {
                removeImportTask(importTask.getId());
            }
        }
    }

    private void queueImportTask(final PlaylistImportTask importTask) {
        // поток выполнения задачи
        // importTaskExecutor обеспечивает последовательное выполнение в очереди

        if (!importTaskMap.containsKey(importTask.getId())) {
            // новая задача не была в очереди
            importTaskMap.put(importTask.getId(), importTask);
            taskCountIncrement();

            if (serviceListener != null) {
                serviceListener.onImportPlaylistTaskAdded(importTask);
            }
        }

        importTask.getTaskController().setState(TaskController.TaskState.ENQUEUED);
        importTaskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // могли отменить задачу до того, как начали выполнение
                if (!importTask.getTaskController().isCanceled()) {
                    activeImportTask = importTask;
                    importTask.getTaskController().setState(TaskController.TaskState.ACTIVE);
                    importTask.getTaskAction().run();
                    importTask.getTaskController().setState(TaskController.TaskState.WAIT);
                    activeImportTask = null;
                } else {
                    importTask.getTaskController().setState(TaskController.TaskState.WAIT);
                }

                // здесь количество задач не уменьшаем, т.к. завершенные задачи
                // продолжает висеть в списке до тех пор, пока не будет перезапущен
                // сервис или пользователь сам их не сбросит
                //taskCountDecrement();
            }
        });
    }

    private void removeImportTask(final long importTaskId) {
        if (importTaskMap.containsKey(importTaskId)) {
            final PlaylistImportTask importTask = importTaskMap.get(importTaskId);
            importTaskMap.remove(importTaskId);
            taskCountDecrement();

            if (serviceListener != null) {
                serviceListener.onImportPlaylistTaskRemoved(importTask);
            }
        }
    }

    private void queueTaskAddPlaylist(
            final long newImportTaskId, final String playlistUrl,
            final String playlistInfoDisplayCacheName,
            final String playlistInfoDisplayCacheTbumbUrl,
            final PlaylistInfo.PlaylistType playlistInfoDisplayCacheType) {
        // в этом месте для добавления плейлиста не нужно ничего, кроме его адреса
        // но остальную информацию могли передать в виде параметров из тех мест, где
        // эта информация уже есть
        final PlaylistInfo playlistInfo = new PlaylistInfo(playlistInfoDisplayCacheName, playlistUrl,
                playlistInfoDisplayCacheTbumbUrl, playlistInfoDisplayCacheType);
        final TaskController taskController = new TaskController();

        final PlaylistImportTask importTask = new PlaylistImportTask(
                newImportTaskId, PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_PLAYLIST,
                playlistInfo, taskController, new Runnable() {
            @Override
            public void run() {
                final long playlistId = ContentLoader.getInstance().addPlaylist(PlaylistsImportService.this,
                        playlistUrl, taskController);
                playlistInfo.setId(playlistId);
            }
        });
        queueImportTask(importTask);
    }

    private void queueTaskAddPlaylistNewItems(final long newImportTaskId, final long playlistId) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final VideoDatabase videodb = VideoDatabase.getDbInstance(PlaylistsImportService.this);
                final PlaylistInfo playlistInfo = videodb.playlistInfoDao().getById(playlistId);

                final TaskController taskController = new TaskController();
                final PlaylistImportTask importTask = new PlaylistImportTask(
                        newImportTaskId, PlaylistImportTask.PlaylistImportTaskType.ADD_NEW_ITEMS_TO_PLAYLIST,
                        playlistInfo, taskController, new Runnable() {
                    @Override
                    public void run() {
                        ContentLoader.getInstance().addPlaylistNewItems(PlaylistsImportService.this,
                                playlistId, taskController);
                    }
                });

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        queueImportTask(importTask);
                    }
                });
            }
        });
    }

    private void queueTaskDevelModeFakeTask(
            final long newImportTaskId, final PlaylistImportTask.PlaylistImportTaskType importTaskType,
            final long playlistId, final long taskDurationMls, final boolean finishWithError) {
        final PlaylistInfo playlistInfo = new PlaylistInfo("FAKE PLAYLIST", "FAKE/PLAYLIST/URL", "", "");
        playlistInfo.setId(playlistId);
        final TaskController taskController = new TaskController();

        final PlaylistImportTask importTask = new PlaylistImportTask(
                newImportTaskId, importTaskType, playlistInfo, taskController, new Runnable() {
            @Override
            public void run() {
                ContentLoader.getInstance().develModeFakeTask(taskDurationMls, finishWithError, taskController);
            }
        });
        queueImportTask(importTask);
    }

    /**
     *
     * @param context
     * @param playlistUrl адрес плейлиста онлайн
     * @param playlistInfoDisplayCache предварительно загруженная информация о плейлисте,
     *     например, по введенному url онлайн, или из файла json. При добавлении плейлиста
     *     будет запрошена повторно онлайн, но здесь может потребоваться для отображения
     *     информации о плейлисте в списке задач, пока задача находится в очереди.
     * @return
     */
    public static long addPlaylist(final Context context, final String playlistUrl, final PlaylistInfo playlistInfoDisplayCache) {
        final long newImportTaskId = nextImportTaskId();
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.ADD_PLAYLIST.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, newImportTaskId);
        intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_URL, playlistUrl);
        if (playlistInfoDisplayCache != null) {
            intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_INFO_CACHE_NAME, playlistInfoDisplayCache.getName());
            intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_INFO_CACHE_THUMB_URL, playlistInfoDisplayCache.getThumbUrl());
            intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_INFO_CACHE_TYPE, playlistInfoDisplayCache.getType().toString());
        }
        context.startService(intent);
        return newImportTaskId;
    }

    public static long addPlaylistNewItems(final Context context, final long playlistId) {
        final long newImportTaskId = nextImportTaskId();
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.ADD_PLAYLIST_NEW_ITEMS.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, newImportTaskId);
        intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_ID, playlistId);
        context.startService(intent);
        return newImportTaskId;
    }

    public static long develModeStartFakeTask(
            final Context context,
            final PlaylistImportTask.PlaylistImportTaskType importTaskType,
            final long playlistId,
            final long taskDurationMls, final boolean finishWithError) {
        final long newImportTaskId = nextImportTaskId();
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.DEVEL_MODE_START_FAKE_TASK.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, newImportTaskId);
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_TYPE, importTaskType.toString());
        intent.putExtra(PlaylistsImportService.PARAM_PLAYLIST_ID, playlistId);
        intent.putExtra(PlaylistsImportService.PARAM_DEVEL_MODE_START_FAKE_TASK_DURATION_MLS, taskDurationMls);
        intent.putExtra(PlaylistsImportService.PARAM_DEVEL_MODE_START_FAKE_TASK_FINISH_WITH_ERROR, finishWithError);
        context.startService(intent);
        return newImportTaskId;
    }

    public static void cancelPlaylistImportTask(final Context context, final long importTaskId) {
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.CANCEL_TASK.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, importTaskId);
        context.startService(intent);
    }

    public static void cancelAllLivePlaylistImportTasks(final Context context) {
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.CANCEL_ALL_LIVE_TASKS.name());
        context.startService(intent);
    }

    public static void retryPlaylistImportTask(final Context context, final long importTaskId) {
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.RETRY_TASK.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, importTaskId);
        context.startService(intent);
    }

    public static void dismissPlaylistImportTask(final Context context, final long importTaskId) {
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.DISMISS_TASK.name());
        intent.putExtra(PlaylistsImportService.PARAM_IMPORT_TASK_ID, importTaskId);
        context.startService(intent);
    }

    public static void dismissAllFinishedPlaylistImportTask(final Context context) {
        // здесь запускаем действие не через прямое обращение, а через интент, чтобы
        // сервис считался запущенным через onStartCommand и не завершался автоматически
        // сразу после выхода из активити и unbind
        final Intent intent = new Intent(context, PlaylistsImportService.class);
        intent.putExtra(PlaylistsImportService.PARAM_CMD, PlaylistsImportServiceCmd.DISMISS_ALL_FINISHED_TASKS.name());
        context.startService(intent);
    }
}
