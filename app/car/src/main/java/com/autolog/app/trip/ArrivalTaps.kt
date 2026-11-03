package com.autolog.app.trip

import com.autolog.app.purpose.ArrivalChoice
import kotlinx.coroutines.channels.Channel

/**
 * 도착 질문 팝업에서 누른 버튼을 화면(MainViewModel)에서 기록 서비스(TripRecorder)로 넘긴다.
 * 확정 뒤 전송 전 대기 중에 누른 '다시 선택'도 같이 넘긴다.
 * 앱에 하나만 둔다(AppContainer.arrivalTaps). 질문을 시작할 때 비워서 지난 질문에 늦게 누른 값이 다음 질문에 쓰이지 않게 한다.
 */
class ArrivalTaps {
    private val channel = Channel<ArrivalChoice>(Channel.CONFLATED)
    private val reselects = Channel<Unit>(Channel.CONFLATED)

    fun send(choice: ArrivalChoice) {
        channel.trySend(choice)
    }

    fun reselect() {
        reselects.trySend(Unit)
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) Unit
        while (reselects.tryReceive().isSuccess) Unit
    }

    suspend fun receive(): ArrivalChoice = channel.receive()

    suspend fun awaitReselect() = reselects.receive()
}
