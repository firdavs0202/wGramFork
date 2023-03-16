package org.telegram.messenger;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BillingController implements PurchasesUpdatedListener, BillingClientStateListener {
    public final static String PREMIUM_PRODUCT_ID = "telegram_premium";
    public final static QueryProductDetailsParams.Product PREMIUM_PRODUCT = QueryProductDetailsParams.Product.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .setProductId(PREMIUM_PRODUCT_ID)
            .build();

    @Nullable
    public static ProductDetails PREMIUM_PRODUCT_DETAILS;

    private static BillingController instance;

    private Map<String, Consumer<BillingResult>> resultListeners = new HashMap<>();
    private List<String> requestingTokens = new ArrayList<>();

    private Map<String, Integer> currencyExpMap = new HashMap<>();

    public static BillingController getInstance() {
        if (instance == null) {
            instance = new BillingController(ApplicationLoader.applicationContext);
        }
        return instance;
    }

    private BillingClient billingClient;

    private BillingController(Context ctx) {
        billingClient = BillingClient.newBuilder(ctx)
                .enablePendingPurchases()
                .setListener(this)
                .build();
    }


    public String formatCurrency(long amount, String currency) {
        return formatCurrency(amount, currency, getCurrencyExp(currency));
    }

    public String formatCurrency(long amount, String currency, int exp) {
        if (currency.isEmpty()) {
            return String.valueOf(amount);
        }
        Currency cur = Currency.getInstance(currency);
        if (cur != null) {
            NumberFormat numberFormat = NumberFormat.getCurrencyInstance();
            numberFormat.setCurrency(cur);

            return numberFormat.format(amount / Math.pow(10, exp));
        }
        return amount + " " + currency;
    }

    public int getCurrencyExp(String currency) {
        Integer exp = currencyExpMap.get(currency);
        if (exp == null) {
            return 0;
        }
        return exp;
    }

    public void startConnection() {
        if (isReady()) {
            return;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            InputStream in = ctx.getAssets().open("currencies.json");
            JSONObject obj = new JSONObject(new String(Util.toByteArray(in), "UTF-8"));
            parseCurrencies(obj);
            in.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (!BuildVars.useInvoiceBilling()) {
            billingClient.startConnection(this);
        }
    }

    private void parseCurrencies(JSONObject obj) {
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String key = it.next();
            JSONObject currency = obj.optJSONObject(key);
            currencyExpMap.put(key, currency.optInt("exp"));
        }
    }

    public boolean isReady() {
        return billingClient.isReady();
    }

    public void queryProductDetails(List<QueryProductDetailsParams.Product> products, ProductDetailsResponseListener responseListener) {
        if (!isReady()) {
            throw new IllegalStateException("Billing controller should be ready for this call!");
        }
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build(), responseListener);
    }

    public void queryPurchases(String productType, PurchasesResponseListener responseListener) {
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(productType).build(), responseListener);
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        FileLog.d("Billing purchases updated: " + billingResult + ", " + list);
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {

            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                AccountInstance acc = AccountInstance.getInstance(i);
                if (!acc.getUserConfig().awaitBillingProductIds.isEmpty()) {
                    acc.getUserConfig().awaitBillingProductIds.clear();
                    acc.getUserConfig().billingPaymentPurpose = null;
                    acc.getUserConfig().saveConfig(false);
                }
            }

            return;
        }
        if (list == null) {
            return;
        }
        for (Purchase purchase : list) {
            if (!requestingTokens.contains(purchase.getPurchaseToken())) {
                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                    AccountInstance acc = AccountInstance.getInstance(i);
                    if (acc.getUserConfig().awaitBillingProductIds.containsAll(purchase.getProducts()) && purchase.getPurchaseState() != Purchase.PurchaseState.PENDING) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            if (!purchase.isAcknowledged()) {
                                requestingTokens.add(purchase.getPurchaseToken());
                                TLRPC.TL_payments_assignPlayMarketTransaction req = new TLRPC.TL_payments_assignPlayMarketTransaction();
                                req.receipt = new TLRPC.TL_dataJSON();
                                req.receipt.data = purchase.getOriginalJson();
                                req.purpose = acc.getUserConfig().billingPaymentPurpose;
                                acc.getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (response instanceof TLRPC.Updates) {
                                        acc.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                                        requestingTokens.remove(purchase.getPurchaseToken());

                                        for (String productId : purchase.getProducts()) {
                                            Consumer<BillingResult> listener = resultListeners.remove(productId);
                                            if (listener != null) {
                                                listener.accept(billingResult);
                                            }
                                        }

                                        if (req.purpose instanceof TLRPC.TL_inputStorePaymentGiftPremium) {
                                            billingClient.consumeAsync(ConsumeParams.newBuilder()
                                                            .setPurchaseToken(purchase.getPurchaseToken())
                                                    .build(), (billingResult1, s) -> {});
                                        }
                                    }
                                    if (response != null || (ApplicationLoader.isNetworkOnline() && error != null && error.code != -1000)) {
                                        acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                                        acc.getUserConfig().saveConfig(false);
                                    }
                                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagInvokeAfter);
                            } else {
                                acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                                acc.getUserConfig().saveConfig(false);
                            }
                        } else {
                            acc.getUserConfig().awaitBillingProductIds.removeAll(purchase.getProducts());
                            acc.getUserConfig().saveConfig(false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        FileLog.d("Billing service disconnected");
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult setupBillingResult) {
        if (setupBillingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            queryProductDetails(Collections.singletonList(PREMIUM_PRODUCT), (billingResult, list) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (ProductDetails details : list) {
                        if (details.getProductId().equals(PREMIUM_PRODUCT_ID)) {
                            PREMIUM_PRODUCT_DETAILS = details;
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.billingProductDetailsUpdated));
                }
            });

            queryPurchases(BillingClient.ProductType.SUBS, this::onPurchasesUpdated);
        }
    }
}
