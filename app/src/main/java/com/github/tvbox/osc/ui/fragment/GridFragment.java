package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

public class GridFragment extends BaseLazyFragment {

    private LinearLayout emptyLayout;
    private Button btnAddSource;
    private Button btnEnterLive;

    public static GridFragment newInstance() {
        return new GridFragment();
    }

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
        // 如果你的布局里有 mGridView，可保留；否则可删除相关代码
        // mGridView = findViewById(R.id.mGridView); ...

        // 自定义空状态布局
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
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 40;
        btnAddSource.setLayoutParams(params);
        btnAddSource.setOnClickListener(v -> jumpActivity(SettingActivity.class));
        emptyLayout.addView(btnAddSource);

        btnEnterLive = new Button(requireContext());
        btnEnterLive.setText("进入直播");
        btnEnterLive.setTextColor(0xFFFFFFFF);
        btnEnterLive.setBackgroundColor(0xFF4CAF50);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 20;
        btnEnterLive.setLayoutParams(params);
        btnEnterLive.setOnClickListener(v -> {
            String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
            if (liveUrl.isEmpty()) {
                Toast.makeText(requireContext(), "请先在设置中添加直播源", Toast.LENGTH_LONG).show();
                jumpActivity(SettingActivity.class);
            } else {
                jumpActivity(LivePlayActivity.class);
            }
        });
        emptyLayout.addView(btnEnterLive);

        // 如果有 gridAdapter，可设置 emptyView
        // gridAdapter.setEmptyView(emptyLayout);
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
        } else {
            Toast.makeText(requireContext(), "已检测到直播源：" + liveUrl + "\n点击“进入直播”查看频道", Toast.LENGTH_LONG).show();
        }
        updateUIState();
    }
}
