package com.example.myapplication.classic.messages

interface MessageParser {

    var onMessageParsed:
            ((ClassicMessage) -> Unit)?

    fun feed(
        bytes: ByteArray,
        length: Int
    )

    fun reset()
}