package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
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

        // 将 emptyLayout 添加到 Fragment 根视图（兼容空布局）
        View root = getView();
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).removeAllViews();
            ((ViewGroup) root).addView(emptyLayout);
        }
    }

    // ==================== 核心：loadConfig + success 回调手动确认包装 ====================
    private void enterLive() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "").trim();
        if (liveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "请先添加直播源地址", Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), SettingActivity.class));
            return;
        }

        ApiConfig apiConfig = ApiConfig.get();

        // 主动调用 loadConfig，确保 parseJson 完整执行
        apiConfig.loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                // success 回调：parseJson 已执行
                // 手动确认/强制包装（防止 parseJson 没读到 LIVE_URL 或分组丢失）
                if (apiConfig.getChannelGroupList().isEmpty() ||
                    !apiConfig.getChannelGroupList().get(0).getGroupName().startsWith("http://127.0.0.1")) {
                    
                    try {
                        String base64 = Base64.encodeToString(liveUrl.getBytes("UTF-8"),
                                Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        String proxyUrl = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + base64.trim();

                        apiConfig.getChannelGroupList().clear();
                        LiveChannelGroup group = new LiveChannelGroup();
                        group.setGroupName(proxyUrl);
                        group.setLiveChannels(new ArrayList<>());
                        apiConfig.getChannelGroupList().add(group);

                        Toast.makeText(requireContext(), "已手动确认包装直播源", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                }

                Toast.makeText(requireContext(), "源配置初始化成功，3秒后进入直播...", Toast.LENGTH_SHORT).show();

                // 增加到 3 秒延迟，确保所有异步初始化完成
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(requireContext(), LivePlayActivity.class));
                }, 3000);
            }

            @Override
            public void error(String msg) {
                Toast.makeText(requireContext(), "配置文件加载失败: " + msg, Toast.LENGTH_LONG).show();
                // fallback 手动包装
                manualProxyFallback(liveUrl);
            }

            @Override
            public void retry() {
                // 重试一次
                new Handler(Looper.getMainLooper()).postDelayed(() -> enterLive(), 2000);
            }
        }, requireActivity());
    }

    // fallback：配置文件失败时手动包装
    private void manualProxyFallback(String liveUrl) {
        try {
            String base64 = Base64.encodeToString(liveUrl.getBytes("UTF-8"),
                    Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
            String proxyUrl = "http://127.0.0.1:9978/proxy?do=live&type=txt&ext=" + base64.trim();

            ApiConfig apiConfig = ApiConfig.get();
            apiConfig.getChannelGroupList().clear();

            LiveChannelGroup group = new LiveChannelGroup();
            group.setGroupName(proxyUrl);
            group.setLiveChannels(new ArrayList<>());
            apiConfig.getChannelGroupList().add(group);

            Toast.makeText(requireContext(), "配置文件失败，已手动包装直播源，3秒后尝试进入", Toast.LENGTH_LONG).show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(requireContext(), LivePlayActivity.class));
            }, 3000);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "手动包装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(requireContext(), "直播源已保存\n点击“进入直播”或等待自动加载", Toast.LENGTH_LONG).show();
            // 已取消 3 秒自动进入，只在按钮点击时触发
        }
    }
}
