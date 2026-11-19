package com.notelock.app;

import android.app.Activity;
import android.content.Context;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Note Lock Pro: a one-time Google Play purchase that unlocks chords and scales.
 * Create a one-time product with the ID below in Play Console.
 */
final class Pro {
    static final String PRODUCT_ID = "notelock_pro";

    interface Listener {
        void onProChanged(boolean owned);
        void onPrice(String formattedPrice);
        void onMessage(String message);
    }

    private final Activity activity;
    private final Listener listener;
    private final BillingClient client;
    private ProductDetails details;
    private String offerToken;
    private boolean ready;
    /** Why the price couldn't be loaded last time (shown if the user taps Buy). */
    private String problem;

    static boolean owned(Context c) {
        // The test APK you install yourself (debug build) can't buy through Play, so it's always Pro.
        if ((c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) return true;
        return Notes.prefs(c).getBoolean(Notes.KEY_PRO, false);
    }

    private static void setOwned(Context c, boolean on) {
        Notes.prefs(c).edit().putBoolean(Notes.KEY_PRO, on).apply();
    }

    Pro(Activity a, Listener l) {
        activity = a;
        listener = l;
        client = BillingClient.newBuilder(a)
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    ready = true;
                    loadProduct();
                    restore(false);
                } else {
                    problem = "Couldn't connect to Google Play (" + result.getDebugMessage()
                            + "). Check that the Play Store app is signed in, then try again.";
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Auto reconnection is on; the next call reconnects.
            }
        });
    }

    void end() {
        client.endConnection();
    }

    private void loadProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();
        client.queryProductDetailsAsync(params, (result, productResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                problem = "Google Play couldn't load Note Lock Pro (" + result.getDebugMessage()
                        + "). Try again in a moment.";
                return;
            }
            List<ProductDetails> list = productResult.getProductDetailsList();
            if (list == null || list.isEmpty()) {
                problem = "Note Lock Pro isn't available from Google Play yet. A newly created "
                        + "product can take a few hours to appear. Try again later.";
                return;
            }
            problem = null;
            ProductDetails d = list.get(0);
            List<ProductDetails.OneTimePurchaseOfferDetails> offers = d.getOneTimePurchaseOfferDetailsList();
            if (offers == null || offers.isEmpty()) return;
            ProductDetails.OneTimePurchaseOfferDetails offer = offers.get(0);
            activity.runOnUiThread(() -> {
                details = d;
                offerToken = offer.getOfferToken();
                listener.onPrice(offer.getFormattedPrice());
            });
        });
    }

    /** Check Google Play for an existing purchase (also catches refunds). */
    void restore(boolean userAsked) {
        if (!ready) {
            if (userAsked) say("Couldn't reach Google Play. Check your connection and try again.");
            return;
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                if (userAsked) say("Couldn't check your purchase right now. Try again later.");
                return;
            }
            boolean found = false;
            for (Purchase p : purchases) {
                if (p.getProducts().contains(PRODUCT_ID)
                        && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    found = true;
                    acknowledge(p);
                }
            }
            final boolean has = found;
            activity.runOnUiThread(() -> {
                boolean before = owned(activity);
                setOwned(activity, has);
                if (before != has) listener.onProChanged(has);
                if (userAsked) {
                    listener.onMessage(has ? "Note Lock Pro restored."
                            : "No Pro purchase found for this Google account.");
                }
            });
        });
    }

    /** Ask Google Play for the price again if we don't have it yet (called when the screen opens). */
    void refreshProduct() {
        if (ready && details == null) loadProduct();
        else if (!ready) connect();
    }

    private boolean installedFromPlay() {
        try {
            String installer;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                installer = activity.getPackageManager()
                        .getInstallSourceInfo(activity.getPackageName()).getInstallingPackageName();
            } else {
                installer = activity.getPackageManager().getInstallerPackageName(activity.getPackageName());
            }
            return "com.android.vending".equals(installer);
        } catch (Exception e) {
            return true; // can't tell; don't blame the install source
        }
    }

    void buy() {
        if (details == null || offerToken == null) {
            if (!installedFromPlay()) {
                say("Purchases work only when Note Lock is installed from Google Play.");
            } else if (problem != null) {
                say(problem);
                refreshProduct();
            } else {
                say("Getting the price from Google Play. Try again in a few seconds.");
                refreshProduct();
            }
            return;
        }
        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .setOfferToken(offerToken)
                                .build()))
                .build();
        BillingResult r = client.launchBillingFlow(activity, flow);
        if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            say("Couldn't open Google Play checkout (" + r.getDebugMessage() + ").");
        }
    }

    private void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                if (!p.getProducts().contains(PRODUCT_ID)) continue;
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledge(p);
                    activity.runOnUiThread(() -> {
                        setOwned(activity, true);
                        listener.onProChanged(true);
                        listener.onMessage("Thank you! Chords and scales are unlocked.");
                    });
                } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    say("Payment pending. Pro unlocks as soon as it completes.");
                }
            }
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore(true);
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
            say("Purchase didn't go through (" + result.getDebugMessage() + ").");
        }
    }

    private void acknowledge(Purchase p) {
        if (p.isAcknowledged()) return;
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
    }

    private void say(String msg) {
        activity.runOnUiThread(() -> listener.onMessage(msg));
    }
}
