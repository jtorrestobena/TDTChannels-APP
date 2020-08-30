package laquay.com.open.canalestdt;

import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import laquay.com.open.canalestdt.component.ChannelList;
import laquay.com.open.canalestdt.controller.VolleyController;
import laquay.com.open.canalestdt.model.Channel;
import laquay.com.open.canalestdt.model.ChannelOptions;
import laquay.com.open.canalestdt.utils.SourcesManagement;
import laquay.com.open.canalestdt.utils.VideoUtils;

public class DetailChannelActivity extends AppCompatActivity {
    public static final String TAG = DetailChannelActivity.class.getSimpleName();
    public static final String EXTRA_MESSAGE = "laquay.com.open.canalestdt.CHANNEL_DETAIL";
    public static final String EXTRA_TYPE = "laquay.com.open.canalestdt.CHANNEL_TYPE";
    public static final String TYPE_TV = "TV";
    public static final String TYPE_RADIO = "RADIO";
    private Channel channel;
    private String typeOfStream;

    private ImageView channelImageIV;
    private TextView channelNameTV;
    private TextView channelURLTV;
    private ListView channelSourceLV;
    private MediaPlayer mediaPlayer; //TODO This should be moved to another Dialog/Fragment
    private CastPlayer castPlayer;
    private MenuItem castItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
        }

        Bundle intentExtras = getIntent().getExtras();
        if (intentExtras != null) {
            ChannelList channelList = (ChannelList) intentExtras.getSerializable(EXTRA_MESSAGE);
            if (channelList != null) {
                channel = channelList.getChannel();
                getSupportActionBar().setTitle(channelList.getChannel().getName());
            }
            typeOfStream = intentExtras.getString(EXTRA_TYPE);
        }

        setUpElements();
        setUpListeners();

        loadChannel();
    }

    private void setUpElements() {
        channelImageIV = findViewById(R.id.channel_image_detail_tv);
        channelNameTV = findViewById(R.id.channel_name_detail_tv);
        channelURLTV = findViewById(R.id.channel_url_detail_tv);
        channelSourceLV = findViewById(R.id.channel_source_detail_lv);
    }

    private void setUpListeners() {
    }

    private void loadChannel() {
        channelNameTV.setText(channel.getName());
        channelURLTV.setText(channel.getWeb());

        // Load first option
        if (!channel.getOptions().isEmpty()) {
            // Fill available options
            ArrayList<String> sources = new ArrayList<>();
            for (ChannelOptions channelOption : channel.getOptions()) {
                sources.add(channelOption.getUrl());
            }

            ArrayAdapter<String> sourcesAdapter = new ArrayAdapter<>(this,
                    R.layout.item_list_detail_channel, android.R.id.text1, sources);
            channelSourceLV.setAdapter(sourcesAdapter);

            channelSourceLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String source = (String) channelSourceLV.getItemAtPosition(position);
                    switch (typeOfStream) {
                        case TYPE_TV:
                            if (isReproducibleWithExoplayer(source)) {
                                loadVideo(source);
                            } else if (isReproducibleWithYoutube(source)) {
                                VideoUtils.watchYoutubeVideo(getApplicationContext(), source);
                            } else {
                                VideoUtils.watchUnknownVideo(getApplicationContext(), source);
                            }
                            break;
                        case TYPE_RADIO:
                            loadRadio(source);
                            break;
                    }
                }
            });
        }

        ImageRequest request = new ImageRequest(channel.getLogo(),
                new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap bitmap) {
                        channelImageIV.setImageBitmap(bitmap);
                    }
                }, 0, 0, ImageView.ScaleType.CENTER_INSIDE, null,
                new Response.ErrorListener() {
                    public void onErrorResponse(VolleyError error) {
                        channelImageIV.setImageResource(R.mipmap.ic_launcher);
                    }
                });
        VolleyController.getInstance(this).addToQueue(request);
    }

    private boolean isReproducibleWithExoplayer(String url) {
        return url.contains("m3u8");
    }

    private boolean isReproducibleWithYoutube(String url) {
        return url.contains("youtube") || url.contains("youtu.be");
    }

    private void loadVideo(String streamURL) {
        Toast.makeText(this, getString(R.string.channel_detail_reproducing_tv), Toast.LENGTH_SHORT).show();
        DialogFragment newFragment = VideoDialogFragment.newInstance(streamURL, channel.getName());
        newFragment.show(getSupportFragmentManager(), "VideoDialog");
    }

    private void loadRadio(String streamURL) {
        Toast.makeText(this, getString(R.string.channel_detail_reproducing_radio), Toast.LENGTH_SHORT).show();
        mediaPlayer = MediaPlayer.create(this, Uri.parse(streamURL));
        mediaPlayer.start();
        setupCasting(streamURL);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }

        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            if (castPlayer.isLoading() || castPlayer.isPlaying()) {
                castPlayer.stop(true);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_detail_channel, menu);

        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        castItem = menu.findItem(R.id.media_route_menu_item);
        castItem.setVisible(false);

        boolean isItemFavorite;
        if (typeOfStream.equals(TYPE_TV)) {
            isItemFavorite = SourcesManagement.isTVChannelFavorite(channel.getName());
        } else {
            isItemFavorite = SourcesManagement.isRadioChannelFavorite(channel.getName());
        }

        if (isItemFavorite) {
            menu.getItem(0).setIcon(R.drawable.heart);
        } else {
            menu.getItem(0).setIcon(R.drawable.heart_outline);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_favorites) {
            boolean isItemFavorite;
            if (typeOfStream.equals(TYPE_TV)) {
                isItemFavorite = SourcesManagement.isTVChannelFavorite(channel.getName());
                SourcesManagement.setTVChannelFavorite(channel.getName(), !isItemFavorite);
            } else {
                isItemFavorite = SourcesManagement.isRadioChannelFavorite(channel.getName());
                SourcesManagement.setRadioChannelFavorite(channel.getName(), !isItemFavorite);
            }
            if (isItemFavorite) {
                item.setIcon(R.drawable.heart_outline);
            } else {
                item.setIcon(R.drawable.heart);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupCasting(final String streamURL) {
        castPlayer = new CastPlayer(CastContext.getSharedInstance(this));
        castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
            @Override
            public void onCastSessionAvailable() {
                castPlayer.loadItem(buildMediaQueueItem(streamURL), 0);
                mediaPlayer.stop();
            }

            @Override
            public void onCastSessionUnavailable() {
                mediaPlayer.start();
            }
        });
        castItem.setVisible(true);
    }

    private MediaQueueItem buildMediaQueueItem(String url) {
        final MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, channel.getName());
        final MediaInfo mediaInfo = new MediaInfo.Builder(Uri.parse(url).toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(MimeTypes.APPLICATION_ID3)
                .setMetadata(movieMetadata).build();
        return new MediaQueueItem.Builder(mediaInfo).build();
    }
}