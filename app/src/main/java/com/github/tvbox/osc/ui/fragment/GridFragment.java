package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import java.util.List;

public class GridFragment extends BaseLazyFragment {

    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;
    private LinearLayout emptyLayout;
    private Button btnAddSource;
    private Button btnEnterLive;

    public static GridFragment newInstance() {
        return new GridFragment();
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
        btnEnterLive.setOnClickListener(v -> {
            jumpActivity(LivePlayActivity.class);
        });
        emptyLayout.addView(btnEnterLive);

        gridAdapter.setEmptyView(emptyLayout);
    }

    private void updateUIState() {
        // 始终显示“进入直播”按钮，让 LivePlayActivity 自己处理源
        btnAddSource.setVisibility(View.VISIBLE);
        btnEnterLive.setVisibility(View.VISIBLE);
        btnEnterLive.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();

        // 从设置返回后，检查是否保存了直播源 URL，并提示用户
        new Thread(() -> {
            String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
            final String msg;
            if (liveUrl.isEmpty()) {
                msg = "未检测到直播源地址，请在设置中添加";
            } else {
                msg = "已检测到直播源地址：" + liveUrl + "\n请点击“进入直播”查看频道（自动解析）";
            }

            requireActivity().runOnUiThread(() -> {
                updateUIState();
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
