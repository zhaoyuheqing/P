package com.github.tvbox.osc.ui.panel;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * 直播设置面板管理类
 * 职责：管理设置面板的UI交互
 * 
 * 重要：构造函数必须传入有效的 View，否则会抛出 IllegalArgumentException
 */
public class LiveSettingsPanel {
    // 弱引用持有 Context 和 View，避免内存泄漏
    private final WeakReference<Context> contextRef;
    private final WeakReference<LinearLayout> rootViewRef;
    private final WeakReference<TvRecyclerView> groupRecyclerViewRef;
    private final WeakReference<TvRecyclerView> itemRecyclerViewRef;
    
    private LiveSettingGroupAdapter groupAdapter;
    private LiveSettingItemAdapter itemAdapter;
    private final ArrayList<LiveSettingGroup> settingGroups = new ArrayList<>();
    
    private SettingsListener listener;
    private boolean isShowing = false;
    private LiveChannelItem currentChannel;
    
    // 当前选中的设置值（用于界面显示）
    private int currentScaleIndex = 0;
    private int currentPlayerTypeIndex = 0;
    
    // Handler 用于延迟任务
    private final Handler handler;
    
    // Runnable 使用静态内部类 + WeakReference 避免内存泄漏
    private final HideRunnable hideRunnable;
    private final FocusAndShowRunnable focusAndShowRunnable;
    private final RequestLayoutRunnable requestLayoutRunnable;
    
    private static class HideRunnable implements Runnable {
        private final WeakReference<LiveSettingsPanel> panelRef;
        
        HideRunnable(LiveSettingsPanel panel) {
            this.panelRef = new WeakReference<>(panel);
        }
        
        @Override
        public void run() {
            LiveSettingsPanel panel = panelRef.get();
            if (panel != null) {
                panel.hideInternal();
            }
        }
    }
    
    private static class FocusAndShowRunnable implements Runnable {
        private final WeakReference<LiveSettingsPanel> panelRef;
        
        FocusAndShowRunnable(LiveSettingsPanel panel) {
            this.panelRef = new WeakReference<>(panel);
        }
        
        @Override
        public void run() {
            LiveSettingsPanel panel = panelRef.get();
            if (panel != null) {
                panel.focusAndShowInternal();
            }
        }
    }
    
    private static class RequestLayoutRunnable implements Runnable {
        private final WeakReference<LinearLayout> rootViewRef;
        
        RequestLayoutRunnable(LinearLayout rootView) {
            this.rootViewRef = new WeakReference<>(rootView);
        }
        
        @Override
        public void run() {
            LinearLayout rootView = rootViewRef.get();
            if (rootView != null) {
                rootView.requestLayout();
            }
        }
    }
    
    public interface SettingsListener {
        void onSourceChanged(int sourceIndex);
        void onScaleChanged(int scaleIndex);
        void onPlayerTypeChanged(int typeIndex);
        void onTimeoutChanged(int timeoutSec);
        void onPreferenceChanged(String key, boolean value);
        void onLiveAddressSelected();
        void onExit();
    }
    
