package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
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
    }

    private void initView() {
        emptyLayout = new LinearLayout(requireContext());
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setPadding(0, 180, 0, 0);

        TextView tvEmpty = new TextView(requireContext());
        tvEmpty.setText("暂无直播频道\n点击屏幕任意位置添加直播源");
        tvEmpty.setTextColor(0xFFFFFFFF);
        tvEmpty.setTextSize(22);
        tvEmpty.setGravity(Gravity.CENTER);
        emptyLayout.addView(tvEmpty);

        btnAddSource = new Button(requireContext());
        btnAddSource.setText("添加直播源");
        btnAddSource.setTextColor(0xFFFFFFFF);
        btnAddSource.setBackgroundColor(0xFF3366CC);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 50;
        btnAddSource.setLayoutParams(params);
        btnAddSource.setOnClickListener(v -> 
            startActivity(new Intent(requireContext(), SettingActivity.class)));
        emptyLayout.addView(btnAddSource);

        btnEnterLive = new Button(requireContext());
        btnEnterLive.setText("进入直播");
        btnEnterLive.setTextColor(0xFFFFFFFF);
        btnEnterLive.setBackgroundColor(0xFF4CAF50);
        params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 30;
        btnEnterLive.setLayoutParams(params);
        btnEnterLive.setOnClickListener(v -> enterLive());
        emptyLayout.addView(btnEnterLive);

        View root = getView();
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).removeAllViews();
            ((ViewGroup) root).addView(emptyLayout);
        }
    }

    // ==================== 核心：只依赖 loadConfig + success 回调延迟跳转 ====================
    private void enterLive() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "请先添加直播源地址", Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), SettingActivity.class));
            return;
        }

        ApiConfig apiConfig = ApiConfig.get();

        apiConfig.loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                // parseJson 已完整执行，无需手动包装
                Toast.makeText(requireContext(), "源配置初始化成功，3秒后进入直播...", Toast.LENGTH_SHORT).show();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(requireContext(), LivePlayActivity.class));
                }, 3000);
            }

            @Override
            public void error(String msg) {
                Toast.makeText(requireContext(), "配置文件加载失败: " + msg, Toast.LENGTH_LONG).show();
                // 失败时提示，但不跳转（避免空列表崩溃）
            }

            @Override
            public void retry() {
                new Handler(Looper.getMainLooper()).postDelayed(() -> enterLive(), 2000);
            }
        }, requireActivity());
    }

    // 点击屏幕任意位置跳转设置
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view != null) {
            view.setOnClickListener(v -> 
                startActivity(new Intent(requireContext(), SettingActivity.class)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "未检测到直播源\n点击屏幕任意位置添加", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "直播源已保存\n点击“进入直播”开始播放", Toast.LENGTH_LONG).show();
        }
    }
}
