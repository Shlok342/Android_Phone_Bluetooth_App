package com.example.myapplication

interface MessageParser {
    var onMessageParsed: ((ClassicMessage) -> Unit)?
    fun feed(bytes: ByteArray, length: Int)
    fun reset()
}