/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Objects;

public class SessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private FlickerLoadingView globalFlickerLoadingView;

    private ArrayList<TLObject> sessions = new ArrayList<>();
    private ArrayList<TLObject> passwordSessions = new ArrayList<>();
    private TLRPC.TL_authorization currentSession;
    private boolean loading;
    private UndoView undoView;
    private int ttlDays;

    private int currentType;

    private int currentSessionSectionRow;
    private int currentSessionRow;
    private int terminateAllSessionsRow;
    private int terminateAllSessionsDetailRow;
    private int passwordSessionsSectionRow;
    private int passwordSessionsStartRow;
    private int passwordSessionsEndRow;
    private int passwordSessionsDetailRow;
    private int otherSessionsSectionRow;
    private int otherSessionsStartRow;
    private int otherSessionsEndRow;
    private int otherSessionsTerminateDetail;
    private int noOtherSessionsRow;
    private int rowCount;
    private int ttlHeaderRow;
    private int ttlRow;
    private int ttlDivideRow;

    private int repeatLoad = 0;
    private Delegate delegate;

    public SessionsActivity(int type) {
        currentType = type;
    }


    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        loadSessions(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSessionReceived);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSessionReceived);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);

        if (isOpen && !backward) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View ch = listView.getChildAt(i);
            }
        }
    }

    @Override
    public View createView(Context context) {
        globalFlickerLoadingView = new FlickerLoadingView(context);
        globalFlickerLoadingView.setIsSingleCell(true);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("Devices", R.string.Devices));
        } else {
            actionBar.setTitle(LocaleController.getString("WebSessionsTitle", R.string.WebSessionsTitle));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(150);
        itemAnimator.setMoveInterpolator(CubicBezierInterpolator.DEFAULT);
        itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        listView.setItemAnimator(itemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (position == ttlRow) {
                if (getParentActivity() == null) {
                    return;
                }
                int selected;
                if (ttlDays <= 7) {
                    selected = 0;
                } else if (ttlDays <= 93) {
                    selected = 1;
                } else if (ttlDays <= 183) {
                    selected = 2;
                } else {
                    selected = 3;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SessionsSelfDestruct", R.string.SessionsSelfDestruct));
                String[] items = new String[]{
                        LocaleController.formatPluralString("Weeks", 1),
                        LocaleController.formatPluralString("Months", 3),
                        LocaleController.formatPluralString("Months", 6),
                        LocaleController.formatPluralString("Years", 1)
                };
                final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < items.length; a++) {
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[a], selected == a);
                    linearLayout.addView(cell);
                    cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                    cell.setOnClickListener(v -> {
                        builder.getDismissRunnable().run();
                        Integer which = (Integer) v.getTag();

                        int value = 0;
                        if (which == 0) {
                            value = 7;
                        } else if (which == 1) {
                            value = 90;
                        } else if (which == 2) {
                            value = 183;
                        } else if (which == 3) {
                            value = 365;
                        }

                        final TLRPC.TL_account_setAuthorizationTTL req = new TLRPC.TL_account_setAuthorizationTTL();
                        req.authorization_ttl_days = value;
                        ttlDays = value;
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        getConnectionsManager().sendRequest(req, (response, error) -> {

                        });
                    });
                }
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == terminateAllSessionsRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                String buttonText;
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("AreYouSureSessions", R.string.AreYouSureSessions));
                    builder.setTitle(LocaleController.getString("AreYouSureSessionsTitle", R.string.AreYouSureSessionsTitle));
                    buttonText = LocaleController.getString("Terminate", R.string.Terminate);
                } else {
                    builder.setMessage(LocaleController.getString("AreYouSureWebSessions", R.string.AreYouSureWebSessions));
                    builder.setTitle(LocaleController.getString("TerminateWebSessionsTitle", R.string.TerminateWebSessionsTitle));
                    buttonText = LocaleController.getString("Disconnect", R.string.Disconnect);
                }
                builder.setPositiveButton(buttonText, (dialogInterface, i) -> {
                    if (currentType == 0) {
                        TLRPC.TL_auth_resetAuthorizations req = new TLRPC.TL_auth_resetAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                    BulletinFactory.of(SessionsActivity.this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("AllSessionsTerminated", R.string.AllSessionsTerminated)).show();
                                    loadSessions(false);
                                }
                            });

                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                UserConfig userConfig = UserConfig.getInstance(a);
                                if (!userConfig.isClientActivated()) {
                                    continue;
                                }
                                userConfig.registeredForPush = false;
                                userConfig.saveConfig(false);
                                MessagesController.getInstance(a).registerForPush(SharedConfig.pushType, SharedConfig.pushString);
                                ConnectionsManager.getInstance(a).setUserId(userConfig.getClientUserId());
                            }
                        });
                    } else {
                        TLRPC.TL_account_resetWebAuthorizations req = new TLRPC.TL_account_resetWebAuthorizations();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null) {
                                return;
                            }
                            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                BulletinFactory.of(SessionsActivity.this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("AllWebSessionsTerminated", R.string.AllWebSessionsTerminated)).show();
                            } else {
                                BulletinFactory.of(SessionsActivity.this).createSimpleBulletin(R.raw.error, LocaleController.getString("UnknownError", R.string.UnknownError)).show();
                            }
                            loadSessions(false);
                        }));
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                }
            } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow || position == currentSessionRow) {
                if (getParentActivity() == null) {
                    return;
                }
                if (currentType == 0) {
                    final TLRPC.TL_authorization authorization;
                    boolean isCurrentSession = false;
                    if (position == currentSessionRow) {
                        authorization = currentSession;
                        isCurrentSession = true;
                    } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        authorization = (TLRPC.TL_authorization) sessions.get(position - otherSessionsStartRow);
                    } else {
                        authorization = (TLRPC.TL_authorization) passwordSessions.get(position - passwordSessionsStartRow);
                    }
                    showSessionBottomSheet(authorization, isCurrentSession);
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                final boolean[] param = new boolean[1];
                String buttonText;
                if (currentType == 0) {
                    builder.setMessage(LocaleController.getString("TerminateSessionText", R.string.TerminateSessionText));
                    builder.setTitle(LocaleController.getString("AreYouSureSessionTitle", R.string.AreYouSureSessionTitle));
                    buttonText = LocaleController.getString("Terminate", R.string.Terminate);
                } else {
                    final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);

                    builder.setMessage(LocaleController.formatString("TerminateWebSessionText", R.string.TerminateWebSessionText, authorization.domain));
                    builder.setTitle(LocaleController.getString("TerminateWebSessionTitle", R.string.TerminateWebSessionTitle));
                    buttonText = LocaleController.getString("Disconnect", R.string.Disconnect);

                    FrameLayout frameLayout1 = new FrameLayout(getParentActivity());

                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(authorization.bot_id);
                    String name;
                    if (user != null) {
                        name = UserObject.getFirstName(user);
                    } else {
                        name = "";
                    }

                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setText(LocaleController.formatString("TerminateWebSessionStop", R.string.TerminateWebSessionStop, name), "", false, false);
                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                    frameLayout1.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                    cell.setOnClickListener(v -> {
                        if (!v.isEnabled()) {
                            return;
                        }
                        CheckBoxCell cell1 = (CheckBoxCell) v;
                        param[0] = !param[0];
                        cell1.setChecked(param[0], true);
                    });
                    builder.setCustomViewOffset(16);
                    builder.setView(frameLayout1);
                }
                builder.setPositiveButton(buttonText, (dialogInterface, option) -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.setCanCancel(false);
                    progressDialog.show();

                    if (currentType == 0) {
                        final TLRPC.TL_authorization authorization;
                        if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                            authorization = (TLRPC.TL_authorization) sessions.get(position - otherSessionsStartRow);
                        } else {
                            authorization = (TLRPC.TL_authorization) passwordSessions.get(position - passwordSessionsStartRow);
                        }
                        TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                passwordSessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                    } else {
                        final TLRPC.TL_webAuthorization authorization = (TLRPC.TL_webAuthorization) sessions.get(position - otherSessionsStartRow);
                        TLRPC.TL_account_resetWebAuthorization req = new TLRPC.TL_account_resetWebAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (error == null) {
                                sessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                            }
                        }));
                        if (param[0]) {
                            MessagesController.getInstance(currentAccount).blockPeer(authorization.bot_id);
                        }
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                }
            }
        });

        if (currentType == 0) {
            undoView = new UndoView(context) {
                @Override
                public void hide(boolean apply, int animated) {
                    if (!apply) {
                        TLRPC.TL_authorization authorization = (TLRPC.TL_authorization) getCurrentInfoObject();
                        TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                        req.hash = authorization.hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error == null) {
                                sessions.remove(authorization);
                                passwordSessions.remove(authorization);
                                updateRows();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                                loadSessions(true);
                            }
                        }));
                    }
                    super.hide(apply, animated);
                }
            };
            frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        updateRows();
        return fragmentView;
    }


    private void showSessionBottomSheet(TLRPC.TL_authorization authorization, boolean isCurrentSession) {
        if (authorization == null) {
            return;
        }
        SessionBottomSheet bottomSheet = new SessionBottomSheet(this, authorization, isCurrentSession, new SessionBottomSheet.Callback() {
            @Override
            public void onSessionTerminated(TLRPC.TL_authorization authorization) {
                sessions.remove(authorization);
                passwordSessions.remove(authorization);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                TLRPC.TL_account_resetAuthorization req = new TLRPC.TL_account_resetAuthorization();
                req.hash = authorization.hash;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {

                }));
            }
        });
        bottomSheet.show();

    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSessionReceived) {
            loadSessions(true);
        }
    }

    public void loadSessions(boolean silent) {
        if (loading) {
            return;
        }
        if (!silent) {
            loading = true;
        }
        if (currentType == 0) {
            TLRPC.TL_account_getAuthorizations req = new TLRPC.TL_account_getAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                int oldItemsCount = listAdapter != null ? listAdapter.getItemCount() : 0;
                if (error == null) {
                    sessions.clear();
                    passwordSessions.clear();
                    TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
                    for (int a = 0, N = res.authorizations.size(); a < N; a++) {
                        TLRPC.TL_authorization authorization = res.authorizations.get(a);
                        if ((authorization.flags & 1) != 0) {
                            currentSession = authorization;
                        } else if (authorization.password_pending) {
                            passwordSessions.add(authorization);
                        } else {
                            sessions.add(authorization);
                        }
                    }
                    ttlDays = res.authorization_ttl_days;
                    updateRows();
                    if (delegate != null) {
                        delegate.sessionsLoaded();
                    }
                }
//                itemsEnterAnimator.showItemsAnimated(oldItemsCount + 1);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }

                if (repeatLoad > 0) {
                    repeatLoad--;
                    if (repeatLoad > 0) {
                        AndroidUtilities.runOnUIThread(() -> loadSessions(silent), 2500);
                    }
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        } else {
            TLRPC.TL_account_getWebAuthorizations req = new TLRPC.TL_account_getWebAuthorizations();
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (error == null) {
                    sessions.clear();
                    TLRPC.TL_account_webAuthorizations res = (TLRPC.TL_account_webAuthorizations) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    sessions.addAll(res.authorizations);
                    updateRows();
                }
//                itemsEnterAnimator.showItemsAnimated(0);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }

                if (repeatLoad > 0) {
                    repeatLoad--;
                    if (repeatLoad > 0) {
                        AndroidUtilities.runOnUIThread(() -> loadSessions(silent), 2500);
                    }
                }
            }));
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    private void updateRows() {
        rowCount = 0;
        currentSessionSectionRow = -1;
        currentSessionRow = -1;
        terminateAllSessionsRow = -1;
        terminateAllSessionsDetailRow = -1;
        passwordSessionsSectionRow = -1;
        passwordSessionsStartRow = -1;
        passwordSessionsEndRow = -1;
        passwordSessionsDetailRow = -1;
        otherSessionsSectionRow = -1;
        otherSessionsStartRow = -1;
        otherSessionsEndRow = -1;
        otherSessionsTerminateDetail = -1;
        noOtherSessionsRow = -1;
        ttlHeaderRow = -1;
        ttlRow = -1;
        ttlDivideRow = -1;


        if (loading) {
            if (currentType == 0) {
                currentSessionSectionRow = rowCount++;
                currentSessionRow = rowCount++;
            }
            return;
        }
        if (currentSession != null) {
            currentSessionSectionRow = rowCount++;
            currentSessionRow = rowCount++;
        }


        if (!passwordSessions.isEmpty() || !sessions.isEmpty()) {
            terminateAllSessionsRow = rowCount++;
            terminateAllSessionsDetailRow = rowCount++;
            noOtherSessionsRow = -1;
        } else {
            terminateAllSessionsRow = -1;
            terminateAllSessionsDetailRow = -1;
            if (currentType == 1 || currentSession != null) {
                noOtherSessionsRow = rowCount++;
            } else {
                noOtherSessionsRow = -1;
            }
        }
        if (!passwordSessions.isEmpty()) {
            passwordSessionsSectionRow = rowCount++;
            passwordSessionsStartRow = rowCount;
            rowCount += passwordSessions.size();
            passwordSessionsEndRow = rowCount;
            passwordSessionsDetailRow = rowCount++;
        }
        if (!sessions.isEmpty()) {
            otherSessionsSectionRow = rowCount++;
            otherSessionsStartRow = rowCount;
            otherSessionsEndRow = rowCount + sessions.size();
            rowCount += sessions.size();
            otherSessionsTerminateDetail = rowCount++;
        }

        if (ttlDays > 0) {
            ttlHeaderRow = rowCount++;
            ttlRow = rowCount++;
            ttlDivideRow = rowCount++;
        }
    }

    private final int VIEW_TYPE_TEXT = 0;
    private final int VIEW_TYPE_INFO = 1;
    private final int VIEW_TYPE_HEADER = 2;
    private final int VIEW_TYPE_SESSION = 4;
    private final int VIEW_TYPE_SETTINGS = 6;

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
            setHasStableIds(true);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == terminateAllSessionsRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow || position == currentSessionRow || position == ttlRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_TEXT:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SESSION:
                default:
                    view = new SessionCell(mContext, currentType);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == terminateAllSessionsRow) {
                        textCell.setColors(Theme.key_windowBackgroundWhiteRedText2, Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        if (currentType == 0) {
                            textCell.setTextAndIcon(LocaleController.getString("TerminateAllSessions", R.string.TerminateAllSessions), false);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("TerminateAllWebSessions", R.string.TerminateAllWebSessions), false);
                        }
                    }
                    break;
                case VIEW_TYPE_INFO:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    privacyCell.setFixedSize(0);
                    if (position == terminateAllSessionsDetailRow) {
                        if (currentType == 0) {
                            privacyCell.setText(LocaleController.getString("ClearOtherSessionsHelp", R.string.ClearOtherSessionsHelp));
                        } else {
                            privacyCell.setText(LocaleController.getString("ClearOtherWebSessionsHelp", R.string.ClearOtherWebSessionsHelp));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == otherSessionsTerminateDetail) {
                        if (currentType == 0) {
                            if (sessions.isEmpty()) {
                                privacyCell.setText("");
                            } else {
                                privacyCell.setText(LocaleController.getString("SessionsListInfo", R.string.SessionsListInfo));
                            }
                        } else {
                            privacyCell.setText(LocaleController.getString("TerminateWebSessionInfo", R.string.TerminateWebSessionInfo));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordSessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("LoginAttemptsInfo", R.string.LoginAttemptsInfo));
                        if (otherSessionsTerminateDetail == -1) {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == ttlDivideRow || position == noOtherSessionsRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        privacyCell.setText("");
                        privacyCell.setFixedSize(12);
                    }
                    break;
                case VIEW_TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == currentSessionSectionRow) {
                        headerCell.setText(LocaleController.getString("CurrentSession", R.string.CurrentSession));
                    } else if (position == otherSessionsSectionRow) {
                        if (currentType == 0) {
                            headerCell.setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
                        } else {
                            headerCell.setText(LocaleController.getString("OtherWebSessions", R.string.OtherWebSessions));
                        }
                    } else if (position == passwordSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("LoginAttempts", R.string.LoginAttempts));
                    } else if (position == ttlHeaderRow) {
                        headerCell.setText(LocaleController.getString("TerminateOldSessionHeader", R.string.TerminateOldSessionHeader));
                    }
                    break;
                case VIEW_TYPE_SETTINGS:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    String value;
                    if (ttlDays > 30 && ttlDays <= 183) {
                        value = LocaleController.formatPluralString("Months", ttlDays / 30);
                    } else if (ttlDays == 365) {
                        value = LocaleController.formatPluralString("Years", ttlDays / 365);
                    } else {
                        value = LocaleController.formatPluralString("Weeks", ttlDays / 7);
                    }
                    textSettingsCell.setTextAndValue(LocaleController.getString("IfInactiveFor", R.string.IfInactiveFor), value, true, false);
                    break;
                case VIEW_TYPE_SESSION:
                default:
                    SessionCell sessionCell = (SessionCell) holder.itemView;
                    if (position == currentSessionRow) {
                        if (currentSession == null) {
                            sessionCell.showStub(globalFlickerLoadingView);
                        } else {
                            sessionCell.setSession(currentSession, !sessions.isEmpty() || !passwordSessions.isEmpty());
                        }
                    } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        sessionCell.setSession(sessions.get(position - otherSessionsStartRow), position != otherSessionsEndRow - 1);
                    } else if (position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                        sessionCell.setSession(passwordSessions.get(position - passwordSessionsStartRow), position != passwordSessionsEndRow - 1);
                    }
                    break;
            }
        }

        @Override
        public long getItemId(int position) {
            if (position == terminateAllSessionsRow) {
                return Objects.hash(0, 0);
            } else if (position == terminateAllSessionsDetailRow) {
                return Objects.hash(0, 1);
            } else if (position == otherSessionsTerminateDetail) {
                return Objects.hash(0, 2);
            } else if (position == passwordSessionsDetailRow) {
                return Objects.hash(0, 3);
            } else if (position == ttlDivideRow) {
                return Objects.hash(0, 5);
            } else if (position == noOtherSessionsRow) {
                return Objects.hash(0, 6);
            } else if (position == currentSessionSectionRow) {
                return Objects.hash(0, 7);
            } else if (position == otherSessionsSectionRow) {
                return Objects.hash(0, 8);
            } else if (position == passwordSessionsSectionRow) {
                return Objects.hash(0, 9);
            } else if (position == ttlHeaderRow) {
                return Objects.hash(0, 10);
            } else if (position == currentSessionRow) {
                return Objects.hash(0, 11);
            } else if (position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                TLObject session = sessions.get(position - otherSessionsStartRow);
                if (session instanceof TLRPC.TL_authorization) {
                    return Objects.hash(1, ((TLRPC.TL_authorization) session).hash);
                } else if (session instanceof TLRPC.TL_webAuthorization) {
                    return Objects.hash(1, ((TLRPC.TL_webAuthorization) session).hash);
                }
            } else if (position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                TLObject session = passwordSessions.get(position - passwordSessionsStartRow);
                if (session instanceof TLRPC.TL_authorization) {
                    return Objects.hash(2, ((TLRPC.TL_authorization) session).hash);
                } else if (session instanceof TLRPC.TL_webAuthorization) {
                    return Objects.hash(2, ((TLRPC.TL_webAuthorization) session).hash);
                }
            } else if (position == ttlRow) {
                return Objects.hash(0, 13);
            }
            return Objects.hash(0, -1);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == terminateAllSessionsRow) {
                return VIEW_TYPE_TEXT;
            } else if (position == terminateAllSessionsDetailRow || position == otherSessionsTerminateDetail || position == passwordSessionsDetailRow || position == ttlDivideRow || position == noOtherSessionsRow) {
                return VIEW_TYPE_INFO;
            } else if (position == currentSessionSectionRow || position == otherSessionsSectionRow || position == passwordSessionsSectionRow || position == ttlHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == currentSessionRow || position >= otherSessionsStartRow && position < otherSessionsEndRow || position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                return VIEW_TYPE_SESSION;
            } else if (position == ttlRow) {
                return VIEW_TYPE_SETTINGS;
            }
            return VIEW_TYPE_TEXT;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, SessionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SessionCell.class}, new String[]{"detailExTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{UndoView.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_undo_infoColor));

        return themeDescriptions;
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (getParentActivity() == null) {
            return;
        }
        if (requestCode == ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(getParentActivity())
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.getString("QRCodePermissionNoCameraWithHint", R.string.QRCodePermissionNoCameraWithHint)))
                        .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                getParentActivity().startActivity(intent);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        })
                        .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
                        .show();
            }
        }
    }

    int getSessionsCount() {
        if (sessions.size() == 0 && loading) {
            return 0;
        }
        return sessions.size() + 1;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public interface Delegate {
        void sessionsLoaded();
    }
}
