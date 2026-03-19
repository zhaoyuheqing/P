package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
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
 * 纯直播壳 - 无内置源 + 点击/长按添加源 + 添加后自动进入播放
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return newInstance();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        EventBus.getDefault().register(this);

        initView();

        // 启动时检查是否有直播源
        if (ApiConfig.get().getChannelGroupList() != null && !ApiConfig.get().getChannelGroupList().isEmpty()) {
            jumpActivity(LivePlayActivity.class);
        } else {
            showEmptyState();
        }
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, 1));

        gridAdapter = new GridAdapter(false, null);
        mGridView.setAdapter(gridAdapter);

        gridAdapter.setEnableLoadMore(false);

        // 焦点动画
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

        // 长按任意位置 → 添加源
        mGridView.setOnLongClickListener(v -> {
            jumpActivity(SettingActivity.class);
            return true;
        });

        // 空状态提示（可点击 + 长按都跳转）
        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n\n点击这里或长按屏幕任意位置\n添加直播源订阅");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(20);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, 300, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setFocusableInTouchMode(true);
        emptyTv.setOnClickListener(v -> jumpActivity(SettingActivity.class));
        gridAdapter.setEmptyView(emptyTv);
    }

    private void showEmptyState() {
        gridAdapter.setNewData(null);
        showEmpty();
    }

    // 监听添加源后的刷新事件
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefresh(RefreshEvent event) {
        jumpActivity(LivePlayActivity.class);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
