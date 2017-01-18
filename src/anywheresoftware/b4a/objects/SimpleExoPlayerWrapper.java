package anywheresoftware.b4a.objects;

import java.io.IOException;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelector.EventListener;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Handler;
import anywheresoftware.b4a.AbsObjectWrapper;
import anywheresoftware.b4a.BA;
import anywheresoftware.b4a.BA.DependsOn;
import anywheresoftware.b4a.BA.Events;
import anywheresoftware.b4a.BA.Hide;
import anywheresoftware.b4a.BA.Permissions;
import anywheresoftware.b4a.BA.Version;
import anywheresoftware.b4a.BA.ShortName;
import anywheresoftware.b4a.keywords.B4AApplication;
import anywheresoftware.b4a.objects.collections.List;
import anywheresoftware.b4a.objects.streams.File;

/**
 * An advanced audio and video player. It supports more formats than MediaPlayer.
 *Can be used together with SimpleExoPlayerView.
 *<b>Should be a process global variable.</b>
 */
@ShortName("SimpleExoPlayer")
@Version(1.01f)
@DependsOn(values={"exoplayer.aar"})
@Permissions(values = {"android.permission.INTERNET"})
@Events(values = {"Complete", "Error (Message As String)", "Ready"})
public class SimpleExoPlayerWrapper  {
	@Hide
	public SimpleExoPlayer player;
	@Hide
	TrackSelector<MappedTrackInfo> trackSelector;
	private int currentState;
	private String eventName;
	/**
	 * Initializes the player.
	 */
	public void Initialize(final BA ba, String EventName) {
		eventName = EventName.toLowerCase(BA.cul);
		Handler mainHandler = new Handler();
		BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
		TrackSelection.Factory videoTrackSelectionFactory =
		    new AdaptiveVideoTrackSelection.Factory(bandwidthMeter);
		trackSelector =
		    new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
		trackSelector.addListener(new EventListener<MappedTrackInfo>() {

			@Override
			public void onTrackSelectionsChanged(TrackSelections<? extends MappedTrackInfo> trackSelections) {
				System.out.println("onTrackSelectionChanged: " + trackSelections);
				
			}
			
		});
		player = ExoPlayerFactory.newSimpleInstance(BA.applicationContext, trackSelector, new DefaultLoadControl());
		player.addListener(new ExoPlayer.EventListener() {

			@Override
			public void onLoadingChanged(boolean isLoading) {
			}

			@Override
			public void onPlayerError(ExoPlaybackException error) {
				ba.raiseEvent(SimpleExoPlayerWrapper.this, eventName + "_error", String.valueOf(error.getCause()));
			}

			@Override
			public void onPlayerStateChanged(boolean playWhenReady,
					int playbackState) {
				if (playbackState != currentState) {
					currentState = playbackState;
					if (currentState == ExoPlayer.STATE_ENDED)
						ba.raiseEvent(SimpleExoPlayerWrapper.this, eventName + "_complete");
					else if (currentState == ExoPlayer.STATE_READY)
						ba.raiseEvent(SimpleExoPlayerWrapper.this, eventName + "_ready");
				}
			}

			@Override
			public void onPositionDiscontinuity() {
				
			}

			@Override
			public void onTimelineChanged(Timeline timeline, Object manifest) {
			}
			
		});
	}
	/**
	 * Concatenates multiple sources.
	 */
	public Object CreateListSource (List Sources) {
		MediaSource[] sources = new MediaSource[Sources.getSize()];
		for (int i = 0;i < Sources.getSize();i++)
			sources[i] = (MediaSource)Sources.Get(i);
		return new ConcatenatingMediaSource(sources);
	}
	/**
	 * Creates a local file source.
	 */
	public Object CreateFileSource (String Dir, String FileName) throws IOException {
		String path;
		if (Dir.equals(File.getDirAssets())) {
			if (File.virtualAssetsFolder != null) {
				path = "file://" + File.Combine(File.virtualAssetsFolder, File.getUnpackedVirtualAssetFile(FileName));
			} else {
				path = "asset:///" + FileName.toLowerCase(BA.cul);
			}
		}
		else {
			path = "file://" + File.Combine(Dir, FileName);
		}
		return CreateUriSource(path);
	}
	/**
	 * Creates a Uri source for non-streaming media resources.
	 */
	public Object CreateUriSource (String Uri) {
		ExtractorMediaSource e = new ExtractorMediaSource(android.net.Uri.parse(Uri), 
				createDefaultDataFactory(), 
				new DefaultExtractorsFactory(), null, null);
		return e;
	}
	/**
	 * Creates a loop source. The child source will be played multiple times.
	 *Pass -1 to play it indefinitely.
	 */
	public Object CreateLoopSource (Object Source, int Count) {
		return new LoopingMediaSource((MediaSource) Source, Count > 0 ? Count : Integer.MAX_VALUE);
	}
	/**
	 * Creates a HLS (Http live streaming) source.
	 */
	public Object CreateHLSSource (String Uri)  {
		return new HlsMediaSource(android.net.Uri.parse(Uri), createDefaultDataFactory(), null, null);
	}
	/**
	 * Creates a Dash (Dynamic Adaptive Streaming over Http) source.
	 */
	public Object CreateDashSource (String Uri) {
		return new DashMediaSource(android.net.Uri.parse(Uri), createDefaultDataFactory(), new DefaultDashChunkSource.Factory(createDefaultDataFactory()), null, null);
	}
	/**
	 * Creates a Smooth Streaming source.
	 */
	public Object CreateSmoothStreamingSource (String Uri) {
		return new SsMediaSource(android.net.Uri.parse(Uri), createDefaultDataFactory(), new DefaultSsChunkSource.Factory(createDefaultDataFactory()), null, null);
	}
	@Hide
	public DefaultDataSourceFactory createDefaultDataFactory() {
		try {
			return new DefaultDataSourceFactory(BA.applicationContext, Util.getUserAgent(BA.applicationContext, B4AApplication.getLabelName()));
		} catch (NameNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	/**
	 * Prepares the media source. The Ready event will be raised when the playback is ready. You can call play immediately after calling this method.
	 */
	public void Prepare (Object Source) {
		player.prepare((MediaSource)Source);
	}
	/**
	 * Starts or resumes playback. If the source is currently loading then it will starting playing when ready.
	 */
	public void Play() {
		player.setPlayWhenReady(true);
	}
	/**
	 * Pauses the playback.
	 */
	public void Pause() {
		player.setPlayWhenReady(false);
	}
	/**
	 * Releases the player resources. The player needs to be initialized again before it can be used.
	 */
	public void Release() {
		player.release();
	}
	/**
	 * Gets or sets the current position (in milliseconds). Note that the Ready event will be raised after this call.
	 */
	public long getPosition() {
		return player.getCurrentPosition();
	}
	public void setPosition(int value) {
		player.seekTo(value);
	}
	/**
	 * Returns the resource duration (in milliseconds).
	 */
	public long getDuration() {
		return player.getDuration();
	}
	/**
	 * Gets or sets the volume (0 - 1).
	 */
	public float getVolume() {
		return player.getVolume();
	}
	public void setVolume(float f) {
		player.setVolume(f);
	}
}
