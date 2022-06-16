package su.sadrobot.yashlang.service;

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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoStreamDownloader;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;

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

    public class StreamCacheDownloadServiceBinder extends android.os.Binder {
        public StreamCacheDownloadService getService(){
            return StreamCacheDownloadService.this;
        }
    }

    private IBinder serviceBinder;

    private Map<Long, TaskController> taskControllerMap = new HashMap<>();

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(ConfigOptions.MAX_STREAM_CACHE_DOWNLOADS);

    @Override
    public void onCreate() {
        if (ConfigOptions.DEVEL_MODE_ON) {
            Toast.makeText(this, "StreamCacheDownloadService START", Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(this, "StreamCacheDownloadService FINISH", Toast.LENGTH_LONG).show();
        }
        pauseAll();
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

    private void pause(final TaskController taskController) {
        if (taskController.getState() == TaskController.TaskState.ENQUEUED) {
            taskController.setState(TaskController.TaskState.WAIT);
        }
        // cancel делаем в любом случае на всякий случай
        // например: фоновый поток сначала проверил флаг ENQUEUED и решил запустить процесс закачки,
        // потом был вызыван этот участок кода, а уже потом фоновый поток переключил состояние на ACTIVE
        taskController.cancel();
    }

    public void pause(final long streamCacheItemId) {
        pause(getTaskController(streamCacheItemId));
    }

    public void pauseAll() {
        for(final TaskController taskController : taskControllerMap.values()) {
            pause(taskController);
        }
    }

    public void start(final Context context, final long streamCacheItemId) {
        final TaskController taskController = getTaskController(streamCacheItemId);
        if (taskController.getState() == TaskController.TaskState.WAIT) {
            taskController.setState(TaskController.TaskState.ENQUEUED);
            downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final StreamCache streamCacheItem = VideoDatabase.getDbInstance(StreamCacheDownloadService.this).
                            streamCacheDao().getById(streamCacheItemId);
                    // пока ждали очедь на закачку, состояние могло поменяться - например, на WAIT,
                    // в таком случае игнорировать первоначальный запрос на закачку
                    if (taskController.getState() == TaskController.TaskState.ENQUEUED) {
                        taskController.setState(TaskController.TaskState.ACTIVE);
                        VideoStreamDownloader.downloadStream(context, streamCacheItem, taskController);
                    }
                }
            });
        }
    }

    public void startAll(final Context context) {
        // список роликов для загрузки
        for(final Long streamCacheItemId : taskControllerMap.keySet()) {
            start(context, streamCacheItemId);
        }
    }
}
