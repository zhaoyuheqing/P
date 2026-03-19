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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import android.os.Handler;
import android.os.Looper;

/**
 * @author pj567
 * @date :2020/12/21
 * @description: 纯直播壳 - 内置 TXT 源，完全去除首页源依赖
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

    // 内置直播源（TXT 格式）
    private static final String BUILT_IN_LIVE_SOURCE = "https://frosty-block-011f.pohoy71288.workers.dev/";

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
            loadBuiltInLiveChannels();  // 直接加载内置源
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
        // 不重新加载源，只切换 UI 模式
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
        gridAdapter.setEnableLoadMore(false);
    }

    private void rebindClickListeners() {
        gridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    if( video.tag !=null && (video.tag.equals("folder") || video.tag.equals("cover"))){
                        focusedView = view;
                        if(("12".indexOf(getUITag()) != -1)){
                            changeView(video.id,video.tag.equals("folder"));
                        }else {
                            changeView(video.id,false);
                        }
                    } else {
                        if (video.id == null || video.id.isEmpty() || video.id.startsWith("msearch:")) {
                            if(Hawk.get(HawkConfig.FAST_SEARCH_MODE, false) && enableFastSearch()){
                                jumpActivity(FastSearchActivity.class, bundle);
                            }else {
                                jumpActivity(SearchActivity.class, bundle);
                            }
                        } else {
                            jumpActivity(DetailActivity.class, bundle);
                        }
                    }
                }
            }
        });

        gridAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = gridAdapter.getData().get(position);
                if (video != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", video.id);
                    bundle.putString("sourceKey", video.sourceKey);
                    bundle.putString("title", video.name);
                    jumpActivity(FastSearchActivity.class, bundle);
                }
                return true;
            }
        });
    }

    // 手动加载内置 TXT 直播源（完全不依赖首页源）
    private void loadBuiltInLiveChannels() {
        new Thread(() -> {
            try {
                URL url = new URL(BUILT_IN_LIVE_SOURCE);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                List<Movie.Video> videos = new ArrayList<>();
                String line;
                int index = 1;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    String[] parts = line.split(",", 2);
                    if (parts.length >= 2) {
                        Movie.Video video = new Movie.Video();
                        video.name = parts[0].trim();
                        video.id = String.valueOf(index++);
                        video.sourceKey = "built_in_live";
                        video.tag = "live";
                        video.pic = "";
                        video.vodPlayUrl = parts[1].trim();  // 播放地址
                        videos.add(video);
                    }
                }
                reader.close();
                conn.disconnect();

                // UI 线程更新网格
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (videos.isEmpty()) {
                        showEmpty();
                    } else {
                        showSuccess();
                        isLoad = true;
                        gridAdapter.setNewData(videos);
                    }
                });
            } catch (Exception e) {
                LOG.e("内置直播源加载失败", e);
                new Handler(Looper.getMainLooper()).post(() -> showEmpty());
            }
        }).start();
    }

    public boolean isLoad() {
        return isLoad || !mGrids.empty();
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
        loadBuiltInLiveChannels();  // 刷新内置源
    }
}
