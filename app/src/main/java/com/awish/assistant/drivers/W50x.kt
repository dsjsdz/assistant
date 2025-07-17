package com.awish.assistant.drivers

import android.util.Log
import com.awish.assistant.modbus.ModbusManager
import com.licheedev.modbus4android.ModbusCallback
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse
import com.serotonin.modbus4j.msg.WriteRegisterResponse
import com.serotonin.modbus4j.msg.WriteRegistersResponse
import java.nio.charset.Charset

/**
 * W50x 驱动板操作类。
 */
class W50x(private val modbusManager: ModbusManager) {
  companion object {
    const val NAME = "Awish-W50x-v1.6"
  }

  /**
   * 读取硬件版本信息（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取版本、层数、列数等信息。
   * 调用结果通过回调返回 `WCReplyPara` 数据结构，包含版本号、层数和列数。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [WCReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readHardwareVersion { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "硬件版本: ${reply.version}, ${reply.count}层")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readHardwareVersion(addr: Int = 1, callback: (Result<W50xHCReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0001, // 起始寄存器地址
      2,      // 读取两个寄存器
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            // BCD 解码
            val yearBcd = data[0]
            val monthBcd = data[1]
            val trayHigh = data[2]
            val trayLow = data[3]

            val year = ((yearBcd.toInt() shr 4) * 10 + (yearBcd.toInt() and 0x0F)) + 2000
            val month = (monthBcd.toInt() shr 4) * 10 + (monthBcd.toInt() and 0x0F)
            val version =
              "20${year.toString().padStart(2, '0')}${month.toString().padStart(2, '0')}"

            val count = (trayHigh.toInt() shl 8) or (trayLow.toInt() and 0xFF)

            callback(Result.success(W50xHCReplyPara(version, count)))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询硬件版本失败", t)
          callback(Result.failure(Exception("查询失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取软件版本信息（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取版本信息。
   * 调用结果通过回调返回 `WCReplyPara` 数据结构，包含版本号。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [WCReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readSoftwareVersion { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "软件版本: ${reply.version}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readSoftwareVersion(addr: Int = 1, callback: (Result<W50xSVReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0002, // 软件版本号寄存器
      2,      // 读取2个寄存器（4字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            val year = bcdToInt(data[0])
            val month = bcdToInt(data[1])
            val day = bcdToInt(data[2])
            // 第4字节可能是保留字段，也可以忽略或继续显示
            val version = String.format("20%02d-%02d-%02d", year, month, day)
            callback(Result.success(W50xSVReplyPara(version)))
          } else {
            callback(Result.failure(Exception("返回数据格式异常")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询软件版本失败", t)
          callback(Result.failure(Exception("查询失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取称重数据（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取称重数据。
   * 调用结果通过回调返回 `WeighReplyPara` 数据结构，包含称重数据。
   *
   * @param para 请求参数，包含地址和通道数量
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [WeighReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readWeights(WeighRequestPara(1, 2)) { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "称重数据: ${reply.weights}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readWeights(para: WeighRequestPara, callback: (Result<WeighReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      para.addr,
      0x0003,                         // 寄存器地址
      para.channelCount,             // 读取通道数量（每通道占2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data == null || data.size < para.channelCount * 2) {
            callback(Result.failure(Exception("返回数据长度不足")))
            return
          }

          val weights = mutableListOf<Int>()
          for (i in 0 until para.channelCount) {
            val high = data[i * 2].toInt() and 0xFF
            val low = data[i * 2 + 1].toInt() and 0xFF
            val rawValue = (high shl 8) or low

            // 转换成有符号 short 再提升为 Int
            val weight = rawValue.toShort().toInt()
            weights.add(weight)
          }

          callback(Result.success(WeighReplyPara(weights)))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取称重数据失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取温湿度数据（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取温湿度数据。
   * 调用结果通过回调返回 `W50xTempReplyPara` 数据结构，包含温度和湿度数据。
   *
   * @param addr 地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [W50xTempReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readTemperatureAndHumidity(1) { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "温度:${reply.temperature} °C, 湿度:${reply.humidity} %")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readTemperatureAndHumidity(addr: Int = 1, callback: (Result<W50xTempReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0004, // 温湿度寄存器地址
      2,      // 读取两个寄存器（各 2 字节，共 4 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 2) {
              callback(Result.failure(Exception("返回数据不足")))
              return
            }

            val tempRaw = data[0].toInt()
            val humidRaw = data[1].toInt()

            if (tempRaw == 0x7FFF || humidRaw == 0x7FFF) {
              callback(Result.failure(Exception("传感器未连接或故障")))
              return
            }

            val temperature = tempRaw.toShort() / 10.0 // 有符号除以10
            val humidity = humidRaw.toShort() / 10.0

            callback(Result.success(W50xTempReplyPara(temperature, humidity)))
          } catch (e: Exception) {
            Log.e("Modbus", "温湿度解析异常", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取温湿度失败", tr)
          callback(Result.failure(Exception("读取温湿度失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取柜门状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取柜门状态。
   * 调用结果通过回调返回 `List<W50xDoorStatusPara>` 数据结构，包含柜门状态数据。
   *
   * @param addr 地址
   * @param count 柜门数量
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [List] 类型的柜门状态数据
   * - 失败时包含异常信息
   *
   * 使用示例：`readDoorStatus(1, 2) { result ->
   *     result.onSuccess { statusList ->
   *         Log.d("Modbus", "柜门状态:$statusList")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readDoorStatus(
    addr: Int = 1,
    count: Int = 2, // 柜门数量，每个柜门占2字节
    callback: (Result<List<W50xDoorStatusPara>>) -> Unit
  ) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0005,          // 柜门状态寄存器
      count,           // 每个柜门占用2个字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val raw = response?.data
            if (raw == null || raw.size < count * 2) {
              callback(Result.failure(Exception("返回数据不足")))
              return
            }

            val results = mutableListOf<W50xDoorStatusPara>()
            for (i in 0 until count) {
              val lock = raw[i * 2].toInt() and 0xFF
              val door = raw[i * 2 + 1].toInt() and 0xFF
              results.add(W50xDoorStatusPara(i + 1, lock, door))
            }

            callback(Result.success(results))
          } catch (e: Exception) {
            Log.e("Modbus", "柜门状态解析失败", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取柜门状态失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 IC 卡数据（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 IC 卡数据。
   * 调用结果通过回调返回 `List<W50xICCardPara>` 数据结构，包含 IC 卡数据。
   *
   * @param addr 地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [List] 类型的 IC 卡数据
   * - 失败时包含异常信息
   *
   * 使用示例：`readICCards(1) { result ->
   *     result.onSuccess { cardList ->
   *         Log.d("Modbus", "IC 卡数据:$cardList")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readICCards(addr: Int = 1, callback: (Result<List<W50xICCardPara>>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0006,     // IC 卡寄存器起始地址
      8,          // 读取 8 个寄存器 = 16 字节（2 个读卡器）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val data = response?.data
            if (data == null || data.size < 16) {
              callback(Result.failure(Exception("返回数据不足")))
              return
            }

            val results = mutableListOf<W50xICCardPara>()

            for (i in 0 until 2) { // 每个读卡器8字节
              val start = i * 8
              val cardBytes = data.sliceArray(start until (start + 8))
              val allZero = cardBytes.all { it == 0.toByte() }
              val cardHex = cardBytes.joinToString("") { "%02X".format(it) }
              // 转换成十进制
              // val cardDec = cardHex.toBigInteger(16).toString(10)
              results.add(
                W50xICCardPara(
                  readerIndex = i + 1,
                  cardHex = cardHex,
                  isPresent = !allZero
                )
              )
            }

            callback(Result.success(results))
          } catch (e: Exception) {
            Log.e("Modbus", "IC卡解析失败", e)
            callback(Result.failure(Exception("IC卡解析失败: ${e.message}")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取IC卡失败", t)
          callback(Result.failure(Exception("读取IC卡失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取按键数据（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取按键数据。
   * 调用结果通过回调返回 `W50xKeypadStatusPara` 数据结构，包含按键数据。
   *
   * @param addr 地址
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [W50xKeypadStatusPara] 类型的按键数据
   * - 失败时包含异常信息
   *
   * 使用示例：`readKeypad(1) { result ->
   *     result.onSuccess { keypadData ->
   *         Log.d("Modbus", "按键数据:$keypadData")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readKeypad(addr: Int = 1, callback: (Result<W50xKeypadStatusPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0007,     // 按键寄存器
      1,          // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val rawValue = response?.shortData?.firstOrNull()?.toInt() ?: 0
            val keysPressed = mutableListOf<Int>()

            for (i in 0 until 16) {
              if ((rawValue shr i) and 0x01 == 1) {
                keysPressed.add(i + 1) // 按键编号从 1 开始
              }
            }

            callback(Result.success(W50xKeypadStatusPara(keysPressed, rawValue)))
          } catch (e: Exception) {
            Log.e("Modbus", "按键解析失败", e)
            callback(Result.failure(Exception("按键解析失败: ${e.message}")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取按键失败", t)
          callback(Result.failure(Exception("读取按键失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 检查重量状态（异步）。
   *
   * 该方法通过 Modbus 协议检查重量状态。
   * 调用结果通过回调返回 `W50xWeightStatusPara` 数据结构，包含重量状态。
   *
   * @param addr 地址
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [W50xWeightStatusPara] 类型的重量状态
   * - 失败时包含异常信息
   *
   * 使用示例：`checkWeightReadCompleted(1) { result ->
   *     result.onSuccess { weightStatus ->
   *         Log.d("Modbus", "重量状态:$weightStatus")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "查询失败: ${e.message}")
   *     }
   * }`
   */
  fun checkWeightReadCompleted(addr: Int = 1, callback: (Result<W50xWeightStatusPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0008, // 寄存器地址
      1,      // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val value = response?.shortData?.firstOrNull()?.toInt() ?: 0
            val completed = value == 1
            callback(Result.success(W50xWeightStatusPara(completed, value)))
          } catch (e: Exception) {
            Log.e("Modbus", "重量状态解析失败", e)
            callback(Result.failure(Exception("重量状态解析失败: ${e.message}")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询重量状态失败", t)
          callback(Result.failure(Exception("查询重量状态失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取多个参数（异步）。
   *
   * 该方法通过 Modbus 协议读取多个参数。
   * 调用结果通过回调返回 `MultiReadResult` 数据结构，包含读取的参数。
   *
   * @param addr 地址
   * @param channelCount 通道数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [MultiReadResult] 类型的多个参数
   * - 失败时包含异常信息
   *
   * 使用示例：`readMultiParameters(1, 4) { result ->
   *     result.onSuccess { multiReadResult ->
   *         Log.d("Modbus", "多参数读取成功:$multiReadResult")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "多参数读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readMultiParameters(
    addr: Int = 1,
    channelCount: Int = 4,
    callback: (Result<MultiReadResult>) -> Unit
  ) {
    val readLength = channelCount + 9 // 总寄存器长度 = 9 + 通道数
    this.modbusManager.readHoldingRegisters(
      addr,
      0x0009,
      readLength,
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val rawBytes = response?.data ?: throw IllegalStateException("无数据返回")

            if (rawBytes.size < 2 + 8 + 8 + (channelCount * 2)) {
              throw IllegalArgumentException("返回数据长度不足")
            }

            val inputStatus =
              ((rawBytes[0].toInt() and 0xFF) shl 8) or (rawBytes[1].toInt() and 0xFF)
            val door1Lock = inputStatus and 0x0001 != 0
            val door1Closed = inputStatus and 0x0002 != 0
            val door2Lock = inputStatus and 0x0004 != 0
            val door2Closed = inputStatus and 0x0008 != 0

            val rfid1 = rawBytes.sliceArray(2 until 10)
            val rfid2 = rawBytes.sliceArray(10 until 18)

            val weights = mutableListOf<Int>()
            var offset = 18
            repeat(channelCount) {
              val high = rawBytes[offset].toInt() and 0xFF
              val low = rawBytes[offset + 1].toInt() and 0xFF
              val value = (high shl 8) or low
              weights.add(value)
              offset += 2
            }

            callback(
              Result.success(
                MultiReadResult(
                  door1Lock = door1Lock,
                  door1Closed = door1Closed,
                  door2Lock = door2Lock,
                  door2Closed = door2Closed,
                  rfid1 = rfid1,
                  rfid2 = rfid2,
                  weights = weights
                )
              )
            )
          } catch (e: Exception) {
            Log.e("Modbus", "多参数读取解析失败", e)
            callback(Result.failure(Exception("多参数解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "多参数读取失败", tr)
          callback(Result.failure(Exception("读取失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取多个参数（异步）。
   *
   * 该方法通过 Modbus 协议读取多个参数。
   * 调用结果通过回调返回 `MultiReadResult` 数据结构，包含读取的参数。
   *
   * @param addr 地址
   * @param channelCount 通道数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [WeightStabilityResult] 类型的多个参数
   * - 失败时包含异常信息
   *
   * 使用示例：`readMultiParameters(1, 4) { result ->
   *     result.onSuccess { multiReadResult ->
   *         Log.d("Modbus", "多参数读取成功:$multiReadResult")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "多参数读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readWeightStability(
    addr: Int = 1,
    channelCount: Int = 8,
    callback: (Result<WeightStabilityResult>) -> Unit
  ) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x000A,
      channelCount,
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val raw = response?.data ?: throw IllegalStateException("未返回数据")

            if (raw.size < channelCount * 2) {
              throw IllegalArgumentException("返回数据不足，通道数为 $channelCount")
            }

            val stability = mutableListOf<Boolean>()
            for (i in 0 until channelCount) {
              val offset = i * 2
              val high = raw[offset].toInt() and 0xFF
              val low = raw[offset + 1].toInt() and 0xFF
              val value = (high shl 8) or low
              stability.add(value == 0x0001)
            }

            callback(Result.success(WeightStabilityResult(stability)))
          } catch (e: Exception) {
            Log.e("Modbus", "稳定性解析失败", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 查询传感器状态（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送读取命令，获取传感器状态。
   * 调用结果通过回调返回 `Result` 对象，包含 [SensorStatusResult] 类型的数据。
   *
   * @param addr 从机地址
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [SensorStatusResult] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`querySensorStatus(0x01) { result ->
   *     result.onSuccess { data ->
   *         Log.d("Modbus", "获取传感器状态成功: $data")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "获取传感器状态失败: ${e.message}")
   *     }
   * }`
   */
  fun querySensorStatus(
    addr: Int = 1,
    callback: (Result<SensorStatusResult>) -> Unit
  ) {
    this.modbusManager.readHoldingRegisters(
      addr,
      0x000B,
      2,  // 读取2个寄存器 = 4字节 = 32 bit
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          try {
            val raw = response?.data ?: throw IllegalStateException("无响应数据")
            if (raw.size < 4) throw IllegalArgumentException("返回数据长度不足 4 字节")

            // 将4字节合成为一个Int类型（Big-Endian）
            val sensorBits = ((raw[0].toInt() and 0xFF) shl 24) or
              ((raw[1].toInt() and 0xFF) shl 16) or
              ((raw[2].toInt() and 0xFF) shl 8) or
              (raw[3].toInt() and 0xFF)

            val statusList = List(32) { i ->
              (sensorBits shr (31 - i)) and 1 == 1
            }

            callback(Result.success(SensorStatusResult(statusList)))
          } catch (e: Exception) {
            Log.e("Modbus", "解析传感器状态失败", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置波特率（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置波特率。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 从机地址
   * @param baudRate 波特率
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setBaudRate(0x01, 9600) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置波特率成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置波特率失败: ${e.message}")
   *     }
   * }`
   */
  fun setBaudRate(
    addr: Int = 1,
    baudRate: Int,
    callback: (Result<Unit>) -> Unit
  ) {
    val presetValue = when (baudRate) {
      9600 -> 0x0001
      19200 -> 0x0002
      38400 -> 0x0003
      57600 -> 0x0004
      115200 -> 0x0005
      else -> {
        callback(Result.failure(IllegalArgumentException("不支持的波特率: $baudRate")))
        return
      }
    }

    this.modbusManager.writeSingleRegister(
      addr,
      0x1001,
      presetValue,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response?.slaveId == addr &&
            response.writeOffset == 0x1001 &&
            response.writeValue == presetValue
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(IllegalStateException("驱动板应答异常，可能写入失败")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置控制板地址（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置控制板地址。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param currentAddr 当前控制板地址
   * @param newAddr 新控制板地址
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setControllerAddress(0x01, 0x02) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置控制板地址成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置控制板地址失败: ${e.message}")
   *     }
   * }`
   */
  fun setControllerAddress(
    currentAddr: Int = 1,             // 当前控制板地址
    newAddr: Int,
    callback: (Result<Unit>) -> Unit
  ) {
    // 地址范围必须是 1~255
    if (newAddr !in 1..255) {
      callback(Result.failure(IllegalArgumentException("地址必须在 1 到 255 范围内")))
      return
    }

    this.modbusManager.writeSingleRegister(
      currentAddr, // 当前地址
      0x1002,      // 地址寄存器
      newAddr,     // 新地址写入
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == currentAddr &&
            response.writeOffset == 0x1002 &&
            response.writeValue == newAddr
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 控制继电器（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，控制继电器。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param relayNumber 继电器编号：1=加热，2=制冷，3=灯光
   * @param isOn true=输出（闭合）
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`controlRelay(0x01, 1, true) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "控制继电器成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "控制继电器失败: ${e.message}")
   *     }
   * }`
   */
  fun controlRelay(
    addr: Int = 1,
    relayNumber: Int,        // 继电器编号：1=加热，2=制冷，3=灯光
    isOn: Boolean,           // true=输出（闭合），false=释放（断开）
    callback: (Result<Unit>) -> Unit
  ) {
    if (relayNumber !in 1..3) {
      callback(Result.failure(IllegalArgumentException("继电器编号应在1~3之间")))
      return
    }

    val value = (relayNumber shl 8) or if (isOn) 0x01 else 0x00
    // relayNumber 是高位字节，状态是低位字节，如 01 01

    this.modbusManager.writeSingleRegister(
      addr,
      0x1003,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1003 &&
            response.writeValue == value
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 控制门锁（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，控制门锁。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param lockNumber 门锁编号，如 1 号锁
   * @param isOpen true=开门，false=关门
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`controlDoorLock(0x01, 1, true) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "控制门锁成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "控制门锁失败: ${e.message}")
   *     }
   * }`
   */
  fun controlDoorLock(
    addr: Int = 1,
    lockNumber: Int = 1,      // 门锁编号，如 1 号锁
    isOpen: Boolean = true,      // true=开门，false=关门
    callback: (Result<Unit>) -> Unit
  ) {
    if (lockNumber !in 1..255) {
      callback(Result.failure(IllegalArgumentException("门锁编号应在1~255之间")))
      return
    }

    val value = (lockNumber shl 8) or if (isOpen) 0x01 else 0x00
    // 高字节是门锁编号，低字节是状态（00=关，01=开）

    this.modbusManager.writeSingleRegister(
      addr,
      0x1004,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1004 &&
            response.writeValue == value
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 零点校准（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，零点校准。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param channelNumber 货道编号
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`zeroCalibration(0x01, 1) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "零点校准成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "零点校准失败: ${e.message}")
   *     }
   * }`
   */
  fun zeroCalibration(
    addr: Int = 1,
    channelNumber: Int = 1,  // 货道编号
    callback: (Result<Unit>) -> Unit
  ) {
    if (channelNumber !in 0..0xFFFF) {
      callback(Result.failure(IllegalArgumentException("货道编号应在0~65535之间")))
      return
    }

    this.modbusManager.writeSingleRegister(
      addr,
      0x1005,
      channelNumber,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1005 &&
            response.writeValue == channelNumber
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置砝码重量（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置砝码重量。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param weightGrams 砝码重量，单位为克
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setCalibrationWeight(0x01, 100) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置砝码重量成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置砝码重量失败: ${e.message}")
   *     }
   * }`
   */
  fun setCalibrationWeight(
    addr: Int = 1,
    weightGrams: Int = 1000,
    callback: (Result<Unit>) -> Unit
  ) {
    if (weightGrams !in 0..0xFFFF) {
      callback(Result.failure(IllegalArgumentException("砝码重量应在 0~65535 范围内")))
      return
    }

    this.modbusManager.writeSingleRegister(
      addr,
      0x1006,
      weightGrams,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1006 &&
            response.writeValue == weightGrams
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 货道校准（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，货道校准。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param channel 货道编号
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`calibrateWeightChannel(0x01, 1) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "货道校准成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "货道校准失败: ${e.message}")
   *     }
   * }`
   */
  fun calibrateWeightChannel(
    addr: Int = 1,
    channel: Int = 1,
    callback: (Result<Unit>) -> Unit
  ) {
    if (channel !in 0..0xFFFF) {
      callback(Result.failure(IllegalArgumentException("货道编号必须在 0~65535 范围内")))
      return
    }

    this.modbusManager.writeSingleRegister(
      addr,
      0x1007,
      channel,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1007 &&
            response.writeValue == channel
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 清除价签（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，清除价签。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param tagId 价签编号
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`clearPriceTag(0x01, 1) { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "清除价签成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "清除价签失败: ${e.message}")
   *     }
   * }`
   */
  fun clearPriceTag(
    addr: Int = 1,
    tagId: Int = 1,
    callback: (Result<Unit>) -> Unit
  ) {
    if (tagId !in 0..0xFFFF) {
      callback(Result.failure(IllegalArgumentException("价签编号必须在 0~65535 范围内")))
      return
    }

    this.modbusManager.writeSingleRegister(
      addr,
      0x1008,
      tagId,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.writeOffset == 0x1008 &&
            response.writeValue == tagId
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置数字屏显示（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置数字屏显示。 数字屏编号默认为 1。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param screenId 数字屏编号
   * @param content 显示内容
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setDigitalTubeDisplay(0x01, 1, "12345678") { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置数字屏显示成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置数字屏显示失败: ${e.message}")
   *     }
   * }`
   */
  fun setDigitalTubeDisplay(
    addr: Int = 1,
    screenId: Int = 1,
    content: String = "12345678",
    callback: (Result<Unit>) -> Unit
  ) {
    val data = mutableListOf<Byte>()

    // 屏编号（2字节）
    data.add((screenId shr 8).toByte())
    data.add((screenId and 0xFF).toByte())

    // 显示内容（最大8字节）
    val maxLength = 8
    val contentBytes = content.take(maxLength).padStart(maxLength, '\u0000').toByteArray()
    data.addAll(contentBytes.toList())

    // 补齐偶数字节对齐
    if (data.size % 2 != 0) data.add(0x00)

    // 转为 ShortArray
    val values = ShortArray(data.size / 2) { i ->
      ((data[i * 2].toInt() and 0xFF) shl 8 or (data[i * 2 + 1].toInt() and 0xFF)).toShort()
    }

    val wordLength = (data.size + 1) / 2 // Modbus寄存器字长（2字节1个寄存器）

    this.modbusManager.writeRegisters(
      addr,
      0x2001,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.startOffset == 0x2001 &&
            response.numberOfRegisters == wordLength
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置汉字屏显示（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置汉字屏显示。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param screenId 汉字屏编号
   * @param bold true=加粗
   * @param reverse true=反显
   * @param row 起始行
   * @param column 起始列
   * @param text 显示内容
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setChineseDisplay(0x01, 1, true, false, 1, 1, "你好") { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置汉字屏显示成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置汉字屏显示失败: ${e.message}")
   *     }
   * }`
   */
  fun setChineseDisplay(
    addr: Int = 1,
    screenId: Int = 1,
    bold: Boolean = false,
    reverse: Boolean = false,
    row: Int = 1,
    column: Int = 1,
    text: String = "你好",
    callback: (Result<Unit>) -> Unit
  ) {
    val data = mutableListOf<Byte>()

    // 屏编号（2字节）
    data.add((screenId shr 8).toByte())
    data.add((screenId and 0xFF).toByte())

    // 显示模式（加粗/反显/起始行列）
    data.add(if (bold) 1 else 0)
    data.add(if (reverse) 1 else 0)
    data.add(row.toByte())
    data.add(column.toByte())

    // 汉字内容（GB2312 编码）
    val encoded = try {
      text.toByteArray(Charset.forName("GB2312"))
    } catch (e: Exception) {
      callback(Result.failure(Exception("GB2312 编码失败: ${e.message}")))
      return
    }

    data.addAll(encoded.toList())

    // 补齐偶数字节对齐
    if (data.size % 2 != 0) data.add(0x00)

    // 转换为 ShortArray
    val values = ShortArray(data.size / 2) { i ->
      ((data[i * 2].toInt() and 0xFF) shl 8 or (data[i * 2 + 1].toInt() and 0xFF)).toShort()
    }

    val registerAddress = 0x2002
    val wordLength = values.size

    this.modbusManager.writeRegisters(
      addr,
      registerAddress,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.startOffset == registerAddress &&
            response.numberOfRegisters == wordLength
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置语音播报（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送写入命令，设置语音播报。
   * 调用结果通过回调返回 `Result` 对象，包含 [Unit] 类型的数据。
   *
   * @param addr 控制板地址
   * @param content 播报内容
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [Unit] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`setVoiceBroadcast(0x01, "你好") { result ->
   *     result.onSuccess {
   *         Log.d("Modbus", "设置语音播报成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置语音播报失败: ${e.message}")
   *     }
   * }`
   */
  fun setVoiceBroadcast(
    addr: Int = 1,
    content: String = "欢迎光临",
    callback: (Result<Unit>) -> Unit
  ) {
    val data = mutableListOf<Byte>()

    // 汉字内容，编码为 GB2312（每个汉字 2 字节）
    val encoded = try {
      content.toByteArray(Charset.forName("GB2312"))
    } catch (e: Exception) {
      callback(Result.failure(Exception("GB2312 编码失败: ${e.message}")))
      return
    }

    // 最多支持 120 个汉字，即 240 字节
    val limited = encoded.take(240)

    // 如果是奇数字节数，补 0 保证偶数对齐
    val aligned = if (limited.size % 2 == 1) limited + listOf(0x00.toByte()) else limited

    data.addAll(aligned)

    // 转换为 ShortArray（每2字节为1个寄存器）
    val values = ShortArray(data.size / 2) { i ->
      ((data[i * 2].toInt() and 0xFF) shl 8 or (data[i * 2 + 1].toInt() and 0xFF)).toShort()
    }

    val registerAddress = 0x2003
    val wordLength = values.size

    this.modbusManager.writeRegisters(
      addr,
      registerAddress,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          if (response != null &&
            response.slaveId == addr &&
            response.startOffset == registerAddress &&
            response.numberOfRegisters == wordLength
          ) {
            callback(Result.success(Unit))
          } else {
            callback(Result.failure(Exception("驱动板应答异常")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("通信失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }
}

// BCD码转十进制整数
fun bcdToInt(bcd: Byte): Int {
  val value = bcd.toInt() and 0xFF
  return (value shr 4) * 10 + (value and 0x0F)
}

// 查询驱动板硬件版本
data class W50xHCReplyPara(
  val version: String, // 版本号
  val count: Int,     // 托盘数量
)

// 查询驱动板软件版本
data class W50xSVReplyPara(
  val version: String, // 软件版本号
)

// 读取称重数据(入参)
data class WeighRequestPara(
  val addr: Int = 1,       // 从机地址
  val channelCount: Int = 8 // 通道数量，最大支持127（因为每个通道占2字节）
)

// 读取称重数据(回参)
data class WeighReplyPara(
  val weights: List<Int>   // 每个通道的重量，单位：克（g），若为0x7FFF则代表通道故障
)

// 查询温湿度
data class W50xTempReplyPara(
  val temperature: Double, // 温度
  val humidity: Double, // 湿度
)

// 查询柜门开关状态
data class W50xDoorStatusPara(
  val index: Int,          // 柜门编号（从 1 开始）
  val lockStatus: Int,     // 0=锁关闭，1=锁打开，255=无效/故障
  val doorStatus: Int      // 0=门打开，1=门关闭，255=无效/故障
)

// 读取 IC 卡
data class W50xICCardPara(
  val readerIndex: Int,     // 读卡器编号（从 1 开始）
  val cardHex: String,      // 卡号（16进制字符串）
  val isPresent: Boolean    // 是否有卡（true=有卡，false=无卡）
)

// 按键读取
data class W50xKeypadStatusPara(
  val pressedKeys: List<Int>,  // 被按下的按键列表（1~16）
  val rawValue: Int            // 原始按键值（16位）
)

// 查询重量读完
data class W50xWeightStatusPara(
  val isCompleted: Boolean,
  val rawValue: Int
)

// 多参数一次性读取
data class MultiReadResult(
  val door1Lock: Boolean,
  val door1Closed: Boolean,
  val door2Lock: Boolean,
  val door2Closed: Boolean,
  val rfid1: ByteArray,
  val rfid2: ByteArray,
  val weights: List<Int>
)

// 读称重传感器稳定性
data class WeightStabilityResult(
  val stableStates: List<Boolean>  // true = 稳定, false = 不稳定
)

// 查询称重传感器状态
data class SensorStatusResult(
  val sensorOnline: List<Boolean>  // 长度为 32，true 表示正常，false 表示故障/未连接
)

enum class ModbusExceptionCode(val code: Byte, val description: String) {
  ILLEGAL_FUNCTION(0x01, "不合法功能代码"),
  ILLEGAL_DATA_ADDRESS(0x02, "不合法数据地址"),
  ILLEGAL_DATA_VALUE(0x03, "不合法数据值"),
  SLAVE_DEVICE_FAILURE(0x04, "从机设备故障"),
  ACKNOWLEDGE(0x05, "确认，设备处理中"),
  SLAVE_DEVICE_BUSY(0x06, "从机设备忙碌"),
  NEGATIVE_ACKNOWLEDGE(0x07, "从机执行错误"),
  MEMORY_PARITY_ERROR(0x08, "存储奇偶校验错误"),
  UNKNOWN(-1, "未知错误");

  companion object {
    fun fromCode(code: Byte): ModbusExceptionCode {
      return ModbusExceptionCode.entries.find { it.code == code } ?: UNKNOWN
    }
  }
}
