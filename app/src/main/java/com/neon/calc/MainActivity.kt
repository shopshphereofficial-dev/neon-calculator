package com.neon.calc

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var current = ""
    private var accumulator: Double? = null
    private var pendingOp = ""
    private var waitingForNumber = false
    private var justEvaluated = false

    private var equalsCount = 0
    private var interstitialAd: InterstitialAd? = null

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)
        tvPreview = findViewById(R.id.tvPreview)

        MobileAds.initialize(this) { }
        findViewById<AdView>(R.id.adView).loadAd(AdRequest.Builder().build())
        loadInterstitialAd()

        restoreState()
        render()
        if (justEvaluated && tvExpression.text.isEmpty()) {
            tvExpression.text = tvResult.text
        }
    }

    override fun onStop() {
        super.onStop()
        saveState()
    }

    fun onDigit(view: View) {
        val d = view.tag.toString()
        if (isError()) reset()
        if (waitingForNumber) {
            current = ""
            waitingForNumber = false
        }
        if (current.replace("-", "").replace(".", "").length >= 12) return
        current = if (current == "0") d else current + d
        render()
    }

    fun onDecimal(view: View) {
        if (isError()) reset()
        if (waitingForNumber) {
            current = "0."
            waitingForNumber = false
        } else if (!current.contains(".")) {
            current = if (current.isEmpty() || current == "-") "0." else "$current."
        }
        render()
    }

    fun onOperator(view: View) {
        val op = view.tag.toString()
        if (isError()) reset()
        if (!waitingForNumber && current.isNotEmpty()) {
            val value = current.toDouble()
            accumulator = if (accumulator == null || pendingOp.isEmpty()) {
                value
            } else {
                applyOp(accumulator!!, value, pendingOp)
            }
            if (accumulator!!.isNaN() || accumulator!!.isInfinite()) {
                renderError()
                return
            }
        }
        if (accumulator != null) {
            pendingOp = op
            waitingForNumber = true
            justEvaluated = false
        }
        render()
    }

    fun onEquals(view: View) {
        if (isError()) return
        if (accumulator != null && pendingOp.isNotEmpty() && current.isNotEmpty()) {
            val value = current.toDouble()
            val result = applyOp(accumulator!!, value, pendingOp)
            if (result.isNaN() || result.isInfinite()) {
                renderError()
                return
            }
            accumulator = result
            current = fmt(result)
            pendingOp = ""
            waitingForNumber = true
            justEvaluated = true
            tvResult.text = fmt(result)
            // top expression line becomes the result, so the user can keep
            // calculating from it (e.g. press + again)
            tvExpression.text = fmt(result)
            tvPreview.text = ""
            maybeShowInterstitial()
            return
        }
        render()
    }

    fun onClear(view: View) {
        reset()
        render()
    }

    fun onBackspace(view: View) {
        if (isError()) {
            reset()
            render()
            return
        }
        if (waitingForNumber) return
        current = if (current.length <= 1 || (current.length == 2 && current.startsWith("-"))) {
            ""
        } else {
            current.dropLast(1)
        }
        render()
    }

    fun onPercent(view: View) {
        if (isError() || current.isEmpty()) return
        current = fmt(current.toDouble() / 100.0)
        render()
    }

    private fun maybeShowInterstitial() {
        equalsCount++
        if (equalsCount >= INTERSTITIAL_EVERY) {
            equalsCount = 0
            interstitialAd?.show(this)
            loadInterstitialAd()
        }
    }

    private fun loadInterstitialAd() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    // ---- persistence: remember the last calculation ----

    private fun saveState() {
        val p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        p.putString("current", current)
        p.putString("pendingOp", pendingOp)
        if (accumulator != null) {
            p.putString("accumulator", accumulator.toString())
        } else {
            p.remove("accumulator")
        }
        p.putBoolean("waiting", waitingForNumber)
        p.putBoolean("justEvaluated", justEvaluated)
        p.putString("expr", tvExpression.text.toString())
        p.apply()
    }

    private fun restoreState() {
        val p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = p.getString("current", "") ?: ""
        pendingOp = p.getString("pendingOp", "") ?: ""
        accumulator = p.getString("accumulator", null)?.toDoubleOrNull()
        waitingForNumber = p.getBoolean("waiting", false)
        justEvaluated = p.getBoolean("justEvaluated", false)
        val savedExpr = p.getString("expr", "") ?: ""
        if (savedExpr.isNotEmpty()) {
            tvExpression.text = savedExpr
        }
    }

    // ---- core helpers ----

    private fun applyOp(a: Double, b: Double, op: String): Double = when (op) {
        "+" -> a + b
        "-" -> a - b
        "*" -> a * b
        "/" -> a / b
        else -> b
    }

    private fun opSymbol(op: String): String = when (op) {
        "+" -> "+"
        "-" -> "\u2212"
        "*" -> "\u00D7"
        "/" -> "\u00F7"
        else -> op
    }

    private fun fmt(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return ERROR_TEXT
        val l = d.toLong()
        if (d == l.toDouble() && abs(l) < 1e15) return l.toString()
        return String.format(Locale.US, "%.9f", d).trimEnd('0').trimEnd('.')
    }

    private fun isError() = tvResult.text.toString() == ERROR_TEXT

    private fun renderError() {
        tvResult.text = ERROR_TEXT
        tvExpression.text = ""
        tvPreview.text = ""
        current = ""
        accumulator = null
        pendingOp = ""
        waitingForNumber = false
        justEvaluated = false
    }

    private fun reset() {
        current = ""
        accumulator = null
        pendingOp = ""
        waitingForNumber = false
        justEvaluated = false
        tvExpression.text = ""
    }

    private fun render() {
        val resultText = when {
            current.isNotEmpty() -> current
            accumulator != null -> fmt(accumulator!!)
            else -> "0"
        }
        tvResult.text = resultText
        tvExpression.text = if (accumulator != null && pendingOp.isNotEmpty()) {
            "${fmt(accumulator!!)} ${opSymbol(pendingOp)}"
        } else if (justEvaluated && current.isNotEmpty()) {
            current
        } else {
            ""
        }
        updatePreview()
    }

    // live preview: shows "= 10" below while the user is typing "5 + 5"
    private fun updatePreview() {
        if (accumulator != null && pendingOp.isNotEmpty() && !waitingForNumber && current.isNotEmpty()) {
            val r = applyOp(accumulator!!, current.toDouble(), pendingOp)
            tvPreview.text = if (r.isNaN() || r.isInfinite()) "" else "= ${fmt(r)}"
        } else {
            tvPreview.text = ""
        }
    }

    companion object {
        private const val ERROR_TEXT = "Oops! \uD83D\uDE05"
        private const val PREFS_NAME = "neon_calc_state"
        // Real AdMob interstitial ad unit ID
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-7206645274499834/4544638793"
        private const val INTERSTITIAL_EVERY = 5
    }
}