    /**
     * 构造函数 - 必须传入有效的 View，否则抛出异常
     */
    public LiveSettingsPanel(Context context, 
                              Handler handler, 
                              LinearLayout rootView, 
                              TvRecyclerView groupRecyclerView, 
                              TvRecyclerView itemRecyclerView) {
        // 参数校验
        if (context == null) {
            throw new IllegalArgumentException("LiveSettingsPanel: context cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("LiveSettingsPanel: handler cannot be null");
        }
        if (rootView == null) {
            throw new IllegalArgumentException("LiveSettingsPanel: rootView cannot be null");
        }
        if (groupRecyclerView == null) {
            throw new IllegalArgumentException("LiveSettingsPanel: groupRecyclerView cannot be null");
        }
        if (itemRecyclerView == null) {
            throw new IllegalArgumentException("LiveSettingsPanel: itemRecyclerView cannot be null");
        }
        
        this.contextRef = new WeakReference<>(context);
        this.handler = handler;
        this.rootViewRef = new WeakReference<>(rootView);
        this.groupRecyclerViewRef = new WeakReference<>(groupRecyclerView);
        this.itemRecyclerViewRef = new WeakReference<>(itemRecyclerView);
        
        // 初始化 Runnable
        this.hideRunnable = new HideRunnable(this);
        this.focusAndShowRunnable = new FocusAndShowRunnable(this);
        this.requestLayoutRunnable = new RequestLayoutRunnable(rootView);
        
        // 设置面板默认隐藏
        rootView.setVisibility(View.INVISIBLE);
    }
    
    /**
     * 初始化设置面板（在 Activity 的 init 中调用）
     */
    public void init() {
        initSettingGroups();
        initGroupView();
        initItemView();
    }
    
    public void setListener(SettingsListener listener) {
        this.listener = listener;
    }
    
    public void setCurrentScale(int scaleIndex) {
        this.currentScaleIndex = scaleIndex;
        updateItemSelectionIfNeeded(1, scaleIndex);
    }
    
    public void setCurrentPlayerType(int typeIndex) {
        this.currentPlayerTypeIndex = typeIndex;
        updateItemSelectionIfNeeded(2, typeIndex);
    }
    
    private void updateItemSelectionIfNeeded(int groupIndex, int itemIndex) {
        if (!isShowing) {
            return;
        }
        if (groupAdapter == null || itemAdapter == null) {
            return;
        }
        if (groupAdapter.getSelectedGroupIndex() != groupIndex) {
            return;
        }
        
        TvRecyclerView itemView = itemRecyclerViewRef.get();
        if (itemView == null) {
            return;
        }
        itemAdapter.selectItem(itemIndex, true, false);
    }
    
    // ==================== 初始化数据 ====================
    
    private void initSettingGroups() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置", "直播地址", "退出直播"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        itemsArrayList.add(new ArrayList<>());
        itemsArrayList.add(new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("关", "5s", "10s", "15s", "20s", "25s", "30s")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类", "关闭密码")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("列表历史")));
        itemsArrayList.add(new ArrayList<>(Arrays.asList("确定")));
        
        settingGroups.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup group = new LiveSettingGroup();
            ArrayList<LiveSettingItem> itemList = new ArrayList<>();
            group.setGroupIndex(i);
            group.setGroupName(groupNames.get(i));
            ArrayList<String> items = itemsArrayList.get(i);
            for (int j = 0; j < items.size(); j++) {
                LiveSettingItem item = new LiveSettingItem();
                item.setItemIndex(j);
                item.setItemName(items.get(j));
                itemList.add(item);
            }
            group.setLiveSettingItems(itemList);
            settingGroups.add(group);
        }
        
        // 从Hawk读取当前设置（添加边界检查）
        if (settingGroups.size() > 3) {
            LiveSettingGroup timeoutGroup = settingGroups.get(3);
            if (timeoutGroup != null && timeoutGroup.getLiveSettingItems() != null) {
                int timeoutValue = Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 2);
                if (timeoutValue >= 0 && timeoutValue < timeoutGroup.getLiveSettingItems().size()) {
                    timeoutGroup.getLiveSettingItems().get(timeoutValue).setItemSelected(true);
                }
            }
        }
        
