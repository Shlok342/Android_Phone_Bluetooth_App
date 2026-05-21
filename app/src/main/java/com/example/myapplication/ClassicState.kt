package com.example.myapplication

sealed class ClassicState {

    object IDLE : ClassicState()

    object CONNECTING : ClassicState()

    object CONNECTED : ClassicState()

    object DISCONNECTED : ClassicState()

    data class RECONNECTING(
        val attempt: Int
    ) : ClassicState()

    data class FAILED(
        val reason: FailureReason
    ) : ClassicState()
}

sealed class FailureReason {

    object Timeout : FailureReason()

    object ConnectionLost : FailureReason()

    object SocketClosed : FailureReason()

    object PermissionDenied : FailureReason()

    object MaxReconnectAttempts : FailureReason()

    data class Unknown(
        val message: String
    ) : FailureReason()
}