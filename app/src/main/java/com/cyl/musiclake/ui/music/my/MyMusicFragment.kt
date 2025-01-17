package com.cyl.musiclake.ui.music.my

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.cyl.musiclake.BuildConfig
import com.cyl.musiclake.MusicApp
import com.cyl.musiclake.R
import com.music.lake.musiclib.bean.BaseMusicInfo
import com.cyl.musiclake.bean.NoticeInfo
import com.cyl.musiclake.bean.Playlist
import com.cyl.musiclake.common.Constants
import com.cyl.musiclake.common.Extras
import com.cyl.musiclake.common.NavigationHelper
import com.cyl.musiclake.data.PlaylistLoader
import com.cyl.musiclake.event.*
import com.cyl.musiclake.ui.base.BaseFragment
import com.cyl.musiclake.ui.music.dialog.CreatePlaylistDialog
import com.cyl.musiclake.ui.music.edit.PlaylistManagerUtils
import com.cyl.musiclake.ui.music.playlist.PlaylistAdapter
import com.cyl.musiclake.ui.music.playlist.edit.PlaylistManagerActivity
import com.cyl.musiclake.ui.my.user.UserStatus
import com.cyl.musiclake.utils.LogUtil
import com.cyl.musiclake.utils.SPUtils
import com.cyl.musiclake.utils.ToastUtils
import com.google.android.material.tabs.TabLayout
import com.music.lake.musiclib.MusicPlayerManager
import kotlinx.android.synthetic.main.frag_local.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.yesButton


/**
 * Created by yonglong on 2015/6/29.
 * 我的 界面
 */
class MyMusicFragment : BaseFragment<MyMusicPresenter>(), MyMusicContract.View {

    private var localPlaylists = mutableListOf<Playlist>()
    private var playlists = mutableListOf<Playlist>()
    private var wyPlaylists = mutableListOf<Playlist>()

    private var playlistTag = Constants.PLAYLIST_LOCAL_ID
    private var mAdapter: PlaylistAdapter? = null

    override fun getLayoutId(): Int {
        return R.layout.frag_local
    }

    override fun showNoticeInfo(notice: NoticeInfo) {
        alert {
            isCancelable = false
            title = notice.title
            message = notice.message
            if (notice.dismiss) {
                yesButton {
                    SPUtils.putAnyCommit(SPUtils.SP_KEY_NOTICE_CODE, notice.id)
                }
            }
        }.show()
    }

    public override fun initViews() {
        //初始化下载 Item
        downloadView.visibility = if (BuildConfig.HAS_DOWNLOAD) View.VISIBLE else View.GONE

        //初始化歌单列表
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.isSmoothScrollbarEnabled = false

        playlistRcv.layoutManager = LinearLayoutManager(context)
        playlistRcv.isNestedScrollingEnabled = false

        mAdapter = PlaylistAdapter(playlists)
        playlistRcv.adapter = mAdapter
        mAdapter?.setEmptyView(R.layout.view_playlist_empty)

        //加载通知
        mPresenter?.loadMusicLakeNotice()


        //初始化歌单Tab
        playlistTab.addTab(playlistTab.newTab().setText("本地歌单").setTag(Constants.PLAYLIST_LOCAL_ID))
        playlistTab.addTab(playlistTab.newTab().setText("在线歌单").setTag(Constants.PLAYLIST_CUSTOM_ID))
        playlistTab.addTab(playlistTab.newTab().setText("网易歌单").setTag(Constants.PLAYLIST_WY_ID))
        playlistTab.addOnTabSelectedListener(object : TabLayout.BaseOnTabSelectedListener<TabLayout.Tab> {
            override fun onTabReselected(p0: TabLayout.Tab?) {

            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
            }

            override fun onTabSelected(p0: TabLayout.Tab?) {
                p0?.tag?.let {
                    playlistTag = it.toString()
                }
                updatePlaylist()
            }
        })
    }


    override fun initInjector() {
        mFragmentComponent.inject(this)
    }

