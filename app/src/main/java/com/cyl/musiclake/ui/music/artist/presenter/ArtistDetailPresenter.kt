package com.cyl.musiclake.ui.music.artist.presenter

import com.cyl.musiclake.api.music.MusicApiServiceImpl
import com.cyl.musiclake.api.music.baidu.BaiduApiServiceImpl
import com.cyl.musiclake.api.music.netease.NeteaseApiServiceImpl
import com.cyl.musiclake.api.net.ApiManager
import com.cyl.musiclake.api.net.RequestCallBack
import com.cyl.musiclake.api.playlist.PlaylistApiServiceImpl
import com.cyl.musiclake.bean.Album
import com.cyl.musiclake.bean.Artist
import com.music.lake.musiclib.bean.BaseMusicInfo
import com.cyl.musiclake.bean.Playlist
import com.cyl.musiclake.data.PlaylistLoader
import com.cyl.musiclake.data.SongLoader
import com.cyl.musiclake.common.Constants
import com.cyl.musiclake.event.MyPlaylistEvent
import com.cyl.musiclake.ui.base.BasePresenter
import com.cyl.musiclake.ui.music.artist.contract.ArtistDetailContract
import com.cyl.musiclake.utils.LogUtil
import com.cyl.musiclake.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import javax.inject.Inject


/**
 * Created by yonglong on 2018/1/7.
 */

