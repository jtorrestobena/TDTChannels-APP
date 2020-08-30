package laquay.com.open.canalestdt;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

public class VideoDialogFragment extends DialogFragment implements Player.EventListener {
    public static final String TAG = VideoDialogFragment.class.getSimpleName();
    private static final String CHANNEL_KEY = "CHANNEL_URL";
    private static final String CHANNEL_NAME = "CHANNEL_NAME";
    private SimpleExoPlayer player;
    private PlayerView channelVideoView;
    private DefaultDataSourceFactory dataSourceFactory;
    private CastPlayer castPlayer;
    private TextView externalDeviceTextView;

    public static VideoDialogFragment newInstance(String streamURL, String channelName) {
        VideoDialogFragment videoDialogFragment = new VideoDialogFragment();

        Bundle args = new Bundle();
        args.putString(CHANNEL_KEY, streamURL);
        args.putString(CHANNEL_NAME, channelName);
        videoDialogFragment.setArguments(args);

        return videoDialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_video_dialog, container, false);

        channelVideoView = rootView.findViewById(R.id.channel_video_detail_exoplayer);

        externalDeviceTextView = (TextView) rootView.findViewById(R.id.external_device_message_textview);
        final MediaRouteButton mediaRouteButton = (MediaRouteButton) rootView.findViewById(R.id.media_route_button);
        CastButtonFactory.setUpMediaRouteButton(getContext().getApplicationContext(), mediaRouteButton);

        channelVideoView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
            @Override
            public void onVisibilityChange(int visibility) {
                mediaRouteButton.setVisibility(visibility);
            }
        });

        if (getArguments() != null) {
            String channelUrl = getArguments().getString(CHANNEL_KEY);
            if (getActivity() != null && getContext() != null && channelUrl != null) {
                player = new SimpleExoPlayer.Builder(getContext()).build();

                DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                        Util.getUserAgent(getContext(), "laquay.com.open.canalestdt"),
                        DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                        DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                        true
                );

                dataSourceFactory = new DefaultDataSourceFactory(getContext(), httpDataSourceFactory);

                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                loadVideo(channelUrl);

                final String channelName = getArguments().getString(CHANNEL_NAME);
                setupCasting(channelUrl, channelName);
            }
        }
        return rootView;
    }

    private void setupCasting(final String streamURL, final String channelName) {
        castPlayer = new CastPlayer(CastContext.getSharedInstance(requireActivity()));
        castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
            @Override
            public void onCastSessionAvailable() {
                player.stop(true);
                castPlayer.loadItem(buildMediaQueueItem(streamURL, channelName), 0);
                externalDeviceTextView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onCastSessionUnavailable() {
                externalDeviceTextView.setVisibility(View.GONE);
                loadVideo(streamURL);
            }
        });
    }

    public void loadVideo(String streamURL) {
        channelVideoView.setPlayer(player);

        // Add listener for onPlayerError
        player.addListener(this);

        // This is the MediaSource representing the media to be played.
        MediaSource videoSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(streamURL));

        // Prepare the player with the source.
        player.prepare(videoSource);
        player.setPlayWhenReady(true);
    }

    private MediaQueueItem buildMediaQueueItem(String url, String channelName) {
        final MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, channelName);
        final MediaInfo mediaInfo = new MediaInfo.Builder(Uri.parse(url).toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(MimeTypes.APPLICATION_M3U8)
                .setMetadata(movieMetadata).build();
        return new MediaQueueItem.Builder(mediaInfo).build();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getActivity() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // Activity onStop, player must be release because of memory saving
    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            if (player != null) {
                player.release();
            }

            castPlayer.setSessionAvailabilityListener(null);

            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (getContext() != null) {
            switch (error.type) {
                case ExoPlaybackException.TYPE_SOURCE:
                    Toast.makeText(getContext(), getContext().getString(R.string.channel_detail_source_error_message), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "TYPE_SOURCE: " + error.getSourceException().getMessage());
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    Toast.makeText(getContext(), getContext().getString(R.string.channel_detail_renderer_error_message), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "TYPE_RENDERER: " + error.getRendererException().getMessage());
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    Toast.makeText(getContext(), getContext().getString(R.string.channel_detail_unexpected_error_message), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "TYPE_UNEXPECTED: " + error.getUnexpectedException().getMessage());
                    break;
                default:
                    Toast.makeText(getContext(), getContext().getString(R.string.channel_detail_unexpected_error_message), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "TYPE_UNKNOWN: " + error.getCause().getMessage());
            }
        }

        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }
}
