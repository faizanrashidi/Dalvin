package com.example.managers

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import com.example.core.WebTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class WeatherResult(
    val city: String,
    val temperatureC: Double,
    val windKmh: Double,
    val summary: String
)

class InternetManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun performSearch(query: String, target: WebTarget): String {
        val encoded = Uri.encode(query.trim())
        val intent = when (target) {
            WebTarget.GOOGLE -> Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query.trim())
            }
            WebTarget.YOUTUBE -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            )
            WebTarget.MAPS -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=$encoded")
            )
            WebTarget.PLAY_STORE -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
            )
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        return runCatching {
            context.startActivity(intent)
            "Searching ${target.name.lowercase().replace('_', ' ')} for '$query'."
        }.getOrElse {
            openUrl("https://www.google.com/search?q=$encoded")
        }
    }

    fun openUrl(rawUrl: String): String {
        val clean = rawUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened $clean"
        }.getOrElse {
            "No browser available to open URL: ${it.localizedMessage}"
        }
    }

    suspend fun fetchWeather(city: String): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(city)}&count=1&language=en&format=json"
            val geoJson = executeGet(geoUrl)
            val results = JSONObject(geoJson).optJSONArray("results")
                ?: throw IllegalStateException("City '$city' not found.")
            if (results.length() == 0) throw IllegalStateException("No coordinates found for '$city'.")
            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val resolvedName = place.optString("name", city)
            val country = place.optString("country", "")

            val wxUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val wxJson = JSONObject(executeGet(wxUrl))
            val current = wxJson.getJSONObject("current_weather")
            val temp = current.getDouble("temperature")
            val wind = current.getDouble("windspeed")
            val code = current.optInt("weathercode", 0)
            val desc = weatherCodeToLabel(code)

            WeatherResult(
                city = if (country.isNotBlank()) "$resolvedName, $country" else resolvedName,
                temperatureC = temp,
                windKmh = wind,
                summary = "$resolvedName weather: ${temp}°C, $desc, wind ${wind} km/h."
            )
        }
    }

    suspend fun fetchWikipediaSummary(topic: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = Uri.encode(topic.trim().replace(" ", "_"))
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$slug"
            val json = JSONObject(executeGet(url))
            val title = json.optString("title", topic)
            val extract = json.optString("extract", "").trim()
            if (extract.isEmpty()) throw IllegalStateException("No summary available for $topic.")
            "$title: $extract"
        }
    }

    suspend fun fetchTopHeadlines(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val xml = executeGet("https://feeds.bbci.co.uk/news/world/rss.xml")
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            val titles = mutableListOf<String>()
            var insideItem = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && titles.size < 6) {
                if (event == XmlPullParser.START_TAG) {
                    if (parser.name == "item") insideItem = true
                    else if (insideItem && parser.name == "title") {
                        titles.add(parser.nextText().trim())
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                    insideItem = false
                }
                event = parser.next()
            }
            if (titles.isEmpty()) throw IllegalStateException("No headlines parsed.")
            titles
        }
    }

    suspend fun measureConnectivityLatencyMs(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val req = Request.Builder().url("https://www.google.com/generate_204").head().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 204) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
            }
            System.currentTimeMillis() - start
        }
    }

    private fun executeGet(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "NovaVoiceAI/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IllegalStateException("Empty body")
        }
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable sky"
    }
}
