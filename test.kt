import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader

fun main() {
    val apiKey = System.getenv("GEMINI_API_KEY")
    val payload = """
    {
        "contents": [{"role": "user", "parts": [{"text": "Who is the prime minister of Burkina Faso?"}]}],
        "tools": [{"googleSearch": {}}]
    }
    """
    
    val models = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-3.1-flash-lite-preview", "gemini-3.5-flash", "gemini-1.5-pro")
    
    for (model in models) {
        println("Testing model: " + model)
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }
            
            val responseCode = conn.responseCode
            println("Response Code: " + responseCode)
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var line: String?
                val response = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                println("Success! Size: " + response.length)
            } else {
                val reader = BufferedReader(InputStreamReader(conn.errorStream))
                var line: String?
                val response = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                println("Error: " + response.toString())
            }
        } catch (e: Exception) {
            println("Exception: " + e.message)
        }
    }
}
