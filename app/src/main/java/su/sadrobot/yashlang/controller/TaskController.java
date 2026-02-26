package su.sadrobot.yashlang.controller;

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

import java.util.HashMap;
import java.util.Map;

public class TaskController {

    public enum TaskState {

        /**
         * Статус: ожидание действия пользователя
         */
        WAIT,

        /**
         * Добавлено в очередь на выполнение, но выполнение еще не началось
         */
        ENQUEUED,

        /**
         * Задача в процессе выполнения
         */
        ACTIVE
    }

    public interface TaskListener {
        void onStart(final TaskController taskController);

        void onFinish(final TaskController taskController);

        void onCancel(final TaskController taskController);

        void onReset(final TaskController taskController);

        void onStateChange(final TaskController taskController, final TaskState state);

        void onProgressChange(final TaskController taskController, final long progress, final long rogressMax);

        void onStatusMsgChange(final TaskController taskController, final String statusMsg, final Exception e);
    }

    public static class TaskAdapter implements TaskListener {
        public void onStart(final TaskController taskController) {}

        public void onFinish(final TaskController taskController) {}

        public void onCancel(final TaskController taskController) {}

        public void onReset(final TaskController taskController) {}

        public void onStateChange(final TaskController taskController, final TaskState state) {}

        public void onProgressChange(final TaskController taskController, final long progress, final long progressMax) {}

        public void onStatusMsgChange(final TaskController taskController, final String statusMsg, final Exception e) {}
    }

    public static int PROGRESS_UNDEFINED = -1;
    public static int PROGRESS_INFINITE = -1;

    private boolean running = false;
    private boolean canceled = false;
    private TaskState state = TaskState.WAIT;
    private long progress = PROGRESS_UNDEFINED;
    private long progressMax = PROGRESS_INFINITE;
    private String statusMsg = "";
    private Exception exception;
    /**
     * прочие разные атрибуты контроллера, например,
     * финальный результат выполнения задачи/
     */
    private Map<String, Object> attrs = new HashMap<>();

    private TaskListener taskListener;

    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public void removeTaskListener(final TaskListener taskListener) {
        if (this.taskListener == taskListener) {
            this.taskListener = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(final boolean running) {
        boolean changed = this.running != running;
        this.running = running;
        // сбросить флаг canceled, статус и ошибку при запуске (возможно, повторном)
        if (running) {
            this.canceled = false;
            this.setStatusMsg("", null);
        }
        if (this.taskListener != null && changed) {
            if (running) {
                this.taskListener.onStart(this);
            } else {
                this.taskListener.onFinish(this);
            }
        }
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void cancel() {
        cancel("");
    }

    public void cancel(final String msg) {
        canceled = true;
        this.setStatusMsg(msg, null);
        if (this.taskListener != null) {
            taskListener.onCancel(this);
        }
    }

    public void reset() {
        this.canceled = false;
        this.running = false;
        this.setStatusMsg("", null);
        if (this.taskListener != null) {
            taskListener.onReset(this);
        }
    }

    public void setState(final TaskState state) {
        if (this.state != state) {
            this.state = state;
            if (this.taskListener != null) {
                taskListener.onStateChange(this, state);
            }
        }
    }

    public TaskState getState() {
        return state;
    }

    public long getProgress() {
        return progress;
    }

    public void setProgress(final long progress) {
        this.progress = progress;

        if (this.taskListener != null) {
            taskListener.onProgressChange(this, progress, progressMax);
        }
    }

    public long getProgressMax() {
        return progressMax;
    }

    public void setProgressMax(final long progressMax) {
        this.progressMax = progressMax;

        if (this.taskListener != null) {
            taskListener.onProgressChange(this, progress, progressMax);
        }
    }

    public void setStatusMsg(final String msg) {
        setStatusMsg(msg, null);
    }

    public void setStatusMsg(final String msg, final Exception e) {
        this.statusMsg = msg;
        this.exception = e;

        if (this.taskListener != null) {
            taskListener.onStatusMsgChange(this, msg, e);
        }
    }

    public String getStatusMsg() {
        return statusMsg;
    }

    public Exception getException() {
        return exception;
    }

    public void setAttr(final String name, final Object attr) {
        this.attrs.put(name, attr);
    }

    public Object getAttr(final String name) {
        return this.attrs.get(name);
    }
}
