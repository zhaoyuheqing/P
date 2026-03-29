package com.github.tvbox.osc.ui.panel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 左侧频道列表面板 - 最终解耦版
 * 职责：管理左侧容器的显示/隐藏动画、频道列表的展示和用户交互
 * 通过回调通知 Activity 切换 EPG 模式，避免视图冲突
 */
public class LiveChannelListPanel {

    public interface ChannelListListener {
        void onGroupSelected(int groupIndex);
        void onChannelSelected(int groupIndex, int channelIndex);
        void onEpgModeRequest();          // 请求切换到 EPG 模式（显示节目列表）
        void onChannelModeRequest();      // 请求切换回频道模式
        List<LiveChannelGroup> getChannelGroups();
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

    private LiveChannelGroupAdapter groupAdapter;
    private LiveChannelItemAdapter channelAdapter;

    private ChannelListListener listener;
    private boolean isShowing = false;
    private boolean isEpgMode = false;   // 当前是否处于 EPG 模式

    private final Runnable hideRunnable = this::hideInternal;
    private final Runnable focusAndShowRunnable = this::focusAndShowInternal;

    public LiveChannelListPanel(@NonNull Context context,
                                @NonNull Handler handler,
                                @NonNull LinearLayout rootView,
                                @NonNull TvRecyclerView groupView,
                                @NonNull TvRecyclerView channelView) {

        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupViewRef = new WeakReference<>(groupView);
        this.channelViewRef = new WeakReference<>(channelView);

        rootView.setVisibility(View.INVISIBLE);
    }

    public void setListener(ChannelListListener listener) {
        this.listener = listener;
    }

    public void init() {
        initGroupView();
        initChannelView();
    }

    // ==================== 公共方法 ====================

    /**
     * 完全刷新面板（数据源变化时调用）
     */
    public void refreshFull(List<LiveChannelGroup> groups, int currentGroupIndex, int currentChannelIndex) {
        if (groupAdapter != null) {
            groupAdapter.setNewData(groups);
            groupAdapter.setSelectedGroupIndex(currentGroupIndex);
        }

        List<LiveChannelItem> channels = null;
        if (currentGroupIndex >= 0 && groups != null && currentGroupIndex < groups.size()) {
            channels = groups.get(currentGroupIndex).getLiveChannels();
        }
        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(currentChannelIndex);
        }

        if (isShowing && !isEpgMode) {
            scrollToCurrent(currentGroupIndex, currentChannelIndex);
        }
    }

    /**
     * 仅更新高亮和滚动（播放频道切换时调用）
     */
    public void updateSelectionAndScroll(int groupIndex, int channelIndex) {
        if (groupAdapter != null) groupAdapter.setSelectedGroupIndex(groupIndex);
        if (channelAdapter != null) channelAdapter.setSelectedChannelIndex(channelIndex);

        if (isShowing && !isEpgMode) {
            scrollToCurrent(groupIndex, channelIndex);
        }
    }

    /**
     * 切换到指定分组（密码验证成功后调用）
     */
    public void loadGroup(int groupIndex, List<LiveChannelGroup> allGroups) {
        if (groupAdapter != null) {
            groupAdapter.setSelectedGroupIndex(groupIndex);
        }

        List<LiveChannelItem> channels = null;
        if (groupIndex >= 0 && allGroups != null && groupIndex < allGroups.size()) {
            channels = allGroups.get(groupIndex).getLiveChannels();
        }

        if (channelAdapter != null) {
            channelAdapter.setNewData(channels != null ? channels : new ArrayList<>());
            channelAdapter.setSelectedChannelIndex(-1);
        }

        if (isShowing && !isEpgMode) {
            scrollToCurrent(groupIndex, -1);
        }
    }

    /**
     * 切换到 EPG 模式（由 Activity 调用）
     */
    public void showEpgMode() {
        if (isEpgMode) return;
        isEpgMode = true;
        if (listener != null) listener.onEpgModeRequest();
        // 确保面板是显示状态
        if (!isShowing) show();
    }

    /**
     * 切换回频道模式（由 Activity 调用）
     */
    public void showChannelMode() {
        if (!isEpgMode) return;
        isEpgMode = false;
        if (listener != null) listener.onChannelModeRequest();
        // 如果面板在显示，刷新频道列表数据
        if (isShowing && listener != null) {
            refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }
    }

    /**
     * 显示面板（频道模式，由 Activity 在确定键时调用）
     */
    public void show() {
        if (isShowing) {
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            return;
        }

        // 显示前强制同步为当前播放频道
        if (listener != null) {
            refreshFull(listener.getChannelGroups(), listener.getCurrentGroupIndex(), listener.getCurrentChannelIndex());
        }

        handler.postDelayed(focusAndShowRunnable, 200);
        isShowing = true;
    }

    public void hide() {
        hideInternal();
    }

    public boolean isShowing() {
        return isShowing;
    }

    public boolean isEpgMode() {
        return isEpgMode;
    }

    // ==================== 私有方法 ====================

    private void scrollToCurrent(int groupIndex, int channelIndex) {
        TvRecyclerView gv = groupViewRef.get();
        if (gv != null && groupIndex >= 0) {
            gv.scrollToPosition(groupIndex);
            gv.setSelection(groupIndex);
        }

        TvRecyclerView cv = channelViewRef.get();
        if (cv != null && channelIndex >= 0) {
            cv.scrollToPosition(channelIndex);
            cv.setSelection(channelIndex);
        }
    }

    private void initGroupView() {
        TvRecyclerView groupView = groupViewRef.get();
        if (groupView == null) return;

        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));

        groupAdapter = new LiveChannelGroupAdapter();
        groupView.setAdapter(groupAdapter);

        groupView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(position);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(-1);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
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
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                if (groupAdapter != null) groupAdapter.setFocusedGroupIndex(-1);
                if (channelAdapter != null) channelAdapter.setFocusedChannelIndex(position);

                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
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

    // ==================== 动画逻辑 ====================

    private void focusAndShowInternal() {
        TvRecyclerView groupView = groupViewRef.get();
        TvRecyclerView channelView = channelViewRef.get();
        LinearLayout rootView = rootViewRef.get();

        if (groupView == null || rootView == null) {
            isShowing = false;
            return;
        }

        // 等待滚动完成
        boolean isScrolling = groupView.isScrolling() ||
                (channelView != null && channelView.isScrolling()) ||
                groupView.isComputingLayout() ||
                (channelView != null && channelView.isComputingLayout());

        if (isScrolling) {
            handler.postDelayed(focusAndShowRunnable, 100);
            return;
        }

        // 获取当前播放位置（从 Adapter 中获取）
        int currentGroup = groupAdapter.getSelectedGroupIndex();
        int currentChannel = channelAdapter.getSelectedChannelIndex();
        if (currentGroup < 0) currentGroup = 0;
        if (currentChannel < 0) currentChannel = 0;

        // 滚动到当前分组和频道
        groupView.scrollToPosition(currentGroup);
        groupView.setSelection(currentGroup);
        if (channelView != null) {
            channelView.scrollToPosition(currentChannel);
            channelView.setSelection(currentChannel);
        }

        // 请求焦点
        groupView.requestFocus();

        // 显示动画
        rootView.setVisibility(View.VISIBLE);
        rootView.setAlpha(0.0f);
        rootView.setTranslationX(-rootView.getWidth() / 2f);
        rootView.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(null);

        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_CHANNEL_LIST_MS);
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
                        // 注意：模式标志不重置，因为模式由外部控制
                    }
                });

        handler.removeCallbacks(hideRunnable);
    }

    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusAndShowRunnable);

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
