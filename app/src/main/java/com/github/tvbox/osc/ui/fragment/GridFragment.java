package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

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
    }

    // ==================== 核心：手动包装 + 初始化 IJK 配置 ====================
    private void enterLive() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "请先添加直播源地址", Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), SettingActivity.class));
            return;
        }

        try {
            String base64 = Base64.encodeToString(liveUrl.getBytes("UTF-8"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            String proxyUrl = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + base64;

            ApiConfig apiConfig = ApiConfig.get();
            apiConfig.getChannelGroupList().clear();

            LiveChannelGroup group = new LiveChannelGroup();
            group.setGroupName(proxyUrl);
            group.setLiveChannels(new ArrayList<>());   // 必须
            apiConfig.getChannelGroupList().add(group);

            // ==================== 关键修复：确保 IJK 配置已初始化 ====================
            if (apiConfig.getIjkCodes() == null || apiConfig.getIjkCodes().isEmpty()) {
                // 通过调用 loadConfig 触发 parseJson 中的 IJK 初始化逻辑（最稳妥）
                apiConfig.loadConfig(false, new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jumpToLivePlay();
                    }

                    @Override
                    public void error(String msg) {
                        Toast.makeText(requireContext(), "初始化失败: " + msg, Toast.LENGTH_LONG).show();
                        jumpToLivePlay(); // 即使失败也尝试进入
                    }

                    @Override
                    public void retry() {
                        // 可忽略
                    }
                }, requireActivity());
            } else {
                jumpToLivePlay();
            }

            Toast.makeText(requireContext(), "直播源已包装，即将进入播放...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "包装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 统一跳转方法
    private void jumpToLivePlay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(requireContext(), LivePlayActivity.class));
        }, 300);
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

        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "未检测到直播源\n点击屏幕任意位置添加", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "直播源已保存\n点击“进入直播”开始播放", Toast.LENGTH_LONG).show();
        }
    }
}
