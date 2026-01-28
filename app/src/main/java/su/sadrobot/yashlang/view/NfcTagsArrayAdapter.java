package su.sadrobot.yashlang.view;

/*
 * Copyright (C) Anton Moiseev 2026 <github.com/sadr0b0t>
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

import java.util.List;

import su.sadrobot.yashlang.R;
import su.sadrobot.yashlang.model.ProfileNfcTags;

public class NfcTagsArrayAdapter extends RecyclerView.Adapter<NfcTagsArrayAdapter.NfcTagsViewHolder> {
    // https://guides.codepath.com/android/Paging-Library-Guide
    // https://github.com/codepath/android_guides/wiki/Using-the-RecyclerView#using-with-listadapter
    // https://developer.android.com/reference/android/support/v7/recyclerview/extensions/ListAdapter.html
    // https://developer.android.com/topic/libraries/architecture/paging/

    private final List<ProfileNfcTags> profileNfcTags;
    private final OnListItemClickListener<ProfileNfcTags> onItemClickListener;

    public static class NfcTagsViewHolder extends RecyclerView.ViewHolder {
        final TextView nfcTagIdTxt;
        final TextView nfcTagLabelTxt;
        final ImageButton menuBtn;

        public NfcTagsViewHolder(final View itemView) {
            super(itemView);
            nfcTagIdTxt = itemView.findViewById(R.id.nfc_tag_id_txt);
            nfcTagLabelTxt = itemView.findViewById(R.id.nfc_tag_label_txt);
            menuBtn = itemView.findViewById(R.id.nfc_tag_menu_btn);
        }
    }

    public NfcTagsArrayAdapter(final List<ProfileNfcTags> profiles,
                               final OnListItemClickListener<ProfileNfcTags> onItemClickListener) {
        this.profileNfcTags = profiles;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public NfcTagsViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.profile_nfc_tag_list_item, parent, false);
        return new NfcTagsViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final NfcTagsViewHolder holder, final int position) {
        final ProfileNfcTags item = profileNfcTags.get(position);

        holder.nfcTagIdTxt.setText(item.getNfcTagId());
        holder.nfcTagLabelTxt.setText(item.getNfcTagLabel());

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
        return profileNfcTags.size();
    }
}
