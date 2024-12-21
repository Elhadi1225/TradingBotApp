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

    fun connect(symbol: String = "EURUSD", emailAddress: String, emailPassword: String) {
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

    fun disconnect() {
        job?.cancel()
        job = null
    }

    private fun fetchMarketData(symbol: String): MarketData {
        // في الواقع، هنا سيتم الاتصال بـ MT5 لجلب البيانات الفعلية
        val price = 1.1000 // مثال
        val volume = 1000.0 // مثال
        
        priceHistory.add(price)
        volumeHistory.add(volume)
        
        if (priceHistory.size > 100) priceHistory.removeAt(0)
        if (volumeHistory.size > 100) volumeHistory.removeAt(0)

        return MarketData(
            symbol = symbol,
            price = price,
            volume = volume,
            rsi = calculateRSI(),
            macdLine = calculateMACD().first,
            signalLine = calculateMACD().second,
            upperBand = calculateBollingerBands().first,
            lowerBand = calculateBollingerBands().second,
            atr = calculateATR(),
            adx = calculateADX(),
            trendStrength = calculateTrendStrength(),
            supportLevel = findSupportLevel(),
            resistanceLevel = findResistanceLevel(),
            volumeRatio = calculateVolumeRatio()
        )
    }

    private fun analyzeMarket(data: MarketData) {
        // تحليل متعدد المستويات
        val trendSignal = analyzeTrend(data)
        val momentumSignal = analyzeMomentum(data)
        val volumeSignal = analyzeVolume(data)
        val supportResistanceSignal = analyzeSupportResistance(data)

        // تجميع الإشارات
        val signalStrength = (trendSignal + momentumSignal + volumeSignal + supportResistanceSignal) / 4.0

        val newSignal = when {
            signalStrength > 0.8 -> TradingSignal.STRONG_BUY
            signalStrength > 0.3 -> TradingSignal.BUY
            signalStrength < -0.8 -> TradingSignal.STRONG_SELL
            signalStrength < -0.3 -> TradingSignal.SELL
            else -> TradingSignal.NEUTRAL
        }

        if (_tradingSignal.value != newSignal) {
            _tradingSignal.value = newSignal
            calculateRiskParameters(data, newSignal)
            sendSignalAlert(newSignal, data)
        }
    }

    private fun analyzeTrend(data: MarketData): Double {
        // تحليل الاتجاه باستخدام ADX وقوة الاتجاه
        return when {
            data.adx > 25 && data.trendStrength > minimumTrendStrength -> 1.0
            data.adx < 20 || data.trendStrength < minimumTrendStrength -> -1.0
            else -> 0.0
        }
    }

    private fun analyzeMomentum(data: MarketData): Double {
        // تحليل الزخم باستخدام RSI و MACD
        val rsiSignal = when {
            data.rsi < 30 -> 1.0
            data.rsi > 70 -> -1.0
            else -> 0.0
        }

        val macdSignal = when {
            data.macdLine > data.signalLine -> 1.0
            data.macdLine < data.signalLine -> -1.0
            else -> 0.0
        }

        return (rsiSignal + macdSignal) / 2.0
    }

    private fun analyzeVolume(data: MarketData): Double {
        // تحليل الحجم
        return when {
            data.volumeRatio > minimumVolumeRatio -> 1.0
            data.volumeRatio < 1/minimumVolumeRatio -> -1.0
            else -> 0.0
        }
    }

    private fun analyzeSupportResistance(data: MarketData): Double {
        // تحليل مستويات الدعم والمقاومة
        return when {
            data.price < data.supportLevel -> 1.0
            data.price > data.resistanceLevel -> -1.0
            else -> 0.0
        }
    }

    private fun calculateRiskParameters(data: MarketData, signal: TradingSignal) {
        val atr = data.atr
        when (signal) {
            TradingSignal.STRONG_BUY, TradingSignal.BUY -> {
                val stopLoss = data.price - (atr * stopLossAtrMultiplier)
                val takeProfit = data.price + (atr * takeProfitAtrMultiplier)
                updateStopLossAndTakeProfit(stopLoss, takeProfit)
            }
            TradingSignal.STRONG_SELL, TradingSignal.SELL -> {
                val stopLoss = data.price + (atr * stopLossAtrMultiplier)
                val takeProfit = data.price - (atr * takeProfitAtrMultiplier)
                updateStopLossAndTakeProfit(stopLoss, takeProfit)
            }
            else -> {}
        }
    }

    private fun updateStopLossAndTakeProfit(stopLoss: Double, takeProfit: Double) {
        // تحديث وقف الخسارة وهدف الربح في MT5
    }

    private fun calculateRSI(): Double = 50.0 // تنفيذ حساب RSI الفعلي
    private fun calculateMACD(): Pair<Double, Double> = Pair(0.0, 0.0) // تنفيذ حساب MACD الفعلي
    private fun calculateBollingerBands(): Pair<Double, Double> = Pair(1.1100, 1.0900) // تنفيذ حساب Bollinger الفعلي
    private fun calculateATR(): Double = 0.001 // تنفيذ حساب ATR الفعلي
    private fun calculateADX(): Double = 25.0 // تنفيذ حساب ADX الفعلي
    private fun calculateTrendStrength(): Double = 30.0 // تنفيذ حساب قوة الاتجاه
    private fun findSupportLevel(): Double = 1.0950 // تنفيذ حساب مستوى الدعم
    private fun findResistanceLevel(): Double = 1.1050 // تنفيذ حساب مستوى المقاومة
    private fun calculateVolumeRatio(): Double = 1.5 // تنفيذ حساب نسبة الحجم

    private fun sendSignalAlert(signal: TradingSignal, data: MarketData) {
        val subject = "Trading Signal Alert: ${signal.name}"
        val body = """
            New Trading Signal: ${signal.name}
            Symbol: ${data.symbol}
            Price: ${data.price}
            
            Technical Indicators:
            RSI: ${data.rsi}
            MACD Line: ${data.macdLine}
            Signal Line: ${data.signalLine}
            ADX: ${data.adx}
            Trend Strength: ${data.trendStrength}
            Volume Ratio: ${data.volumeRatio}
            
            Price Levels:
            Upper Band: ${data.upperBand}
            Lower Band: ${data.lowerBand}
            Support: ${data.supportLevel}
            Resistance: ${data.resistanceLevel}
            
            Risk Management:
            ATR: ${data.atr}
            Suggested Stop Loss: ${data.price - (data.atr * stopLossAtrMultiplier)}
            Suggested Take Profit: ${data.price + (data.atr * takeProfitAtrMultiplier)}
            
            Time: ${Date()}
            
            IMPORTANT: This is an automated signal. Please verify with your own analysis.
        """.trimIndent()

        sendEmailAlert(subject, body)
    }

    private fun sendEmailAlert(subject: String, body: String) {
        try {
            val session = Session.getInstance(emailProps, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication("your-email@gmail.com", "your-app-password")
                }
            })

            MimeMessage(session).apply {
                setFrom(InternetAddress("your-email@gmail.com"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse("your-email@gmail.com"))
                setSubject(subject)
                setText(body)
                Transport.send(this)
            }
        } catch (e: Exception) {
            println("Error sending email: ${e.message}")
        }
    }
}
