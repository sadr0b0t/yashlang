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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemProgressControlListener;
import su.sadrobot.yashlang.view.StreamCachePagedListAdapter;


public class StreamCacheFragment extends Fragment {

    private View emptyView;
    private RecyclerView streamList;

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
        return inflater.inflate(R.layout.fragment_stream_cache, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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

        setupStreamListAdapter();
    }

    private void setupStreamListAdapter() {
        if (streamCacheItemsLiveData != null) {
            streamCacheItemsLiveData.removeObservers(this);
        }
        if (streamList.getAdapter() != null) {
            streamList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final StreamCachePagedListAdapter adapter = new StreamCachePagedListAdapter(this.getActivity(),
                new OnListItemClickListener<StreamCache>() {
                    @Override
                    public void onItemClick(final View view, final int position, final StreamCache streamCacheItem) {
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final StreamCache streamCacheItem) {
                        return true;
                    }
                },
                new OnListItemProgressControlListener<StreamCache>() {
                    @Override
                    public void onItemProgressStartClick(View view, int position, StreamCache item) {

                    }

                    @Override
                    public void onItemProgressPauseClick(View view, int position, StreamCache item) {

                    }

                    @Override
                    public void onItemRedownloadClick(final View view, final int position, final StreamCache item) {
                        StreamCacheManager.getInstance().redownload(StreamCacheFragment.this.getContext(), item);
                    }

                    @Override
                    public void onItemDeleteClick(final View view, final int position, final StreamCache item) {
                        new AlertDialog.Builder(StreamCacheFragment.this.getContext())
                                .setTitle(getString(R.string.delete_stream_title))
                                .setMessage(getString(R.string.delete_stream_message))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        StreamCacheManager.getInstance().delete(
                                                StreamCacheFragment.this.getContext(), item);
                                    }
                                })
                                .setNegativeButton(android.R.string.no, null).show();
                    }
        });
        // если список пустой, показываем специальный экранчик с сообщением
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory = VideoDatabase.getDbInstance(StreamCacheFragment.this.getContext()).
                streamCacheDao().getFinishedDs();

        streamCacheItemsLiveData = new LivePagedListBuilder(factory, config).build();

        streamCacheItemsLiveData.observe(this.getViewLifecycleOwner(), new Observer<PagedList<StreamCache>>() {
            @Override
            public void onChanged(@Nullable PagedList<StreamCache> streamCacheItems) {
                adapter.submitList(streamCacheItems);
            }
        });

        streamList.setAdapter(adapter);
    }
}
