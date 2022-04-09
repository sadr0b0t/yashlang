package su.sadrobot.yashlang;

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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import su.sadrobot.yashlang.service.StreamCacheDownloadService;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemProgressControlListener;
import su.sadrobot.yashlang.view.StreamCacheArrayAdapter;


/**
 *
 */
public class StreamCacheDownloadFragment extends Fragment {

    private Button startAllBtn;
    private Button pauseAllBtn;
    private View emptyView;
    private RecyclerView streamList;

    private final Handler handler = new Handler();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stream_cache_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        startAllBtn = view.findViewById(R.id.start_all_btn);
        pauseAllBtn = view.findViewById(R.id.pause_all_btn);
        emptyView = view.findViewById(R.id.empty_view);
        streamList = view.findViewById(R.id.stream_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this.getContext());
        streamList.setLayoutManager(linearLayoutManager);

        startAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAll();
            }
        });

        pauseAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseAll();
            }
        });

        // текущие элементы (при первом обращении будет пустой список)
        setupStreamListAdapter(StreamCacheDownloadService.getInstance().getCacheProgressList());

        // обновлять список элементов при изменении списка закачек в сервисе
        StreamCacheDownloadService.getInstance().setServiceListener(
                new StreamCacheDownloadService.StreamCacheDownloadServiceListener() {
                    @Override
                    public void onCacheProgressListChange(final List<StreamCacheDownloadService.CacheProgressItem> cacheProgressList) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                setupStreamListAdapter(cacheProgressList);
                            }
                        });
                    }
                });
        // загрузить список закачек - пока здесь
        StreamCacheDownloadService.getInstance().initCacheProgressListBg(this.getContext());
    }

    private void updateControlsVisibility() {
        if (streamList.getAdapter() == null || streamList.getAdapter().getItemCount() > 0) {
            emptyView.setVisibility(View.GONE);
            streamList.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.VISIBLE);
            streamList.setVisibility(View.GONE);
        }
    }

    private void setupStreamListAdapter(final List<StreamCacheDownloadService.CacheProgressItem> cacheProgressList) {
        final StreamCacheArrayAdapter adapter = new StreamCacheArrayAdapter(
                StreamCacheDownloadFragment.this.getActivity(),
                cacheProgressList,
                new OnListItemClickListener<StreamCacheDownloadService.CacheProgressItem>() {
                    @Override
                    public void onItemClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {

                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {
                        return true;
                    }
                },
                new OnListItemProgressControlListener<StreamCacheDownloadService.CacheProgressItem>() {
                    @Override
                    public void onItemProgressStartClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {
                        StreamCacheDownloadService.getInstance().start(StreamCacheDownloadFragment.this.getContext(), item);
                    }

                    @Override
                    public void onItemProgressPauseClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {
                        StreamCacheDownloadService.getInstance().pause(item);
                    }

                    @Override
                    public void onItemRedownloadClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {
                    }

                    @Override
                    public void onItemDeleteClick(final View view, final int position, final StreamCacheDownloadService.CacheProgressItem item) {

                        new AlertDialog.Builder(StreamCacheDownloadFragment.this.getContext())
                                .setTitle(getString(R.string.delete_stream_title))
                                .setMessage(getString(R.string.delete_stream_message))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                // сначала поставим на паузу, если закачивается
                                                StreamCacheDownloadService.getInstance().pause(item);
                                                // todo: здесь будет правильно дождаться, когда поток будет точно остановлен
                                                // чтобы не удалить файл, например, в момент записи
                                                StreamCacheDownloadService.getInstance().delete(
                                                        StreamCacheDownloadFragment.this.getContext(), item.streamCacheItem);

                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(StreamCacheDownloadFragment.this.getContext(),
                                                                getString(R.string.stream_is_deleted),
                                                                Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            }
                                        }).start();

                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    }
                });

        streamList.setAdapter(adapter);
        // emptyListObserver здесь не сработает (т.к. у нас ArrayAdapter),
        // обновим видимость элементов управления прямо здесь
        updateControlsVisibility();
    }

    private void pauseAll() {
        StreamCacheDownloadService.getInstance().pauseAll();
    }

    private void startAll() {
        StreamCacheDownloadService.getInstance().startAll(this.getContext());
    }
}
