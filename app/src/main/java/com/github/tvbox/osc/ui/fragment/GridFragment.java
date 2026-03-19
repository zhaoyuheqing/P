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
 * 纯直播壳 - 无内置源版
 * 启动后显示空网格 + “添加订阅”按钮
 * 添加源后通过原版机制刷新（ApiConfig + LivePlayActivity）
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;  // 复用原版适配器（只显示空或占位）

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    // 兼容旧版调用（原版传 SortData，这里忽略）
    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return newInstance();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);  // 监听源更新事件
        initView();
        showEmptyState();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, 1));  // 单列，适合提示

        gridAdapter = new GridAdapter(false, null);  // 复用原版适配器，但不加载数据
        mGridView.setAdapter(gridAdapter);

        // 禁用加载更多
        gridAdapter.setEnableLoadMore(false);

        // 焦点动画（保持原版风格）
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

        // 长按整个网格 → 添加源
        mGridView.setOnLongClickListener(v -> {
            jumpToAddLiveSource();
            return true;
        });

        // 空状态自定义视图（按钮 + 提示）
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
        gridAdapter.setNewData(null);  // 清空数据，显示空状态视图
        showEmpty();
    }

    private void jumpToAddLiveSource() {
        // 跳转原版设置页（可传参数限制只显示直播 tab，如果原版支持）
        jumpActivity(SettingActivity.class);
        // 如果想只显示直播源 tab，可加参数（需 SettingActivity 支持）：
        // Bundle bundle = new Bundle();
        // bundle.putString("tab", "live");
        // jumpActivity(SettingActivity.class, bundle);
    }

    // 监听源更新事件（添加源后刷新界面）
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_LIVEPLAY_UPDATE ||
            event.type == RefreshEvent.TYPE_SOURCE_UPDATE) {
            // 原版添加源后会触发此事件
            // 这里直接跳 LivePlayActivity，让它显示频道列表
            jumpActivity(LivePlayActivity.class);
            // 或调用 forceRefresh() 显示占位提示
            showEmptyState();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
