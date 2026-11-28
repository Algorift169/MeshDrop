package com.meshdrop.ui.adapter;

import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.meshdrop.R;
import com.meshdrop.data.model.Conversation;
import com.meshdrop.messaging.PeerAvatarStore;

import java.util.Date;
import java.util.Locale;

public final class ConversationAdapter
        extends ListAdapter<Conversation, ConversationAdapter.ViewHolder> {
    public interface OnConversationClickListener {
        void onConversationClick(@NonNull Conversation conversation);
    }

    private final OnConversationClickListener clickListener;

    public ConversationAdapter(@NonNull OnConversationClickListener clickListener) {
        super(new DiffUtil.ItemCallback<Conversation>() {
            @Override
            public boolean areItemsTheSame(@NonNull Conversation oldItem,
                                           @NonNull Conversation newItem) {
                return oldItem.peerUserId.equals(newItem.peerUserId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Conversation oldItem,
                                              @NonNull Conversation newItem) {
                return oldItem.peerUsername.equals(newItem.peerUsername)
                        && oldItem.lastMessage.equals(newItem.lastMessage)
                    && oldItem.updatedAt == newItem.updatedAt
                    && oldItem.unreadCount == newItem.unreadCount;
            }
        });
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = getItem(position);
        holder.username.setText(conversation.peerUsername);
        holder.preview.setText(conversation.lastMessage.isEmpty()
                ? holder.itemView.getContext().getString(R.string.no_messages_yet)
                : conversation.lastMessage);
        holder.avatar.setText(initialFor(conversation.peerUsername));
        Uri avatarUri = PeerAvatarStore.getPeerAvatarUri(
                holder.itemView.getContext(), conversation.peerUserId);
        if (avatarUri == null) {
            holder.avatarPhoto.setVisibility(View.GONE);
            holder.avatar.setVisibility(View.VISIBLE);
        } else {
            holder.avatarPhoto.setImageURI(null);
            holder.avatarPhoto.setImageURI(avatarUri);
            holder.avatarPhoto.setVisibility(View.VISIBLE);
            holder.avatar.setVisibility(View.GONE);
        }
        holder.timestamp.setText(formatTimestamp(holder, conversation.updatedAt));
        holder.unreadDot.setVisibility(conversation.unreadCount > 0 ? View.VISIBLE : View.GONE);
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.chat_row_description, conversation.peerUsername, conversation.lastMessage));
        holder.itemView.setOnClickListener(view -> clickListener.onConversationClick(conversation));
    }

    private String initialFor(@NonNull String username) {
        String trimmed = username.trim();
        return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String formatTimestamp(@NonNull ViewHolder holder, long timestamp) {
        Date date = new Date(timestamp);
        return DateUtils.isToday(timestamp)
                ? DateFormat.getTimeFormat(holder.itemView.getContext()).format(date)
                : DateFormat.getDateFormat(holder.itemView.getContext()).format(date);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final ImageView avatarPhoto;
        final TextView username;
        final TextView preview;
        final TextView timestamp;
        final View unreadDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.conversation_avatar);
            avatarPhoto = itemView.findViewById(R.id.conversation_avatar_photo);
            username = itemView.findViewById(R.id.conversation_username);
            preview = itemView.findViewById(R.id.conversation_preview);
            timestamp = itemView.findViewById(R.id.conversation_timestamp);
            unreadDot = itemView.findViewById(R.id.conversation_unread_dot);
        }
    }
}
