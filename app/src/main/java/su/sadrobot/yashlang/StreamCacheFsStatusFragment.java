package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2022.
 *
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

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Timer;
import java.util.TimerTask;

import su.sadrobot.yashlang.controller.StreamCacheFsManager;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.util.StringFormatUtil;

/**
 *
 */
public class StreamCacheFsStatusFragment extends Fragment {

    private static final long FS_INFO_UPDATE_DELAY_MS = 1000;

    private TextView streamCacheDirPathTxt;
    private TextView streamCacheFsTotalTxt;
    private TextView streamCacheFsFinishedTxt;
    private TextView streamCacheFsNotFinishedTxt;
    private TextView streamCacheFsUnmanagedTxt;

    private final Handler handler = new Handler();

    // Обычно главные изменения кэша сопровождаются изменением в базе данных
    // (например, если удалили запись с кэшэм, или если закачка завершилась).
    // Но далеко не всё: например, в процессе закачки недокачанные
    // файлы будут расти в размере; при удалении бесхозных файлов из базы данных событий
    // тоже не будет.
    // Чтобы держать значения на экране в актуальном состоянии
    // - (во-первых, можно вообще не обновлять их в реальном времени, но это не наш метод)
    // - можно пробовать вешать слушателей на все эти события
    // - или просто зарядить раз в секунду таймер
    private final Timer fsInfoUpdateTimer = new Timer();
    private TimerTask fsInfoUpdateTask;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stream_cache_fs_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        streamCacheDirPathTxt = view.findViewById(R.id.stream_cache_dir_path_txt);
        streamCacheFsTotalTxt = view.findViewById(R.id.stream_cache_fs_total_txt);
        streamCacheFsFinishedTxt = view.findViewById(R.id.stream_cache_fs_finished_txt);
        streamCacheFsNotFinishedTxt = view.findViewById(R.id.stream_cache_fs_not_finished_txt);
        streamCacheFsUnmanagedTxt = view.findViewById(R.id.stream_cache_fs_unmanaged_txt);

        streamCacheDirPathTxt.setText(StreamCacheFsManager.getStreamCacheDir(getContext()).getPath());
    }
    @Override
    public void onResume() {
        super.onResume();

        updateStreamCacheFsInfo();
        fsInfoUpdateTask = new TimerTask() {
            @Override
            public void run() {
                updateStreamCacheFsInfo();
            }
        };
        fsInfoUpdateTimer.scheduleAtFixedRate(fsInfoUpdateTask, 0, FS_INFO_UPDATE_DELAY_MS);

    }
    @Override
    public void onPause() {
        super.onPause();
        fsInfoUpdateTask.cancel();
    }

    private void updateStreamCacheFsInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long cacheTotalFsSize = StreamCacheFsManager.getStreamCacheFsSize(getContext());
                final long cacheFinishedFsSize = VideoDatabase.getDbInstance(getContext()).streamCacheDao().getFinishedSize();
                final long cacheUnfinishedPartsFsSize = StreamCacheFsManager.getStreamCacheUnfinishedPartsFsSize(getContext());
                final long unmanagedFilesFsSize = StreamCacheFsManager.getUnmanagedFilesFsSize(getContext());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        streamCacheFsTotalTxt.setText(StringFormatUtil.formatFileSize(
                                StreamCacheFsStatusFragment.this.getContext(),
                                cacheTotalFsSize));
                        streamCacheFsFinishedTxt.setText(StringFormatUtil.formatFileSize(
                                StreamCacheFsStatusFragment.this.getContext(),
                                cacheFinishedFsSize));
                        streamCacheFsNotFinishedTxt.setText(StringFormatUtil.formatFileSize(
                                StreamCacheFsStatusFragment.this.getContext(),
                                cacheUnfinishedPartsFsSize));
                        streamCacheFsUnmanagedTxt.setText(StringFormatUtil.formatFileSize(
                                StreamCacheFsStatusFragment.this.getContext(),
                                unmanagedFilesFsSize));
                    }
                });
            }
        }).start();
    }
}
