package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;  // 原版路径（3.x Brvah）
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;  // ← 新增这个 import
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 纯直播壳 - 无内置源版（修复版）
 * - 启动后显示空网格 + 添加订阅提示
 * - 添加源后监听 RefreshEvent → 自动跳 LivePlayActivity 显示频道
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;  // 复用原版适配器

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return newInstance();  // 兼容旧调用
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        initView();
        showEmptyState();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, 1));  // 单列显示提示

        gridAdapter = new GridAdapter(false, null);  // 复用原版
        mGridView.setAdapter(gridAdapter);

        gridAdapter.setEnableLoadMore(false);

        // 焦点动画（原版风格）
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {}
        });

        // 长按网格任意位置 → 添加源
        mGridView.setOnLongClickListener(v -> {
            jumpToAddLiveSource();
            return true;
        });

        // 空状态视图（可点击添加源）
        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n\n点击下方按钮或长按屏幕任意位置\n添加直播源订阅");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(20);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, 300, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setFocusableInTouchMode(true);
        emptyTv.setOnClickListener(v -> jumpToAddLiveSource());
        gridAdapter.setEmptyView(emptyTv);
    }

    private void showEmptyState() {
        gridAdapter.setNewData(null);  // 清空数据 → 显示空视图
        showEmpty();
    }

    private void jumpToAddLiveSource() {
        jumpActivity(SettingActivity.class);
    }

    // 监听原版刷新事件（添加源后触发）
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefresh(RefreshEvent event) {
        // 原版添加源后常用 TYPE_LIVEPLAY_UPDATE 或 TYPE_SOURCE_CHANGED
        if (event.type == RefreshEvent.TYPE_LIVEPLAY_UPDATE) {
            // 源更新后直接进入播放界面（原版机制会自动加载频道列表）
            jumpActivity(LivePlayActivity.class);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
