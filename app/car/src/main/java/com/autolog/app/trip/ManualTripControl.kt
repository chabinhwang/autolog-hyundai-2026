package com.autolog.app.trip

import kotlinx.coroutines.channels.Channel

/**
 * 화면의 '운행 시작'·'운행 종료' 버튼을 기록 서비스(TripRecorder)로 넘긴다. 앱에 하나만 둔다(AppContainer.manualTrips).
 */
class ManualTripControl {
    enum class Command { START, STOP }

    private val channel = Channel<Command>(Channel.UNLIMITED)

    fun start() {
        channel.trySend(Command.START)
    }

    fun stop() {
        channel.trySend(Command.STOP)
    }

    suspend fun receive(): Command = channel.receive()
}
