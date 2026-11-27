package com.meshdrop.ui.adapter;

import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.meshdrop.data.model.DiscoveredDevice;

import java.util.ArrayList;
import java.util.List;

public final class DiscoveredDeviceAdapter
        extends RecyclerView.Adapter<DiscoveredDeviceAdapter.ViewHolder> {
    private final List<DiscoveredDevice> devices = new ArrayList<>();

    public void submitDevices(@NonNull List<DiscoveredDevice> newDevices) {
        devices.clear();
        devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = new TextView(parent.getContext());
        view.setPadding(0, 8, 0, 8);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiscoveredDevice device = devices.get(position);
        holder.textView.setText(device.getDeviceName());
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        ViewHolder(@NonNull TextView itemView) {
            super(itemView);
            textView = itemView;
        }
    }
}
