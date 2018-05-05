package com.dawhey.mlij_blogapp.Fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.dawhey.mlij_blogapp.Activities.MainActivity;
import com.dawhey.mlij_blogapp.Api.ApiManager;
import com.dawhey.mlij_blogapp.Listeners.OnChangeFontSizeListener;
import com.dawhey.mlij_blogapp.Listeners.OnChapterDownloadedListener;
import com.dawhey.mlij_blogapp.Managers.PreferencesManager;
import com.dawhey.mlij_blogapp.Models.Bookmark;
import com.dawhey.mlij_blogapp.Models.Chapter;
import com.dawhey.mlij_blogapp.R;
import com.dawhey.mlij_blogapp.Utils.FirebaseLogger;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by dawhey on 18.03.17.
 */

public class ChapterFragment extends Fragment implements ViewTreeObserver.OnScrollChangedListener{

    public static final int SLIDER_FONT_OFFSET = 15;
    public static final int CONTENT_BEGINNING = 0;

    private static final String FIRST_VISIBLE_CHAR_KEY = "firstVisibleChar";
    private static final String TAG = "ChapterFragment";

    private int scrollPosition = 0;
    private int fontSize = 0;
    private int changedFontSize = 0;

    private boolean openedFromBookmark;
    private Chapter chapter;

    private OnChapterDownloadedListener listener;

    @BindView(R.id.font_slider)
    SeekBar fontSlider;

    @BindView(R.id.accept_font_size)
    ImageView changeFontButtonDone;

    @BindView(R.id.error_chapter_view)
    RelativeLayout errorView;

    @BindView(R.id.font_slider_view)
    RelativeLayout changeFontSizeView;

    @BindView(R.id.text_scroll_view)
    ScrollView scrollView;

    @BindView(R.id.error_refresh_button)
    FloatingActionButton refreshButton;

    @BindView(R.id.chapter_progressbar)
    ProgressBar progressBar;

    @BindView(R.id.chapter_content_view)
    TextView contentView;

    @BindView(R.id.display_font_size_text)
    TextView displayFontSizeView;

    @BindView(R.id.chapter_title_view)
    TextView titleView;

    @BindView(R.id.title_divider)
    View dividerLine;

