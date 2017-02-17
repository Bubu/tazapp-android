package de.thecode.android.tazreader.start;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import de.mateware.dialog.Dialog;
import de.mateware.dialog.DialogIndeterminateProgress;
import de.thecode.android.tazreader.BuildConfig;
import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.okhttp3.OkHttp3Helper;
import de.thecode.android.tazreader.sync.AccountHelper;
import de.thecode.android.tazreader.utils.BaseFragment;
import de.thecode.android.tazreader.utils.RunnableExtended;

import java.io.IOException;
import java.lang.ref.WeakReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;


/**
 * A simple {@link Fragment} subclass.
 */
public class LoginFragment extends BaseFragment {

    public static final String DIALOG_CHECK_CREDENTIALS = "checkCrd";
    public static final String DIALOG_ERROR_CREDENTIALS = "errorCrd";
    private EditText                      editUser;
    private EditText                      editPass;
    private WeakReference<IStartCallback> callback;

    public LoginFragment() {
        // Required empty public constructor
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        callback = new WeakReference<>((IStartCallback) getActivity());
        if (hasCallback()) getCallback().onUpdateDrawer(this);

        View view = inflater.inflate(R.layout.start_login, container, false);
        Button loginButton = (Button) view.findViewById(R.id.buttonLogin);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkLogin();
            }
        });
        Button orderButton = (Button) view.findViewById(R.id.buttonOrder);
        orderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(BuildConfig.ABOURL));
                startActivity(i);
            }
        });

        editUser = (EditText) view.findViewById(R.id.editUser);
        editPass = (EditText) view.findViewById(R.id.editPass);
        editPass.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    checkLogin();
                    return true;
                }
                return false;
            }
        });

        if (!AccountHelper.getInstance(getContext())
                          .isDemoMode()) {
            editUser.setText(AccountHelper.getInstance(getContext())
                                          .getUser());
            editPass.setText(AccountHelper.getInstance(getContext())
                                          .getPassword());
        }

        return view;
    }

    private boolean hasCallback() {
        return callback.get() != null;
    }

    private IStartCallback getCallback() {
        return callback.get();
    }

    private void blockUi() {
        new DialogIndeterminateProgress.Builder().setCancelable(false)
                                                 .setMessage(R.string.dialog_check_credentials)
                                                 .buildSupport()
                                                 .show(getFragmentManager(), DIALOG_CHECK_CREDENTIALS);
        editUser.setEnabled(false);
        editPass.setEnabled(false);
    }

    private void unblockUi() {

        editUser.setEnabled(true);
        editPass.setEnabled(true);
        DialogIndeterminateProgress.dismissDialog(getFragmentManager(), DIALOG_CHECK_CREDENTIALS);
    }

    private void checkLogin() {

        String username = editUser.getText()
                                  .toString();
        String password = editPass.getText()
                                  .toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            new Dialog.Builder().setIcon(R.drawable.ic_alerts_and_states_warning)
                                .setTitle(R.string.dialog_error_title)
                                .setMessage(R.string.dialog_error_no_credentials)
                                .setPositiveButton()
                                .buildSupport()
                                .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
            return;
        }
        if (AccountHelper.ACCOUNT_DEMO_USER.equalsIgnoreCase(username)) {
            new Dialog.Builder().setIcon(R.drawable.ic_alerts_and_states_warning)
                                .setTitle(R.string.dialog_error_title)
                                .setMessage(R.string.dialog_error_credentials_not_allowed)
                                .setPositiveButton()
                                .buildSupport()
                                .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
            return;
        }


        blockUi();


        Call call = OkHttp3Helper.getInstance(getContext())
                                 .getCall(HttpUrl.parse(BuildConfig.CHECKLOGINURL), username, password);

        call.enqueue(new LoginCallback(username, password) {

            Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override
            public void onResponse(Call call, Response response, String username, String password) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(new RunnableExtended(username, password) {
                        @Override
                        public void run() {
                            unblockUi();
                            AccountHelper.getInstance(getContext())
                                         .setUser((String) getObject(0), (String) getObject(1));
                            if (hasCallback()) getCallback().loginFinished();
                        }
                    });
                } else {
                    onFailure(call, new IOException(response.body()
                                                            .string()));
                }
            }

            @Override
            public void onFailure(Call call, IOException e, String username, String password) {
                mainHandler.post(new RunnableExtended(e) {
                    @Override
                    public void run() {
                        unblockUi();
                        new Dialog.Builder().setIcon(R.drawable.ic_alerts_and_states_warning)
                                            .setTitle(R.string.dialog_error_title)
                                            .setMessage(((Exception) getObject(0)).getMessage())
                                            .setPositiveButton()
                                            .buildSupport()
                                            .show(getFragmentManager(), DIALOG_ERROR_CREDENTIALS);
                    }
                });
            }
        });
    }

    private static abstract class LoginCallback implements Callback {

        private final String username;
        private final String password;

        LoginCallback(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            onResponse(call, response, username, password);
        }

        @Override
        public void onFailure(Call call, IOException e) {
            onFailure(call, e, username, password);
        }

        public abstract void onResponse(Call call, Response response, String username, String password) throws IOException;

        public abstract void onFailure(Call call, IOException e, String username, String password);
    }
}
