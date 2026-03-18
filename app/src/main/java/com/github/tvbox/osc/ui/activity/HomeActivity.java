package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.orhanobut.hawk.Hawk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static Resources res;
    private LinearLayout topLayout;
    private TextView tvDate;
    private ImageView tvMenu;
    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);  // 复用原布局，但只显示直播内容

        res = getResources();

        // 极简初始化：隐藏所有非直播控件
        initMinimalView();

        // DIYP风格：开机直接进入直播频道列表
        loadLiveFragment();

        // 时间更新
        mHandler.post(mRunnable);
    }

    private void initMinimalView() {
        topLayout = findViewById(R.id.topLayout);
        tvDate = findViewById(R.id.tvDate);
        tvMenu = findViewById(R.id.tvMenu);

        // 隐藏所有多余按钮，只保留菜单键入口
        findViewById(R.id.tvName).setVisibility(View.GONE);
        findViewById(R.id.tvWifi).setVisibility(View.GONE);
        findViewById(R.id.tvFind).setVisibility(View.GONE);
        findViewById(R.id.tvStyle).setVisibility(View.GONE);
        findViewById(R.id.tvDrawer).setVisibility(View.GONE);

        // 显示菜单图标（订阅入口）
        tvMenu.setVisibility(View.VISIBLE);
        tvMenu.setOnClickListener(v -> jumpActivity(SettingActivity.class));

        // 极简顶部栏（只留时间 + 菜单）
        topLayout.setVisibility(View.VISIBLE);
    }

    private void loadLiveFragment() {
        // DIYP风格：直接创建直播频道页面（GridFragment）
        GridFragment liveFragment = GridFragment.newInstance(getLiveSortData());

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentLayout, liveFragment)  // 复用原布局的 contentLayout
                .commitAllowingStateLoss();
    }

    // 获取直播分类数据（从缓存或默认源）
    private MovieSort.SortData getLiveSortData() {
        SourceBean homeSource = ApiConfig.get().getHomeSourceBean();
        if (homeSource != null) {
            // 如果源已配置，尝试获取直播分类
            // 这里简化：假设源已订阅，GridFragment 会自动加载直播
            MovieSort.SortData data = new MovieSort.SortData();
            data.id = "live";
            data.name = "直播";
            return data;
        }

        // 如果没有源，显示空列表 + 提示配置
        Toast.makeText(this, "请按菜单键配置直播源订阅地址", Toast.LENGTH_LONG).show();
        return new MovieSort.SortData();
    }

    // 时间更新
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint({"DefaultLocale", "SetTextI18n"})
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat(getString(R.string.hm_date1) + " | " + getString(R.string.hm_date2));
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };

    // 菜单键直接打开设置（填写订阅地址）
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                startActivity(new Intent(this, SettingActivity.class));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
    }

    // 跳转 Activity 工具方法（从 BaseActivity 复制过来）
    public void jumpActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }
}
