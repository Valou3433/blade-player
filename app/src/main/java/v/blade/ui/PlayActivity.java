/*
 *    Blade - Android music player
 *    Copyright (C) 2018 Valentin HAUDIQUET
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package v.blade.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

import java.util.ArrayList;

import v.blade.R;
import v.blade.library.LibraryService;
import v.blade.library.Song;
import v.blade.player.PlayerService;
import v.blade.ui.adapters.LibraryObjectAdapter;
import v.blade.ui.settings.SettingsActivity;
import v.blade.ui.settings.ThemesActivity;

public class PlayActivity extends AppCompatActivity {
    private static final float DELTA_X_MIN = 350;

    private PlayerService musicPlayer;
    boolean isDisplayingAlbumArt = true;
    /* activity components */
    private ImageView albumView;
    private Bitmap lastAlbumBitmap = null;
    private Bitmap nullBitmap = null;
    private TextView songTitle;
    private TextView songArtistAlbum;
    private TextView playlistPosition;
    private TextView songDuration;
    private TextView songCurrentPosition;
    private ImageView playAction;
    private ImageView playlistAction;
    private ImageView shuffleAction;
    private ImageView prevAction;
    private ImageView nextAction;
    private ImageView repeatAction;
    private SeekBar seekBar;
    private DragSortListView playlistView;
    private LibraryObjectAdapter playlistAdapter;
    private ListView.OnItemClickListener playlistViewListener = new ListView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            musicPlayer.setCurrentPosition(position);
        }
    };
    private DragSortListView.DropListener playlistDropListener = new DragSortListView.DropListener() {
        @Override
        public void drop(int from, int to) {
            ArrayList<Song> playList = musicPlayer.getCurrentPlaylist();

            Song toSwap = playList.get(from);
            playList.remove(from);
            playList.add(to, toSwap);

            int selectedPos = musicPlayer.getCurrentPosition();
            if (selectedPos == from) {
                musicPlayer.updatePosition(to);
                playlistView.setItemChecked(to, true);
                playlistAdapter.setSelectedPosition(to);
            } else {
                int modifier = 0;
                if (to >= selectedPos && from < selectedPos) modifier = -1;
                else if (to <= selectedPos && from > selectedPos) modifier = +1;

                musicPlayer.updatePosition(musicPlayer.getCurrentPosition() + modifier);
                playlistView.setItemChecked(musicPlayer.getCurrentPosition(), true);
                playlistAdapter.setSelectedPosition(musicPlayer.getCurrentPosition());
            }

            LibraryService.currentCallback.onLibraryChange();
        }
    };

    private ImageView.OnTouchListener albumDragListener = new ImageView.OnTouchListener() {
        float touchStartX = 0;
        float touchStartY = 0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    //image was touched, start touch action
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                }
                case MotionEvent.ACTION_UP: {
                    //image was released, look at diff and change song if enough
                    float touchDeltaX = event.getX() - touchStartX;
                    if (touchDeltaX >= DELTA_X_MIN) {
                        //swipe back
                        if (PlayerConnection.getService().getCurrentPosition() > 5000)
                            onPrevClicked(v);
                        onPrevClicked(v);
                    } else if (touchDeltaX <= -DELTA_X_MIN) {
                        //swipe next
                        onNextClicked(v);
                    }
                }
            }
            return true;
        }
    };

    /* more button actions/menu */
    private ImageView.OnClickListener moreListener = v -> {
        PopupMenu popupMenu = new PopupMenu(PlayActivity.this, v);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_add_to_list:
                    MainActivity.showAddToPlaylist(PlayActivity.this, PlayerConnection.getService().getCurrentSong());
                    break;

                case R.id.action_manage_libraries:
                    MainActivity.showManageLibraries(PlayActivity.this, PlayerConnection.getService().getCurrentSong());
            }
            return false;
        });
        getMenuInflater().inflate(R.menu.play_more, popupMenu.getMenu());
        popupMenu.show();
    };

    /* music player callbacks (UI refresh) */
    private MediaControllerCompat.Callback musicCallbacks = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            refreshState(state);
        }

        /*
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata)
        {
            super.onMetadataChanged(metadata);
        }

        @Override
        public void onRepeatModeChanged(int repeatMode)
        {
            super.onRepeatModeChanged(repeatMode);
        }

        @Override
        public void onShuffleModeChanged(boolean enabled)
        {
            super.onShuffleModeChanged(enabled);
        }
        */
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set theme
        setTheme(ThemesActivity.currentAppTheme);

        setContentView(R.layout.activity_play);
        android.support.v7.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //get all components
        albumView = findViewById(R.id.album_display);
        songTitle = findViewById(R.id.textview_title);
        songArtistAlbum = findViewById(R.id.textview_subtitle);
        playlistPosition = findViewById(R.id.textview_playlist_pos);
        songDuration = findViewById(R.id.song_duration);
        songCurrentPosition = findViewById(R.id.song_position);
        playAction = findViewById(R.id.play_button);
        playlistAction = findViewById(R.id.playlist_edit);
        ImageView moreAction = findViewById(R.id.more);
        shuffleAction = findViewById(R.id.shuffle_button);
        prevAction = findViewById(R.id.prev_button);
        nextAction = findViewById(R.id.next_button);
        repeatAction = findViewById(R.id.repeat_button);
        seekBar = findViewById(R.id.seek_bar);
        playlistView = findViewById(R.id.playlist_view);
        playlistView.setOnItemClickListener(playlistViewListener);
        DragSortController playlistDragController = new DragSortController(playlistView);
        playlistView.setFloatViewManager(playlistDragController);
        playlistView.setOnTouchListener(playlistDragController);
        playlistDragController.setDragHandleId(R.id.element_more);
        playlistView.setDropListener(playlistDropListener);
        albumView.setOnTouchListener(albumDragListener);

        nullBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_albums);

        LibraryService.configureLibrary(getApplicationContext());
        if (!PlayerConnection.init(new PlayerConnection.Callback() {
            @Override
            public void onConnected() {
                PlayerConnection.musicController.registerCallback(musicCallbacks);
                musicPlayer = PlayerConnection.getService();
                refreshState(musicPlayer.getPlayerState());
            }

            @Override
            public void onDisconnected() {
                finish();
            }
        }, getApplicationContext())) finish();

        //setup handler that will keep seekBar and playTime in sync
        final Handler handler = new Handler();
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int pos = 0;
                if (musicPlayer != null) pos = musicPlayer.resolveCurrentSongPosition();
                int posMns = (pos / 60000) % 60000;
                int posScs = pos % 60000 / 1000;
                String songPos = String.format("%02d:%02d", posMns, posScs);
                songCurrentPosition.setText(songPos);

                seekBar.setProgress(pos);

                handler.postDelayed(this, 200);
            }
        });
        //setup listener that will update time on seekbar clicked
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) musicPlayer.seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        moreAction.setOnClickListener(moreListener);

        //set theme
        findViewById(R.id.play_layout).setBackgroundColor(ContextCompat.getColor(this, ThemesActivity.currentColorPrimary));
        playlistView.setBackgroundColor(ContextCompat.getColor(this, ThemesActivity.currentColorBackground));
        playlistPosition.setTextColor(ContextCompat.getColor(this, ThemesActivity.currentColorAccent));
        songTitle.setTextColor(ContextCompat.getColor(this, ThemesActivity.currentColorAccent));
        songArtistAlbum.setTextColor(ContextCompat.getColor(this, ThemesActivity.currentColorAccent));
        songCurrentPosition.setTextColor(ContextCompat.getColor(this, ThemesActivity.currentColorAccent));
        songDuration.setTextColor(ContextCompat.getColor(this, ThemesActivity.currentColorAccent));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void refreshState(PlaybackStateCompat state) {
        Song currentSong = musicPlayer.getCurrentSong();

        //set album view / playlistView
        if (currentSong == null) return;

        Bitmap currentAlbumBitmap = musicPlayer.getCurrentArt() == null ? nullBitmap : musicPlayer.getCurrentArt();
        if (lastAlbumBitmap == null | (!LibraryService.ENABLE_SONG_CHANGE_ANIM)) {
            albumView.setImageBitmap(currentAlbumBitmap);
            lastAlbumBitmap = currentAlbumBitmap;
        } else if (!lastAlbumBitmap.sameAs(currentAlbumBitmap)) {
            final Animation anim_out = AnimationUtils.loadAnimation(PlayActivity.this, android.R.anim.fade_out);
            final Animation anim_in = AnimationUtils.loadAnimation(PlayActivity.this, android.R.anim.fade_in);
            anim_out.setDuration(150);
            anim_in.setDuration(300);
            anim_out.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    albumView.setImageBitmap(currentAlbumBitmap);
                    lastAlbumBitmap = currentAlbumBitmap;
                    anim_in.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                        }
                    });
                    albumView.startAnimation(anim_in);
                }
            });
            albumView.startAnimation(anim_out);
        }

        if (playlistAdapter == null) {
            playlistAdapter = new LibraryObjectAdapter(this, musicPlayer.getCurrentPlaylist());
            playlistAdapter.setMoreImage(R.drawable.ic_action_move_black);
            playlistAdapter.repaintSongBackground();
            playlistView.setAdapter(playlistAdapter);
            playlistAdapter.setSelectedPosition(musicPlayer.getCurrentPosition());
        } else {
            playlistAdapter.resetList(musicPlayer.getCurrentPlaylist());
            playlistAdapter.setSelectedPosition(musicPlayer.getCurrentPosition());
            playlistAdapter.notifyDataSetChanged();
        }
        //playlistView.setSelection(PlayerConnection.musicPlayer.getCurrentPosition());
        playlistView.setItemChecked(musicPlayer.getCurrentPosition(), true);

        //set song info
        songTitle.setText(currentSong.getTitle());
        songArtistAlbum.setText(currentSong.getArtist().getName() + " - " + currentSong.getAlbum().getName());
        playlistPosition.setText((musicPlayer.getCurrentPosition() + 1) + "/" + musicPlayer.getCurrentPlaylist().size());

        int dur = musicPlayer.resolveCurrentSongDuration();
        int durMns = (dur / 60000) % 60000;
        int durScs = dur % 60000 / 1000;
        String songDur = String.format("%02d:%02d", durMns, durScs);
        songDuration.setText(songDur);
        seekBar.setMax(dur);

        //set play button icon
        if (musicPlayer.isPlaying()) playAction.setImageResource(R.drawable.ic_action_pause);
        else playAction.setImageResource(R.drawable.ic_play_action);

        //set shuffle button icon
        if (musicPlayer.isShuffleEnabled())
            shuffleAction.setImageResource(R.drawable.ic_action_shuffle_enabled);
        else shuffleAction.setImageResource(R.drawable.ic_action_shuffle_white);

        //set repeat button icon
        int repeatMode = musicPlayer.getRepeatMode();
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_NONE)
            repeatAction.setImageResource(R.drawable.ic_action_repeat_white);
        else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE)
            repeatAction.setImageResource(R.drawable.ic_action_repeat_one);
        else repeatAction.setImageResource(R.drawable.ic_action_repeat_enabled);
    }

    /* button actions */
    public void onPlayClicked(View v) {
        if (musicPlayer != null && PlayerConnection.musicController != null) {
            if (musicPlayer.isPlaying())
                PlayerConnection.musicController.getTransportControls().pause();
            else PlayerConnection.musicController.getTransportControls().play();
        }
    }

    public void onPrevClicked(View v) {
        PlayerConnection.musicController.getTransportControls().skipToPrevious();
    }

    public void onNextClicked(View v) {
        PlayerConnection.musicController.getTransportControls().skipToNext();
    }

    public void onRepeatClicked(View v) {
        int currentRepeatMode = musicPlayer.getRepeatMode();

        if (currentRepeatMode == PlaybackStateCompat.REPEAT_MODE_NONE)
            currentRepeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
        else if (currentRepeatMode == PlaybackStateCompat.REPEAT_MODE_ONE)
            currentRepeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
        else if (currentRepeatMode == PlaybackStateCompat.REPEAT_MODE_ALL)
            currentRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;

        PlayerConnection.musicController.getTransportControls().setRepeatMode(currentRepeatMode);

        /* manually refresh UI */
        if (currentRepeatMode == PlaybackStateCompat.REPEAT_MODE_NONE)
            repeatAction.setImageResource(R.drawable.ic_action_repeat_white);
        else if (currentRepeatMode == PlaybackStateCompat.REPEAT_MODE_ONE)
            repeatAction.setImageResource(R.drawable.ic_action_repeat_one);
        else repeatAction.setImageResource(R.drawable.ic_action_repeat_enabled);
    }

    public void onShuffleClicked(View v) {
        boolean shuffle = !musicPlayer.isShuffleEnabled();
        PlayerConnection.musicController.getTransportControls().setShuffleMode(0);

        /* manually refresh UI */
        if (shuffle) shuffleAction.setImageResource(R.drawable.ic_action_shuffle_enabled);
        else shuffleAction.setImageResource(R.drawable.ic_action_shuffle_white);
    }

    public void onPlaylistClicked(View v) {
        isDisplayingAlbumArt = !isDisplayingAlbumArt;

        if (isDisplayingAlbumArt) {
            playlistView.setVisibility(View.GONE);
            albumView.setVisibility(View.VISIBLE);
        } else {
            albumView.setVisibility(View.GONE);
            playlistView.setSelection(musicPlayer.getCurrentPosition());
            playlistView.setVisibility(View.VISIBLE);
        }
    }
}
