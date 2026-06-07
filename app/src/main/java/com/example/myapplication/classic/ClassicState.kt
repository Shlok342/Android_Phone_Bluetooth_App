package com.example.myapplication.classic

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

    object AuthenticationFailed : FailureReason()

    object PairingRejected : FailureReason()

    object BondingFailed : FailureReason()

    object DeviceRefusedConnection : FailureReason()

    data class Unknown(
        val message: String
    ) : FailureReason()
}