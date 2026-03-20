package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;
    private LinearLayout emptyLayout;
    private Button btnAddSource;
    private Button btnEnterLive;

    public static GridFragment newInstance() {
        return new GridFragment();
    }

    // 兼容 HomeActivity 原版调用（关键修复之前报错）
    public static GridFragment newInstance(Object sortData) {
        return newInstance();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        updateUIState();
    }

    private void initView() {
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(requireContext(), 1));

        gridAdapter = new GridAdapter(false, null);
        mGridView.setAdapter(gridAdapter);
        gridAdapter.setEnableLoadMore(false);

        mGridView.setOnLongClickListener(v -> {
            jumpActivity(SettingActivity.class);
            return true;
        });

        emptyLayout = new LinearLayout(requireContext());
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setPadding(0, 200, 0, 0);

        TextView tvEmpty = new TextView(requireContext());
        tvEmpty.setText("暂无直播频道");
        tvEmpty.setTextColor(0xFFFFFFFF);
        tvEmpty.setTextSize(24);
        emptyLayout.addView(tvEmpty);

        btnAddSource = new Button(requireContext());
        btnAddSource.setText("添加直播源");
        btnAddSource.setTextColor(0xFFFFFFFF);
        btnAddSource.setBackgroundColor(0xFF3366CC);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 40;
        btnAddSource.setLayoutParams(params);
        btnAddSource.setOnClickListener(v -> jumpActivity(SettingActivity.class));
        emptyLayout.addView(btnAddSource);

        btnEnterLive = new Button(requireContext());
        btnEnterLive.setText("进入直播");
        btnEnterLive.setTextColor(0xFFFFFFFF);
        btnEnterLive.setBackgroundColor(0xFF4CAF50);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 20;
        btnEnterLive.setLayoutParams(params);
        btnEnterLive.setOnClickListener(v -> jumpActivity(LivePlayActivity.class));
        emptyLayout.addView(btnEnterLive);

        gridAdapter.setEmptyView(emptyLayout);
    }

    private void updateUIState() {
        btnAddSource.setVisibility(View.VISIBLE);
        btnEnterLive.setVisibility(View.VISIBLE);
        btnEnterLive.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();

        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "未检测到直播源地址，请在设置中添加", Toast.LENGTH_LONG).show();
            updateUIState();
            return;
        }

        // 手动模拟 ApiConfig.parseJson 里的包装逻辑（核心修复）
        try {
            String base64Url = Base64.encodeToString(liveUrl.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            String proxyUrl = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + base64Url.trim();

            // 清空旧列表并添加 proxy 虚拟分组（模拟 parseJson）
            ApiConfig.get().getChannelGroupList().clear();
            LiveChannelGroup fakeGroup = new LiveChannelGroup();
            fakeGroup.setGroupName(proxyUrl);
            ApiConfig.get().getChannelGroupList().add(fakeGroup);

            Toast.makeText(requireContext(), "已包装直播源：" + proxyUrl + "\n即将进入直播查看频道", Toast.LENGTH_LONG).show();

            // 直接跳转 LivePlayActivity，让它执行 loadProxyLives → OkGo下载TXT → TxtSubscribe → loadLives → 显示
            jumpActivity(LivePlayActivity.class);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "包装源地址失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        updateUIState();
    }
}
