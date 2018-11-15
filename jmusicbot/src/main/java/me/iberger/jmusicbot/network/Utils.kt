package me.iberger.jmusicbot.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import me.iberger.jmusicbot.MusicBot
import me.iberger.jmusicbot.exceptions.AuthException
import me.iberger.jmusicbot.exceptions.InvalidParametersException
import me.iberger.jmusicbot.exceptions.NotFoundException
import me.iberger.jmusicbot.exceptions.ServerErrorException
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.UnknownHostException

private const val GROUP_ADDRESS = "224.0.0.142"
private const val PORT = 42945
private const val LOCK_TAG = "enq_broadcast"

internal fun discoverHost(context: Context): Deferred<String> = MusicBot.mCRScope.async {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifiManager.createMulticastLock(LOCK_TAG)
    lock.acquire()
    try {
        MulticastSocket(PORT).use { socket ->
            val groupAddress = InetAddress.getByName(GROUP_ADDRESS)
            socket.joinGroup(groupAddress)
            socket.soTimeout = 4000
            val buffer = ByteArray(8)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.broadcast = true
            socket.receive(packet)
            socket.leaveGroup(groupAddress)
            MusicBot.hostAddress = "http://${packet.address.hostAddress}:$PORT/v1/"
            Timber.d(MusicBot.hostAddress)
            return@async MusicBot.hostAddress!!
        }
    } catch (e: IOException) {
        throw UnknownHostException("No valid hostname found")
    } finally {
        lock.release()
    }
}

internal fun <T> Response<T>.process(
    successCodes: List<Int> = listOf(200, 201, 204),
    errorCodes: Map<Int, Exception> = mapOf(),
    notFoundType: NotFoundException.Type = NotFoundException.Type.SONG,
    invalidParamsType: InvalidParametersException.Type = InvalidParametersException.Type.MISSING
): T {
    return when (this.code()) {
        in successCodes -> this.body()!!
        in errorCodes -> throw errorCodes[this.code()]!!
        400 -> throw InvalidParametersException(invalidParamsType)
        401 -> throw AuthException(AuthException.Reason.NEEDS_AUTH)
        403 -> throw AuthException(AuthException.Reason.NEEDS_PERMISSION)
        404 -> throw NotFoundException(notFoundType)
        else -> throw ServerErrorException(this.code())
    }
}