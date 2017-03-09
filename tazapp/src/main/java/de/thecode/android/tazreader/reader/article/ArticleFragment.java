package de.thecode.android.tazreader.reader.article;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.MailTo;
import android.net.ParseException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.Paper.Plist.Page.Article;
import de.thecode.android.tazreader.data.Store;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.reader.AbstractContentFragment;
import de.thecode.android.tazreader.reader.ReaderActivity;
import de.thecode.android.tazreader.reader.ReaderActivity.DIRECTIONS;
import de.thecode.android.tazreader.reader.ReaderTtsFragment;
import de.thecode.android.tazreader.reader.article.ArticleWebView.ArticleWebViewCallback;
import de.thecode.android.tazreader.reader.index.IIndexItem;
import de.thecode.android.tazreader.utils.StorageManager;
import de.thecode.android.tazreader.utils.TintHelper;
import de.thecode.android.tazreader.widget.PageIndexButton;
import de.thecode.android.tazreader.widget.ShareButton;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class ArticleFragment extends AbstractContentFragment implements ArticleWebViewCallback {

    private static final String JAVASCRIPT_API_NAME = "ANDROIDAPI";
    public static final String ARGUMENT_KEY = "key";
    private boolean debugArticles = false;
//    private boolean ttsActive = false;


    private enum GESTURES {
        undefined, swipeUp, swipeDown, swipeRight, swipeLeft
    }

    IIndexItem mArticle;
    String mStartPosition = "";

    ArticleWebView mWebView;
    ProgressBar mProgressBar;
    FrameLayout mBookmarkClickLayout;
    ShareButton mShareButton;
    PageIndexButton mPageIndexButton;


    // StorageManager mStorage;

    Handler mUiThreadHandler;
    boolean mIndexUpdated;
    GESTURES mLastGesture = GESTURES.undefined;

    public ArticleFragment() {
        super();
        mUiThreadHandler = new Handler(Looper.getMainLooper());
    }


    @Override
    public void onAttach(Activity activity) {

        super.onAttach(activity);
        //mStorage = new StorageManager(mContext);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @SuppressLint({"SetJavaScriptEnabled", "NewApi", "AddJavascriptInterface"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {



        View result = inflater.inflate(R.layout.reader_article, container, false);

        mBookmarkClickLayout = (FrameLayout) result.findViewById(R.id.bookmarkClickLayout);

        mWebView = (ArticleWebView) result.findViewById(R.id.webview);
        mWebView.setArticleWebViewCallback(this);

        mWebView.setBackgroundColor(getCallback().onGetBackgroundColor(TazSettings.getInstance(mContext).getPrefString(TazSettings.PREFKEY.THEME, "normal")));

        mWebView.setWebViewClient(new ArticleWebViewClient());
        mWebView.setWebChromeClient(new ArticleWebChromeClient());
        mWebView.getSettings()
                .setJavaScriptEnabled(true);
        mWebView.addJavascriptInterface(new ANDROIDAPI(), JAVASCRIPT_API_NAME);


        //fade(0F, 0);

        mWebView.setHorizontalScrollBarEnabled(true);
        mWebView.setVerticalScrollBarEnabled(true);
        mWebView.setScrollbarFadingEnabled(true);

        mWebView.getSettings()
                .setBuiltInZoomControls(true);
        mWebView.getSettings()
                .setSupportZoom(true);
        mWebView.getSettings()
                .setUseWideViewPort(true);


        mProgressBar = (ProgressBar) result.findViewById(R.id.progressBar);

        if (mArticle != null) {
            initialBookmark();
            loadArticleInWebview();
        }

        mShareButton = (ShareButton) result.findViewById(R.id.share);
        mPageIndexButton = (PageIndexButton) result.findViewById(R.id.pageindex);
        if (TazSettings.getInstance(mContext).getPrefBoolean(TazSettings.PREFKEY.PAGEINDEXBUTTON, false)) {
            mPageIndexButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() instanceof ReaderActivity) {
                        ((ReaderActivity) getActivity()).openPageIndexDrawer();
                    }
                }
            });
        } else mPageIndexButton.setVisibility(View.GONE);

        //ttsActive = TazSettings.getPrefBoolean(mContext, TazSettings.PREFKEY.TEXTTOSPEACH, false);

        return (result);
    }


    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {

        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onStart() {

        super.onStart();
    }

    @Override
    public void onResume() {

        super.onResume();
    }

    @Override
    public void onPause() {

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {

        super.onStop();
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    @Override
    public void onDetach() {

        super.onDetach();
    }

    @Override
    public void init(Paper paper, String key, String position) {
        Timber.d("initialising ArticleFragment %s", key);
        mArticle = paper.getPlist()
                        .getIndexItem(key);
        mStartPosition = position;
    }

    private void runOnUiThread(Runnable runnable) {
        mUiThreadHandler.post(runnable);
    }

    private void loadArticleInWebview() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                String baseUrl = "file://" + StorageManager.getInstance(getActivity())
                                                           .getPaperDirectory(mArticle.getPaper()) + "/" + mArticle.getKey() + "?position=" + mStartPosition;
                mWebView.loadDataWithBaseURL(baseUrl, getHtml(), "text/html", "UTF-8", null);
                mShareButton.setCallback(mArticle);
            }
        });
    }


    private void callTazapi(String function, String value) {
        mWebView.loadUrl("javascript:TAZAPI." + function + "(" + value + ")");
    }

    private void onGestureToTazapi(GESTURES gesture, MotionEvent e1) {
        //mOpenGesureResult = true;
        Timber.d("%s %s", mArticle.getKey(), gesture);
        callTazapi("onGesture", "'" + gesture.name() + "'," + e1.getX() + "," + e1.getY());
    }

    @Override
    public void onSwipeLeft(ArticleWebView view, MotionEvent e1, MotionEvent e2) {

        mLastGesture = GESTURES.swipeLeft;
        onGestureToTazapi(mLastGesture, e1);
    }

    @Override
    public void onSwipeRight(ArticleWebView view, MotionEvent e1, MotionEvent e2) {

        mLastGesture = GESTURES.swipeRight;
        onGestureToTazapi(mLastGesture, e1);
    }

    @Override
    public void onSwipeTop(ArticleWebView view, MotionEvent e1, MotionEvent e2) {

        mLastGesture = GESTURES.swipeUp;
        onGestureToTazapi(mLastGesture, e1);
    }

    @Override
    public void onSwipeBottom(ArticleWebView view, MotionEvent e1, MotionEvent e2) {

        mLastGesture = GESTURES.swipeDown;
        onGestureToTazapi(mLastGesture, e1);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (hasCallback()) {
            getCallback().speak(mArticle.getKey(), getTextToSpeech());
        }
        return true;
    }

    @Override
    public void onConfigurationChange(String key, String value) {
        Timber.d("%s %s", key, value);
        callTazapi("onConfigurationChanged", "'" + key + "','" + value + "'");
    }

    @Override
    public void onScrollStarted(ArticleWebView view) {
        Timber.d("%s %s", view.getScrollX(), view.getScrollY());
    }

    @Override
    public void onScrollFinished(ArticleWebView view) {
        Timber.d("%s %s", view.getScrollX(), view.getScrollY());
    }


    float mAlpha = 1F;

    //    @SuppressLint("NewApi")
    //    private void fade(final float to, final int duration) {
    //        if (to != mAlpha) {
    //            runOnUiThread(new Runnable() {
    //
    //                @Override
    //                public void run() {
    //                    float from = mAlpha;
    //                    try {
    //                            ValueAnimator fadeAnim = ObjectAnimator.ofFloat(mWebView, "alpha", from, to);
    //                            fadeAnim.setDuration(duration);
    //                            fadeAnim.start();
    //                        mAlpha = to;
    //                    } catch (Exception e) {
    //                        Log.e(e);
    //                    }
    //                }
    //            });
    //        }
    //    }

    public void initialBookmark() {
        if (!(mArticle instanceof Article)) mBookmarkClickLayout.setVisibility(View.GONE);
        else {
            mBookmarkClickLayout.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (hasCallback()) getCallback().onBookmarkClick(mArticle);
                }
            });

            ImageView bookmark = (ImageView) mBookmarkClickLayout.findViewById(R.id.bookmark);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bookmark.getLayoutParams();
            if (mArticle.isBookmarked()) {
                TintHelper.tintDrawable(bookmark.getDrawable(), ContextCompat.getColor(getActivity(), R.color.index_bookmark_on));
                layoutParams.topMargin = mContext.getResources()
                                                 .getDimensionPixelOffset(R.dimen.reader_bookmark_offset_active);
            } else {
                TintHelper.tintDrawable(bookmark.getDrawable(), ContextCompat.getColor(getActivity(), R.color.index_bookmark_off));
                layoutParams.topMargin = mContext.getResources()
                                                 .getDimensionPixelOffset(R.dimen.reader_bookmark_offset_normal);
            }
            bookmark.setLayoutParams(layoutParams);
        }
    }


    public class ArticleWebChromeClient extends WebChromeClient {

        @Override
        public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {

            StringBuilder messagBuilder = new StringBuilder(consoleMessage.messageLevel()
                                                                          .toString()).append(" in ")
                                                                                      .append(consoleMessage.sourceId())
                                                                                      .append(" on line ")
                                                                                      .append(consoleMessage.lineNumber())
                                                                                      .append(": ")
                                                                                      .append(consoleMessage.message());

            switch (consoleMessage.messageLevel()) {
                case TIP:
                    Timber.i("%s %s", mArticle.getKey(), messagBuilder.toString());
                    break;
                case WARNING:
                    Timber.w("%s %s", mArticle.getKey(), messagBuilder.toString());
                    break;
                case DEBUG:
                    Timber.d("%s %s", mArticle.getKey(), messagBuilder.toString());
                    break;
                case ERROR:
                    Timber.e("%s %s", mArticle.getKey(), messagBuilder.toString());
                    break;
                case LOG:
                    Timber.i("%s %s", mArticle.getKey(), messagBuilder.toString());
                    break;
            }
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            Timber.d("%s %s %s", message, url, result.toString());

            return true;
        }

    }

    public class ANDROIDAPI {

        @JavascriptInterface
        public void openUrl(final String url) {
            Timber.d("%s %s", mArticle.getKey(), url);
            if (url.startsWith("http")) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            } else if (url.startsWith("mailto:")) {
                try {
                    MailTo mt = MailTo.parse(url);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, mt.getSubject());
                    i.putExtra(Intent.EXTRA_TEXT, mt.getBody());
                    startActivity(i);
                } catch (ParseException e) {
                    Toast.makeText(mContext, "Kein gültiger RFC 2368 mailto: Link\n" + url, Toast.LENGTH_LONG)
                         .show();
                }
            } else if (url.startsWith(mArticle.getKey()) || url.startsWith("?")) {
                loadArticleInWebview();
            } else {
                if (hasCallback()) getCallback().onLoad(url);
            }

        }

        @JavascriptInterface
        public String getValue(String path) {
            String result = Store.getValueForKey(mContext, path);
            Timber.d("%s %s %s", mArticle.getKey(), path, result);
            return result;
        }

        @JavascriptInterface
        public boolean setValue(String path, String value) {
            boolean result = false;
            Store store = Store.getStoreForKey(mContext, path);
            if (store == null) {
                store = new Store(path, value);
                Uri resultUri = mContext.getContentResolver()
                                        .insert(Store.CONTENT_URI, store.getContentValues());
                if (resultUri != null) result = true;
            } else {
                store.setValue(value);
                int affected = mContext.getContentResolver()
                                       .update(Store.getUriForKey(path), store.getContentValues(), null, null);
                if (affected > 0) result = true;
            }
            Timber.d("%s %s %s", mArticle.getKey(), path, value, result);
            return result;
        }

        @JavascriptInterface
        public String getConfiguration(String name) {
            String result = getConfig(name);
            Timber.d("%s %s %s", mArticle.getKey(), name, result);
            return result;
        }

        @JavascriptInterface
        public boolean setConfiguration(String name, String value) {
            boolean result = setConfig(name, value);
            Timber.d("%s %s %s", mArticle.getKey(), name, value, result);
            return result;
        }

        @JavascriptInterface
        public void pageReady(String percentSeen, final String position, String numberOfPages) {
            Timber.d("%s %s %s", mArticle.getKey(), percentSeen, position, numberOfPages);

            //fade(1F, 800);

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    mProgressBar.setVisibility(View.GONE);
                    if (!mIndexUpdated) {
                        String newposition = position;
                        if (TazSettings.getInstance(mContext).getPrefBoolean(TazSettings.PREFKEY.ISSCROLL, false))
                            newposition = "0";
                        if (hasCallback())
                            getCallback().updateIndexes(mArticle.getKey(), newposition);
                        mIndexUpdated = true;
                    }
                }
            });
        }

        @JavascriptInterface
        public void enableRegionScroll(boolean isOn) {
            Timber.d("%s %s", mArticle.getKey(), isOn);
        }

        @JavascriptInterface
        public void beginRendering() {
            Timber.d(mArticle.getKey());
        }

        @JavascriptInterface
        public void nextArticle(final int position) {
            Timber.d("%s %s", mArticle.getKey(), position);
            //mAnimationLock = true;
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    DIRECTIONS direction;
                    switch (mLastGesture) {
                        case swipeUp:
                            direction = DIRECTIONS.BOTTOM;
                            break;
                        case swipeDown:
                            direction = DIRECTIONS.TOP;
                            break;
                        case swipeRight:
                            direction = DIRECTIONS.LEFT;
                            break;
                        default:
                            direction = DIRECTIONS.RIGHT;
                            break;
                    }

                    if (hasCallback())
                        getCallback().onLoadNextArticle(direction, String.valueOf(position));
                }
            });

        }

        @JavascriptInterface
        public void previousArticle(final int position) {
            Timber.d("%s %s", mArticle.getKey(), position);

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    String positionString = String.valueOf(position);
                    DIRECTIONS direction;

                    switch (mLastGesture) {
                        case swipeUp:
                            direction = DIRECTIONS.BOTTOM;
                            break;
                        case swipeDown:
                            direction = DIRECTIONS.TOP;
                            break;
                        case swipeLeft:
                            direction = DIRECTIONS.RIGHT;
                            break;
                        default:
                            if (!TazSettings.getInstance(mContext).getPrefBoolean(TazSettings.PREFKEY.ISSCROLL, false))
                                positionString = "EOF";
                            direction = DIRECTIONS.LEFT;
                            break;
                    }

                    if (hasCallback()) getCallback().onLoadPrevArticle(direction, positionString);
                }
            });
        }

        @JavascriptInterface
        public String getAbsoluteResourcePath() {
            File resourceDir = StorageManager.getInstance(getActivity())
                                             .getResourceDirectory(mArticle.getPaper()
                                                                           .getResource());
            return "file://" + resourceDir.getAbsolutePath() + "/";
        }

        @JavascriptInterface
        public void clearWebCache() {


        }
    }

    public class ArticleWebViewClient extends WebViewClient {

        @Override
        public void onLoadResource(WebView view, String url) {
            Timber.i(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Timber.i(url);
            if (url != null) {
                if (url.toLowerCase(Locale.getDefault())
                       .startsWith("http")) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                } else {
                    if (hasCallback()) {
                        IIndexItem indexItem = getCallback().getPaper()
                                                            .getPlist()
                                                            .getIndexItem(url);
                        if (indexItem != null) {
                            getCallback().onLoad(url);
                        }
                    }
                }
            }
            return true;
        }


        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Timber.e("%s errorCode: %d", mArticle.getKey(), errorCode);
            Timber.e("%s description: %s", mArticle.getKey(), description);
            Timber.e("%s failingUrl: %s", mArticle.getKey(), failingUrl);
            super.onReceivedError(view, errorCode, description, failingUrl);
        }
    }

    private static final String TAZAPI_FILE_STRING = "TAZAPI.js";


    public String getHtml() {
        File articleFile = new File(StorageManager.getInstance(getActivity())
                                                  .getPaperDirectory(mArticle.getPaper())
                                                  .getAbsolutePath(), mArticle.getKey());
        File resourceDir = StorageManager.getInstance(getActivity())
                                         .getResourceDirectory(mArticle.getPaper()
                                                                       .getResource());

        String resourceReplacement = "file://" + resourceDir.getAbsolutePath() + "/";
        String tazapiReplacement = "file:///android_asset/js/TAZAPI.js";

        String result = null;
        try {
            result = IOUtils.toString(new FileInputStream(articleFile), "UTF-8");

            //            Pattern tazapiPattern = Pattern.compile("(<script.+?src\\s*?=\\s*?(?:\"|'))(res.+?TAZAPI.js)((?:\"|').*?>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

            //            Matcher matcher = tazapiPattern.matcher(result);
            //            result = matcher.replaceAll("$1" + tazapiReplacement + "$3");

            Pattern resPattern = Pattern.compile("(<[^>]+?(?:href|src)\\s*?=\\s*?(?:\"|'))(res.+?)((?:\"|').*?>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = resPattern.matcher(result);
            result = matcher.replaceAll("$1" + resourceReplacement + "$2$3");

            Pattern tazapiPattern = Pattern.compile("file://.+?TAZAPI.js", Pattern.CASE_INSENSITIVE);
            Matcher matcher2 = tazapiPattern.matcher(result);
            result = matcher2.replaceAll(tazapiReplacement);

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(result)) result = "Fehler beim Laden des Artikels";

        return result;

        // (\<[^\>]+?(?:href|src)\=(?:\"|\'))(res)(.+?(?:\"|\')\>)
    }

    @Override
    public void onTtsStateChanged(ReaderTtsFragment.TTS state) {
        Timber.d("%s", state);
    }

    private CharSequence getTextToSpeech() {
        Pattern pattern = Pattern.compile(".*?<body.*?>(.*?)</body>.*?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(getHtml());
        if (matcher.matches()) {

            Pattern replacePattern = Pattern.compile("[\u00AD]?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher replaceMatcher = replacePattern.matcher(Html.fromHtml(matcher.group(1)));
            return replaceMatcher.replaceAll("");
        }
        return null;
    }
}