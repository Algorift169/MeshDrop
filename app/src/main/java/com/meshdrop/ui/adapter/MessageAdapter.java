package com.meshdrop.ui.adapter;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.meshdrop.R;
import com.meshdrop.data.model.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class MessageAdapter extends ListAdapter<Message, MessageAdapter.ViewHolder> {
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    public MessageAdapter() {
        super(new DiffUtil.ItemCallback<Message>() {
            @Override
            public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                return oldItem.messageId.equals(newItem.messageId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
                return oldItem.body.equals(newItem.body)
                        && oldItem.deliveryStatus.equals(newItem.deliveryStatus)
                        && oldItem.createdAt == newItem.createdAt;
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).outgoing ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = getItem(position);
        holder.body.setText(message.body);
        String time = DateFormat.getTimeFormat(holder.itemView.getContext())
                .format(new Date(message.createdAt));
        if (message.outgoing) {
            holder.metadata.setText(time + "  "
                + statusLabel(holder.itemView, message.deliveryStatus));
        } else {
            holder.metadata.setText(time);
        }
    }

    public void addOrUpdate(@NonNull Message message) {
        List<Message> updated = new ArrayList<>(getCurrentList());
        int existingIndex = -1;
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).messageId.equals(message.messageId)) {
                existingIndex = index;
                break;
            }
        }
        if (existingIndex >= 0) updated.set(existingIndex, message);
        else updated.add(message);
        Collections.sort(updated, (first, second) -> {
            int timeComparison = Long.compare(first.createdAt, second.createdAt);
            return timeComparison != 0 ? timeComparison : first.messageId.compareTo(second.messageId);
        });
        submitList(updated);
    }

    public void mergeHistory(@NonNull List<Message> history) {
        List<Message> merged = new ArrayList<>(getCurrentList());
        for (Message message : history) {
            int existingIndex = -1;
            for (int index = 0; index < merged.size(); index++) {
                if (merged.get(index).messageId.equals(message.messageId)) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex < 0) merged.add(message);
            else merged.set(existingIndex, message);
        }
        Collections.sort(merged, (first, second) -> {
            int timeComparison = Long.compare(first.createdAt, second.createdAt);
            return timeComparison != 0 ? timeComparison : first.messageId.compareTo(second.messageId);
        });
        submitList(merged);
    }

    public void updateDeliveryStatus(@NonNull String messageId, @NonNull String status) {
        List<Message> updated = new ArrayList<>(getCurrentList());
        for (int index = 0; index < updated.size(); index++) {
            Message current = updated.get(index);
            if (current.messageId.equals(messageId)) {
                updated.set(index, new Message(current.messageId, current.conversationId,
                        current.senderUserId, current.receiverUserId, current.body,
                        current.outgoing, current.createdAt, status, current.messageType));
                submitList(updated);
                return;
            }
        }
    }

    @NonNull
    private String statusLabel(@NonNull View view, @NonNull String status) {
        switch (status) {
            case "SENDING": return view.getContext().getString(R.string.message_status_sending);
            case "SENT": return view.getContext().getString(R.string.message_status_sent);
            case "DELIVERED": return view.getContext().getString(R.string.message_status_delivered);
            case "FAILED": return view.getContext().getString(R.string.message_status_failed);
            default: return view.getContext().getString(R.string.message_status_pending);
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView body;
        final TextView metadata;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            body = itemView.findViewById(R.id.message_body);
            metadata = itemView.findViewById(R.id.message_metadata);
        }
    }
}
