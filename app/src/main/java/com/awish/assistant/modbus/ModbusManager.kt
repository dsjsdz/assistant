package com.awish.assistant.modbus

import com.licheedev.impl.rxjava.RxModbusWorker


class ModbusManager private constructor() : RxModbusWorker() {
  companion object {
    @Volatile
    private var instance: ModbusManager? = null
    fun get(): ModbusManager = instance ?: synchronized(this) {
      instance ?: ModbusManager().also { instance = it }
    }
  }

  @Synchronized
  override fun release() {
    super.release()
    instance = null
  }
}
