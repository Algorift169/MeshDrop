package com.meshdrop.ui.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.meshdrop.R;
import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;
import com.meshdrop.database.repository.ChatRepository;
import com.meshdrop.database.repository.ChatRepositoryImpl;
import com.meshdrop.ui.activity.MainActivity;

import java.util.Date;
import java.util.List;

public class ChatFragment extends Fragment {
    public static final String ARG_PEER_ID = "peer_id";
    public static final String ARG_PEER_NAME = "peer_name";
        private ChatRepository repository;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
                repository = ChatRepositoryImpl.getInstance(requireContext());
        Bundle arguments = getArguments();
                if (arguments != null && !TextUtils.isEmpty(arguments.getString(ARG_PEER_ID))) {
                        showConversation(view, arguments);
                } else {
                        showConversationList(view);
        }
        return view;
    }

        @Override
        public void onHiddenChanged(boolean hidden) {
                super.onHiddenChanged(hidden);
                if (!hidden && getView() != null && !isConversation()) {
                        showConversationList(getView());
        }
        }

        private void showConversationList(@NonNull View root) {
                ScrollView list = root.findViewById(R.id.conversation_list);
                LinearLayout rows = root.findViewById(R.id.conversation_rows);
                LinearLayout emptyState = root.findViewById(R.id.empty_state);
                rows.removeAllViews();
                list.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);

                repository.getConversations(new ChatRepository.Callback<List<Conversation>>() {
                    @Override
                    public void onSuccess(List<Conversation> conversations) {
                        if (!isCurrentView(root)) {
                            return;
                        }
                        rows.removeAllViews();
                        if (conversations.isEmpty()) {
                            emptyState.setVisibility(View.VISIBLE);
                            ((TextView) root.findViewById(R.id.empty_title)).setText(R.string.no_conversations);
                            ((TextView) root.findViewById(R.id.empty_description))
                                    .setText(R.string.chat_empty_description);
                            return;
                        }

                        emptyState.setVisibility(View.GONE);
                        list.setVisibility(View.VISIBLE);
                        for (Conversation conversation : conversations) {
                            rows.addView(createConversationRow(conversation));
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (isCurrentView(root)) {
                            showStorageError();
                        }
                    }
                });
            }

            private View createConversationRow(@NonNull Conversation conversation) {
                LinearLayout container = new LinearLayout(requireContext());
                container.setOrientation(LinearLayout.VERTICAL);

                LinearLayout row = new LinearLayout(requireContext());
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(12), dp(4), dp(12));
                row.setBackgroundResource(android.R.drawable.list_selector_background);
                row.setClickable(true);
                row.setFocusable(true);

                TextView avatar = new TextView(requireContext());
                avatar.setGravity(Gravity.CENTER);
                avatar.setText(initialFor(conversation.peerName));
                avatar.setTextColor(requireContext().getColor(R.color.background));
                avatar.setTextSize(16);
                avatar.setTypeface(null, android.graphics.Typeface.BOLD);
                GradientDrawable avatarBackground = new GradientDrawable();
                avatarBackground.setShape(GradientDrawable.OVAL);
                avatarBackground.setColor(requireContext().getColor(R.color.accent));
                avatar.setBackground(avatarBackground);
                row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout details = new LinearLayout(requireContext());
                details.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                detailsParams.leftMargin = dp(12);
                row.addView(details, detailsParams);

                LinearLayout heading = new LinearLayout(requireContext());
                heading.setGravity(Gravity.CENTER_VERTICAL);
                heading.setOrientation(LinearLayout.HORIZONTAL);
                details.addView(heading);

                TextView name = new TextView(requireContext());
                name.setText(conversation.peerName);
                name.setTextColor(requireContext().getColor(R.color.primary_text));
                name.setTextSize(16);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                name.setMaxLines(1);
                name.setEllipsize(TextUtils.TruncateAt.END);
                heading.addView(name, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView time = new TextView(requireContext());
                time.setText(formatConversationTime(conversation.updatedAt));
                time.setTextColor(requireContext().getColor(R.color.secondary_text));
                time.setTextSize(12);
                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                timeParams.leftMargin = dp(8);
                heading.addView(time, timeParams);

                TextView preview = new TextView(requireContext());
                preview.setText(conversation.lastMessage);
                preview.setTextColor(requireContext().getColor(R.color.secondary_text));
                preview.setTextSize(14);
                preview.setMaxLines(1);
                preview.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                previewParams.topMargin = dp(4);
                details.addView(preview, previewParams);

                row.setContentDescription(getString(R.string.chat_row_description,
                        conversation.peerName, conversation.lastMessage));
                row.setOnClickListener(clicked ->
                        ((MainActivity) requireActivity()).openConversation(
                                conversation.peerId, conversation.peerName));
                container.addView(row);

                View divider = new View(requireContext());
                divider.setBackgroundColor(requireContext().getColor(R.color.divider));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(64);
                container.addView(divider, dividerParams);
                return container;
            }

            @NonNull
            private String initialFor(@NonNull String name) {
                String trimmed = name.trim();
                return trimmed.isEmpty() ? "?" : trimmed.substring(0, 1).toUpperCase();
            }

            @NonNull
            private String formatConversationTime(long timestamp) {
                Date date = new Date(timestamp);
                return DateUtils.isToday(timestamp)
                        ? DateFormat.getTimeFormat(requireContext()).format(date)
                        : DateFormat.getDateFormat(requireContext()).format(date);
            }

        private void showConversation(@NonNull View root, @NonNull Bundle arguments) {
                String peerId = arguments.getString(ARG_PEER_ID, "");
                String peerName = arguments.getString(ARG_PEER_NAME, getString(R.string.peer));
                ((TextView) root.findViewById(R.id.screen_title)).setText(peerName);
                ((TextView) root.findViewById(R.id.screen_subtitle))
                                .setText(R.string.conversation_subtitle);
                root.findViewById(R.id.back_to_chats).setVisibility(View.VISIBLE);
                root.findViewById(R.id.back_to_chats).setOnClickListener(button ->
                                ((MainActivity) requireActivity()).showChatList());
                root.findViewById(R.id.empty_state).setVisibility(View.GONE);
                root.findViewById(R.id.conversation_list).setVisibility(View.GONE);
                root.findViewById(R.id.conversation_panel).setVisibility(View.VISIBLE);

                EditText input = root.findViewById(R.id.message_input);
                View sendButton = root.findViewById(R.id.send_message);
                sendButton.setOnClickListener(button -> {
                        String body = input.getText().toString().trim();
                        if (body.isEmpty() || !sendButton.isEnabled()) {
                                return;
        }
                        sendButton.setEnabled(false);
                        repository.saveMessage(peerId, peerName, body, true, System.currentTimeMillis(),
                                        new ChatRepository.Callback<Void>() {
                                                @Override
                                                public void onSuccess(Void result) {
                                                        if (!isCurrentView(root)) {
                                                                return;
                                                        }
                                                        sendButton.setEnabled(true);
                                                        input.setText("");
                                                        showMessages(root, peerId);
                                                }

                                                @Override
                                                public void onError(@NonNull Exception error) {
                                                        if (!isCurrentView(root)) {
                                                                return;
                                                        }
                                                        sendButton.setEnabled(true);
                                                        showStorageError();
                                                }
                                        });
                });
                showMessages(root, peerId);
        }

        private void showMessages(@NonNull View root, @NonNull String peerId) {
                LinearLayout messages = root.findViewById(R.id.messages_container);
                messages.removeAllViews();
                repository.getMessages(peerId, new ChatRepository.Callback<List<Message>>() {
                        @Override
                        public void onSuccess(List<Message> conversation) {
                                if (!isCurrentView(root)) {
                                        return;
                                }
                                messages.removeAllViews();
                                if (conversation.isEmpty()) {
                                        TextView empty = new TextView(requireContext());
                                        empty.setText(R.string.conversation_empty_description);
                                        empty.setTextColor(requireContext().getColor(R.color.secondary_text));
                                        empty.setTextSize(14);
                                        empty.setGravity(Gravity.CENTER);
                                        empty.setPadding(dp(16), dp(24), dp(16), dp(24));
                                        messages.addView(empty, new LinearLayout.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                        return;
                                }

                                for (Message message : conversation) {
                                        messages.addView(createMessageBubble(message));
                                }
                                ScrollView scroll = root.findViewById(R.id.messages_scroll);
                                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }

                        @Override
                        public void onError(@NonNull Exception error) {
                                if (isCurrentView(root)) {
                                        showStorageError();
                                }
        }
                });
        }

        private View createMessageBubble(@NonNull Message message) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(message.outgoing ? Gravity.END : Gravity.START);

                TextView body = new TextView(requireContext());
                body.setText(message.body);
                body.setTextColor(requireContext().getColor(
                                message.outgoing ? R.color.background : R.color.primary_text));
                body.setTextSize(15);
                body.setPadding(dp(14), dp(10), dp(14), dp(10));
                body.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
                GradientDrawable bubble = new GradientDrawable();
                bubble.setColor(requireContext().getColor(
                                message.outgoing ? R.color.accent : R.color.elevated));
                bubble.setCornerRadius(dp(16));
                body.setBackground(bubble);
                row.addView(body);

                TextView timestamp = new TextView(requireContext());
                timestamp.setText(DateFormat.getTimeFormat(requireContext()).format(new Date(message.createdAt)));
                timestamp.setTextColor(requireContext().getColor(R.color.secondary_text));
                timestamp.setTextSize(11);
                timestamp.setGravity(message.outgoing ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams timestampParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                timestampParams.topMargin = dp(3);
                row.addView(timestamp, timestampParams);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(10);
                row.setLayoutParams(rowParams);
                return row;
        }

        private void showStorageError() {
                Toast.makeText(requireContext(), R.string.chat_storage_error, Toast.LENGTH_SHORT).show();
        }

        private boolean isCurrentView(@NonNull View root) {
                return isAdded() && getView() == root;
        }

        private boolean isConversation() {
                Bundle arguments = getArguments();
                return arguments != null && !TextUtils.isEmpty(arguments.getString(ARG_PEER_ID));
        }

        private int dp(int value) {
                return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }
}
