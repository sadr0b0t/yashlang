package su.sadrobot.yashlang.player;

/*
 * Copyright (C) Anton Moiseev 2023 <github.com/sadr0b0t>
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

import android.app.Activity;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import su.sadrobot.yashlang.model.VideoItem;
import su.sadrobot.yashlang.view.OnListItemClickListener;

public interface RecommendationsProvider {

    RecyclerView.Adapter createVideoListAdapter(final Activity uiContext, final Context dataContext);

    RecyclerView.Adapter getVideoListAdapter();

    void setupVideoList(final Activity context, final LifecycleOwner lifecycleOwner,
                        final RecyclerView videoList, RecyclerView.AdapterDataObserver dataObserver,
                        final OnListItemClickListener<VideoItem> onItemClickListener);
}
