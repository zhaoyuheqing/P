package com.github.tvbox.osc.ui.panel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class LiveChannelListPanel {

    public interface ChannelListListener {
        void onGroupSelected(int groupIndex);
        void onChannelSelected(int groupIndex, int channelIndex);
        void onEpgModeChanged(boolean isEpg);
        void onEpgItemClicked(Epginfo epgItem, int selectedDateIndex);

        List<LiveChannelGroup> getChannelGroups();
        List<LiveChannelItem> getLiveChannels(int groupIndex);
        int getCurrentGroupIndex();
        int getCurrentChannelIndex();
        void updateCurrentChannel(int groupIndex, int channelIndex);
        boolean isNeedInputPassword(int groupIndex);
    }

    private final WeakReference<Context> contextRef;
    private final Handler handler;

    private final WeakReference<LinearLayout> rootViewRef;
    private final WeakReference<TvRecyclerView> groupViewRef;
    private final WeakReference<TvRecyclerView> channelViewRef;

    private final WeakReference<LinearLayout> groupEpgRef;
    private final WeakReference<LinearLayout> divLeftRef;
    private final WeakReference<LinearLayout> divRightRef;
    private final WeakReference<TvRecyclerView> epgDateViewRef;
    private final WeakReference<TvRecyclerView> epgInfoViewRef;

    private LiveChannelGroupAdapter groupAdapter;
    private LiveChannelItemAdapter channelAdapter;

    private ChannelListListener listener;
    private boolean isShowing = false;
    private boolean isEpgMode = false;

    private final Runnable hideRunnable = this::hideInternal;

    private final Runnable focusCurrentChannelRunnable = new Runnable() {
        private int retryCount = 0;
        private static final int MAX_RETRY = 15;

        @Override
        public void run() {
            TvRecyclerView groupView = groupViewRef.get();
            TvRecyclerView channelView = channelViewRef.get();
            if (groupView == null || channelView == null || listener == null) return;

            int groupIdx = listener.getCurrentGroupIndex();
            int channelIdx = listener.getCurrentChannelIndex();

            if (groupAdapter != null) {
                groupAdapter.setSelectedGroupIndex(groupIdx);
                groupAdapter.setFocusedGroupIndex(groupIdx);
            }
            if (channelAdapter != null) {
                channelAdapter.setSelectedChannelIndex(channelIdx);
                channelAdapter.setFocusedChannelIndex(channelIdx);
            }

            if (groupView.isScrolling() || channelView.isScrolling() ||
                    groupView.isComputingLayout() || channelView.isComputingLayout()) {
                if (retryCount++ < MAX_RETRY) {
                    handler.postDelayed(this, 80);
                }
                return;
            }

            groupView.scrollToPosition(groupIdx);
            groupView.setSelection(groupIdx);
            channelView.scrollToPosition(channelIdx);
            channelView.setSelection(channelIdx);

            if (channelIdx >= 0 && channelIdx < channelAdapter.getItemCount()) {
                RecyclerView.ViewHolder holder = channelView.findViewHolderForAdapterPosition(channelIdx);
                if (holder != null && holder.itemView != null) {
                    holder.itemView.requestFocus();
                    retryCount = 0;
                    return;
                }
            }

            if (retryCount++ < MAX_RETRY) {
                handler.postDelayed(this, 100);
            } else {
                retryCount = 0;
            }
        }
    };

    public LiveChannelListPanel(@NonNull Context context, @NonNull Handler handler,
                                @NonNull LinearLayout rootView, @NonNull TvRecyclerView groupView,
                                @NonNull TvRecyclerView channelView, @NonNull LinearLayout groupEpg,
                                @NonNull LinearLayout divLeft, @NonNull LinearLayout divRight,
                                @NonNull TvRecyclerView epgDate, @NonNull TvRecyclerView epgInfo) {
        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupViewRef = new WeakReference<>(groupView);
        this.channelViewRef = new WeakReference<>(channelView);
        this.groupEpgRef = new WeakReference<>(groupEpg);
        this.divLeftRef = new WeakReference<>(divLeft);
        this.divRightRef = new WeakReference<>(divRight);
        this.epgDateViewRef = new WeakReference<>(epgDate);
        this.epgInfoViewRef = new WeakReference<>(epgInfo);
        rootView.setVisibility(View.INVISIBLE);
    }

    public void setListener(ChannelListListener listener) {
        this.listener = listener;
    }

    public void init() {
        initGroupView();
        initChannelView();
    }

    public void syncHighlightFromActivity(int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
            groupAdapter.setFocusedGroupIndex(groupIndex);
        }
        if (channelAdapter != null) {
            channelAdapter.setSelectedChannelIndex(channelIndex);
            channelAdapter.setFocusedChannelIndex(channelIndex);
        }
    }

    public void refreshFull(List<LiveChannelGroup> groups, int groupIndex, int channelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }
        List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(channelIndex);
        }
    }

    public void updateSelectionAndScroll(int groupIndex, int channelIndex) {
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) channelAdapter.setSelectedChannelIndex(channelIndex);
    }

    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (isEpgMode) showChannelMode();
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);

        int targetIndex = (groupIndex == listener.getCurrentGroupIndex())
                ? listener.getCurrentChannelIndex() : 0;

        List<LiveChannelItem> channels = listener != null ? listener.getLiveChannels(groupIndex) : new ArrayList<>();
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels);
            channelAdapter.setSelectedChannelIndex(targetIndex);
        }
        syncHighlightFromActivity(groupIndex, targetIndex);
    }

    public void notifyEpgClicked(Epginfo item, int dateIndex) {
        if (listener != null) listener.onEpgItemClicked(item, dateIndex);
    }

    public void showEpgMode() {
        if (isEpgMode) return;
        isEpgMode = true;
        setEpgViewsVisible(true);
        setChannelViewsVisible(false);
        if (listener != null) listener.onEpgModeChanged(true);
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    public void showChannelMode() {
        if (!isEpgMode) return;
        isEpgMode = false;
        setEpgViewsVisible(false);
        setChannelViewsVisible(true);
        if (listener != null) {
            listener.onEpgModeChanged(false);
            refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
        handler.postDelayed(focusCurrentChannelRunnable, 220);
    }

    public void show() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null) return;

        if (isEpgMode) showEpgMode();
        else showChannelMode();

        if (rootView.getVisibility() != View.VISIBLE) {
            rootView.setVisibility(View.VISIBLE);
            rootView.setAlpha(0.0f);
            rootView.setTranslationX(-rootView.getWidth() / 2f);
            rootView.animate()
                    .translationX(0)
                    .alpha(1.0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            handler.postDelayed(focusCurrentChannelRunnable, 100);
                        }
                    });
        }

        isShowing = true;
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    private void setEpgViewsVisible(boolean visible) {
        LinearLayout groupEpg = groupEpgRef.get();
        LinearLayout divLeft = divLeftRef.get();
        TvRecyclerView epgInfo = epgInfoViewRef.get();
        if (groupEpg != null) groupEpg.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (divLeft != null) divLeft.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (epgInfo != null) epgInfo.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setChannelViewsVisible(boolean visible) {
        LinearLayout divRight = divRightRef.get();
        TvRecyclerView groupView = groupViewRef.get();
        if (divRight != null) divRight.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (groupView != null) groupView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void initGroupView() {
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView == null) return;
        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));
        groupAdapter = new LiveChannelGroupAdapter();
        groupView.setAdapter(groupAdapter);
        groupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(position);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                FastClickCheckUtil.check(itemView);
                if (listener != null) listener.onGroupSelected(position);
            }
        });
        groupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (listener != null) listener.onGroupSelected(position);
        });
    }

    private void initChannelView() {
        TvRecyclerView channelView = channelViewRef.get();
        if (channelView == null) return;
        channelView.setHasFixedSize(true);
        channelView.setLayoutManager(new V7LinearLayoutManager(channelView.getContext(), 1, false));
        channelAdapter = new LiveChannelItemAdapter();
        channelView.setAdapter(channelAdapter);
        channelView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}
            @Override public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(position);
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }
            @Override public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickChannel(position);
            }
        });
        channelAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickChannel(position);
        });
    }

    private void clickChannel(int position) {
        if (listener == null || groupAdapter == null) return;
        int groupIndex = groupAdapter.getSelectedGroupIndex();
        if (groupIndex < 0) return;
        handler.removeCallbacks(hideRunnable);
        handler.post(hideRunnable);
        listener.onChannelSelected(groupIndex, position);
    }

    private void hideInternal() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null || rootView.getVisibility() != View.VISIBLE) return;

        rootView.animate()
                .translationX(-rootView.getWidth() / 2f)
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootView.setVisibility(View.INVISIBLE);
                        isShowing = false;
                    }
                });

        handler.removeCallbacks(hideRunnable);
    }

    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusCurrentChannelRunnable);

        if (groupAdapter != null) {
            groupAdapter.setNewData(null);
            groupAdapter = null;
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(null);
            channelAdapter = null;
        }

        listener = null;
        isShowing = false;
        isEpgMode = false;

        LinearLayout root = rootViewRef.get();
        if (root != null && root.getVisibility() == View.VISIBLE) {
            root.setVisibility(View.INVISIBLE);
            root.clearAnimation();
        }
    }
}
