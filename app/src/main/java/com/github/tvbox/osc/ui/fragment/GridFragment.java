package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.LOG;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯直播极简壳 - 已修正字段为 url（兼容 takagen99/Box 标准）
 */
public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;
    private boolean isLoad = false;

    private static final String BUILT_IN_URL = "https://frosty-block-011f.pohoy71288.workers.dev/";

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        GridFragment fragment = new GridFragment();
        fragment.sortData = sortData;
        return fragment;
    }

    private MovieSort.SortData sortData;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        loadChannels();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(mContext, 5));

        gridAdapter = new GridAdapter(false, null);
        mGridView.setAdapter(gridAdapter);

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) { }
        });

        mGridView.setOnLongClickListener(v -> {
            jumpToSetting();
            return true;
        });

        gridAdapter.setOnItemClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            Movie.Video video = gridAdapter.getData().get(position);
            if (video != null && video.url != null && !video.url.isEmpty()) {
                Bundle bundle = new Bundle();
                bundle.putString("id", video.id);
                bundle.putString("sourceKey", "built_in");
                bundle.putString("title", video.name);
                bundle.putString("url", video.url);  // LivePlayActivity 读取这个字段起播
                jumpActivity(LivePlayActivity.class, bundle);
            } else {
                Toast.makeText(mContext, "无效播放地址", Toast.LENGTH_SHORT).show();
            }
        });

        gridAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            jumpToSetting();
            return true;
        });

        gridAdapter.setLoadMoreView(new LoadMoreView());
        gridAdapter.setEnableLoadMore(false);

        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n\n长按遥控器任意键 或 点击这里\n添加/更新直播源");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(22);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, 400, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setFocusableInTouchMode(true);
        emptyTv.setOnClickListener(v -> jumpToSetting());
        gridAdapter.setEmptyView(emptyTv);
    }

    private void jumpToSetting() {
        jumpActivity(SettingActivity.class);
    }

    private void loadChannels() {
        new Thread(() -> {
            try {
                URL url = new URL(BUILT_IN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                List<Movie.Video> channels = new ArrayList<>();
                String line;
                int index = 1;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split(",", 2);
                    if (parts.length >= 2) {
                        Movie.Video v = new Movie.Video();
                        v.name = parts[0].trim();
                        v.id = "live_" + index++;
                        v.sourceKey = "built_in";
                        v.url = parts[1].trim();  // ← 修正为 url
                        v.tag = "live";
                        v.pic = "";
                        channels.add(v);
                    }
                }
                reader.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (channels.isEmpty()) {
                        showEmpty();
                    } else {
                        showSuccess();
                        isLoad = true;
                        gridAdapter.setNewData(channels);
                    }
                });

            } catch (Exception e) {
                LOG.e("直播源加载失败", e);
                new Handler(Looper.getMainLooper()).post(this::showEmpty);
            }
        }).start();
    }

    public void forceRefresh() {
        gridAdapter.setNewData(null);
        loadChannels();
    }
}
