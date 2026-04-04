package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.constant.LiveConstants;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlaybackManager {

    private final WeakReference<Context> contextRef;
    private final Handler mainHandler;
    private VideoView videoView;
    private LiveController controller;
    private final LivePlayerManager playerManager = new LivePlayerManager();

    private LiveChannelItem currentChannel;
    private boolean isShiyiMode = false;
    private String shiyiTime = null;
    private int currentChangeSourceTimes = 0;

    private PlaybackListener listener;

    private final Runnable timeoutChangeSourceRun = this::handleTimeoutChangeSource;
    private final Runnable timeoutReplayRun = this::replayChannel;

    public interface PlaybackListener {
        boolean onSingleTap(MotionEvent e);
        void onLongPress();
        void onPlayStateChanged(int playState);
        void onVideoSizeChanged(int width, int height);
        void onChannelInfoUpdate(LiveChannelItem channel);
        void onShiyiModeChanged(boolean isShiyi, String timeRange);
        void onNeedShowBottomEpg();
        void onAutoSwitchToNextChannel();
    }

    public LivePlaybackManager(@NonNull Context context, @NonNull Handler handler, @NonNull VideoView videoView) {
        this.contextRef = new WeakReference<>(context);
        this.mainHandler = handler;
        this.videoView = videoView;
        initController();
    }

    private void initController() {
        Context ctx = contextRef.get();
        if (ctx == null) return;

        controller = new LiveController(ctx);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap(MotionEvent e) {
                return listener != null && listener.onSingleTap(e);
            }

            @Override
            public void longPress() {
                if (listener != null) listener.onLongPress();
            }

            @Override
            public void playStateChanged(int playState) {
                handlePlayState(playState);
                if (listener != null) listener.onPlayStateChanged(playState);
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0) playNextSource();
                else playPreSource();
            }
        });

        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        videoView.setVideoController(controller);
        videoView.setProgressManager(null);

        playerManager.init(videoView);
    }

    // 其余方法保持不变（时移工具、seekTo、pause/resume/release 等）
    // ...（此处省略，与之前提供的正确版本相同）
}
