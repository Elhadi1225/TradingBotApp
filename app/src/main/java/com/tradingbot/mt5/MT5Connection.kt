package com.tradingbot.mt5

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import javax.mail.*
import javax.mail.internet.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class TradingSignal {
    STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
}

data class MarketData(
    val symbol: String,
    val price: Double,
    val volume: Double,
    val rsi: Double,
    val macdLine: Double,
    val signalLine: Double,
    val upperBand: Double,
    val lowerBand: Double,
    val atr: Double,
    val adx: Double,
    val trendStrength: Double,
    val supportLevel: Double,
    val resistanceLevel: Double,
    val volumeRatio: Double
)

class MT5Connection {
    private val _tradingSignal = MutableStateFlow<TradingSignal>(TradingSignal.NEUTRAL)
    val tradingSignal: StateFlow<TradingSignal> = _tradingSignal
    
    private val _marketData = MutableStateFlow<MarketData?>(null)
    val marketData: StateFlow<MarketData?> = _marketData

    // Technical Analysis Parameters
    private val rsiPeriod = 14
    private val macdFast = 12
    private val macdSlow = 26
    private val macdSignal = 9
    private val bollingerPeriod = 20
    private val bollingerDeviation = 2.0
    private val atrPeriod = 14
    private val adxPeriod = 14
    private val volumeRatioPeriod = 20

    // Risk Management
    private val maxRiskPerTrade = 0.02 // 2% من رأس المال
    private val stopLossAtrMultiplier = 1.5
    private val takeProfitAtrMultiplier = 2.5
    private val minimumTrendStrength = 25.0
    private val minimumVolumeRatio = 1.2

    // Email Configuration
    private val emailProps = Properties().apply {
        put("mail.smtp.host", "smtp.gmail.com")
        put("mail.smtp.port", "587")
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val priceHistory = mutableListOf<Double>()
    private val volumeHistory = mutableListOf<Double>()

    // تكوين البريد الإلكتروني
    private val userEmail = "hamidaneelhadi4@googlemail.com"

    fun connect(symbol: String = "EURUSD", emailPassword: String) {
        job?.cancel()
        job = scope.launch {
            try {
                while (isActive) {
                    val data = fetchMarketData(symbol)
                    _marketData.value = data
                    analyzeMarket(data)
                    delay(1000) // تحديث كل ثانية
                }
            } catch (e: Exception) {
                println("Error in market analysis: ${e.message}")
            }
        }
    }

    // ... (باقي الكود كما هو)

    private fun sendEmailAlert(subject: String, body: String) {
        try {
            val session = Session.getInstance(emailProps, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(userEmail, "your-app-password")
                }
            })

            MimeMessage(session).apply {
                setFrom(InternetAddress(userEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail))
                setSubject(subject)
                setText(body)
                Transport.send(this)
            }
        } catch (e: Exception) {
            println("Error sending email: ${e.message}")
        }
    }
}
