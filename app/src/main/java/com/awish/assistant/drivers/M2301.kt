package com.awish.assistant.drivers

import android.util.Log
import com.awish.assistant.modbus.ModbusManager
import com.licheedev.modbus4android.ModbusCallback
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse
import com.serotonin.modbus4j.msg.WriteRegisterResponse
import com.serotonin.modbus4j.msg.WriteRegistersResponse

/**
 * M2301 驱动板操作类。
 */
open class M2301(private val modbusManager: ModbusManager) {
  companion object {
    const val NAME = "Awish-M2301-v1.4"
  }

  /**
   * 读取硬件版本信息（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取版本、层数、列数等信息。
   * 调用结果通过回调返回 `HCReplyPara` 数据结构，包含版本号、层数和列数。
   *
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [HCReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readHardwareVersion { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "硬件版本: ${reply.version}, ${reply.row}层 ${reply.column}列")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readHardwareVersion(addr: Int = 1, callback: (Result<HCReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,  // 驱动板地址（SlaveId）
      0x0001,  // 起始地址 0x0001
      2,  // 读取长度：2个寄存器（4字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 2) {
              callback(Result.failure(Exception("硬件版本数据不足")))
              return
            }

            val bytes = ByteArray(4)
            bytes[0] = (data[0].toInt() shr 8).toByte()
            bytes[1] = data[0].toByte()
            bytes[2] = (data[1].toInt() shr 8).toByte()
            bytes[3] = data[1].toByte()

            val year = bcdToDecimal(bytes[0].toInt())
            val month = bcdToDecimal(bytes[1].toInt())
            val row = bcdToDecimal(bytes[2].toInt())
            val column = bcdToDecimal(bytes[3].toInt())

            val version =
              "20${year.toString().padStart(2, '0')}${month.toString().padStart(2, '0')}"

            val reply = HCReplyPara(version, row, column)
            callback(Result.success(reply))
          } catch (e: Exception) {
            Log.e("Modbus", "硬件版本解析异常", e)
            callback(Result.failure(Exception("硬件版本解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取硬件版本失败", tr)
          callback(Result.failure(Exception("读取硬件版本失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取软件版本信息（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取版本信息。
   * 调用结果通过回调返回 `SVReplyPara` 数据结构，包含版本号。
   *
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [SVReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readSoftwareVersion { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "软件版本: ${reply.version}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readSoftwareVersion(addr: Int = 1, callback: (Result<SVReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr, // 驱动板地址
      0x0002, // 起始地址 0x0002
      2, // 长度2个寄存器 = 4字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 2) {
              callback(Result.failure(Exception("数据不足")))
              return
            }

            val bytes = ByteArray(4)
            bytes[0] = (data[0].toInt() shr 8).toByte()
            bytes[1] = data[0].toByte()
            bytes[2] = (data[1].toInt() shr 8).toByte()
            bytes[3] = data[1].toByte()

            val year = bcdToDecimal(bytes[0].toInt())
            val month = bcdToDecimal(bytes[1].toInt())
            val day = bcdToDecimal(bytes[2].toInt())

            val version = "20${year.toString().padStart(2, '0')}${
              month.toString().padStart(2, '0')
            }${day.toString().padStart(2, '0')}"

            callback(Result.success(SVReplyPara(version)))
          } catch (e: Exception) {
            Log.e("Modbus", "解析异常", e)
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
   * 读取运行状态信息（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取运行状态信息。
   * 调用结果通过回调返回 `SSReplyPara` 数据结构，包含运行状态和故障代码。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [SSReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readShipmentStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "运行状态: ${reply.runStatus}, 故障代码: ${reply.errorCode}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readShipmentStatus(addr: Int = 1, callback: (Result<SSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,  // 从机地址
      0x0003,  // 起始地址 0x0003
      1,  // 读取 1 个寄存器（2 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val value = data[0].toInt() and 0xFFFF
            val runStatus = (value shr 8) and 0xFF  // 高位字节
            val errorCode = value and 0xFF           // 低位字节

            callback(Result.success(SSReplyPara(runStatus, errorCode)))
          } catch (e: Exception) {
            Log.e("Modbus", "解析异常", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取驱动板状态失败", tr)
          callback(Result.failure(Exception("读取失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取温湿度（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取温湿度信息。
   * 调用结果通过回调返回 `TempReplyPara` 数据结构，包含温度和湿度。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [TempReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readTemperatureAndHumidity { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "温度: ${reply.temperature}, 湿度: ${reply.humidity}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readTemperatureAndHumidity(addr: Int = 1, callback: (Result<TempReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,  // 从机地址
      0x0004,  // 起始地址 0x0004
      2,  // 读取两个寄存器（4 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 2) {
              callback(Result.failure(Exception("数据不足")))
              return
            }

            val tempRaw = data[0].toInt() and 0xFFFF
            val humidityRaw = data[1].toInt() and 0xFFFF

            val temperature = tempRaw / 10.0
            val humidity = humidityRaw / 10.0
            callback(Result.success(TempReplyPara(temperature, humidity)))
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
   * 查询格子柜状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取格子柜状态信息。
   * 调用结果通过回调返回 `BSReplyPara` 数据结构，包含格子柜状态和错误码。
   *
   * @param para 查询参数，包含格子柜地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [BSReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * getBoxStatus(BSRequestPara(addr = 1, code = 1)) { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "格子柜状态: ${reply.status}, 错误码: ${reply.code}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "查询失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun getBoxStatus(para: BSRequestPara, callback: (Result<BSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      para.addr,
      0x0005, // 寄存器地址
      para.code,      // 将编号传入，非标
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 2) {
            val code = data[0].toInt() and 0xFF
            val status = data[1].toInt() and 0xFF
            callback(Result.success(BSReplyPara(status = status, code = code)))
          } else {
            callback(Result.failure(Exception("返回数据格式异常")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询格子柜状态失败", t)
          callback(Result.failure(Exception("查询失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }


  /**
   * 读取 Y 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 Y 轴位置信息。
   * 调用结果通过回调返回 `CYReplyPara` 数据结构，包含 Y 轴位置。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [CYReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readYPos { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "Y 轴位置: ${reply.position}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun getYPos(addr: Int = 1, callback: (Result<CYReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,    // 从机地址
      0x0006,    // 起始地址 0x0006
      1,    // 读取 1 个寄存器（2 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val position = data[0].toInt() and 0xFFFF
            callback(Result.success(CYReplyPara(position)))
          } catch (e: Exception) {
            Log.e("Modbus", "电机位置解析异常", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取升降电机位置失败", tr)
          callback(Result.failure(Exception("读取升降电机位置失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 X 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 X 轴位置信息。
   * 调用结果通过回调返回 `CXReplyPara` 数据结构，包含 X 轴位置。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [CXReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readXPos { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "X 轴位置: ${reply.position}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun getXPos(addr: Int = 1, callback: (Result<CXReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,    // 从机地址
      0x0007,    // 起始地址 0x0007
      1,    // 读取 1 个寄存器（2 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            // 注意：data[0] 是 Kotlin Short，有符号 16 位，直接转换保留正负
            val position = data[0].toInt()
            callback(Result.success(CXReplyPara(position)))
          } catch (e: Exception) {
            Log.e("Modbus", "水平电机位置解析异常", e)
            callback(Result.failure(Exception("水平电机位置解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取水平电机位置失败", tr)
          callback(Result.failure(Exception("读取水平电机位置失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取掉货检测状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取掉货检测状态信息。
   * 调用结果通过回调返回 `DSReplyPara` 数据结构，包含掉货检测状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [DSReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readDropStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "掉货检测状态: ${reply.status}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readDropStatus(addr: Int = 1, callback: (Result<DSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,    // 从机地址（驱动板地址）
      0x0008,    // 起始地址 0x0008
      1,    // 读取 1 个寄存器（2 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val status = data[0].toInt() and 0xFFFF  // 保证为正整数
            callback(Result.success(DSReplyPara(status)))
          } catch (e: Exception) {
            Log.e("Modbus", "掉货检测解析异常", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取掉货检测状态失败", tr)
          callback(Result.failure(Exception("读取掉货检测状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 获取阵列状态（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送读取命令，获取阵列状态。
   * 调用结果通过回调返回 `Result` 对象，包含 [ASReplyPara] 类型的数据。
   *
   * @param addr 从机地址
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [ASReplyPara] 类型的数据
   * - 失败时包含异常信息
   *
   * 使用示例：`GetArrayStatus(0x01) { result ->
   *     result.onSuccess { data ->
   *         Log.d("Modbus", "获取阵列状态成功: $data")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "获取阵列状态失败: ${e.message}")
   *     }
   * }`
   */
  fun getArrayStatus(addr: Int = 1, callback: (Result<ASReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      1,    // 从机地址
      0x0009,    // 寄存器地址 0x0009
      2,    // 读取 2 个寄存器（共 4 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 2) {
              callback(Result.failure(Exception("数据读取失败：长度不足")))
              return
            }

            // 将两个 Short 拆成 4 字节（高位在前，Big Endian）
            val byteData = byteArrayOf(
              (data[0].toInt() shr 8).toByte(),
              (data[0].toInt() and 0xFF).toByte(),
              (data[1].toInt() shr 8).toByte(),
              (data[1].toInt() and 0xFF).toByte()
            )

            // 组装成 32 位整数
            val value = ((byteData[0].toInt() and 0xFF) shl 24) or
              ((byteData[1].toInt() and 0xFF) shl 16) or
              ((byteData[2].toInt() and 0xFF) shl 8) or
              (byteData[3].toInt() and 0xFF)

            // 提取各位状态
            fun bit(v: Int, i: Int) = if ((v shr i) and 1 == 1) 1 else 0

            val result = ASReplyPara(
              n0 = bit(value, 0), n1 = bit(value, 1), n2 = bit(value, 2),
              n3 = bit(value, 3), n4 = bit(value, 4), n5 = bit(value, 5),
              n6 = bit(value, 6), n7 = bit(value, 7), n8 = bit(value, 8),
              n9 = bit(value, 9),
              y0 = bit(value, 10), y1 = bit(value, 11), y2 = bit(value, 12),
              y3 = bit(value, 13), y4 = bit(value, 14), y5 = bit(value, 15),
              y6 = bit(value, 16), y7 = bit(value, 17), y8 = bit(value, 18),
              y9 = bit(value, 19)
            )

            callback(Result.success(result))
          } catch (e: Exception) {
            Log.e("Modbus", "阵列输出状态解析失败", e)
            callback(Result.failure(Exception("阵列输出状态解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取阵列输出状态失败", tr)
          callback(Result.failure(Exception("读取阵列输出状态失败: ${tr?.message}")))
        }

        override fun onFinally() {
        }
      }
    )
  }

  /**
   * 读取 X 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 X 轴位置信息。
   * 调用结果通过回调返回 `XPReplyPara` 数据结构，包含 X 轴位置。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [XPReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readXAxisPos { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "X 轴位置: ${reply}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readXAxisPos(addr: Int = 1, callback: (Result<XPReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,        // 从机地址
      0x0010,   // 寄存器地址 0x0010
      10,       // 读取10个寄存器（20字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 10) {
              callback(Result.failure(Exception("读取数据长度不足")))
              return
            }
            val xpReply = XPReplyPara(
              x0 = data[0].toInt() and 0xFFFF,
              x1 = data[1].toInt() and 0xFFFF,
              x2 = data[2].toInt() and 0xFFFF,
              x3 = data[3].toInt() and 0xFFFF,
              x4 = data[4].toInt() and 0xFFFF,
              x5 = data[5].toInt() and 0xFFFF,
              x6 = data[6].toInt() and 0xFFFF,
              x7 = data[7].toInt() and 0xFFFF,
              x8 = data[8].toInt() and 0xFFFF,
              x9 = data[9].toInt() and 0xFFFF
            )

            callback(Result.success(xpReply))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析X轴设定位置信息失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取X轴设定位置信息失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 IO 状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 IO 状态信息。
   * 调用结果通过回调返回 `IOReplyPara` 数据结构，包含 IO 状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [IOReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readIOStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "IO 状态: ${reply}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun getIOStatus(addr: Int = 1, callback: (Result<IOReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,  // 从机地址
      0x000A, // 寄存器地址0x000A
      1,  // 读取1个寄存器(2字节)
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val value = data[0].toInt() and 0xFFFF // 16 位无符号值

            val ioReply = IOReplyPara(
              io0 = if ((value and (1 shl 0)) != 0) 1 else 0,
              io1 = if ((value and (1 shl 1)) != 0) 1 else 0,
              io2 = if ((value and (1 shl 2)) != 0) 1 else 0,
              io3 = if ((value and (1 shl 3)) != 0) 1 else 0,
              io4 = if ((value and (1 shl 4)) != 0) 1 else 0,
              io5 = if ((value and (1 shl 5)) != 0) 1 else 0,
              io6 = if ((value and (1 shl 6)) != 0) 1 else 0,
              io7 = if ((value and (1 shl 7)) != 0) 1 else 0,
              io8 = if ((value and (1 shl 8)) != 0) 1 else 0,
              io9 = if ((value and (1 shl 9)) != 0) 1 else 0,
              io10 = if ((value and (1 shl 10)) != 0) 1 else 0,
              io11 = if ((value and (1 shl 11)) != 0) 1 else 0,
              io12 = if ((value and (1 shl 12)) != 0) 1 else 0,
            )

            callback(Result.success(ioReply))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析输入IO状态失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取输入IO状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 YIO 状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 YIO 状态信息。
   * 调用结果通过回调返回 `YioReplyPara` 数据结构，包含 YIO 状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [YioReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：
   * ```
   * readYIOStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "YIO 状态: ${reply.io0}, ${reply.io1}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }
   * ```
   */
  fun readYIOStatus(addr: Int = 1, callback: (Result<YioReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,        // 从机地址
      0x000B,   // 寄存器地址 0x000B
      1,        // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val value = data[0].toInt() and 0xFFFF // 转为无符号 16 位整型
            val ioReply = YioReplyPara(
              io0 = if ((value and (1 shl 0)) != 0) 1 else 0,
              io1 = if ((value and (1 shl 1)) != 0) 1 else 0,
              io2 = if ((value and (1 shl 2)) != 0) 1 else 0,
              io3 = if ((value and (1 shl 3)) != 0) 1 else 0,
              io4 = if ((value and (1 shl 4)) != 0) 1 else 0,
              io5 = if ((value and (1 shl 5)) != 0) 1 else 0,
              io6 = if ((value and (1 shl 6)) != 0) 1 else 0,
              io7 = if ((value and (1 shl 7)) != 0) 1 else 0,
            )

            callback(Result.success(ioReply))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取Y轴升降板IO状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 XIO 状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 XIO 状态信息。
   * 调用结果通过回调返回 `XioReplyPara` 数据结构，包含 XIO 状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [XioReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readXIOStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "XIO 状态: ${reply.io0}, ${reply.io1}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readXIOStatus(addr: Int = 1, callback: (Result<XioReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,       // 从机地址
      0x000C,  // 寄存器地址 0x000C
      1,       // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("数据为空")))
              return
            }

            val value = data[0].toInt() and 0xFFFF // 转为无符号 16 位整数
            val ioReply = XioReplyPara(
              io0 = if ((value and (1 shl 0)) != 0) 1 else 0,  // Bit0: 左限位
              io1 = if ((value and (1 shl 1)) != 0) 1 else 0,  // Bit1: 右限位
              io2 = if ((value and (1 shl 2)) != 0) 1 else 0,
              io3 = if ((value and (1 shl 3)) != 0) 1 else 0,
              io4 = if ((value and (1 shl 4)) != 0) 1 else 0,
              io5 = if ((value and (1 shl 5)) != 0) 1 else 0,
              io6 = if ((value and (1 shl 6)) != 0) 1 else 0,
              io7 = if ((value and (1 shl 7)) != 0) 1 else 0,
            )
            // NOTE: 从机地址错误
            callback(Result.success(ioReply))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取X轴电机IO状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 Y 状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 Y 状态信息。
   * 调用结果通过回调返回 `YSReplyPara` 数据结构，包含 Y 状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [YSReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readYStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "Y 状态: ${reply}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun getYStatus(addr: Int = 1, callback: (Result<YSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,       // 从机地址
      0x000D,  // 寄存器地址 0x000D
      1,       // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("读取数据为空")))
              return
            }
            val value = data[0].toInt() and 0xFFFF

            // 高8位是第1字节，低8位是第2字节
            val runStatus = (value shr 8) and 0xFF
            val faultCode = value and 0xFF

            callback(Result.success(YSReplyPara(runStatus, faultCode)))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析Y轴状态失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取Y轴状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 X 状态（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 X 状态信息。
   * 调用结果通过回调返回 `XSReplyPara` 数据结构，包含 X 状态。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [XSReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readXStatus { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "X 状态: ${reply}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun getXStatus(addr: Int = 1, callback: (Result<XSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      1,       // 从机地址
      0x000E,  // 寄存器地址 0x000E
      1,       // 读取1个寄存器（2字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.isEmpty()) {
              callback(Result.failure(Exception("读取数据为空")))
              return
            }
            val value = data[0].toInt() and 0xFFFF

            // 高8位是第1字节，低8位是第2字节
            val runStatus = (value shr 8) and 0xFF
            val faultCode = value and 0xFF
            callback(Result.success(XSReplyPara(runStatus, faultCode)))
          } catch (e: Exception) {
            Log.e("Modbus", "解析失败", e)
            callback(Result.failure(Exception("解析X轴状态失败: ${e.message}")))
          }
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "读取失败", tr)
          callback(Result.failure(Exception("读取X轴状态失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 读取 Y 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议从驱动板读取 Y 轴位置信息。
   * 调用结果通过回调返回 `YPReplyPara` 数据结构，包含 Y 轴位置。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 [YPReplyPara]
   * - 失败时包含异常信息
   *
   * 使用示例：`readYAxisPos { result ->
   *     result.onSuccess { reply ->
   *         Log.d("Modbus", "Y 轴位置: ${reply}")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "读取失败: ${e.message}")
   *     }
   * }`
   */
  fun readYAxisPos(addr: Int = 1, callback: (Result<YPReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      addr,        // 从机地址
      0x000F,   // 寄存器地址 0x000F
      10,       // 读取10个寄存器（20字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(t: ReadHoldingRegistersResponse?) {
          try {
            val data = t?.shortData
            if (data == null || data.size < 10) {
              callback(Result.failure(Exception("读取数据长度不足")))
              return
            }

            val para = YPReplyPara(
              y0 = data[0].toInt() and 0xFFFF,
              y1 = data[1].toInt() and 0xFFFF,
              y2 = data[2].toInt() and 0xFFFF,
              y3 = data[3].toInt() and 0xFFFF,
              y4 = data[4].toInt() and 0xFFFF,
              y5 = data[5].toInt() and 0xFFFF,
              y6 = data[6].toInt() and 0xFFFF,
              y7 = data[7].toInt() and 0xFFFF,
              y8 = data[8].toInt() and 0xFFFF,
              y9 = data[9].toInt() and 0xFFFF
            )

            callback(Result.success(para))
          } catch (e: Exception) {
            callback(Result.failure(Exception("解析Y轴设定位置信息失败: ${e.message}")))
            Log.e("Modbus", "解析失败", e)
          }
        }

        override fun onFailure(tr: Throwable?) {
          callback(Result.failure(Exception("读取Y轴设定位置信息失败: ${tr?.message}")))
          Log.e("Modbus", "读取失败", tr)
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 控制继电器（异步）。
   *
   * @param para 继电器控制参数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`controlRelay(CRPReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "继电器控制成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "继电器控制失败: ${e.message}")
   *     }
   * }`
   */
  fun controlRelay(para: ControlRelayPara, callback: (Result<Boolean>) -> Unit) {
    val value = ((para.no and 0xFF) shl 8) or (para.state and 0xFF)

    this.modbusManager.writeSingleRegister(
      para.addr,  // 从机地址
      0x1001,
      value.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(t: WriteRegisterResponse?) {
          Log.d("Modbus", "继电器控制成功：$t")
          callback(Result.success(true))
        }

        override fun onFailure(tr: Throwable?) {
          Log.e("Modbus", "继电器控制失败", tr)
          callback(Result.failure(Exception("继电器控制失败: ${tr?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 复位升降机（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送复位命令，复位升降机。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param addr 驱动板地址
   * @param callback 回调函数，返回一个 [Result] 类型：
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`ResetLift { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "升降机复位成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "升降机复位失败: ${e.message}")
   *     }
   * }`
   */
  fun resetLift(addr: Int = 1, callback: (Result<Boolean>) -> Unit) {
    this.modbusManager.writeSingleRegister(
      addr,
      0x1002,
      0x0001,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "升降机复位成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "升降机复位失败", t)
          callback(Result.failure(Exception("升降机复位失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置电机手动速度模式（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置电机手动速度模式。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 mode 和 status
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`RunMoto(RMReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "电机手动速度模式设置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "电机手动速度模式设置失败: ${e.message}")
   *     }
   * }`
   */
  fun runMoto(para: RMReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 数据格式：高字节为 mode，低字节为 status
    val value = ((para.mode and 0xFF) shl 8) or (para.status and 0xFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1003,
      value.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "电机手动速度模式设置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "电机手动速度模式设置失败", t)
          callback(Result.failure(Exception("电机手动速度模式设置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置电机超时时间（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置电机超时时间。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 duration
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`setMotoTimeout(MTReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置电机超时时间成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置电机超时时间失败: ${e.message}")
   *     }
   * }`
   */
  fun setMotoTimeout(para: MTReplyPara, callback: (Result<Boolean>) -> Unit) {
    val timeValue = para.duration.coerceIn(0, 0xFFFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1004,
      timeValue.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "设置电机超时时间成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置电机超时时间失败", t)
          callback(Result.failure(Exception("设置电机超时时间失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置取货口 Y 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置取货口 Y 轴位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`setPickupYPos(PYReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置取货口 Y 轴位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置取货口 Y 轴位置失败: ${e.message}")
   *     }
   * }`
   */
  fun setPickupYPos(para: PYReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 限制为 16 位无符号整数
    val posValue = para.pos.coerceIn(0, 0xFFFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1005,
      posValue.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "设置取货口 Y 轴位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置取货口 Y 轴位置失败", t)
          callback(Result.failure(Exception("设置取货口 Y 轴位置失败: ${t?.message}"))) // TODO: c
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置取货口 X 轴位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置取货口 X 轴位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`setPickupXPosition(PXReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置取货口 X 轴位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置取货口 X 轴位置失败: ${e.message}")
   *     }
   * }`
   */
  fun setPickupXPosition(para: PXReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 限制为 16 位无符号整数
    val posValue = para.pos.coerceIn(0, 0xFFFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1006,
      posValue.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "设置取货口 X 轴位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置取货口 X 轴位置失败", t)
          callback(Result.failure(Exception("设置取货口 X 轴位置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置 Y 轴电机寻址位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置 Y 轴电机寻址位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`toY(TYReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置 Y 轴电机寻址位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置 Y 轴电机寻址位置失败: ${e.message}")
   *     }
   * }`
   */
  fun toY(para: TYReplyPara, callback: (Result<Boolean>) -> Unit) {
    val posValue = para.pos.coerceIn(0, 0xFFFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1007,
      posValue.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "设置 Y 轴电机寻址位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置 Y 轴电机寻址位置失败", t)
          callback(Result.failure(Exception("设置 Y 轴电机寻址位置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置 X 轴电机寻址位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置 X 轴电机寻址位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`toX(TXReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置 X 轴电机寻址位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置 X 轴电机寻址位置失败: ${e.message}")
   *     }
   * }`
   */
  fun toX(para: TXReplyPara, callback: (Result<Boolean>) -> Unit) {
    val posValue = para.pos.coerceIn(0, 0xFFFF)

    this.modbusManager.writeSingleRegister(
      para.addr,
      0x1008,
      posValue.toShort().toInt(),
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          Log.d("Modbus", "设置 X 轴电机寻址位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置 X 轴电机寻址位置失败", t)
          callback(Result.failure(Exception("设置 X 轴电机寻址位置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 运行货道（异步）。
   *
   * @param para 货道参数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`runShipment(SReplyPara(1, FloorType.B, false, false)) { result ->
   *     result.onSuccess { data ->
   *         Log.d("Modbus", "运行货道成功: $data")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "运行货道失败: ${e.message}")
   *     }
   * }`
   */
  fun runShipment(para: SReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 构造 Modbus 数据内容（4 字节）
    // 第1字节：货道编号
    // 第2字节：货道类型
    // 第3字节：掉货检测（0/1）
    // 第4字节：升降机（0/1）
    val bytes = byteArrayOf(
      (para.code % 100).toByte(),
      para.floorType.code.toByte(),
      if (para.isDc) 1 else 0,
      if (para.isLp) 1 else 0
    )

    // 将4字节数据打包成2个Short（16位）寄存器
    val values = shortArrayOf(
      ((bytes[0].toInt() and 0xFF) shl 8 or (bytes[1].toInt() and 0xFF)).toShort(),
      ((bytes[2].toInt() and 0xFF) shl 8 or (bytes[3].toInt() and 0xFF)).toShort()
    )

    this.modbusManager.writeRegisters(
      para.addr,
      0x2001,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "出货指令发送成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "出货指令发送失败", t)
          callback(Result.failure(Exception("出货指令发送失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置 Y 轴电机寻址位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置 Y 轴电机寻址位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`setYPos(SYPReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置 Y 轴电机寻址位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置 Y 轴电机寻址位置失败: ${e.message}")
   *     }
   * }`
   */
  fun setYPos(para: SYPReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 构造写入的寄存器数据（10 个数值）
    val values = shortArrayOf(
      para.y0.toShort(),
      para.y1.toShort(),
      para.y2.toShort(),
      para.y3.toShort(),
      para.y4.toShort(),
      para.y5.toShort(),
      para.y6.toShort(),
      para.y7.toShort(),
      para.y8.toShort(),
      para.y9.toShort()
    )

    this.modbusManager.writeRegisters(
      para.addr,
      0x2002,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "设置升降电机Y轴寻址位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置升降电机Y轴寻址位置失败", t)
          callback(Result.failure(Exception("设置升降电机Y轴寻址位置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置 X 轴电机寻址位置（异步）。
   *
   * 该方法通过 Modbus 协议向驱动板发送设置命令，设置 X 轴电机寻址位置。
   * 调用结果通过回调返回 `Boolean` 值，表示是否成功。
   *
   * @param para 设置参数，包含 pos
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`SetXPos(SXPReplyPara(0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "设置 X 轴电机寻址位置成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "设置 X 轴电机寻址位置失败: ${e.message}")
   *     }
   * }`
   */
  fun setXPos(para: SXPReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 将 Int 转换为 Short（无符号处理，确保数值范围在 0~65535）
    val values = shortArrayOf(
      para.x0.toShort(),
      para.x1.toShort(),
      para.x2.toShort(),
      para.x3.toShort(),
      para.x4.toShort(),
      para.x5.toShort(),
      para.x6.toShort(),
      para.x7.toShort(),
      para.x8.toShort(),
      para.x9.toShort()
    )

    this.modbusManager.writeRegisters(
      para.addr,
      0x2003,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "设置水平电机X轴寻址位置成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置水平电机X轴寻址位置失败", t)
          callback(Result.failure(Exception("设置水平电机X轴寻址位置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 控制阵列输出（异步）。
   *
   * @param para 阵列输出参数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`setArrayOutputControl(AOReplyPara(arrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))) { result ->
   *     result.onSuccess { data ->
   *         Log.d("Modbus", "控制阵列输出成功: $data")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "控制阵列输出失败: ${e.message}")
   *     }
   * }`
   */
  fun setArrayOutputControl(para: AOReplyPara, callback: (Result<Boolean>) -> Unit) {
    // 1. 构造32位通道状态值
    var channelStatus = 0
    // 负极 1-10 位
    for (i in 0 until 10) {
      if (para.arr[i] == 1) {
        channelStatus = channelStatus or (1 shl i)
      }
    }
    // 正极 11-20 位 (bit 10~19)
    for (i in 10 until 20) {
      if (para.arr[i] == 1) {
        channelStatus = channelStatus or (1 shl i)
      }
    }

    // 2. 构造写寄存器的值数组（6个 16位寄存器）：
    // 寄存器 0: time 高8位 + 低8位（16位）
    // 寄存器 1 & 2: channelStatus 32位拆成两个16位寄存器（高位在前）

    val timeHigh = (para.time shr 8) and 0xFF
    val timeLow = para.time and 0xFF
    val time16 = (timeHigh shl 8) or timeLow

    val chHigh = (channelStatus shr 16) and 0xFFFF
    val chLow = channelStatus and 0xFFFF

    // values 是写寄存器的 16位数组
    intArrayOf(
      time16,
      0x0000,  // 说明协议中写3个寄存器(6字节)，这里用0占位一个寄存器？
      chHigh,
      chLow
    )

    // 发送时如果需要写3个寄存器，values 长度应该是3
    // 你说“00 64 00 00 04 01” 其实是 3 个寄存器，每个16位2字节：
    // 0x0064 (time=100ms), 0x0000 (保留), 0x0401 (通道状态)

    // 由于你读取长度是3个寄存器，那么 values 长度应为3
    // 你的 channelStatus 只用低16位展示了？示例“00000401” 其实是16位的

    // 按照示例，你可以写成3个寄存器：
    // 寄存器0：time
    // 寄存器1：保留 0
    // 寄存器2：通道状态（16位）

    val finalValues = shortArrayOf(
      para.time.toShort(),        // 有效时间（16位）
      0x0000.toShort(),           // 保留字段
      (channelStatus and 0xFFFF).toShort()  // 通道状态（只用了低16位）
    )

    this.modbusManager.writeRegisters(
      para.addr,
      0x2004,
      finalValues,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "控制驱动板阵列输出成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "控制驱动板阵列输出失败", t)
          callback(Result.failure(Exception("控制驱动板阵列输出失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 控制升降板输出（异步）。
   *
   * @param para 升降板控制参数
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 `true`
   * - 失败时包含异常信息
   *
   * 使用示例：`controlLiftBoardOutput(DCReplyPara(0x01, 0x01, 0x01, 0x01)) { result ->
   *     result.onSuccess { success ->
   *         Log.d("Modbus", "升降板驱动输出控制成功")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "升降板驱动输出控制失败: ${e.message}")
   *     }
   * }`
   */
  fun controlLiftBoardOutput(para: DCReplyPara, callback: (Result<Boolean>) -> Unit) {
    val values = shortArrayOf(
      ((para.no and 0xFF) shl 8 or (para.mode and 0xFF)).toShort(),        // 第1寄存器
      ((para.channel and 0xFF) shl 8 or (para.status and 0xFF)).toShort(), // 第2寄存器
      para.time.toShort()
    )

    this.modbusManager.writeRegisters(
      para.addr,
      0x2005,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "升降板驱动输出控制成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "升降板驱动输出控制失败", t)
          callback(Result.failure(Exception("升降板驱动输出控制失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }
}

// 查询驱动板硬件版本
data class HCReplyPara(
  val version: String, // 硬件版本号
  val row: Int, // 货道层数
  val column: Int, // 货道列数
)

// 查询驱动板软件版本
data class SVReplyPara(
  val version: String, // 软件版本号
)

// 查询驱动板出货状态
data class SSReplyPara(
  val runStatus: Int, // 运行状态
  val faultCode: Int, // 故障码
)

// 查询温湿度
data class TempReplyPara(
  val temperature: Double, // 温度
  val humidity: Double, // 湿度
)

data class BSRequestPara(
  val addr: Int = 1, // 从机地址
  val code: Int, // 格子柜编号
)

// 查询格子柜当前状态
data class BSReplyPara(
  val status: Int, // 0- 打开状态, 1- 关闭状态
  val code: Int, // 格子柜编号
)

// 查询掉货检测状态
data class DSReplyPara(
  val status: Int, // 0- 掉货检测未连接或者被遮挡，1- 正常无遮挡
)

// 读取 Y 轴升降板输入 IO 状态
data class YioReplyPara(
  val io0: Int, // 0- 无效，1- 有效
  val io1: Int,
  val io2: Int,
  val io3: Int,
  val io4: Int,
  val io5: Int,
  val io6: Int,
  val io7: Int,
)

// 读取 X 轴移动电机输入 IO 状态
data class XioReplyPara(
  val io0: Int, // 0- 无效，1- 有效
  val io1: Int,
  val io2: Int,
  val io3: Int,
  val io4: Int,
  val io5: Int,
  val io6: Int,
  val io7: Int,
)

// 查询升降电机当前位置
data class CYReplyPara(
  val value: Int, // 当前升降机位置
)

// 查询水平电机当前位置
data class CXReplyPara(
  val value: Int, // 当前移动机位置
)

// 读取驱动板输入 IO 状态
data class IOReplyPara(
  val io0: Int, // 0- 无效，1- 有效
  val io1: Int,
  val io2: Int,
  val io3: Int,
  val io4: Int,
  val io5: Int,
  val io6: Int,
  val io7: Int,
  val io8: Int,
  val io9: Int, // 0-9: 表示弹簧电机反馈输入 1~10 通道状态
  val io10: Int, // 掉货检测口状态
  val io11: Int, // 预留输入口状态
  val io12: Int, // 按钮状态
)

// 读取 Y 轴升降电机控制板状态
data class YSReplyPara(
  val runStatus: Int, // 运行状态
  val faultCode: Int, // 故障码
)

// 读取 X 轴移动电机控制板状态
data class XSReplyPara(
  val runStatus: Int, // 运行状态
  val faultCode: Int, // 故障码
)

// 读取 X 轴设定位置信息
data class XPReplyPara(
  val x0: Int, // 对应 X0~X9 对应的列位置信息
  val x1: Int,
  val x2: Int,
  val x3: Int,
  val x4: Int,
  val x5: Int,
  val x6: Int,
  val x7: Int,
  val x8: Int,
  val x9: Int
)

// 读取 Y 轴设定位置信息
data class YPReplyPara(
  val y0: Int, // 对应 Y0~Y9 对应的层位置信息
  val y1: Int,
  val y2: Int,
  val y3: Int,
  val y4: Int,
  val y5: Int,
  val y6: Int,
  val y7: Int,
  val y8: Int,
  val y9: Int
)

// 电机手动模式
data class RMReplyPara(
  val addr: Int = 1, // 从机地址
  val mode: Int, // 0—升降电机  1—水平电机
  val status: Int, // 0—停止运行，1—电机正转，2-电机反转
)

// 设置电机超时时间
data class MTReplyPara(
  val addr: Int = 1, // 从机地址
  val duration: Int, // 电机超时时间，单位：mS
)

// 设置取货口 Y 轴位置
data class PYReplyPara(
  val addr: Int = 1, // 从机地址
  val pos: Int, // 取货口 Y 轴位置
)

// 设置取货口 X 轴位置位
data class PXReplyPara(
  val addr: Int = 1, // 从机地址
  val pos: Int, // 取货口 X 轴位置
)

// X 轴电机寻址
data class TXReplyPara(
  val addr: Int = 1, // 从机地址
  val pos: Int, // 设定 X 轴寻址位置
)

data class TYReplyPara(
  val addr: Int = 1, // 从机地址
  val pos: Int, // 设定 X 轴寻址位置
)

// 设置升降电机 Y 轴寻址位置
data class SYPReplyPara(
  val addr: Int = 1, // 从机地址
  val y0: Int = 8000, // 1~10 层的高度位置信息
  val y1: Int = 7000,
  val y2: Int = 6000,
  val y3: Int = 5000,
  val y4: Int = 4000,
  val y5: Int = 3000,
  val y6: Int = 0,
  val y7: Int = 0,
  val y8: Int = 0,
  val y9: Int = 0,
)

// 设置水平电机 X 轴寻址位置
data class SXPReplyPara(
  val addr: Int = 1, // 从机地址
  val x0: Int = 2000, // 1~10 列的高度位置信息
  val x1: Int = 1800,
  val x2: Int = 1600,
  val x3: Int = 1400,
  val x4: Int = 1200,
  val x5: Int = 1000,
  val x6: Int = 800,
  val x7: Int = 600,
  val x8: Int = 400,
  val x9: Int = 200,
)

// 查询阵列输出状态
data class ASReplyPara(
  val n0: Int, // 负极 1-10 通道输出状态，1输出有效，0输出无效
  val n1: Int,
  val n2: Int,
  val n3: Int,
  val n4: Int,
  val n5: Int,
  val n6: Int,
  val n7: Int,
  val n8: Int,
  val n9: Int,
  val y0: Int, // 正极 1-10 通道输出状态，1输出有效，0输出无效
  val y1: Int,
  val y2: Int,
  val y3: Int,
  val y4: Int,
  val y5: Int,
  val y6: Int,
  val y7: Int,
  val y8: Int,
  val y9: Int
)

// 控制驱动板出货
data class SReplyPara(
  val addr: Int = 1, // 从机地址
  val code: Int, // 货道编号
  val floorType: FloorType,
  val isDc: Boolean, // 是否开启掉货检测
  val isLp: Boolean, // 是否启用升降机
)

// 货道类型
enum class FloorType(val code: Int) {
  SpringGMotor(1),     // 弹簧电机
  Electromagnetic(2),       // 电磁锁
  Caterpillar(3);   // 履带货道
}

// 控制驱动板阵列输出
data class AOReplyPara(
  val addr: Int = 1, // 从机地址
  val time: Int, // 阵列输出有效时间,单位 ms
  val arr: IntArray = IntArray(20) { 0 }  // 初始化20个元素，默认0无效
  // 下标 0-9 表示负极 1-10 通道输出状态
  // 下标 10-19 表示正极通道输出状态
  // 值：0 无效，1 有效
)

data class ControlRelayPara(
  val addr: Int = 1, // 从机地址
  val no: Int, // 继电器编号
  val state: Int, // 00—释放继电器，01—继电器输出
)

// 升降板驱动输出控制
data class DCReplyPara(
  val addr: Int = 1, // 从机地址
  val no: Int, // 0-9
  val mode: Int, // 0-1
  val channel: Int, // 0-9
  val status: Int, // 0-2
  val time: Int
)

fun bcdToDecimal(bcd: Int): Int {
  val high = (bcd shr 4) and 0xF
  val low = bcd and 0xF
  return high * 10 + low
}
