package com.ridego.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.tripworth.app.BuildConfig

/**
 * Loads and shows a rewarded ad. On success the caller grants +10 free rides.
 */
object RewardedAdManager {

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var loading = false

    fun initialize(context: Context) {
        MobileAds.initialize(context) { }
        preload(context)
    }

    fun preload(context: Context) {
        if (rewardedAd != null || loading) return
        loading = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                }
            }
        )
    }

    fun isReady(): Boolean = rewardedAd != null

    /**
     * Shows a rewarded ad if loaded. [onReward] runs only when the user earns
     * the reward; [onFailed] when the ad cannot be shown.
     */
    fun show(
        activity: Activity,
        onReward: () -> Unit,
        onFailed: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            preload(activity)
            onFailed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loading = false
                preload(activity)
                onFailed()
            }
        }

        ad.show(activity) {
            onReward()
        }
        rewardedAd = null
    }
}
