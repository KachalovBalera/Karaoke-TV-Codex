package ru.karaoketv.remote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/** Локальный HTTP/WebSocket-сервер. Слушает только выбранный локальный адрес ТВ. */
class LocalControlServer(
    private val context: Context,
    private val queueJson: () -> String,
    private val onQueueCommand: (String, Map<String, String>) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val clients = CopyOnWriteArraySet<Socket>()
    private var server: ServerSocket? = null
    var token: String = UUID.randomUUID().toString().replace("-", "")
        private set
    var adminToken: String = UUID.randomUUID().toString().replace("-", "")
        private set
    var port: Int = 0
        private set

    fun start() {
        if (server != null) return
        server = ServerSocket(0, 16, InetAddress.getByName(networkAddress()))
        port = server!!.localPort
        scope.launch {
            while (server != null) {
                runCatching { server?.accept()?.let { socket -> launch { handle(socket) } } }
            }
        }
    }

    fun stop() {
        server?.close()
        server = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
    }

    fun address(admin: Boolean = false): String = "http://${networkAddress()}:$port/?token=${if (admin) adminToken else token}"

    private fun networkAddress(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress ?: "127.0.0.1"
    }.getOrDefault("127.0.0.1")

    private fun handle(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII))
            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                line.substringBefore(':', "").takeIf { it.isNotEmpty() }?.let { key ->
                    headers[key.lowercase()] = line.substringAfter(':').trim()
                }
            }
            val target = requestLine.split(' ').getOrNull(1) ?: "/"
            val query = target.substringAfter('?', "").split('&').mapNotNull { part ->
                part.takeIf { it.contains('=') }?.let { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            }.toMap()
            val suppliedToken = query["token"]
            val isAdmin = suppliedToken == adminToken
            if (suppliedToken != token && !isAdmin) {
                writeResponse(client.getOutputStream(), 403, "Forbidden", "Неверный токен")
                return
            }
            if (headers["upgrade"]?.lowercase() == "websocket") {
                upgradeWebSocket(client, headers["sec-websocket-key"] ?: return)
                return
            }
            val path = target.substringBefore('?')
            when {
                path == "/api/queue/remove" && isAdmin -> {
                    query["id"]?.let { onQueueCommand("remove", mapOf("id" to it)) }
                    writeResponse(client.getOutputStream(), 200, "OK", "{}", "application/json")
                }
                path == "/api/queue/clear" && isAdmin -> {
                    onQueueCommand("clear", emptyMap())
                    writeResponse(client.getOutputStream(), 200, "OK", "{}", "application/json")
                }
                path == "/api/queue" -> writeResponse(client.getOutputStream(), 200, "OK", queueJson(), "application/json; charset=utf-8")
                path == "/" -> writeResponse(client.getOutputStream(), 200, "OK", html(isAdmin), "text/html; charset=utf-8")
                else -> writeResponse(client.getOutputStream(), 404, "Not Found", "Not found")
            }
        }
    }

    private fun upgradeWebSocket(socket: Socket, key: String) {
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
        )
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
        out.flush()
        clients += socket
        runCatching {
            while (!socket.isClosed) {
                val first = socket.getInputStream().read()
                if (first < 0) break
                // Управляющие сообщения от телефона обрабатываются отдельным HTTP API в следующей итерации.
                if ((first and 0x0F) == 8) break
            }
        }
        clients -= socket
    }

    fun broadcastQueueChanged() {
        val payload = queueJson().toByteArray(StandardCharsets.UTF_8)
        val frame = websocketTextFrame(payload)
        clients.forEach { client -> runCatching { client.getOutputStream().write(frame); client.getOutputStream().flush() }.onFailure { clients -= client } }
    }

    private fun websocketTextFrame(payload: ByteArray): ByteArray {
        require(payload.size <= 0xFFFF) { "WebSocket payload is too large" }
        val headerSize = if (payload.size <= 125) 2 else 4
        return ByteArray(headerSize + payload.size).also { frame ->
            frame[0] = 0x81.toByte()
            if (payload.size <= 125) {
                frame[1] = payload.size.toByte()
                payload.copyInto(frame, 2)
            } else {
                frame[1] = 126
                frame[2] = (payload.size ushr 8).toByte()
                frame[3] = payload.size.toByte()
                payload.copyInto(frame, 4)
            }
        }
    }

    private fun writeResponse(out: OutputStream, code: Int, phrase: String, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        out.write("HTTP/1.1 $code $phrase\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun html(admin: Boolean): String = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Karaoke TV</title><style>body{font:18px system-ui;background:#0d0b16;color:#fff;padding:20px}button{padding:12px;margin:4px;border-radius:8px}li{margin:12px 0}</style>
        <h1>Karaoke TV</h1><p>Подключено к телевизору</p>${if (admin) "<button id=clear>Очистить очередь</button>" else ""}<ul id="q"><li>Загрузка очереди…</li></ul>
        <script>
        const token=new URLSearchParams(location.search).get('token');
        const admin=${admin};
        async function refresh(){const response=await fetch('/api/queue?token='+encodeURIComponent(token));if(!response.ok)return;const list=document.getElementById('q');list.replaceChildren();const items=await response.json();if(!items.length){const empty=document.createElement('li');empty.textContent='Очередь пуста';list.append(empty);return;}items.forEach(item=>{const row=document.createElement('li');row.textContent=(item.position+1)+'. '+item.participant;if(admin){const button=document.createElement('button');button.textContent='Удалить';button.onclick=()=>fetch('/api/queue/remove?id='+item.id+'&token='+encodeURIComponent(token)).then(refresh);row.append(button);}list.append(row);});}
        if(admin)document.getElementById('clear').onclick=()=>fetch('/api/queue/clear?token='+encodeURIComponent(token)).then(refresh);
        refresh(); const ws=new WebSocket((location.protocol==='https:'?'wss':'ws')+'://'+location.host+'/?token='+encodeURIComponent(token)); ws.onmessage=refresh;
        </script>
    """.trimIndent()
}
