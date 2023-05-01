package com.cyl.musiclake.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;

import com.cyl.musiclake.MusicApp;
import com.cyl.musiclake.R;
import com.cyl.musiclake.api.music.MusicApi;
import com.cyl.musiclake.api.music.MusicApiServiceImpl;
import com.cyl.musiclake.api.net.ApiManager;
import com.cyl.musiclake.api.net.RequestCallBack;
import com.cyl.musiclake.ui.widget.LyricView;
import com.cyl.musiclake.ui.widget.lyric.FloatLyricView;
import com.cyl.musiclake.ui.widget.lyric.LyricInfo;
import com.cyl.musiclake.ui.widget.lyric.LyricParseUtils;
import com.music.lake.musiclib.MusicPlayerManager;
import com.music.lake.musiclib.bean.BaseMusicInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Observable;

/**
 * 桌面歌词管理类
 */
public class FloatLyricViewManager {
    private static final String TAG = "FloatLyricViewManager";
    private static FloatLyricView mFloatLyricView;
    private static WindowManager.LayoutParams mFloatLyricViewParams;
    private static WindowManager mWindowManager;
    private static LyricInfo mLyricInfo;
    private boolean mIsLock;
    private Handler handler = new Handler();
    private String mSongName;
    private static boolean isFirstSettingLyric; //第一次设置歌词

    /**
     * 歌词信息
     */
    public static String lyricInfo;
    private boolean showLyric;
    private Handler mMainHandler;

    /**
     * 定时器，定时进行检测当前应该创建还是移除悬浮窗。
     */
    private Context mContext;

    @SuppressLint("StaticFieldLeak")
    private volatile static FloatLyricViewManager manager;

    public static FloatLyricViewManager getInstance() {
        if (manager == null) {
            synchronized (FloatLyricViewManager.class) {
                if (manager == null) {
                    manager = new FloatLyricViewManager();
                }
            }
        }
        return manager;
    }

    private FloatLyricViewManager() {
    }

