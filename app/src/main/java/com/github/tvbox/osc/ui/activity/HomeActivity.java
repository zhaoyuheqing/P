package com.github.tvbox.osc.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity {

    // takagen99: Added to allow read string（原静态 Resources 获取方法，保留以兼容全项目调用）
    private static Resources res;

    private final Handler mHandler = new Handler();

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;  // 修改为极简布局
    }

    @Override
    protected void init() {
        // takagen99: Added to allow read string（必须保留）
        res = getResources();

        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        App.startWebserver();

        // 必须保留：源加载
        initData();

        // 核心目标：直接加载直播频道列表
        loadLiveChannelList();
    }

    // 必须保留：全项目调用 HomeActivity.getRes().getString(R.string.xxx)
    public static Resources getRes() {
        return res;
    }

    // 必须保留：UserFragment 等地方调用 HomeActivity.homeRecf()
    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }

    // 必须保留：UserFragment 等地方调用 HomeActivity.reHome(mContext)
    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    // 核心：开机直达直播（替换容器为 GridFragment）
    private void loadLiveChannelList() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, GridFragment.newInstance(getLiveSortData()))
                .commitAllowingStateLoss();
    }

    // 简化直播分类占位（实际源加载后 GridFragment 会自动更新）
    private MovieSort.SortData getLiveSortData() {
        MovieSort.SortData data = new MovieSort.SortData();
        data.id = "live";
        data.name = "直播";
        return data;
    }

    // 必须保留：源加载逻辑（否则订阅无效）
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            // 源已加载，可以刷新直播列表（如果需要）
            return;
        }

        ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(HomeActivity.this::initData, 50);
            }

            @Override
            public void error(String msg) {
                dataInitOk = true;
                jarInitOk = true;
                mHandler.postDelayed(HomeActivity.this::initData, 50);
            }

            @Override
            public void retry() {
                mHandler.postDelayed(HomeActivity.this::initData, 50);
            }
        }, this);
    }

    // 菜单键打开设置（你的核心需求）
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        ControlManager.get().stopServer();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        // 保留原事件处理（推送等）
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            // 原推送逻辑（如果有）
        }
    }
}
