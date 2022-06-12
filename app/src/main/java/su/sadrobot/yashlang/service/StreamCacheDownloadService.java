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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import su.sadrobot.yashlang.ConfigOptions;
import su.sadrobot.yashlang.controller.TaskController;
import su.sadrobot.yashlang.controller.VideoStreamDownloader;
import su.sadrobot.yashlang.model.StreamCache;

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

    private Map<StreamCache, TaskController> taskControllerMap = new HashMap<>();

    private final ExecutorService downloadExecutor =
            new ThreadPoolExecutor(1, 5, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>() {
                        @Override
                        public boolean offer(Runnable o) {
                            super.clear();
                            return super.offer(o);
                        }
                    });

    public TaskController getTaskController(final StreamCache streamCacheItem) {
        final TaskController taskController;
        if (taskControllerMap.containsKey(streamCacheItem)) {
            taskController = taskControllerMap.get(streamCacheItem);
        } else {
            taskController = new TaskController();
            taskControllerMap.put(streamCacheItem, taskController);
        }

        return taskController;
    }

    public void pauseAll() {
        for(final TaskController taskController : taskControllerMap.values()) {
            pause(taskController);
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

    public void pause(final StreamCache streamCacheItem) {
        pause(getTaskController(streamCacheItem));
    }

    public void startAll(final Context context) {
        downloadAll(context);
    }

    public void start(final Context context, final StreamCache streamCacheItem) {
        final TaskController taskController = getTaskController(streamCacheItem);
        if (taskController.getState() == TaskController.TaskState.WAIT) {
            taskController.setState(TaskController.TaskState.ENQUEUED);
            downloadExecutor.execute(new Runnable() {
                @Override
                public void run() {
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

    private void downloadAll(final Context context) {
        // список роликов для загрузки
        for(final StreamCache streamCacheItem : taskControllerMap.keySet()) {
            start(context, streamCacheItem);
        }
    }
}