    public void init(Context context) {
        mContext = context;

        //初始化主线程Handler
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * ---------------------------歌词View更新-----------------------------
     */
    private static List<LyricView> lyricViews = new ArrayList<>();

    public void setLyricChangeListener(LyricView lyricView) {
        lyricViews.add(lyricView);
    }

    public void removeLyricChangeListener(LyricView lyricView) {
        lyricViews.remove(lyricView);
    }

    /**
     * -----------------------------------------------------------------
     */

    public void updatePlayStatus(boolean isPlaying) {
        if (mFloatLyricView != null)
            mFloatLyricView.setPlayStatus(isPlaying);
    }


    /**
     * 加载歌词
     */
    public void loadLyric(BaseMusicInfo mPlayingBaseMusicInfoInfo) {
        resetLyric(MusicApp.getAppContext().getString(R.string.lyric_loading));
        if (mPlayingBaseMusicInfoInfo != null) {
            mSongName = mPlayingBaseMusicInfoInfo.getTitle();
            Observable<String> observable = MusicApi.INSTANCE.getLyricInfo(mPlayingBaseMusicInfoInfo);
            if (observable != null) {
                ApiManager.request(observable, new RequestCallBack<String>() {
                    @Override
                    public void success(String result) {
                        updateLyric(result);
                    }

                    @Override
                    public void error(String msg) {
                        updateLyric("");
                        LogUtil.e("LoadLyric", msg);
                    }
                });
            } else {
                updateLyric("");
            }
        } else {
            updateLyric("");
        }
    }

    /**
     * 保存歌词
     *
     * @param info 歌词
     */
    public void saveLyricInfo(String name, String artist, String info) {
        lyricInfo = info;
        MusicApiServiceImpl.INSTANCE.saveLyricInfo(name, artist, info);
        setLyric(lyricInfo);
        for (int i = 0; i < lyricViews.size(); i++) {
            lyricViews.get(i).setLyricContent(info);
        }
    }

    /**
     * 重置
     *
     * @param info 歌词
     */
    private void resetLyric(String info) {
        lyricInfo = info;
        setLyric(lyricInfo);
        for (int i = 0; i < lyricViews.size(); i++) {
            lyricViews.get(i).reset(info);
        }
    }

    /**
     * 更新歌词
     *
     * @param info 歌词
     */
    private void updateLyric(String info) {
        lyricInfo = info;
        setLyric(lyricInfo);
        for (int i = 0; i < lyricViews.size(); i++) {
            lyricViews.get(i).setLyricContent(info);
        }
    }


    /**
     * 设置歌词
     *
     * @param lyricInfo 歌词信息
     */
    public static void setLyric(String lyricInfo) {
        mLyricInfo = LyricParseUtils.setLyricResource(lyricInfo);
        isFirstSettingLyric = true;
    }


    /**
     * 判断当前界面是否是应用界面
     */
    private boolean isHome() {
        try {
            return MusicApp.count != 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * 创建一个小悬浮窗。初始位置为屏幕的右部中间位置。
     *
     * @param context 必须为应用程序的Context.
     */
    private void createFloatLyricView(Context context) {
        try {
            WindowManager windowManager = getWindowManager();
            Point size = new Point();
            //获取屏幕宽高
            windowManager.getDefaultDisplay().getSize(size);
            int screenWidth = size.x;
            int screenHeight = size.y;
            LogUtil.d(TAG, "开始创建悬浮歌词");
            if (mFloatLyricView == null) {
                LogUtil.d(TAG, "创建悬浮歌词 创建mFloatLyricView");
                mFloatLyricView = new FloatLyricView(context);
                if (mFloatLyricViewParams == null) {
                    LogUtil.d(TAG, "创建悬浮歌词 创建mFloatLyricViewParams");
                    mFloatLyricViewParams = new WindowManager.LayoutParams();
                    mFloatLyricViewParams.type = WindowManager.LayoutParams.TYPE_PHONE;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mFloatLyricViewParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                    } else {
                        mFloatLyricViewParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                    }

                    mFloatLyricViewParams.format = PixelFormat.RGBA_8888;

                    mFloatLyricViewParams.gravity = Gravity.START | Gravity.TOP;
                    mFloatLyricViewParams.width = mFloatLyricView.getViewWidth();
                    mFloatLyricViewParams.height = mFloatLyricView.getViewHeight();
                    mFloatLyricViewParams.x = screenWidth;
                    mFloatLyricViewParams.y = screenHeight / 2;
                }
                //设置可触摸可点击
                mFloatLyricViewParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                //设置布局属性
                mFloatLyricView.setParams(mFloatLyricViewParams);
                windowManager.addView(mFloatLyricView, mFloatLyricViewParams);
                setLyric(lyricInfo);
            }
            LogUtil.d(TAG, "创建悬浮歌词 创建完成");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将小悬浮窗从屏幕上移除。
     *
     * @param context 必须为应用程序的Context.
     */
    public void removeFloatLyricView(Context context) {
        try {
            if (mFloatLyricView != null) {
                WindowManager windowManager = getWindowManager();
                windowManager.removeView(mFloatLyricView);
                mFloatLyricView = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Timer lyricTimer;

    /**
     * 显示桌面歌词
     * 开个定时器定时刷新桌面歌词
     *
     * @param show
     */
    public void showDesktopLyric(boolean show) {
        updateLyric(MusicPlayerManager.getInstance().getPlayingPosition(), MusicPlayerManager.getInstance().getDuration());
        if (show) {
            // 开启定时器，每隔0.5秒刷新一次
            if (lyricTimer == null) {
                lyricTimer = new Timer();
                lyricTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        mMainHandler.post(() -> {
                            if (MusicPlayerManager.getInstance().isPlaying()) {
                                //正在播放时刷新
                                updateLyric(MusicPlayerManager.getInstance().getPlayingPosition(), MusicPlayerManager.getInstance().getDuration());
                            }
                        });
                    }
                }, 0, 200);
            }
        } else {
            if (lyricTimer != null) {
                lyricTimer.cancel();
                lyricTimer = null;
            }
            removeFloatLyricView(mContext);
        }
    }


    /**
     * 开启歌词
     */
    public void startFloatLyric() {
        if (SystemUtils.isOpenFloatWindow()) {
            showLyric = !showLyric;
            SPUtils.putAnyCommit(SPUtils.SP_KEY_FLOAT_LYRIC_LOCK, false);
            showDesktopLyric(showLyric);
        } else {
            SystemUtils.applySystemWindow();
        }
    }


    /**
     * 更新小悬浮窗的TextView上的数据，显示内存使用的百分比。
     */
    public void updateLyric(long positon, long duration) {
        // 当前界面不是本应用界面，且没有悬浮窗显示，则创建悬浮窗。
        if (!isHome() && !isWindowShowing()) {
            handler.post(() -> createFloatLyricView(mContext));
        } else if (isHome() && isWindowShowing()) {
            handler.post(() -> {
                removeFloatLyricView(mContext);
            });
        } else if (isWindowShowing()) {
            handler.post(() -> {
                if (mFloatLyricView != null) {
                    if (isFirstSettingLyric) {
                        mFloatLyricView.getMTitle().setText(mSongName);
                        mFloatLyricView.getMLyricText().setLyricInfo(mLyricInfo);
                        isFirstSettingLyric = false;
                    }
                    mFloatLyricView.getMLyricText().setCurrentTimeMillis(positon);
                    mFloatLyricView.getMLyricText().setDurationMillis(duration);
                }
            });
        }
    }

    /**
     * 是否有悬浮窗(包括小悬浮窗和大悬浮窗)显示在屏幕上。
     *
     * @return 有悬浮窗显示在桌面上返回true，没有的话返回false。
     */

    private static boolean isWindowShowing() {
        return mFloatLyricView != null;
    }

    /**
     * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
     *
     * @return WindowManager的实例，用于控制在屏幕上添加或移除悬浮窗。
     */
    private static WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) MusicApp.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        }
        return mWindowManager;
    }


    public void saveLock(boolean lock, boolean toast) {
        mFloatLyricView.saveLock(lock, toast);
    }

}
