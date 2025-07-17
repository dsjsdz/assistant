package com.awish.assistant.drivers

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.awish.assistant.modbus.ModbusManager
import com.licheedev.modbus4android.ModbusCallback
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse
import com.serotonin.modbus4j.msg.WriteRegisterResponse
import com.serotonin.modbus4j.msg.WriteRegistersResponse
import java.time.LocalDateTime


/**
 * Mdb2302 驱动板操作类。
 */
class MDB230x(private val modbusManager: ModbusManager) : M2301(modbusManager) {
  companion object {
    const val NAME = "Awish-MDB2302-v1.4"
  }

  /**
   * 读取硬件配置信息（异步）。
   *
   * @param callback 回调函数，返回一个 [Result] 类型）
   * - 成功时包含 [MdbHCReplyPara] 对象
   * - 失败时包含异常信息
   *
   * 使用示例：`readMdbHardwareConfig { result ->
   *     result.onSuccess { config ->
   *         Log.d("Modbus", "硬件配置信息读取成功：$config")
   *     }.onFailure { e ->
   *         Log.e("Modbus", "硬件配置信息读取失败：${e.message}")
   *     }
   * }`
   */
  fun readMdbHardwareConfig(callback: (Result<MdbHCReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1, // 固定地址
      0x0001, // 起始寄存器地址
      2,      // 读取两个寄存器
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            try {
              val version = data[0].toUByte().toString() // 版本号为1字节
              val flag = data[1].toInt() // 外设位标志位
              val currencyCode = (data[2].toUByte().toInt() shl 8) or data[3].toUByte().toInt()
              val codeHex = currencyCode.toString(16).padStart(4, '0').uppercase()

              val para = MdbHCReplyPara(
                version = version,
                withCoin = (flag and 0x01) != 0,
                withCash = (flag and 0x02) != 0,
                withPOS = (flag and 0x04) != 0,
                withPulse = (flag and 0x08) != 0,
                withIdentify = (flag and 0x20) != 0,
                code = codeHex
              )

              callback(Result.success(para))
            } catch (e: Exception) {
              callback(Result.failure(Exception("解析失败: ${e.message}")))
            }
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

  fun readMdbSoftwareVersion(callback: (Result<MdbSVReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0002, // 软件版本寄存器
      2,      // 读取两个寄存器（4 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            try {
              // BCD -> 十进制转换
              fun bcdToDec(b: Byte): Int {
                val u = b.toUByte().toInt()
                return ((u shr 4) * 10) + (u and 0x0F)
              }

              val year = 2000 + bcdToDec(data[0])
              val month = bcdToDec(data[2])
              val day = bcdToDec(data[3])
              val versionStr = "%04d%02d%02d".format(year, month, day)

              callback(Result.success(MdbSVReplyPara(version = versionStr)))
            } catch (e: Exception) {
              callback(Result.failure(Exception("解析失败: ${e.message}")))
            }
          } else {
            callback(Result.failure(Exception("返回数据不足")))
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

  fun readMdbPaymentStatus(callback: (Result<PMReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0003,
      2,
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            val statusByte = data[0].toInt() and 0xFF

            // 1️⃣ 支付设备状态与故障解析
            val isOk = statusByte and 0x80 == 0 // 最高位 0 表示正常

            val fault = when (statusByte) {
              0x80 -> 0 // 无设备
              0x81 -> 1 // 硬币器故障
              0x82 -> 2 // 纸币器故障
              0x83 -> 4 // POS 机故障
              else -> 0
            }

            // 2️⃣ 取消请求来源（只保留最低 4 位）
            val cancel = when {
              statusByte and 0x10 == 0x10 -> 1 // 硬币器取消
              statusByte and 0x20 == 0x20 -> 2 // 纸币器取消
              statusByte and 0x40 == 0x40 -> 4 // POS 取消
              else -> 0
            }

            // 3️⃣ 收款方式提取（最低4位）
            val payTypeBits = statusByte and 0x0F
            val payType = when (payTypeBits) {
              0x01 -> 1
              0x02 -> 2
              0x03 -> 3
              0x04 -> 4
              0x05 -> 5
              0x06 -> 6
              0x07 -> 7
              0x08 -> 8
              0x09 -> 9
              0x0A -> 10
              0x0B -> 11
              0x0C -> 12
              0x0D -> 13
              0x0E -> 14
              0x0F -> 15
              else -> 0
            }

            // 4️⃣ 收款金额（3字节）
            val multiple =
              ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)

            val result = PMReplyPara(
              payType = payType,
              status = isOk,
              multiple = multiple,
              cancel = cancel,
              fault = if (!isOk) fault else 0
            )
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询收款信息失败", t)
          callback(Result.failure(Exception("查询失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readMdbPeripheralMinValue(callback: (Result<MPReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0004,
      2,
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            val value = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val decimal = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

            val result = MPReplyPara(value = value, decimal = decimal)
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取最小面额失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readMdbChangeStatus(callback: (Result<CSReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0005,
      2,
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 4) {
            val status = data[0].toInt() and 0xFF
            val multiple = ((data[1].toInt() and 0xFF) shl 16) or
              ((data[2].toInt() and 0xFF) shl 8) or
              (data[3].toInt() and 0xFF)

            val result = CSReplyPara(status = status, multiple = multiple)
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取找零状态失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readMdbCoinBalance(callback: (Result<BBReplyPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0006,
      8, // 读取 8 个寄存器，共 16 字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 16) {
            val coins = data.take(16).map { it.toInt() and 0xFF } // 保证 byte 正确转换为正整数
            val result = BBReplyPara(coins)
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "查询硬币器余额失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readMdbCoinAccepted(callback: (Result<CoinAcceptableInfo>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0007,
      9, // 读取 9 个寄存器，共 18 字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 18) {
            val base = data[0].toInt() and 0xFF
            val decimal = data[1].toInt() and 0xFF
            val multiples = data.slice(2 until 18).map { it.toInt() and 0xFF }
            val result = CoinAcceptableInfo(base, decimal, multiples)
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取可接收面币失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun queryCoinChangeResult(callback: (Result<CoinChangeResult>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0008,
      8, // 读取 8 个寄存器（共 16 字节）
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 16) {
            val coinCounts = data.take(16).map { it.toInt() and 0xFF }
            val result = CoinChangeResult(coinCounts)
            callback(Result.success(result))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取硬币器找零结果失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readBillAcceptable(callback: (Result<BillAcceptPara>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x0009,
      9, // 读取9个寄存器，18字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 18) {
            val base = data[0].toInt() and 0xFF
            val decimal = data[1].toInt() and 0xFF
            val values = data.slice(2 until 18).map { it.toInt() and 0xFF }
            callback(Result.success(BillAcceptPara(base, decimal, values)))
          } else {
            callback(Result.failure(Exception("返回数据不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取纸币器可接收面值失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readBillChangeBalance(callback: (Result<BillChangeBalance>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x000A,
      8, // 读取8个寄存器 = 16字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 16) {
            val quantities = data.take(16).map { it.toInt() and 0xFF }
            callback(Result.success(BillChangeBalance(quantities)))
          } else {
            callback(Result.failure(Exception("返回数据长度不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取纸币器零钱余额失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun readAuthStatus(callback: (Result<AuthStatus>) -> Unit) {
    this.modbusManager.readHoldingRegisters(
      0xE1,
      0x000B,
      1, // 读取1个寄存器 = 2字节
      object : ModbusCallback<ReadHoldingRegistersResponse> {
        override fun onSuccess(response: ReadHoldingRegistersResponse?) {
          val data = response?.data
          if (data != null && data.size >= 2) {
            val status = data[0].toInt() and 0xFF
            callback(Result.success(AuthStatus(status)))
          } else {
            callback(Result.failure(Exception("返回数据长度不足")))
          }
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "读取身份认证状态失败", t)
          callback(Result.failure(Exception("读取失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun finishPayment(callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1001
    val value = 1 // 固定写入 1 表示完成收款

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("写寄存器失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun cancelPayment(callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1002
    val value = 1 // 固定写入1，表示取消收款

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("取消收款失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun sendSaleResult(success: Boolean, callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1003
    val value = if (success) 1 else 0 // 1 表示销售成功，0 表示销售失败

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("发送售卖结果失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun setAllowedCoins(allowedMask: Int, callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1004

    // allowedMask 只使用低16位
    val value = allowedMask and 0xFFFF

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("设置允许硬币币种失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置允许接收的纸币类别
   * @param allowedMask 16 位掩码，bit 为 1 表示允许该类别，bit 为 0 表示禁止
   */
  fun setAllowedBanknotes(allowedMask: Int, callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1005

    val value = allowedMask and 0xFFFF

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("设置允许纸币币种失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置补币模式开关
   * @param enable true 进入补币模式，false 退出补币模式
   */
  fun setRefillMode(enable: Boolean, callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1006
    val value = if (enable) 1 else 0

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("设置补币模式失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置收款方式
   * @param mode PaymentMode 枚举
   */
  fun setPaymentMode(mode: PaymentMode, callback: (Result<Unit>) -> Unit) {
    val address = 0xE1
    val register = 0x1007
    val value = mode.value

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("设置收款方式失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 设置年龄限制（启用身份认证设备前必须设置）
   * @param age 年龄限制（单位：岁）
   */
  fun setAgeLimit(age: Int, callback: (Result<Unit>) -> Unit) {
    if (age <= 0 || age > 120) {
      callback(Result.failure(IllegalArgumentException("年龄必须在 1~120 岁之间")))
      return
    }

    val address = 0xE1
    val register = 0x1008
    val value = age

    modbusManager.writeSingleRegister(
      address,
      register,
      value,
      object : ModbusCallback<WriteRegisterResponse> {
        override fun onSuccess(response: WriteRegisterResponse?) {
          callback(Result.success(Unit))
        }

        override fun onFailure(t: Throwable?) {
          callback(Result.failure(t ?: Exception("设置年龄限制失败")))
        }

        override fun onFinally() {}
      }
    )
  }

  /**
   * 发起找零请求
   * @param amount 要找零的金额（单位：元，支持两位小数）
   * @param minUnit 最小面额，默认 0.01 元
   */
  fun startChange(
    amount: Double,
    minUnit: Double,
    callback: (Result<Boolean>) -> Unit
  ) {
    if (amount <= 0) {
      callback(Result.failure(Exception("找零金额必须大于 0")))
      return
    }

    val multiplier = (amount / minUnit).toInt() // 计算金额对应的单位数（例如 1.00元 / 0.01元 = 100）

    // 将 32 位 int 拆成两个 short（高 16 位、低 16 位）
    val high = ((multiplier shr 16) and 0xFFFF).toShort()
    val low = (multiplier and 0xFFFF).toShort()

    val values = shortArrayOf(high, low)
    this.modbusManager.writeRegisters(
      0xE1,
      0x2001,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "发起找零请求发送成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "发起找零请求发送失败", t)
          callback(Result.failure(Exception("发起找零请求发送失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun setPulseADenomination(
    denomination: Double,
    callback: (Result<Boolean>) -> Unit
  ) {
    if (denomination < 0.01) {
      callback(Result.failure(Exception("脉冲找零面额不能小于 0.01 元")))
      return
    }

    val multiplier = (denomination * 100).toInt() // 转换为整数值（乘以100）
    val high = ((multiplier shr 16) and 0xFFFF).toShort()
    val low = (multiplier and 0xFFFF).toShort()
    val values = shortArrayOf(high, low)

    this.modbusManager.writeRegisters(
      0xE1,
      0x2002,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "设置 A 脉冲找零面额成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置 A 脉冲找零面额失败", t)
          callback(Result.failure(Exception("设置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  fun setPulseBDenomination(
    denomination: Double,
    callback: (Result<Boolean>) -> Unit
  ) {
    if (denomination < 0.01) {
      callback(Result.failure(Exception("脉冲找零面额不能小于 0.01 元")))
      return
    }

    val multiplier = (denomination * 100).toInt() // 转换为整数（乘以100）
    val high = ((multiplier shr 16) and 0xFFFF).toShort()
    val low = (multiplier and 0xFFFF).toShort()
    val values = shortArrayOf(high, low)

    this.modbusManager.writeRegisters(
      0xE1,
      0x2003,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "设置 B 脉冲找零面额成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "设置 B 脉冲找零面额失败", t)
          callback(Result.failure(Exception("设置失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  // 0x2004—发起收款指令
  fun startPayment(
    channel: Int,
    amount: Double,
    minUnit: Double,
    callback: (Result<Boolean>) -> Unit
  ) {
    if (channel <= 0 || channel > 0xFFFF) {
      callback(Result.failure(Exception("货道编号无效")))
      return
    }
    if (amount <= 0) {
      callback(Result.failure(Exception("收款金额必须大于 0")))
      return
    }

    val amountInt = (amount / minUnit).toInt()

    // 拆分货道编号为 short
    val channelShort = channel.toShort()

    // 将 32 位金额拆为两个 short
    val amountHigh = ((amountInt shr 16) and 0xFFFF).toShort()
    val amountLow = (amountInt and 0xFFFF).toShort()

    val values = shortArrayOf(channelShort, amountHigh, amountLow)

    this.modbusManager.writeRegisters(
      0xE1,
      0x2004,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "发起收款请求发送成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "发起收款请求发送失败", t)
          callback(Result.failure(Exception("发起收款失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun syncSystemTime(
    time: LocalDateTime = LocalDateTime.now(),
    callback: (Result<Boolean>) -> Unit
  ) {
    val year = time.year.toShort()
    val month = time.monthValue.toShort()
    val day = time.dayOfMonth.toShort()
    val hour = time.hour.toShort()
    val minute = time.minute.toShort()
    val second = time.second.toShort()

    val values = shortArrayOf(
      year, // 占两个字节
      ((month.toInt() shl 8) or day.toInt()).toShort(),
      ((hour.toInt() shl 8) or minute.toInt()).toShort(),
      ((second.toInt() shl 8) or 0x00).toShort() // 保留字段为 0
    )

    this.modbusManager.writeRegisters(
      0xE1,
      0x2005,
      values,
      object : ModbusCallback<WriteRegistersResponse> {
        override fun onSuccess(response: WriteRegistersResponse?) {
          Log.d("Modbus", "同步系统时间成功: $response")
          callback(Result.success(true))
        }

        override fun onFailure(t: Throwable?) {
          Log.e("Modbus", "同步系统时间失败", t)
          callback(Result.failure(Exception("同步系统时间失败: ${t?.message}")))
        }

        override fun onFinally() {}
      }
    )
  }

}

// 查询 MDB 硬件配置
data class MdbHCReplyPara(
  val version: String, // 硬件版本
  val withCoin: Boolean = false, // 硬币器
  val withCash: Boolean = false, // 纸币器
  val withPOS: Boolean = false, // POS 机
  val withPulse: Boolean = false, // 脉冲收款
  val withIdentify: Boolean = false, // 身份识别
  val code: String = "0001", // 货币代码，如人民币 0086，美元 0001
)

// 查询 MDB 软件版本
data class MdbSVReplyPara(
  val version: String, // 硬件版本
)

// 查询 MDB 收款金额
data class PMReplyPara(
  // 1-硬币器
  // 2-纸币器
  // 3-硬币器 + 纸币器
  // 4-POS 机
  // 5-POS 机 + 硬币器
  // 6-POS 机 + 纸币器
  // 7-POS 机 + 纸币器 + 硬币器
  // 8-脉冲
  // 9-脉冲 + 硬币器
  // 10-脉冲 + 纸币器
  // 11-脉冲 + 纸币器 + 硬币器
  // 12-脉冲 + POS 机
  // 13-脉冲 + POS 机 + 硬币器
  // 14-脉冲 + POS 机 + 纸币器
  // 15-脉冲 + POS 机 + 纸币器 + 硬币器
  val payType: Int = 1,
  // true- 支付设备正常, false- 表示支付设备故障
  val status: Boolean = true,
  // 收款金额，为最小面额的倍数
  val multiple: Int,
  // 1- 硬币器发起取消
  // 2- 纸币器发起取消
  // 4- POS 机发起取消
  val cancel: Int,
  // status = 1 时有效
  // 0 - 支付板未连接任何支付设备，无法收款
  // 1- 硬币器故障
  // 2- 纸币器故障
  // 3- 硬币器/纸币器故障
  // 4- POS 机故障
  val fault: Int,
)

// 读取外设支持最小面额
data class MPReplyPara(
  val value: Int, // 最小面额基数
  val decimal: Int, // 小数位数
)

// 查询找零状态
data class CSReplyPara(
  // 0- 空闲
  // 1- 正在找零
  // 2- 完成找零
  // 3- 零钱不足
  val status: Int, // 找零状态
  val multiple: Int, // 找零金额，最小面币的倍数
)

// 读取硬币器可找零余额
data class BBReplyPara(
  val coins: List<Int> // 每个元素表示某个管道中的硬币数量，最多 16 个管道
)

// 读取硬币器可接收面币
data class CoinAcceptableInfo(
  val base: Int,             // 基数值 F
  val decimal: Int,          // 小数位数 D
  val tubeMultiples: List<Int> // 各管道对应的倍数，共 16 个
) {
  fun getDenominations(): List<Double> {
    val factor = base * Math.pow(10.0, -decimal.toDouble())
    return tubeMultiples.map { it * factor }
  }
}

// 查询硬币器找零结果
data class CoinChangeResult(
  val coins: List<Int> // 每个管道找出的硬币数量，共 16 个，0~255
)

// 读取纸币器可接收面币
data class BillAcceptPara(
  val base: Int,             // 货币基数值 F
  val decimal: Int,          // 小数位数 D
  val faceValues: List<Int>  // 每种纸币的基数倍数，共 16 个
) {
  fun getActualValues(): List<Double> {
    val unit = base * Math.pow(10.0, -decimal.toDouble())
    return faceValues.map { it * unit }
  }
}

// 查询纸币器可找零余额
data class BillChangeBalance(
  val quantities: List<Int> // 16 种纸币的数量，最大255张
)

// 查询身份认证是否成功
data class AuthStatus(
  val status: Int // 身份认证状态: 0=未刷卡, 1=成功, 2=失败(年龄), 3=错误(设备异常)
)

enum class PaymentMode(val value: Int) {
  AUTO(0x0000),
  CASH(0x0001),
  POS(0x0002)
}

enum class MDBExceptionCode(val code: Int, val message: String) {
  ILLEGAL_FUNCTION(0x01, "不合法功能代码：从机不支持该功能"),
  ILLEGAL_DATA_ADDRESS(0x02, "不合法数据地址：访问的寄存器地址无效"),
  ILLEGAL_DATA_VALUE(0x03, "不合法数据：寄存器数据值非法"),
  CRC_ERROR(0x04, "校验错误：CRC 校验失败，请重发"),
  DEVICE_BUSY(0x06, "从机设备忙碌：设备正忙，请稍后重试"),
  DEVICE_FAILURE(0x07, "从机设备故障：设备发生不可恢复错误"),
  ACKNOWLEDGE(0x08, "确认响应：设备处理时间较长，请继续查询"),
  UNKNOWN(-1, "未知错误代码");

  companion object {
    fun fromCode(code: Int): MDBExceptionCode {
      return MDBExceptionCode.entries.find { it.code.toInt() == code } ?: UNKNOWN
    }
  }
}

object ModbusErrorManager {
  fun parseException(functionCode: Int, data: ByteArray?): Exception? {
    if (functionCode >= 0x80 && !(data == null || data.isEmpty())) {
      val code = data[0].toInt() and 0xFF
      val exceptionCode = MDBExceptionCode.fromCode(code)
      val msg = if (exceptionCode == MDBExceptionCode.UNKNOWN) {
        "未知错误代码：0x${code.toString(16)}"
      } else {
        exceptionCode.message
      }
      return Exception("Modbus异常：$msg")
    }
    return null
  }
}

/**
 * override fun onSuccess(response: ModbusResponse?) {
 *     if (response == null) {
 *         callback(Result.failure(Exception("无响应数据")))
 *         return
 *     }
 *
 *     val err = ModbusErrorManager.parseException(response.functionCode, response.data)
 *     if (err != null) {
 *         callback(Result.failure(err))
 *         return
 *     }
 *
 *     // 正常处理逻辑
 *     callback(Result.success(true))
 * }
 */
