package com.neon.calc

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var current = ""
    private var accumulator: Double? = null
    private var pendingOp = ""
    private var waitingForNumber = false
    private var justEvaluated = false

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)
        render()
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
            tvExpression.text = "${fmt(accumulator!!)} ${opSymbol(pendingOp)} ${fmt(value)} ="
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
        } else {
            ""
        }
    }

    companion object {
        private const val ERROR_TEXT = "Oops! \uD83D\uDE05"
    }
}
