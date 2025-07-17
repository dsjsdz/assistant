package com.awish.assistant.err

enum class ErrorCode(val code: Int, val description: String) {
  ERR_NO_ERR(0x00, "没有错误"),
  ERR_SLOT_NUM_INVALID(0x01, "无效货道编号"),
  ERR_MOTOR_DRIVER_BREAK(0x02, "电机没有转动，无电流通过（插头未插好或断线）"),
  ERR_MOTOR_DRIVER_SHORT(0x03, "电机短路（电流过大或线头短路）"),
  ERR_MOTOR_RUN_TIMEOUT(0x04, "电机可以转动，没有反馈信号（电机反馈没接好）"),
  ERR_DLV_STATE_BREAK(0x05, "掉货检测板状态异常（启用掉货检测但检测板未连接或故障）"),
  ERR_DLV_NO_DIRECT(0x06, "电机正常，没有检测到掉货"),
  ERR_SLOT_TYPE_SET_ERROR(0x07, "货道类型设置错误（设定与实际不匹配）"),
  ERR_SLAVE_COMM_ERROR(0x08, "从设备通信故障（没接好线或地址错误）"),
  ERR_LIFT_COMMUNICATION(0x10, "升降系统通信故障（检查线路）"),
  ERR_LIFT_PLATFORMS(0x11, "升降台故障"),
  ERR_LIFT_HAVE_GOODS(0x12, "升降台有物品，无法出货"),
  ERR_VER_LIMIT_DOWN(0x13, "升降系统下限位开关故障"),
  ERR_HOR_LIMIT_RIGHT(0x14, "升降系统右限位开关故障"),
  ERR_SECURITY_BAFFLE(0x15, "防盗挡板故障"),
  ERR_PICK_GOODS_DOOR(0x16, "取货口故障"),
  ERR_VER_MOTOR_BREAK(0x17, "升降电机不通电（没有电流，检测线路）"),
  ERR_VER_MOTOR_SHORT(0x18, "升降电机短路（过载、短路或烧坏）"),
  ERR_VER_ENCODE_BREAK(0x19, "升降电机编码器故障（检查反馈线）"),
  ERR_HOR_MOTOR_BREAK(0x1A, "水平电机不通电（没有电流，检测线路）"),
  ERR_HOR_MOTOR_SHORT(0x1B, "水平电机短路（过载、短路或烧坏）"),
  ERR_HOR_ENCODE_BREAK(0x1C, "水平电机编码器故障（检查反馈线）");

  companion object {
    fun fromCode(code: Int): ErrorCode? {
      return ErrorCode.entries.find { it.code == code }
    }

    fun descriptionFor(code: Int): String {
      return fromCode(code)?.description ?: "未知错误代码: 0x${code.toString(16).uppercase()}"
    }
  }
}

enum class RunStatus(val code: Int, val desc: String) {
  IDLE(0x00, "空闲"),
  BUSY(0x01, "正在出货"),
  SUCCESS(0x02, "出货成功"),
  FAILURE(0x03, "出货失败");

  companion object {
    fun fromCode(code: Int): RunStatus? {
      return RunStatus.entries.find { it.code == code }
    }

    fun descriptionFor(code: Int): String {
      return fromCode(code)?.desc ?: "未知状态代码: 0x${code.toString(16).uppercase()}"
    }
  }
}
