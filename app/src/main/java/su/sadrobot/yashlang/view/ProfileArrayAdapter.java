package su.sadrobot.yashlang.view;

/*
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
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
    private final ListItemSwitchController<Profile> itemSwitchController;

    public static class ProfileViewHolder extends RecyclerView.ViewHolder {
        final View hasNfcView;
        final TextView nameTxt;
        final ImageButton menuBtn;
        final Switch onoffSwitch;
        final View separatorView;

        public ProfileViewHolder(final View itemView) {
            super(itemView);
            hasNfcView = itemView.findViewById(R.id.profile_has_nfc_view);
            nameTxt = itemView.findViewById(R.id.profile_name_txt);
            menuBtn = itemView.findViewById(R.id.profile_menu_btn);
            onoffSwitch = itemView.findViewById(R.id.profile_onoff_switch);
            separatorView = itemView.findViewById(R.id.separator);
        }
    }

    public ProfileArrayAdapter(final List<Profile> profiles, final List<Integer> separators,
                               final OnListItemClickListener<Profile> onItemClickListener,
                               final ListItemSwitchController<Profile> itemSwitchController) {
        this.profiles = profiles;
        if (separators != null) {
            this.separators.addAll(separators);
        }
        this.onItemClickListener = onItemClickListener;
        this.itemSwitchController = itemSwitchController;
    }

    public ProfileArrayAdapter(final List<Profile> profiles, final List<Integer> separators,
                               final OnListItemClickListener<Profile> onItemClickListener) {
        this(profiles, separators, onItemClickListener, null);
    }

    public ProfileArrayAdapter(final List<Profile> profiles,
                               final ListItemSwitchController<Profile> itemSwitchController) {
        this(profiles, null, null, itemSwitchController);
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

        if (separators.contains(position)) {
            holder.separatorView.setVisibility(View.VISIBLE);
        } else {
            holder.separatorView.setVisibility(View.GONE);
        }

        holder.nameTxt.setText(item.getName());

        if (itemSwitchController != null) {
            // состояние "вкл/выкл" будем брать из itemSwitchController
            holder.nameTxt.setEnabled(itemSwitchController.isItemChecked(item));
        }

        // обнулить слушателя событий выключателя:
        // вот это важно здесь, иначе не оберешься трудноуловимых глюков в списках с прокруткой
        holder.onoffSwitch.setOnCheckedChangeListener(null);
        if (itemSwitchController == null || !itemSwitchController.showItemCheckbox(item)) {
            // вот так - не передали слушателя вкл/выкл - прячем кнопку переключения, показываем кнопку меню
            // не вполне красиво, но плодить фгаги тоже не хоечется
            holder.menuBtn.setVisibility(View.VISIBLE);
            holder.onoffSwitch.setVisibility(View.GONE);
        } else {
            holder.menuBtn.setVisibility(View.GONE);
            holder.onoffSwitch.setVisibility(View.VISIBLE);

            holder.onoffSwitch.setChecked(itemSwitchController.isItemChecked(item));
            holder.onoffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    itemSwitchController.onItemCheckedChanged(buttonView, holder.getBindingAdapterPosition(), item, isChecked);
                    notifyItemChanged(holder.getBindingAdapterPosition());
                }
            });
        }

        if (item.hasBoundNfcTags()) {
            holder.hasNfcView.setVisibility(View.VISIBLE);
        } else {
            holder.hasNfcView.setVisibility(View.INVISIBLE);
        }

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
