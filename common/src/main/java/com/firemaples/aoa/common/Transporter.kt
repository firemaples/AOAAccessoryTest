package com.firemaples.aoa.common

import java.io.IOException
import java.nio.ByteBuffer

abstract class Transporter(maxBufferSize: Int) {
    private val maxInputBuffers = 8

    init {
        val outputBuffer = ByteBuffer.allocate(maxBufferSize)
        val bufferPool = BufferPool(maxBufferSize, Protocol.MAX_ENVELOPE_SIZE, maxInputBuffers)
    }

    private var transportThread: TransportThread? = null

    fun startReading() {
        transportThread = TransportThread(this).apply {
            start()
        }
    }

    abstract fun ioClose()

    @Throws(IOException::class)
    abstract fun ioRead(buffer: ByteArray, offset: Int, count: Int): Int

    @Throws(IOException::class)
    abstract fun ioWrite(buffer: ByteArray, offset: Int, count: Int)

    private class TransportThread(val transporter: Transporter) : Thread("TransportThread") {
        override fun run() {
            loop()
            transporter.ioClose()
        }

        private fun loop() {

        }
    }
}