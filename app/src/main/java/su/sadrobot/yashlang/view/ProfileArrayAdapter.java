package su.sadrobot.yashlang.view;

/*
 * Created by Anton Moiseev (sadr0b0t) in 2019.
 *
 * Copyright (C) Anton Moiseev 2019 <github.com/sadr0b0t>
 * VideoItemPagedListAdapter.java is part of YaShlang.
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.Profile;

public class ProfileArrayAdapter extends RecyclerView.Adapter<ProfileArrayAdapter.ProfileViewHolder> {
    // https://guides.codepath.com/android/Paging-Library-Guide
    // https://github.com/codepath/android_guides/wiki/Using-the-RecyclerView#using-with-listadapter
    // https://developer.android.com/reference/android/support/v7/recyclerview/extensions/ListAdapter.html
    // https://developer.android.com/topic/libraries/architecture/paging/

    private final List<Profile> profiles;
    private final List<Integer> separators = new ArrayList<>();
    private final OnListItemClickListener<Profile> onItemClickListener;

    public static class ProfileViewHolder extends RecyclerView.ViewHolder {
        final TextView nameTxt;
        final ImageButton menuBtn;
        final View separatorView;

        public ProfileViewHolder(final View itemView) {
            super(itemView);
            nameTxt = itemView.findViewById(R.id.profile_name_txt);
            menuBtn = itemView.findViewById(R.id.profile_menu_btn);
            separatorView = itemView.findViewById(R.id.separator);
        }
    }

    public ProfileArrayAdapter(final List<Profile> profiles, final List<Integer> separators, final OnListItemClickListener<Profile> onItemClickListener) {
        this.profiles = profiles;
        this.onItemClickListener = onItemClickListener;
        if(separators != null) {
            this.separators.addAll(separators);
        }
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_list_item, parent, false);
        return new ProfileViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final ProfileViewHolder holder, final int position) {
        final Profile item = profiles.get(position);

        if(separators.contains(position)) {
            holder.separatorView.setVisibility(View.VISIBLE);
        } else {
            holder.separatorView.setVisibility(View.GONE);
        }

        holder.nameTxt.setText(item.getName());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(view, holder.getBindingAdapterPosition(), item);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View view) {
                if (onItemClickListener != null) {
                    return onItemClickListener.onItemLongClick(view, holder.getBindingAdapterPosition(), item);
                } else {
                    return false;
                }
            }
        });

        holder.menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(holder.menuBtn, holder.getBindingAdapterPosition(), item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }
}
