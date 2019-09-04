package su.sadrobot.yashlang;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * HistoryActivity.java is part of YaShlang.
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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.model.VideoDatabase;
import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;
import su.sadrobot.yashlang.view.VideoItemPagedListAdapter;

/**
 *
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView videoList;

    private Handler handler = new Handler();

    private LiveData<PagedList<VideoItem>> videoItemsLiveData;
    private VideoDatabase videodb;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);

        videoList = findViewById(R.id.video_list);

        // set a LinearLayoutManager with default vertical orientation
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        videoList.setLayoutManager(linearLayoutManager);

        // подключимся к базе один раз при создании активити,
        // закрывать подключение в onDestroy
        videodb = VideoDatabase.getDb(HistoryActivity.this);
    }


    @Override
    protected void onResume() {
        super.onResume();

        setupVideoListAdapter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (videodb != null) {
            videodb.close();
        }
    }

    private void setupVideoListAdapter() {
        if (videoItemsLiveData != null) {
            videoItemsLiveData.removeObservers(this);
        }

        final VideoItemPagedListAdapter adapter = new VideoItemPagedListAdapter(this,
                new OnListItemClickListener<VideoItem>() {
                    @Override
                    public void onItemClick(View view, int position, VideoItem item) {
                        final Intent intent = new Intent(HistoryActivity.this, WatchVideoActivity.class);
                        intent.putExtra(WatchVideoActivity.PARAM_VIDEO_ID, item.getId());
                        startActivity(intent);
                    }

                    @Override
                    public boolean onItemLongClick(View view, int position, VideoItem item) {
                        Toast.makeText(HistoryActivity.this, position + ":" +
                                        item.getId() + ":" + item.getThumbUrl(),
                                Toast.LENGTH_LONG).show();
                        return false;
                    }
                }, null);

        // Initial page size to fetch can also be configured here too
        final PagedList.Config config = new PagedList.Config.Builder().setPageSize(20).build();

        final DataSource.Factory factory =
                videodb.videoItemDao().getHistoryOrderByLastViewedAtDs();

        videoItemsLiveData = new LivePagedListBuilder(factory, config).build();

        videoItemsLiveData.observe(this, new Observer<PagedList<VideoItem>>() {
            @Override
            public void onChanged(@Nullable PagedList<VideoItem> videos) {
                adapter.submitList(videos);
            }
        });

        videoList.setAdapter(adapter);
    }
}
