package com.ridego.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ridego.app.data.AppState
import com.ridego.app.data.MonetizationState

/**
 * Google Play Billing for the weekly Premium subscription ([MonetizationState.WEEKLY_PRODUCT_ID]).
 */
class BillingManager(
    context: Context,
    private val onMessage: (String) -> Unit = {}
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            queryProductDetails()
            refreshPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    refreshPurchases()
                } else {
                    onMessage("billing_setup_failed:${result.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto-reconnect is enabled on the client.
            }
        })
    }

    fun destroy() {
        runCatching { billingClient.endConnection() }
    }

    fun launchWeeklyPurchase(activity: Activity) {
        ensureConnected {
            val details = productDetails
            if (details == null) {
                queryProductDetails {
                    val loaded = productDetails
                    if (loaded == null) {
                        onMessage("product_unavailable")
                    } else {
                        startFlow(activity, loaded)
                    }
                }
            } else {
                startFlow(activity, details)
            }
        }
    }

    fun restorePurchases() {
        ensureConnected { refreshPurchases() }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> onMessage("purchase_failed:${result.responseCode}")
        }
    }

    private fun startFlow(activity: Activity, details: ProductDetails) {
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
        if (offerToken.isNullOrBlank()) {
            onMessage("offer_unavailable")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun queryProductDetails(onDone: (() -> Unit)? = null) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(MonetizationState.WEEKLY_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList
                    .firstOrNull { it.productId == MonetizationState.WEEKLY_PRODUCT_ID }
            }
            onDone?.invoke()
        }
    }

    private fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val active = purchases.filter {
                it.products.contains(MonetizationState.WEEKLY_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (active.isEmpty()) {
                AppState.setPremiumFromPurchase(false)
            } else {
                active.forEach { handlePurchase(it) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(MonetizationState.WEEKLY_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Weekly subscription — treat as premium while Play reports it active.
        // Expiry is refreshed on each query; use a rolling week from purchase time
        // as a local hint when Play does not expose exact expiry without Real-Time API.
        val expiryHint = purchase.purchaseTime + SEVEN_DAYS_MS
        AppState.setPremiumFromPurchase(true, expiryHint)

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { }
        }
    }

    private fun ensureConnected(block: () -> Unit) {
        if (billingClient.isReady) {
            block()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    block()
                } else {
                    onMessage("billing_unavailable")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