        if (settingGroups.size() > 4) {
            LiveSettingGroup prefGroup = settingGroups.get(4);
            if (prefGroup != null && prefGroup.getLiveSettingItems() != null) {
                int size = prefGroup.getLiveSettingItems().size();
                if (size > 0) {
                    prefGroup.getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
                }
                if (size > 1) {
                    prefGroup.getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
                }
                if (size > 2) {
                    prefGroup.getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
                }
                if (size > 3) {
                    prefGroup.getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
                }
                if (size > 4) {
                    prefGroup.getLiveSettingItems().get(4).setItemSelected(Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false));
                }
            }
        }
    }
    
    private void initGroupView() {
        TvRecyclerView groupView = groupRecyclerViewRef.get();
        if (groupView == null) {
            return;
        }
        
        groupView.setHasFixedSize(true);
        groupView.setLayoutManager(new V7LinearLayoutManager(groupView.getContext(), 1, false));
        groupAdapter = new LiveSettingGroupAdapter();
        groupView.setAdapter(groupAdapter);
        
        groupView.setOnItemListener(new com.owen.tvrecyclerview.widget.TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                // 不需要处理
            }
            
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectGroup(position, true);
            }
            
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                // 点击由 Adapter 的 OnItemClickListener 处理
            }
        });
        
        groupAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            selectGroup(position, false);
        });
        
        groupAdapter.setNewData(settingGroups);
    }
    
    private void initItemView() {
        TvRecyclerView itemView = itemRecyclerViewRef.get();
        if (itemView == null) {
            return;
        }
        
        itemView.setHasFixedSize(true);
        itemView.setLayoutManager(new V7LinearLayoutManager(itemView.getContext(), 1, false));
        itemAdapter = new LiveSettingItemAdapter();
        itemView.setAdapter(itemAdapter);
        
        itemView.setOnItemListener(new com.owen.tvrecyclerview.widget.TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                // 不需要处理
            }
            
            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) {
                    return;
                }
                if (groupAdapter != null) {
                    groupAdapter.setFocusedGroupIndex(-1);
                }
                if (itemAdapter != null) {
                    itemAdapter.setFocusedItemIndex(position);
                }
                handler.removeCallbacks(hideRunnable);
                handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            }
            
            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickItem(position);
            }
        });
        
        itemAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            clickItem(position);
        });
    }
    
    // ==================== 分组/选项选择逻辑 ====================
    
    private void selectGroup(int position, boolean focus) {
        if (position < 0 || position >= settingGroups.size()) {
            return;
        }
        if (groupAdapter == null || itemAdapter == null) {
            return;
        }
        
        if (focus) {
            groupAdapter.setFocusedGroupIndex(position);
            itemAdapter.setFocusedItemIndex(-1);
        }
        if (position == groupAdapter.getSelectedGroupIndex() || position < -1) {
            return;
        }
        
        groupAdapter.setSelectedGroupIndex(position);
        
        LiveSettingGroup selectedGroup = settingGroups.get(position);
        if (selectedGroup != null && selectedGroup.getLiveSettingItems() != null) {
            itemAdapter.setNewData(selectedGroup.getLiveSettingItems());
        } else {
            itemAdapter.setNewData(new ArrayList<>());
        }
        
        // 更新选中状态
        switch (position) {
            case 0:
                if (currentChannel != null && itemAdapter != null) {
                    int sourceIndex = currentChannel.getSourceIndex();
                    int dataSize = itemAdapter.getData() != null ? itemAdapter.getData().size() : 0;
                    if (sourceIndex >= 0 && sourceIndex < dataSize) {
                        itemAdapter.selectItem(sourceIndex, true, false);
                    }
                }
                break;
            case 1:
                if (itemAdapter != null) {
                    itemAdapter.selectItem(currentScaleIndex, true, true);
                }
                break;
            case 2:
                if (itemAdapter != null) {
                    itemAdapter.selectItem(currentPlayerTypeIndex, true, true);
                }
                break;
            default:
                break;
        }
        
        // 滚动到选中项
        if (itemAdapter != null) {
            int scrollPosition = itemAdapter.getSelectedItemIndex();
            if (scrollPosition < 0) {
                scrollPosition = 0;
            }
            TvRecyclerView itemView = itemRecyclerViewRef.get();
            if (itemView != null) {
                itemView.scrollToPosition(scrollPosition);
            }
        }
        
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }
    
    private void clickItem(int position) {
        if (groupAdapter == null || itemAdapter == null) {
            return;
        }
        int groupIndex = groupAdapter.getSelectedGroupIndex();
        
        if (groupIndex == 0 && currentChannel == null) {
            showToast("当前无直播源，无法切换线路");
            return;
        }
        
        // 线路选择、画面比例、播放解码：需要更新选中状态
        if (groupIndex < 4) {
            if (position == itemAdapter.getSelectedItemIndex()) {
                return;
            }
            itemAdapter.selectItem(position, true, true);
        }
        
        switch (groupIndex) {
            case 0:
                if (currentChannel != null && listener != null) {
                    currentChannel.setSourceIndex(position);
                    listener.onSourceChanged(position);
                }
                break;
            case 1:
                currentScaleIndex = position;
                if (listener != null) {
                    listener.onScaleChanged(position);
                }
                break;
            case 2:
                currentPlayerTypeIndex = position;
                if (listener != null) {
                    listener.onPlayerTypeChanged(position);
                }
                break;
            case 3:
                if (listener != null) {
                    listener.onTimeoutChanged(position);
                }
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                break;
            case 4:
                handlePreferenceChange(position);
                break;
            case 5:
                if (position == 0 && listener != null) {
                    listener.onLiveAddressSelected();
                }
                break;
            case 6:
                if (position == 0 && listener != null) {
                    listener.onExit();
                }
                break;
            default:
                break;
        }
        
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
    }
    
    private void handlePreferenceChange(int position) {
        boolean select = false;
        switch (position) {
            case 0:
                select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                if (listener != null) {
                    listener.onPreferenceChanged(HawkConfig.LIVE_SHOW_TIME, select);
                }
                break;
            case 1:
                select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                if (listener != null) {
                    listener.onPreferenceChanged(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                }
                break;
            case 2:
                select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                if (listener != null) {
                    listener.onPreferenceChanged(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                }
                break;
            case 3:
                select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                if (listener != null) {
                    listener.onPreferenceChanged(HawkConfig.LIVE_CROSS_GROUP, select);
                }
                break;
            case 4:
                select = !Hawk.get(HawkConfig.LIVE_SKIP_PASSWORD, false);
                Hawk.put(HawkConfig.LIVE_SKIP_PASSWORD, select);
                if (listener != null) {
                    listener.onPreferenceChanged(HawkConfig.LIVE_SKIP_PASSWORD, select);
                }
                break;
            default:
                return;
        }
        if (itemAdapter != null) {
            itemAdapter.selectItem(position, select, false);
        }
    }
    
    // ==================== 显示/隐藏逻辑 ====================
    
    public void show() {
        if (isShowing) {
            // 已显示，只刷新隐藏计时器
            handler.removeCallbacks(hideRunnable);
            handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
            return;
        }
        
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null) {
            showToast("设置面板已销毁，无法显示");
            return;
        }
        
        if (currentChannel == null) {
            showToast("当前无直播源，部分功能不可用");
        }
        
        // 刷新线路列表
        refreshSourceListDisplay();
        
        if (groupAdapter != null) {
            groupAdapter.setNewData(settingGroups);
        }
        selectGroup(0, false);
        
        TvRecyclerView groupView = groupRecyclerViewRef.get();
        if (groupView != null) {
            groupView.scrollToPosition(0);
        }
        
        TvRecyclerView itemView = itemRecyclerViewRef.get();
        if (currentChannel != null && itemView != null) {
            itemView.scrollToPosition(currentChannel.getSourceIndex());
        }
        
        handler.postDelayed(focusAndShowRunnable, 200);
        isShowing = true;
    }
    
    private void focusAndShowInternal() {
        TvRecyclerView groupView = groupRecyclerViewRef.get();
        LinearLayout rootView = rootViewRef.get();
        
        if (groupView == null || rootView == null) {
            showToast("设置面板已销毁，无法显示");
            isShowing = false;
            return;
        }
        
        // 等待滚动完成
        if (groupView.isScrolling() || groupView.isComputingLayout()) {
            handler.postDelayed(focusAndShowRunnable, 100);
            return;
        }
        
        // 请求焦点
        if (groupAdapter != null && groupView.getAdapter() != null && 
            groupView.getAdapter().getItemCount() > 0) {
            groupView.scrollToPosition(0);
            groupView.setSelection(0);
            groupView.requestFocus();
        }
        
        // 显示动画
        rootView.setVisibility(View.VISIBLE);
        rootView.setAlpha(0.0f);
        rootView.setTranslationX(rootView.getWidth() / 2);
        rootView.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(null);
        
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, LiveConstants.AUTO_HIDE_SETTINGS_MS);
        handler.postDelayed(requestLayoutRunnable, 255);
    }
    
    public void hide() {
        hideInternal();
    }
    
    private void hideInternal() {
        LinearLayout rootView = rootViewRef.get();
        if (rootView == null || rootView.getVisibility() != View.VISIBLE) {
            return;
        }
        
        rootView.animate()
                .translationX(rootView.getWidth() / 2)
                .alpha(0.0f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        LinearLayout view = rootViewRef.get();
                        if (view != null) {
                            view.setVisibility(View.INVISIBLE);
                            view.clearAnimation();
                        }
                        if (groupAdapter != null) {
                            groupAdapter.setSelectedGroupIndex(-1);
                        }
                    }
                });
        handler.removeCallbacks(hideRunnable);
        isShowing = false;
    }
    
    // ==================== 数据更新 ====================
    
    public void updateSourceList(LiveChannelItem channel) {
        this.currentChannel = channel;
        refreshSourceListDisplay();
    }
    
    private void refreshSourceListDisplay() {
        if (settingGroups.isEmpty()) {
            return;
        }
        
        LiveSettingGroup sourceGroup = settingGroups.get(0);
        if (sourceGroup == null) {
            return;
        }
        
        if (currentChannel == null) {
            ArrayList<LiveSettingItem> emptyList = new ArrayList<>();
            LiveSettingItem emptyItem = new LiveSettingItem();
            emptyItem.setItemIndex(0);
            emptyItem.setItemName("无可用线路");
            emptyList.add(emptyItem);
            sourceGroup.setLiveSettingItems(emptyList);
            return;
        }
        
        ArrayList<String> sourceNames = currentChannel.getChannelSourceNames();
        ArrayList<LiveSettingItem> itemList = new ArrayList<>();
        for (int j = 0; j < sourceNames.size(); j++) {
            LiveSettingItem item = new LiveSettingItem();
            item.setItemIndex(j);
            item.setItemName(sourceNames.get(j));
            itemList.add(item);
        }
        sourceGroup.setLiveSettingItems(itemList);
        
        // 如果面板正在显示且当前在线路选择分组，刷新显示
        if (isShowing && groupAdapter != null && groupAdapter.getSelectedGroupIndex() == 0 && itemAdapter != null) {
            itemAdapter.setNewData(itemList);
            if (currentChannel != null) {
                int sourceIndex = currentChannel.getSourceIndex();
                if (sourceIndex >= 0 && sourceIndex < itemList.size()) {
                    itemAdapter.selectItem(sourceIndex, true, false);
                }
            }
        }
    }
    
    public void setCurrentSourceIndex(int index) {
        if (currentChannel == null) {
            return;
        }
        if (index < 0 || index >= currentChannel.getSourceNum()) {
            return;
        }
        currentChannel.setSourceIndex(index);
        if (isShowing && groupAdapter != null && groupAdapter.getSelectedGroupIndex() == 0 && itemAdapter != null) {
            itemAdapter.selectItem(index, true, false);
        }
    }
    
    public boolean isShowing() {
        return isShowing;
    }
    
    // ==================== 辅助方法 ====================
    
    private void showToast(String message) {
        Context context = contextRef.get();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    // ==================== 资源清理 ====================
    
    public void destroy() {
        handler.removeCallbacks(hideRunnable);
        handler.removeCallbacks(focusAndShowRunnable);
        handler.removeCallbacks(requestLayoutRunnable);
        
        if (groupAdapter != null) {
            groupAdapter.setNewData(null);
            groupAdapter = null;
        }
        if (itemAdapter != null) {
            itemAdapter.setNewData(null);
            itemAdapter = null;
        }
        
        listener = null;
        currentChannel = null;
        settingGroups.clear();
        isShowing = false;
        
        // 隐藏面板
        LinearLayout rootView = rootViewRef.get();
        if (rootView != null && rootView.getVisibility() == View.VISIBLE) {
            rootView.setVisibility(View.INVISIBLE);
            rootView.clearAnimation();
        }
    }
}