    override fun listener() {
        //新增歌单管理点击
        playlistAddIv.setOnClickListener {
            val dialog = CreatePlaylistDialog.newInstance()
            dialog.successListener = {
                PlaylistManagerUtils.createPlaylist(it, type = playlistTag) {
                    ToastUtils.show(MusicApp.getAppContext().getString(R.string.create_playlist_success))
                    EventBus.getDefault().post(MyPlaylistEvent(Constants.PLAYLIST_ADD, it))
                }
            }
            if (playlistTag == Constants.PLAYLIST_CUSTOM_ID && !UserStatus.getLoginStatus()) {
                ToastUtils.show(getString(com.cyl.musiclake.R.string.prompt_login))
            } else {
                dialog.show(childFragmentManager, TAG_CREATE)
            }
        }
        //歌单管理点击
        playlistManagerIv.setOnClickListener {
            val intent = Intent(activity, PlaylistManagerActivity::class.java)
            intent.putExtra(Extras.PLAYLIST_TYPE, playlistTag)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        //是否显示
        if (SPUtils.getAnyByKey(SPUtils.SP_KEY_NOTICE_CODE, 0) == 0) {
            //显示通知
            val noticeInfo = NoticeInfo()
            noticeInfo.id = 10
            noticeInfo.dismiss = true
            noticeInfo.message = "因为网易云API是使用免费的云引擎搭建的服务器，所以请求会有些限制，云服务每天最多运行18个小时。所以有时候网易云音乐的API会请求异常，也可以自己部署本地的网易云音乐api服务"
            noticeInfo.title = "提示"
            showNoticeInfo(noticeInfo)
        }
    }

    /**
     * 数据加载
     */
    override fun loadData() {
        mPresenter?.loadSongs()
        mPresenter?.loadPlaylist()
        mPresenter?.loadLocalPlaylist()
        mPresenter?.updateLocalVideo()
    }


    override fun showSongs(songList: MutableList<BaseMusicInfo>) {
        localView.setSongsNum(songList.size, 0)
        localView.setOnItemClickListener { view, position ->
            if (view.id == R.id.iv_play) {
                MusicPlayerManager.getInstance().playMusic(songList, 0)
            } else {
                toFragment(position)
            }
        }
    }

    /**
     * 更新歌单列表
     */
    private fun updatePlaylist() {
        when (playlistTag) {
            Constants.PLAYLIST_LOCAL_ID -> {
                mAdapter?.setNewData(localPlaylists)
                playlistAddIv.visibility = View.VISIBLE
                playlistManagerIv.visibility = View.VISIBLE
            }
            Constants.PLAYLIST_CUSTOM_ID -> {
                mAdapter?.setNewData(playlists)
                playlistAddIv.visibility = View.VISIBLE
                playlistManagerIv.visibility = View.VISIBLE
            }
            Constants.PLAYLIST_WY_ID -> {
                if (wyPlaylists.size == 0) {
                    //加载网易云音乐歌单
                    mPresenter?.loadWyUserPlaylist()
                }
                mAdapter?.setNewData(wyPlaylists)
                playlistAddIv.visibility = View.INVISIBLE
                playlistManagerIv.visibility = View.INVISIBLE
            }
        }
        //如果歌单列表为空则显示空提示
        if (mAdapter?.data?.size == 0) {
            mAdapter?.setEmptyView(R.layout.view_playlist_empty)
        }
    }

    /**
     * 显示本地歌单
     */
    override fun showLocalPlaylist(playlists: MutableList<Playlist>) {
        this.localPlaylists = playlists
        updatePlaylist()
    }

    /**
     * 显示网易歌单
     */
    override fun showWyPlaylist(playlists: MutableList<Playlist>) {
        this.wyPlaylists = playlists
        updatePlaylist()
    }

    /**
     * 显示在线歌单
     */
    override fun showPlaylist(playlists: MutableList<Playlist>) {
        this.playlists = playlists
        updatePlaylist()
    }

    override fun showHistory(baseMusicInfoInfoList: MutableList<BaseMusicInfo>) {
        historyView.setSongsNum(baseMusicInfoInfoList.size, 1)
        historyView.setOnItemClickListener { view, position ->
            if (view.id == R.id.iv_play) {
                MusicPlayerManager.getInstance().playMusic(baseMusicInfoInfoList, 0)
            } else {
                toFragment(position)
            }
        }
    }

    /**
     * 显示收藏列表
     */
    override fun showLoveList(baseMusicInfoInfoList: MutableList<BaseMusicInfo>) {
        favoriteView.setSongsNum(baseMusicInfoInfoList.size, 2)
        favoriteView.setOnItemClickListener { view, position ->
            if (view.id == R.id.iv_play) {
                MusicPlayerManager.getInstance().playMusic(baseMusicInfoInfoList, 0)
            } else {
                toFragment(position)
            }
        }
    }

    /**
     * 显示下载列表
     */
    override fun showDownloadList(baseMusicInfoInfoList: MutableList<BaseMusicInfo>) {
        downloadView.setSongsNum(baseMusicInfoInfoList.size, 4)
        downloadView.setOnItemClickListener { view, position ->
            if (view.id == R.id.iv_play) {
                MusicPlayerManager.getInstance().playMusic(baseMusicInfoInfoList, 0)
            } else {
                toFragment(position)
            }
        }
    }

    /**
     * 显示video列表
     */
    override fun showVideoList(videoList: MutableList<BaseMusicInfo>) {
        videoView.setSongsNum(videoList.size, 3)
        videoView.setOnItemClickListener { _, position ->
            toFragment(position)
        }
    }

    private fun toFragment(position: Int) {
        when (position) {
            0 -> NavigationHelper.navigateToLocalMusic(mFragmentComponent.activity, null)
            1 -> NavigationHelper.navigateToPlaylist(mFragmentComponent.activity, PlaylistLoader.getHistoryPlaylist(), null)
            2 -> NavigationHelper.navigateToPlaylist(mFragmentComponent.activity, PlaylistLoader.getFavoritePlaylist(), null)
            3 -> NavigationHelper.navigateToVideo(mFragmentComponent.activity, null)
            4 -> NavigationHelper.navigateToDownload(mFragmentComponent.activity)
        }
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        super.showError(message, showRetryButton)
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetaChangedEvent(event: MetaChangedEvent) {
        if (mPresenter != null) {
            mPresenter?.updateHistory()
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUserInfoChangedEvent(event: LoginEvent) {
        if (mPresenter != null) {
            mPresenter?.loadPlaylist()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaylistChangedEvent(event: PlaylistEvent) {
        when (event.type) {
            Constants.PLAYLIST_LOCAL_ID -> mPresenter?.loadSongs()
            Constants.PLAYLIST_CUSTOM_ID -> mPresenter?.loadPlaylist()
            Constants.PLAYLIST_LOVE_ID -> mPresenter?.updateFavorite()
            Constants.PLAYLIST_HISTORY_ID -> mPresenter?.updateHistory()
            Constants.PLAYLIST_DOWNLOAD_ID -> mPresenter?.updateDownload()
        }
    }

    /**
     * 更新在线歌单
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaylistChangedEvent(event: MyPlaylistEvent) {
        when (event.operate) {
            Constants.PLAYLIST_ADD -> {
                if (event.playlist?.type == Constants.PLAYLIST_LOCAL_ID) mPresenter?.loadLocalPlaylist()
                if (event.playlist?.type == Constants.PLAYLIST_CUSTOM_ID) mPresenter?.loadPlaylist()
            }
            Constants.PLAYLIST_DELETE -> {
                for (i in 0 until playlists.size) {
                    if (playlists[i].pid == event.playlist?.pid) {
                        playlists.removeAt(i)
                        mAdapter?.notifyItemRemoved(i)
                        return
                    }
                }
                for (i in 0 until localPlaylists.size) {
                    if (localPlaylists[i].pid == event.playlist?.pid) {
                        localPlaylists.removeAt(i)
                        mAdapter?.notifyItemRemoved(i)
                        return
                    }
                }
            }
            Constants.PLAYLIST_UPDATE -> {
                mPresenter?.loadPlaylist()
                mPresenter?.loadLocalPlaylist()
            }
            Constants.PLAYLIST_RENAME -> {
                for (i in 0 until playlists.size) {
                    if (playlists[i].pid == event.playlist?.pid) {
                        playlists[i].name = event.playlist?.name
                        mAdapter?.notifyItemChanged(i)
                        return
                    }
                }
                for (i in 0 until localPlaylists.size) {
                    if (localPlaylists[i].pid == event.playlist?.pid) {
                        localPlaylists[i].name = event.playlist?.name
                        mAdapter?.notifyItemChanged(i)
                        return
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateDownloadEvent(event: FileEvent) {
        LogUtil.d(TAG, "updateDownloadEvent cache${event.isCache}")
        mPresenter?.updateDownload()
        mPresenter?.loadSongs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    companion object {

        private val TAG_CREATE = "create_playlist"
        private val TAG = "MyMusicFragment"

        fun newInstance(): MyMusicFragment {
            val args = Bundle()
            val fragment = MyMusicFragment()
            fragment.arguments = args
            return fragment
        }
    }

}
