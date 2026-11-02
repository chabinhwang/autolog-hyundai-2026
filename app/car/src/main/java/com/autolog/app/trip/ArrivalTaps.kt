package com.autolog.app.trip

import com.autolog.app.purpose.ArrivalChoice
import kotlinx.coroutines.channels.Channel

/**
 * 도착 질문 팝업에서 누른 버튼을 화면(MainViewModel)에서 기록 서비스(TripRecorder)로 넘긴다.
 * 앱에 하나만 둔다(AppContainer.arrivalTaps). 질문을 시작할 때 비워서 지난 질문에 늦게 누른 값이 다음 질문에 쓰이지 않게 한다.
 */
class ArrivalTaps {
    private val channel = Channel<ArrivalChoice>(Channel.CONFLATED)

    fun send(choice: ArrivalChoice) {
        channel.trySend(choice)
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) Unit
    }

    suspend fun receive(): ArrivalChoice = channel.receive()
}