class ArtistDetailPresenter @Inject
constructor() : BasePresenter<ArtistDetailContract.View>(), ArtistDetailContract.Presenter {

    /**
     * 加载歌手歌曲列表
     */
    override fun loadArtistSongs(artist: Artist) {
        if (artist.type == null || artist.type == Constants.LOCAL) {
            doAsync {
                val data = SongLoader.getSongsForArtist(artist.name)
                uiThread {
                    mView.showPlaylistSongs(data)
                }
            }
            return
        } else if (artist.type == Constants.BAIDU) {
            val observable = BaiduApiServiceImpl.getArtistSongList(artist.artistId.toString(), 0)
            ApiManager.request(observable, object : RequestCallBack<Artist> {
                override fun success(result: Artist) {
                    mView?.showPlaylistSongs(result.songs)
                    mView?.showArtistInfo(result)
                }

                override fun error(msg: String) {
                    LogUtil.e(TAG, msg)
                    mView?.showError(msg, true)
                    ToastUtils.show(msg)
                }
            })
            return
        }
        val observable = MusicApiServiceImpl.getArtistSongs(artist.type!!, artist.artistId.toString(), 50, 0)
        ApiManager.request(observable, object : RequestCallBack<Artist> {
            override fun success(result: Artist) {
                val musicLists = result.songs
//                val iterator = musicLists.iterator()
//                while (iterator.hasNext()) {
//                    val temp = iterator.next()
//                    if (temp.isCp) {
//                        //list.remove(temp);// 出现java.util.ConcurrentModificationException
//                        iterator.remove()// 推荐使用
//                    }
//                }
                mView?.showPlaylistSongs(musicLists)
                mView?.showArtistInfo(result)
            }

            override fun error(msg: String) {
                LogUtil.e(TAG, msg)
                mView?.showError(msg, true)
                ToastUtils.show(msg)
            }
        })
    }

    /**
     * 加载歌手专辑列表
     *
     * 暂时只支持本地和百度
     * TODO 增加其他平台的歌手专辑列表
     */
    override fun loadArtistAlbum(artist: Artist) {
        if (artist.type == null || artist.type == Constants.LOCAL) {
            doAsync {
                val albumData = SongLoader.getAllAlbums(artist.name)
                uiThread {
                    mView.showAllAlbum(albumData)
                }
            }
            return
        } else if (artist.type == Constants.BAIDU) {
            val observable = BaiduApiServiceImpl.getArtistAlbumList(artist.artistId.toString(), 0)
            ApiManager.request(observable, object : RequestCallBack<MutableList<Album>> {
                override fun success(result: MutableList<Album>) {
                    mView?.showAllAlbum(result)
                }

                override fun error(msg: String) {
                    LogUtil.e(TAG, msg)
                    mView?.showError(msg, true)
                    ToastUtils.show(msg)
                }
            })
            return
        }
    }

    /**
     * 加载专辑歌曲列表
     */
    override fun loadAlbumSongs(album: Album) {
        if (album.type == null || album.type == Constants.LOCAL) {
            doAsync {
                val data = SongLoader.getSongsForAlbum(album.name)
                uiThread {
                    mView.showPlaylistSongs(data)
                }
            }
            return
        } else if (album.albumId == null) {
            mView?.showPlaylistSongs(null)
            return
        } else if (album.type == Constants.BAIDU) {
            val observable = BaiduApiServiceImpl.getAlbumSongList(album.albumId.toString())
            ApiManager.request(observable, object : RequestCallBack<Album> {
                override fun success(result: Album) {
                    mView?.showPlaylistSongs(result.songs)
                }

                override fun error(msg: String) {
                    LogUtil.e(TAG, msg)
                    mView?.showError(msg, true)
                    ToastUtils.show(msg)
                }
            })
            return
        }
        val observable = MusicApiServiceImpl.getAlbumSongs(album.type.toString(), album.albumId.toString())
        ApiManager.request(observable, object : RequestCallBack<Album> {
            override fun success(result: Album) {
                result.name?.let { mView?.showTitle(it) }
                result.cover?.let { mView?.showCover(it) }
                result.info?.let { mView?.showDescInfo(it) }
                mView?.showPlaylistSongs(result.songs)
            }

            override fun error(msg: String) {
                LogUtil.e(TAG, msg)
                mView?.showError(msg, true)
                ToastUtils.show(msg)
            }
        })
    }

    /**
     * 加载歌单歌曲列表
     */
    override fun loadPlaylistSongs(playlist: Playlist) {
        when (playlist.type) {
            Constants.PLAYLIST_LOCAL_ID,
            Constants.PLAYLIST_HISTORY_ID,
            Constants.PLAYLIST_LOVE_ID,
            Constants.PLAYLIST_QUEUE_ID -> doAsync {
                val data = playlist.pid?.let { PlaylistLoader.getMusicForPlaylist(it, playlist.order) }
                uiThread {
                    if (data != null && data.isNotEmpty()) {
                        mView?.showPlaylistSongs(data)
                    } else {
                        mView?.showEmptyState()
                    }
                }
            }
            Constants.PLAYLIST_BD_ID -> {
                ApiManager.request(BaiduApiServiceImpl.getRadioChannelInfo(playlist), object : RequestCallBack<Playlist> {
                    override fun error(msg: String?) {
                        mView?.showError(msg, true)
                    }

                    override fun success(result: Playlist?) {
                        result?.let {
                            if (it.musicList.isNotEmpty()) {
                                mView?.showPlaylistSongs(it.musicList)
                            } else {
                                mView?.showEmptyState()
                            }
                        }
                    }

                })

            }
            Constants.PLAYLIST_WY_ID -> {
                ApiManager.request(playlist.pid?.let { NeteaseApiServiceImpl.getPlaylistDetail(it) }, object : RequestCallBack<Playlist> {
                    override fun error(msg: String?) {
                        mView?.showError(msg, true)
                    }

                    override fun success(result: Playlist?) {
                        result?.let {
                            if (it.musicList.isNotEmpty()) {
                                mView?.showPlaylistSongs(it.musicList)
                            } else {
                                mView?.showEmptyState()
                            }
                        }
                    }

                })

            }
            Constants.PLAYLIST_WY_RECOMMEND_ID -> {
                loadRecommendSongs()
            }
            else -> ApiManager.request(playlist.pid?.let { PlaylistApiServiceImpl.getMusicList(it) }, object : RequestCallBack<MutableList<BaseMusicInfo>> {
                override fun success(result: MutableList<BaseMusicInfo>) {
                    mView?.showPlaylistSongs(result)
                }

                override fun error(msg: String) {
                    LogUtil.e(TAG, msg)
                    mView?.showError(msg, true)
                    ToastUtils.show(msg)
                }
            })
        }
    }

    /**
     * 加载每日推荐歌曲（需登录）
     */
    private fun loadRecommendSongs() {
        val observable = NeteaseApiServiceImpl.recommendSongs()
        ApiManager.request(observable, object : RequestCallBack<MutableList<BaseMusicInfo>> {
            override fun success(result: MutableList<BaseMusicInfo>) {
                mView?.showPlaylistSongs(result)
            }

            override fun error(msg: String) {
                mView?.showErrorTips(msg, hasTry = true)
            }
        })
    }

    /**
     * 删除歌单
     */
    override fun deletePlaylist(playlist: Playlist) {

    }

    /**
     * 重命名歌单
     */
    override fun renamePlaylist(playlist: Playlist, title: String) {
        if (playlist.type == Constants.PLAYLIST_CUSTOM_ID) {
            ApiManager.request(playlist.pid?.let { PlaylistApiServiceImpl.renamePlaylist(it, title) }, object : RequestCallBack<String> {
                override fun success(result: String) {
                    mView.success(1)
                    playlist.name = title
                    EventBus.getDefault().post(MyPlaylistEvent(Constants.PLAYLIST_RENAME, playlist))
                    ToastUtils.show(result)
                }

                override fun error(msg: String) {
                    ToastUtils.show(msg)
                }
            })
        } else {
            doAsync {
                val success = PlaylistLoader.renamePlaylist(playlist, title)
                uiThread {
                    if (success) {
                        mView.success(1)
                        playlist.name = title
                        EventBus.getDefault().post(MyPlaylistEvent(Constants.PLAYLIST_RENAME, playlist))
                        ToastUtils.show("更新成功")
                    }
                }
            }
        }
    }


    companion object {
        private val TAG = "PlaylistDetailPresenter"
    }
}
