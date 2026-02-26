package su.sadrobot.yashlang.controller;

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

import su.sadrobot.yashlang.model.PlaylistInfo;

public class PlaylistImportTask {
    public static long ID_NONE = 0;

    public enum PlaylistImportTaskType {
        ADD_NEW_PLAYLIST, ADD_NEW_ITEMS_TO_PLAYLIST
    }

    private long _id = ID_NONE;
    private PlaylistImportTaskType taskType;
    private PlaylistInfo playlistInfo;
    private TaskController taskController;
    private Runnable taskAction;

    public PlaylistImportTask(
            final long taskId,
            final PlaylistImportTaskType taskType,
            final PlaylistInfo playlistInfo,
            final TaskController taskController,
            final Runnable taskAction) {
        this._id = taskId;
        this.taskType = taskType;
        this.playlistInfo = playlistInfo;
        this.taskController = taskController;
        this.taskAction = taskAction;
    }

    public long getId() {
        return _id;
    }

    public PlaylistImportTaskType getTaskType() {
        return taskType;
    }

    public PlaylistInfo getPlaylistInfo() {
        return playlistInfo;
    }

    public TaskController getTaskController() {
        return taskController;
    }

    public Runnable getTaskAction() {
        return taskAction;
    }
}
