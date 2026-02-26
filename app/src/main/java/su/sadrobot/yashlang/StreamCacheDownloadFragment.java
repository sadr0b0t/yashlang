package su.sadrobot.yashlang;

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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.controller.StreamCacheManager;
import su.sadrobot.yashlang.model.StreamCache;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.service.StreamCacheDownloadService;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnStreamCacheListItemControlListener;
import su.sadrobot.yashlang.view.StreamCacheDownloadPagedListAdapter;


public class StreamCacheDownloadFragment extends Fragment {

    private Button startAllBtn;
    private Button pauseAllBtn;
    private View emptyView;
    private RecyclerView streamList;

    private StreamCacheDownloadService streamCacheDownloadService;
    private ServiceConnection streamCacheDownloadServiceConnection;

    private LiveData<PagedList<StreamCache>> streamCacheItemsLiveData;

    private final RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            final boolean listIsEmpty = streamList.getAdapter() == null || streamList.getAdapter().getItemCount() == 0;
            emptyView.setVisibility(listIsEmpty ? View.VISIBLE : View.GONE);
            streamList.setVisibility(listIsEmpty ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onChanged() {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

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
        streamList.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
                // чтобы картинки и текст не сбивались в кучку при быстрой промотке
                // см: https://github.com/sadr0b0t/yashlang/issues/129
                return true;
            }
        });

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
    }

    @Override
    public void onStart() {
        super.onStart();

        streamCacheDownloadServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                streamCacheDownloadService = ((StreamCacheDownloadService.StreamCacheDownloadServiceBinder) service).getService();

                // текущие элементы (при первом обращении будет пустой список)
                setupStreamListAdapter();
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                streamCacheDownloadService = null;
            }
        };

        this.getContext().bindService(
                new Intent(this.getContext(), StreamCacheDownloadService.class),
                streamCacheDownloadServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (streamCacheDownloadService != null) {
            streamCacheDownloadService.stopIfFinished();
        }
        this.getContext().unbindService(streamCacheDownloadServiceConnection);

        super.onStop();
    }

    private void setupStreamListAdapter() {
        if (streamCacheItemsLiveData != null) {
            streamCacheItemsLiveData.removeObservers(this);
        }
        if (streamList.getAdapter() != null) {
            streamList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final StreamCacheDownloadPagedListAdapter adapter = new StreamCacheDownloadPagedListAdapter(
                this.getActivity(),
                streamCacheDownloadService,
                new OnListItemClickListener<StreamCache>() {
                    @Override
                    public void onItemClick(final View view, final int position, final StreamCache streamCacheItem) {
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final StreamCache streamCacheItem) {
                        return true;
                    }
                },
                new OnStreamCacheListItemControlListener() {
                    @Override
                    public void onItemProgressStartClick(View view, int position, StreamCache item) {
                        StreamCacheDownloadService.startDownload(StreamCacheDownloadFragment.this.getContext(), item.getId());
                    }

                    @Override
                    public void onItemProgressPauseClick(View view, int position, StreamCache item) {
                        StreamCacheDownloadService.pauseDownload(StreamCacheDownloadFragment.this.getContext(), item.getId());
                    }

                    @Override
                    public void onItemRedownloadClick(final View view, final int position, final StreamCache item) {
                    }

                    @Override
                    public void onItemDeleteClick(final View view, final int position, final StreamCache item) {
                        new AlertDialog.Builder(StreamCacheDownloadFragment.this.getContext())
                                .setTitle(getString(R.string.delete_stream_title))
                                .setMessage(getString(R.string.delete_stream_message))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // сначала поставим на паузу, если закачивается
                                        StreamCacheDownloadService.pauseDownload(StreamCacheDownloadFragment.this.getContext(),
                                                item.getId());
                                        // todo: здесь будет правильно дождаться, когда поток будет точно остановлен
                                        // чтобы не удалить файл, например, в момент записи
                                        StreamCacheManager.getInstance().delete(
                                                StreamCacheDownloadFragment.this.getContext(), item);

                                        Toast.makeText(StreamCacheDownloadFragment.this.getContext(),
                                                getString(R.string.stream_is_deleted),
                                                Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    }
                });
        // если список пустой, показываем специальный экранчик с сообщением
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(ConfigOptions.PAGED_LIST_PAGE_SIZE).build();

        final DataSource.Factory factory = VideoDatabase.getDbInstance(StreamCacheDownloadFragment.this.getContext()).
                streamCacheDao().getNotFinishedDs();

        streamCacheItemsLiveData = new LivePagedListBuilder(factory, config).build();

        streamCacheItemsLiveData.observe(this.getViewLifecycleOwner(), new Observer<PagedList<StreamCache>>() {
            @Override
            public void onChanged(@Nullable PagedList<StreamCache> streamCacheItems) {
                adapter.submitList(streamCacheItems);
            }
        });

        streamList.setAdapter(adapter);
    }

    private void pauseAll() {
        StreamCacheDownloadService.pauseDownloads(StreamCacheDownloadFragment.this.getContext());
    }

    private void startAll() {
        StreamCacheDownloadService.startDownloads(StreamCacheDownloadFragment.this.getContext());
    }
}
