package com.uiza.sdk.floatview;

import android.net.Uri;
import android.os.Handler;

import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.uiza.sdk.util.Constants;

import timber.log.Timber;

public final class UZFloatPlayerManager extends UZFloatPlayerManagerAbs implements AdsMediaSource.MediaSourceFactory {
    private ImaAdsLoader adsLoader = null;
    private FUZVideoAdPlayerListener fUZVideoAdPlayerListener = new FUZVideoAdPlayerListener();

    public UZFloatPlayerManager(final UZFloatVideoView fuzVideo, String linkPlay, String urlIMAAd) {
        this.timestampPlayed = System.currentTimeMillis();
        this.isCanAddViewWatchTime = true;
        this.context = fuzVideo.getContext();
        this.fuzVideo = fuzVideo;
        this.linkPlay = linkPlay;
        this.videoWidth = 0;
        this.videoHeight = 0;
        if (urlIMAAd != null && !urlIMAAd.isEmpty()) {
            adsLoader = new ImaAdsLoader(context, Uri.parse(urlIMAAd));
        }
        manifestDataSourceFactory = new DefaultDataSourceFactory(context, Constants.USER_AGENT);
        mediaDataSourceFactory =
                new DefaultDataSourceFactory(context, Constants.USER_AGENT, new DefaultBandwidthMeter.Builder(context).build());
        handler = new Handler();
        runnable = () -> {
            if (fuzVideo.getPlayerView() != null) {
                boolean isPlayingAd = fUZVideoAdPlayerListener.isPlayingAd();
                if (isPlayingAd) {
                    fuzVideo.hideProgress();
                    if (progressListener != null) {
                        VideoProgressUpdate videoProgressUpdate = adsLoader.getAdProgress();
                        int duration = (int) videoProgressUpdate.getDuration();
                        int s = (int) videoProgressUpdate.getCurrentTime();
                        int percent = 0;
                        if (duration != 0) {
                            percent = s * 100 / duration;
                        }
                        progressListener.onAdProgress(s, duration, percent);
                    }
                } else {
                    if (progressListener != null) {
                        if (player != null) {
                            long mls = player.getCurrentPosition();
                            long duration = player.getDuration();
                            int percent = 0;
                            if (duration != 0) {
                                percent = (int) (mls * 100 / duration);
                            }
                            int s = Math.round((float) mls / 1000);
                            progressListener.onVideoProgress(mls, s, duration, percent);
                        }
                    }
                }
                if (handler != null && runnable != null) {
                    handler.postDelayed(runnable, 1000);
                }
            }
        };
        handler.postDelayed(runnable, 0);
        fuzVideo.getPlayerView().setControllerShowTimeoutMs(0);
    }

    public DefaultTrackSelector getTrackSelector() {
        return trackSelector;
    }

    public void init(boolean isLivestream, long contentPosition) {
        Timber.d("miniplayer STEP 1 FUZPLayerManager init isLivestream %b , contentPosition: %d",
                isLivestream,
                contentPosition);
        reset();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        player = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        fuzVideo.getPlayerView().setPlayer(player);
        MediaSource mediaSourceVideo = createMediaSourceVideo();
        //merge title to media source video
        //IMA ADS
        // Compose the content media source into a new AdsMediaSource with both ads and content.
        MediaSource mediaSourceWithAds = createMediaSourceWithAds(mediaSourceVideo);
        //Prepare the player with the source.
        player.addListener(new FUZPlayerEventListener());
        player.addVideoListener(new FUZVideoListener());
        if (adsLoader != null) {
            adsLoader.setPlayer(player);
            adsLoader.addCallback(fUZVideoAdPlayerListener);
        }
        player.prepare(mediaSourceWithAds);
        //setVolumeOff();
        if (isLivestream)
            player.seekToDefaultPosition();
        else
            seekTo(contentPosition);
        player.setPlayWhenReady(true);
    }

    private MediaSource createMediaSourceWithAds(MediaSource mediaSource) {
        if (adsLoader == null)
            return mediaSource;
        return new AdsMediaSource(mediaSource, this, adsLoader,
                fuzVideo.getPlayerView());
    }

    @Override
    public void release() {
        super.release();
        if (adsLoader != null) {
            adsLoader.setPlayer(null);
            adsLoader.release();
        }
    }

    @Override
    public MediaSource createMediaSource(Uri uri) {
        return buildMediaSource(uri);
    }

    @Override
    public int[] getSupportedTypes() {
        // IMA does not support Smooth Streaming ads.
        return new int[]{C.TYPE_DASH, C.TYPE_HLS, C.TYPE_OTHER};
    }

    private static class FUZVideoAdPlayerListener implements VideoAdPlayer.VideoAdPlayerCallback {
        private boolean isPlayingAd;
        private boolean isEnded;

        @Override
        public void onPlay() {
            isPlayingAd = true;
        }

        @Override
        public void onVolumeChanged(int i) {
        }

        @Override
        public void onPause() {
            isPlayingAd = false;
        }

        @Override
        public void onLoaded() {
        }

        @Override
        public void onResume() {
            isPlayingAd = true;
        }

        @Override
        public void onEnded() {
            isPlayingAd = false;
            isEnded = true;
        }

        @Override
        public void onError() {
            isPlayingAd = false;
        }

        @Override
        public void onBuffering() {
        }

        public boolean isPlayingAd() {
            return isPlayingAd;
        }

        public boolean isEnded() {
            return isEnded;
        }
    }
}
