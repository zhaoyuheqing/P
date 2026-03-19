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
        btnEnterLive.setOnClickListener(v -> jumpActivity(LivePlayActivity.class));
        emptyLayout.addView(btnEnterLive);

        gridAdapter.setEmptyView(emptyLayout);
    }

    private void updateUIState() {
        var groups = ApiConfig.get().getChannelGroupList();
        if (groups != null && !groups.isEmpty()) {
            btnAddSource.setVisibility(View.GONE);
            btnEnterLive.setVisibility(View.VISIBLE);
            btnEnterLive.requestFocus();
        } else {
            btnAddSource.setVisibility(View.VISIBLE);
            btnEnterLive.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // 从设置返回后，强制加载一次直播源（利用 Hawk 保存的 URL）
        new Thread(() -> {
            try {
                String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
                if (!liveUrl.isEmpty()) {
                    // 关键：重新解析直播源
                    ApiConfig.loadLives(liveUrl);
                }
            } catch (Exception ignored) {
            }

            requireActivity().runOnUiThread(() -> {
                updateUIState();
                Toast.makeText(requireContext(), "频道数量: " + (ApiConfig.get().getChannelGroupList() == null ? 0 : ApiConfig.get().getChannelGroupList().size()), Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
