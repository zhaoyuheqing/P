package com.github.tvbox.osc.ui.fragment;

import android.content.res.TypedArray;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import androidx.core.content.ContextCompat;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blankj.utilcode.util.GsonUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.adapter.GridFilterKVAdapter;
import com.github.tvbox.osc.ui.dialog.GridFilterDialog;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Stack;

/**
 * 纯直播网格界面，无源依赖，空网格 + 可交互提示
 */
public class GridFragment extends BaseLazyFragment {
    private MovieSort.SortData sortData = null;
    private TvRecyclerView mGridView;
    private GridAdapter gridAdapter;
    private int page = 1;
    private int maxPage = 1;
    private boolean isLoad = false;
    private boolean isTop = true;
    private View focusedView = null;

    private static class GridInfo{
        public String sortID="";
        public TvRecyclerView mGridView;
        public GridAdapter gridAdapter;
        public int page = 1;
        public int maxPage = 1;
        public boolean isLoad = false;
        public View focusedView = null;
    }

    Stack<GridInfo> mGrids = new Stack<GridInfo>(); //ui栈

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return new GridFragment().setArguments(sortData);
    }

    public GridFragment setArguments(MovieSort.SortData sortData) {
        this.sortData = sortData;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && this.sortData == null) {
            this.sortData = GsonUtils.fromJson(savedInstanceState.getString("sortDataJson"), MovieSort.SortData.class);
        }
    }

    @Override
    protected void init() {
        if (mGridView == null) {
            initView();
            // 不再调用 initViewModel 和 initData，因为不加载任何数据
        }
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("sortDataJson", GsonUtils.toJson(sortData));        
    }

    private void changeView(String id,Boolean isFolder){
        if(isFolder){
            this.sortData.flag =style==null?"1":"2";
        }else {
            this.sortData.flag ="2";
        }
        initView();
        this.sortData.id = id;
        // 不再调用 initViewModel 和 initData
    }

    public boolean isFolederMode() {
        return (getUITag() == '1');
    }

    public char getUITag() {
        return (sortData == null || sortData.flag == null || sortData.flag.length() == 0) ? '0' : sortData.flag.charAt(0);
    }

    public boolean enableFastSearch() {  
        return sortData.flag == null || sortData.flag.length() < 2 || (sortData.flag.charAt(1) == '1'); 
    }

    private void saveCurrentView() {
        if (this.mGridView == null) return;
        GridInfo info = new GridInfo();
        info.sortID = this.sortData.id;
        info.mGridView = this.mGridView;
        info.gridAdapter = this.gridAdapter;
        info.page = this.page;
        info.maxPage = this.maxPage;
        info.isLoad = this.isLoad;
        info.focusedView = this.focusedView;
        this.mGrids.push(info);
    }

    public boolean restoreView() {
        if (mGrids.empty()) return false;
        this.showSuccess();
        ((ViewGroup) mGridView.getParent()).removeView(this.mGridView);
        GridInfo info = mGrids.pop();
        this.sortData.id = info.sortID;
        this.mGridView = info.mGridView;
        this.gridAdapter = info.gridAdapter;
        this.page = info.page;
        this.maxPage = info.maxPage;
        this.isLoad = info.isLoad;
        this.focusedView = info.focusedView;
        this.mGridView.setVisibility(View.VISIBLE);
        if (mGridView != null) {
            mGridView.requestFocus();
            if (info.focusedView != null) {
                info.focusedView.requestFocus();
            }
        }

        if (gridAdapter != null) {
            mGridView.setAdapter(gridAdapter);
            rebindClickListeners();
        }

        return true;
    }

    private void rebindClickListeners() {
        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                // 空网格时无数据，保留原有逻辑但不跳转
                // 如果未来加数据，可恢复跳转
            }
        });

        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                // 同上，空时不处理
                return true;
            }
        });
    }

    private ImgUtil.Style style;

    private void createView() {
        this.saveCurrentView();
        if (mGridView == null) {
            mGridView = findViewById(R.id.mGridView);
        } else {
            TvRecyclerView v3 = new TvRecyclerView(this.mContext);
            v3.setSpacingWithMargins(10, 10);
            v3.setLayoutParams(mGridView.getLayoutParams());
            v3.setPadding(mGridView.getPaddingLeft(), mGridView.getPaddingTop(), mGridView.getPaddingRight(), mGridView.getPaddingBottom());
            v3.setClipToPadding(mGridView.getClipToPadding());
            ((ViewGroup) mGridView.getParent()).addView(v3);
            mGridView.setVisibility(View.GONE);
            mGridView = v3;
            mGridView.setVisibility(View.VISIBLE);

            if (gridAdapter != null) {
                mGridView.setAdapter(gridAdapter);
                rebindClickListeners();
            }
        }
        mGridView.setHasFixedSize(true);

        style = null;

        gridAdapter = new GridAdapter(isFolederMode(), style);
        this.page = 1;
        this.maxPage = 1;
        this.isLoad = false;
    }

    private void initView() {
        this.createView();
        mGridView.setAdapter(gridAdapter);

        mGridView.setFocusable(true);
        mGridView.setFocusableInTouchMode(true);
        mGridView.setClickable(true);

        if (isFolederMode()) {
            mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        } else {
            int spanCount = isBaseOnWidth() ? 5 : 6;
            if (style != null) {
                spanCount = ImgUtil.spanCountByStyle(style, spanCount);
            }
            if (spanCount == 1) {
                mGridView.setLayoutManager(new V7LinearLayoutManager(mContext, spanCount, false));
            } else {
                mGridView.setLayoutManager(new V7GridLayoutManager(mContext, spanCount));
            }
        }

        // 禁用加载更多（无数据源，不需要）
        gridAdapter.setEnableLoadMore(false);

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });

        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                }
                return false;
            }
        });

        rebindClickListeners();

        gridAdapter.setLoadMoreView(new LoadMoreView());

        // 自定义空状态提示（必须可见、可焦点、可点击）
        TextView emptyTv = new TextView(mContext);
        emptyTv.setText("暂无直播频道\n点击这里或按菜单键进入设置添加源");
        emptyTv.setTextColor(0xFFFFFFFF);
        emptyTv.setTextSize(20);
        emptyTv.setGravity(android.view.Gravity.CENTER);
        emptyTv.setPadding(0, 300, 0, 0);
        emptyTv.setClickable(true);
        emptyTv.setFocusable(true);
        emptyTv.setFocusableInTouchMode(true);
        emptyTv.setOnClickListener(v -> {
            Toast.makeText(mContext, "请按菜单键进入设置添加源", Toast.LENGTH_SHORT).show();
        });
        gridAdapter.setEmptyView(emptyTv);

        // 强制空数据，确保显示 emptyTv
        gridAdapter.setNewData(null);
        gridAdapter.notifyDataSetChanged();

        // 多次延迟强制焦点到 emptyTv
        new android.os.Handler().postDelayed(() -> {
            if (emptyTv.getParent() != null) {
                emptyTv.requestFocus();
                emptyTv.setSelected(true);
            }
        }, 300);

        new android.os.Handler().postDelayed(() -> {
            if (emptyTv.getParent() != null) {
                emptyTv.requestFocus();
            }
        }, 800);

        new android.os.Handler().postDelayed(() -> {
            if (emptyTv.getParent() != null) {
                emptyTv.requestFocus();
            }
        }, 1500);
    }

    public boolean isLoad() {
        return isLoad || !mGrids.empty();
    }

    private void toggleFilterStatus() {
        // 无源不处理过滤器
    }

    public boolean isTop() {
        return isTop;
    }

    public void scrollTop() {
        isTop = true;
        mGridView.scrollToPosition(0);
    }

    public void showFilter() {
        // 无源不显示过滤器
    }

    public void setFilterDialogData() {
        // 无源不处理
    }

    public void forceRefresh() {
        page = 1;
        // 无源不刷新
    }
}
