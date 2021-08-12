package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import su.sadrobot.yashlang.model.PlaylistInfo;
import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.OnListItemSwitchListener;
import su.sadrobot.yashlang.view.PlaylistInfoPagedListAdapter;

/**
 *
 */
public class ConfigurePlaylistsFragment extends Fragment {

    private Button addPlaylistBtn;

    // Экран с пустым списком
    private View emptyView;
    private Button addRecommendedBtn;

    //
    private EditText filterPlaylistListInput;
    private RecyclerView playlistList;

    private LiveData<PagedList<PlaylistInfo>> playlistInfosLiveData;

    private RecyclerView.AdapterDataObserver emptyListObserver = new RecyclerView.AdapterDataObserver() {
        // https://stackoverflow.com/questions/47417645/empty-view-on-a-recyclerview
        // https://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview
        // https://gist.github.com/sheharyarn/5602930ad84fa64c30a29ab18eb69c6e
        private void checkIfEmpty() {
            updateControlsVisibility();
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
        return inflater.inflate(R.layout.fragment_configure_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        addPlaylistBtn = view.findViewById(R.id.add_playlist_btn);

        emptyView = view.findViewById(R.id.empty_view);
        addRecommendedBtn = view.findViewById(R.id.add_recommended_btn);

        filterPlaylistListInput = view.findViewById(R.id.filter_playlist_list_input);
        playlistList = view.findViewById(R.id.playlist_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        playlistList.setLayoutManager(linearLayoutManager);

        addPlaylistBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConfigurePlaylistsFragment.this.getContext(), AddPlaylistActivity.class));
            }
        });

        addRecommendedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ConfigurePlaylistsFragment.this.getContext(), AddRecommendedPlaylistsActivity.class));
            }
        });

        filterPlaylistListInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                setupPlaylistInfoPagedListAdapter(v.getText().toString().trim());

                return false;
            }
        });

        filterPlaylistListInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupPlaylistInfoPagedListAdapter(s.toString().trim());
            }
        });

        setupPlaylistInfoPagedListAdapter(null);
    }

    private void updateControlsVisibility() {
        // считаем, что список пустой только если в поле фильтра ничего не введено
        final boolean listIsEmpty = filterPlaylistListInput.getText().length() == 0 &&
                (playlistList.getAdapter() == null || playlistList.getAdapter().getItemCount() == 0);
        if(listIsEmpty) {
            emptyView.setVisibility(View.VISIBLE);
            filterPlaylistListInput.setVisibility(View.GONE);
            playlistList.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            filterPlaylistListInput.setVisibility(View.VISIBLE);
            playlistList.setVisibility(View.VISIBLE);
        }
    }

    private void setupPlaylistInfoPagedListAdapter(final String sstr) {
        if (playlistInfosLiveData != null) {
            playlistInfosLiveData.removeObservers(this);
        }
        if (playlistList.getAdapter() != null) {
            playlistList.getAdapter().unregisterAdapterDataObserver(emptyListObserver);
        }

        final PlaylistInfoPagedListAdapter adapter = new PlaylistInfoPagedListAdapter(this.getActivity(),
                new OnListItemClickListener<PlaylistInfo>() {
                    @Override
                    public void onItemClick(final View view, final int position, final PlaylistInfo item) {
                        final Intent intent = new Intent(ConfigurePlaylistsFragment.this.getContext(), ConfigurePlaylistActivity.class);
                        intent.putExtra(ConfigurePlaylistActivity.PARAM_PLAYLIST_ID, item.getId());
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(final View view, final int position, final PlaylistInfo plInfo) {

                        // параметр Gravity.CENTER не работает (и появился еще только в API 19+),
                        // работает только вариант Gravity.RIGHT
                        //final PopupMenu popup = new PopupMenu(ConfigurePlaylistsActivity.this, view, Gravity.CENTER);
                        final PopupMenu popup = new PopupMenu(ConfigurePlaylistsFragment.this.getContext(),
                                view.findViewById(R.id.playlist_name_txt));
                        popup.getMenuInflater().inflate(R.menu.playlist_item_actions, popup.getMenu());
                        popup.setOnMenuItemClickListener(
                                new PopupMenu.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(final MenuItem item) {
                                        switch (item.getItemId()) {
                                            case R.id.action_copy_playlist_name: {
                                                final ClipboardManager clipboard = (ClipboardManager)
                                                        ConfigurePlaylistsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(plInfo.getName(), plInfo.getName());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistsFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + plInfo.getName(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                            case R.id.action_copy_playlist_url: {
                                                final ClipboardManager clipboard = (ClipboardManager)
                                                        ConfigurePlaylistsFragment.this.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                                final ClipData clip = ClipData.newPlainText(plInfo.getUrl(), plInfo.getUrl());
                                                clipboard.setPrimaryClip(clip);

                                                Toast.makeText(ConfigurePlaylistsFragment.this.getContext(),
                                                        getString(R.string.copied) + ": " + plInfo.getUrl(),
                                                        Toast.LENGTH_LONG).show();
                                                break;
                                            }
                                        }
                                        return true;
                                    }
                                }
                        );
                        popup.show();
                        return true;
                    }
                },
                new OnListItemSwitchListener<PlaylistInfo>() {
                    @Override
                    public void onItemCheckedChanged(final CompoundButton buttonView, final int position,
                                                     final PlaylistInfo item, final boolean isChecked) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                VideoDatabase.getDbInstance(getContext()).
                                        playlistInfoDao().setEnabled(item.getId(), isChecked);
                            }
                        }).start();
                    }

                    @Override
                    public boolean isItemChecked(final PlaylistInfo item) {
                        return item.isEnabled();
                    }
                });
        // если список пустой, показываем специальный экранчик с кнопками
        adapter.registerAdapterDataObserver(emptyListObserver);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();
        final DataSource.Factory factory;
        if (sstr != null && !sstr.isEmpty()) {
            factory = VideoDatabase.getDbInstance(this.getContext()).playlistInfoDao().searchAllDs(sstr);
        } else {
            factory = VideoDatabase.getDbInstance(this.getContext()).playlistInfoDao().getAllDs();
        }
        playlistInfosLiveData = new LivePagedListBuilder(factory, config).build();

        playlistInfosLiveData.observe(this, new Observer<PagedList<PlaylistInfo>>() {
            @Override
            public void onChanged(@Nullable PagedList<PlaylistInfo> videos) {
                adapter.submitList(videos);
            }
        });

        playlistList.setAdapter(adapter);
    }
}