    @BindView(R.id.favorite_button)
    FloatingActionButton favoriteButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chapter = PreferencesManager.getInstance(getContext()).getLastChapter();
        fontSize = PreferencesManager.getInstance(getContext()).getChapterFontSize();
        changedFontSize = fontSize;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chapter, container, false);
        ButterKnife.bind(this, root);
        setHasOptionsMenu(true);
        Objects.requireNonNull(((MainActivity) getActivity()).getSupportActionBar()).setTitle(chapter.getChapterHeaderFormatted());
        initViews();
        initViewListeners();
        return root;
    }

    private void initViews() {
        dividerLine.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fade_in));
        titleView.setText(chapter.getTitleFormatted());
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        displayFontSizeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
        fontSlider.setProgress(fontSize - SLIDER_FONT_OFFSET);

        //Setting up favorite button
        if (PreferencesManager.getInstance(getContext()).isInFavorites(chapter)) {
            favoriteButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
        } else {
            favoriteButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.textDefault)));
        }
    }

    @OnClick(R.id.accept_font_size)
    public void  onChangeFontButtonClick() {
        if (changedFontSize != fontSize) {
            contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, changedFontSize);
            PreferencesManager.getInstance(getContext()).setChapterFontSize(changedFontSize);
            Snackbar.make(favoriteButton, R.string.default_fontsize_changed, Snackbar.LENGTH_LONG).show();
            fontSize = changedFontSize;
        }
        hideFontsliderView();
    }

    @OnClick(R.id.error_refresh_button)
    public void onRefreshButtonClick() {
        downloadChapterContent();
    }

    @OnClick(R.id.favorite_button)
    public void onFavoriteButtonClick() {
        PreferencesManager manager = PreferencesManager.getInstance(getContext());
        favoriteButton.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.scale_up));
        if (manager.isInFavorites(chapter)) {
            manager.removeFromFavorites(chapter);
            favoriteButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.textDefault)));
            Snackbar.make(favoriteButton, R.string.deleted_from_favorites_message, Snackbar.LENGTH_SHORT).show();
        } else {
            manager.addToFavorites(chapter);
            favoriteButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
            Snackbar.make(favoriteButton, R.string.added_to_favorites_message, Snackbar.LENGTH_SHORT).show();
            FirebaseLogger.getInstance(getContext()).logAddingToFavoritesEvent(chapter);
        }
    }

    private void initViewListeners() {
        scrollView.getViewTreeObserver().addOnScrollChangedListener(this);
        fontSlider.setOnSeekBarChangeListener(new OnChangeFontSizeListener(this, displayFontSizeView));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (chapter.getContent() == null) {
            downloadChapterContent();
        } else {
            contentView.setText(Html.fromHtml(chapter.getContent()));
            showContentView();
        }
    }

    private void downloadChapterContent() {
        showLoadingView();
        Call<Chapter> chapterCall = ApiManager.createBloggerService().getChapter(chapter.getId());
        chapterCall.enqueue(new Callback<Chapter>() {
            @Override
            public void onResponse(Call<Chapter> call, Response<Chapter> response) {
                chapter = response.body();
                listener.onChapterDownloaded(chapter);
                PreferencesManager.getInstance(getContext()).setLastChapter(chapter);
                contentView.setText(Html.fromHtml(chapter.getContent()));
                showContentView();
            }

            @Override
            public void onFailure(Call<Chapter> call, Throwable t) {
                showErrorView();
                Log.e(TAG, t.getMessage());
            }
        });
    }

    private void showLoadingView() {
        contentView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.fade_in_faster));
    }

    private void showContentView() {
        progressBar.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        favoriteButton.show();
        animateContent();

        if (openedFromBookmark) {
            Bookmark bookmark = PreferencesManager.getInstance(getContext()).getBookmark();
            scrollToPixelOffset(bookmark.getPosition());
            Snackbar.make(favoriteButton, R.string.opened_from_bookmark, Snackbar.LENGTH_SHORT).show();
            openedFromBookmark = false;
        }
    }

    private void showFontsliderView() {
        changeFontSizeView.setVisibility(View.VISIBLE);
        changeFontSizeView.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.scale_up_faster));
    }

    public void hideFontsliderView() {
        changeFontSizeView.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.scale_down_faster));
        changeFontSizeView.setVisibility(View.GONE);
    }

    private void animateContent() {
        if (getContext() != null) {
            Animation a = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
            a.reset();
            contentView.clearAnimation();
            contentView.startAnimation(a);
            favoriteButton.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.scale_up));
        }
    }

    private void showErrorView() {
        progressBar.setVisibility(View.GONE);
        contentView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorView.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.scale_up_faster));
    }

    @Override
    public void onScrollChanged() {
        int newScrollPosition = scrollView.getScrollY();
        if (newScrollPosition > CONTENT_BEGINNING && newScrollPosition > scrollPosition) {
            favoriteButton.hide();
        } else {
            favoriteButton.show();
        }
        scrollPosition = newScrollPosition;
    }

    public void setOnChapterDownloadedListener(OnChapterDownloadedListener listener) {
        this.listener = listener;
    }

    public void setOpenedFromBookmark(boolean openedFromBookmark) {
        this.openedFromBookmark = openedFromBookmark;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            final int firstVisibleCharacterOffset = savedInstanceState.getInt(FIRST_VISIBLE_CHAR_KEY);
            scrollToPixelOffset(firstVisibleCharacterOffset);
        }
    }

    private void scrollToPixelOffset(final int firstVisibleCharacterOffset) {
        scrollView.post(new Runnable() {
            public void run() {
                final int firstVisibleLineOffset = contentView.getLayout().getLineForOffset(firstVisibleCharacterOffset);
                final int pixelOffset = contentView.getLayout().getLineTop(firstVisibleLineOffset);
                scrollView.scrollTo(CONTENT_BEGINNING, pixelOffset);
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (contentView.getLayout() != null) {
            int firstVisibleCharacterOffset = getFirstVisibleCharacterOffset();
            outState.putInt(FIRST_VISIBLE_CHAR_KEY, firstVisibleCharacterOffset);
        }
    }

    private int getFirstVisibleCharacterOffset() {
        final int firstVisibleLineOffset = contentView.getLayout().getLineForVertical(scrollView.getScrollY());
        return contentView.getLayout().getLineStart(firstVisibleLineOffset);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.chapter_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_last_read) {
            Bookmark bookmark = new Bookmark(getFirstVisibleCharacterOffset(), chapter);
            PreferencesManager.getInstance(getContext()).saveBookmark(bookmark);
            Snackbar.make(favoriteButton, R.string.saved_bookmark, Snackbar.LENGTH_SHORT).show();
            FirebaseLogger.getInstance(getContext()).logPlaceBookmark(chapter);
        } else if (item.getItemId() == R.id.action_change_fontsize && changeFontSizeView.getVisibility() != View.VISIBLE) {
            showFontsliderView();
        }
        return false;
    }

    public RelativeLayout getChangeFontSizeView() {
        return changeFontSizeView;
    }

    public void setChangedFontSize(int fontSize) {
        this.changedFontSize = fontSize;
    }
}