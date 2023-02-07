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
        void onStart();

        void onFinish();

        void onCancel();

        void onStateChange(final TaskState state);

        void onProgressChange(final long progress, final long rogressMax);

        void onStatusMsgChange(final String statusMsg, final Exception e);
    }

    public static class TaskAdapter implements TaskListener {
        public void onStart() {}

        public void onFinish() {}

        public void onCancel() {}

        public void onStateChange(final TaskState state) {}

        public void onProgressChange(final long progress, final long progressMax) {}

        public void onStatusMsgChange(final String statusMsg, final Exception e) {}
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

    private TaskListener taskListener;

    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(final boolean running) {
        boolean changed = this.running != running;
        this.running = running;
        // сбросить флаг canceled при запуске (возможно, повторном)
        if (running) {
            this.canceled = false;
            this.setStatusMsg("", null);
            this.setState(TaskState.ACTIVE);
        } else {
            this.setState(TaskState.WAIT);
        }
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
        if (this.taskListener != null) {
            taskListener.onCancel();
        }
    }

    public void setState(final TaskState state) {
        if (this.state != state) {
            this.state = state;
            if (this.taskListener != null) {
                taskListener.onStateChange(state);
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
            taskListener.onProgressChange(progress, progressMax);
        }
    }

    public long getProgressMax() {
        return progressMax;
    }

    public void setProgressMax(final long progressMax) {
        this.progressMax = progressMax;

        if (this.taskListener != null) {
            taskListener.onProgressChange(progress, progressMax);
        }
    }

    public void setStatusMsg(final String msg) {
        setStatusMsg(msg, null);
    }

    public void setStatusMsg(final String msg, final Exception e) {
        this.statusMsg = msg;
        this.exception = e;

        if (this.taskListener != null) {
            taskListener.onStatusMsgChange(msg, e);
        }
    }

    public String getStatusMsg() {
        return statusMsg;
    }

    public Exception getException() {
        return exception;
    }
}
