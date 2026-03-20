package com.github.tvbox.osc.ui.activity;

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

    private static Resources res;
    private final Handler mHandler = new Handler();

    private boolean dataInitOk = false;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;
    }

    @Override
    protected void init() {
        res = getResources();
        EventBus.getDefault().register(this);
        ControlManager.get().startServer();
        App.startWebserver();

        // 关键修复：必须先完整加载配置，让 parseJson 执行包装逻辑
        initData();

        // 加载成功后自动进入直播（可根据需要调整延迟）
        mHandler.postDelayed(() -> jumpActivity(LivePlayActivity.class), 1200);
    }

    public static Resources getRes() {
        return res;
    }

    public static void homeRecf() {
        int homeRec = Hawk.get(HawkConfig.HOME_REC, -1);
        int limit = 2;
        if (homeRec == limit) homeRec = -1;
        homeRec++;
        Hawk.put(HawkConfig.HOME_REC, homeRec);
    }

    public static boolean reHome(Context appContext) {
        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        appContext.startActivity(intent);
        return true;
    }

    private void initData() {
        ApiConfig.get().loadConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    // jarInitOk = true; // 如果不需要 jar 可注释
                }
            }

            @Override
            public void error(String msg) {
                dataInitOk = true;
            }

            @Override
            public void retry() {
                mHandler.postDelayed(HomeActivity.this::initData, 1500);
            }
        }, this);
    }

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
        // 保留原事件处理
    }
}
