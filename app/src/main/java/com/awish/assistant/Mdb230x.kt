package com.awish.assistant

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.awish.assistant.drivers.MDB230x
import com.awish.assistant.drivers.PaymentMode
import com.awish.assistant.modbus.ModbusManager
import com.awish.assistant.ui.components.LoadingDialog
import com.awish.assistant.ui.components.SerialPortSelectorDialog
import com.awish.assistant.ui.templates.LogEntry
import com.awish.assistant.ui.templates.LogLevel
import com.awish.assistant.ui.templates.Logger
import com.awish.assistant.viewmodel.SerialPortViewModel
import com.licheedev.modbus4android.ModbusCallback
import com.licheedev.modbus4android.param.SerialParam
import com.serotonin.modbus4j.ModbusMaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Mdb2302(navController: NavHostController) {
  var option by remember {
    mutableStateOf(
      Option(
        commid = "/dev/ttyS1",
        addr = 0xE1,
        isDc = false,
        isLp = false,
        code = 0,
        floorType = 1,
        isOpened = false
      )
    )
  }

  val context = LocalContext.current

  val modbusManager: ModbusManager by remember {
    mutableStateOf(ModbusManager.get())
  }
  modbusManager.apply {
    this.closeModbusMaster()
  }

  var driver: MDB230x? by remember {
    mutableStateOf(null)
  }

  var visibleOfSerialPortModel by remember { mutableStateOf(false) }
  var isLoading by remember { mutableStateOf(false) }

  val spvm: SerialPortViewModel = hiltViewModel()
  val serialPaths: List<String> = spvm.serialPaths

  val appName = stringResource(id = R.string.app_name)
  val appVersion = BuildConfig.VERSION_NAME
  val title = "$appName v$appVersion"

  rememberDrawerState(initialValue = DrawerValue.Closed)
  rememberCoroutineScope()

  val logs = remember { mutableStateListOf<LogEntry>() }

  // 首次进入进行加载
  LaunchedEffect(Unit) {
    logs.add(
      LogEntry.new(
        message = context.getString(R.string.welcome_message, title),
        info = LogLevel.INFO
      )
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "MDB230x 板卡") },
        navigationIcon = {
          IconButton(onClick = {
            modbusManager.apply {
              this.closeModbusMaster()
              option.copy(isOpened = false)
            }
            navController.popBackStack()
          }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(id = R.string.nav_back),
            )
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
    ) {
      // 第一半部分
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(innerPadding)
          .padding(8.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            ExposedDropdownMenuBox(
              expanded = false,
              onExpandedChange = {
                visibleOfSerialPortModel = true
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = option.commid ?: stringResource(R.string.label_select),
                onValueChange = { },
                label = {
                  Text(
                    if (option.commid != null) stringResource(R.string.serial_path) else stringResource(
                      R.string.select_serial_path
                    )
                  )
                },
                readOnly = true,
                modifier = Modifier
                  .menuAnchor()
                  .fillMaxWidth(),
                singleLine = true,
              )
            }
          }
          Button(
            onClick = {
              val serialParam = SerialParam.create(option.commid, 9600)
                .setDataBits(8) // 数据位
                .setParity(0) // 奇偶校验
                .setStopBits(1) // 停止位
                .setTimeout(2000) // 超时2s
                .setRetries(0) // 重试次数
              modbusManager.init(serialParam, object : ModbusCallback<ModbusMaster> {
                override fun onSuccess(t: ModbusMaster?) {
                  driver = MDB230x(ModbusManager.get())
                  driver.apply {
                    this?.readMdbHardwareConfig { result ->
                      result.onSuccess { reply ->
                        logs.add(
                          LogEntry.new(
                            context.getString(
                              R.string.open_serial_path_success, option.commid
                            ), LogLevel.SUCCESS
                          )
                        )
                        option = option.copy(isOpened = true)
                        logs.add(
                          LogEntry.new(
                            "硬件版本: ${reply.version}\n" +
                              "硬币器: ${if (reply.withCoin) "支持" else "不支持"}\n" +
                              "纸币器: ${if (reply.withCash) "支持" else "不支持"}\n" +
                              "POS 机: ${if (reply.withPOS) "支持" else "不支持"}\n" +
                              "脉冲收款: ${if (reply.withPulse) "支持" else "不支持"}\n" +
                              "身份识别: ${if (reply.withIdentify) "支持" else "不支持"}\n" +
                              "货币代码: ${reply.code}",
                            LogLevel.SUCCESS
                          )
                        )
                      }.onFailure { e ->
                        Log.e("Modbus", "读取硬件版本失败: ${e.message}")
                        logs.add(
                          LogEntry.new(
                            context.getString(R.string.open_serial_path_fail) + ", error: ${e.message}",
                            LogLevel.DANGER
                          )
                        )
                      }
                    }
                  }
                }

                override fun onFailure(tr: Throwable?) {
                  logs.add(
                    LogEntry.new(
                      context.getString(R.string.open_serial_path_fail_check),
                      LogLevel.DANGER
                    )
                  )
                }

                override fun onFinally() {}
              })
            },
          ) {
            Text(stringResource(R.string.btn_open_serial))
          }
          if (option.isOpened) {
            Button(
              onClick = {
                modbusManager.apply {
                  this.closeModbusMaster()
                  option = option.copy(isOpened = false)
                }
                logs.add(
                  LogEntry.new(
                    context.getString(R.string.close_serial_success, option.commid),
                    LogLevel.SUCCESS
                  )
                )
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
              ),
            ) {
              Text(stringResource(R.string.btn_close_serial))
            }
          }
        }

        if (visibleOfSerialPortModel) {
          SerialPortSelectorDialog(
            serialPaths = serialPaths,
            defaultValue = option.commid,
            onSelect = { selected ->
              option.commid = selected
              visibleOfSerialPortModel = false
              logs.add(
                LogEntry.new(
                  context.getString(R.string.select_serial_port_path, option.commid),
                  LogLevel.WARN
                )
              )
            }
          )
        }

        LoadingDialog(isLoading = isLoading)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readMdbHardwareConfig { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      context.getString(
                        R.string.read_board_hardware_version_success,
                        reply.version
                      ),
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      context.getString(R.string.read_board_hardware_version_fail) + ", error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text(stringResource(R.string.board_hardware_version))
          }
          Button(
            enabled = option.isOpened,
            onClick = {
              driver.apply {
                this?.readMdbSoftwareVersion { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        "软件版本: ${reply.version}",
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    Log.e("Modbus", "读取硬件版本失败: ${e.message}")
                    logs.add(
                      LogEntry.new(
                        "读取硬件版本失败: ${e.message}",
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取软件版本")
          }
          Button(
            enabled = option.isOpened,
            onClick = {
              driver.apply {
                this?.readMdbPaymentStatus { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        "查询 MDB 收款金额: ${reply.payType}, ${reply.status}, ${reply.multiple}, ${reply.cancel}, ${reply.fault}",
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    Log.e("Modbus", "查询 MDB 收款金额失败: ${e.message}")
                    logs.add(
                      LogEntry.new(
                        "查询 MDB 收款金额失败: ${e.message}",
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询 MDB 收款金额")
          }
          Button(
            enabled = option.isOpened,
            onClick = {
              driver.apply {
                this?.readMdbPeripheralMinValue { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        "读取外设支持最小面额: 面额基数 ${reply.value}, 小数位数 ${reply.decimal}",
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    Log.e("Modbus", "读取外设支持最小面额失败: ${e.message}")
                    logs.add(
                      LogEntry.new(
                        "读取外设支持最小面额失败: ${e.message}",
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取外设支持最小面额")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readMdbChangeStatus { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询找零状态, 找零状态：${reply.status}, 找零金额，最小面币的倍数: ${reply.multiple}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询找零状态失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询找零状态")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readMdbCoinBalance { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取硬币器可找零余额：${reply.coins}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取硬币器可找零余额, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取硬币器可找零余额")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readMdbCoinAccepted { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取硬币器可接收面币：${reply.base}, ${reply.decimal}, ${reply.tubeMultiples}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取硬币器可接收面币, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取硬币器可接收面币")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.queryCoinChangeResult { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询硬币器找零结果：${reply.coins}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询硬币器找零结果, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询硬币器找零结果")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readBillAcceptable { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取纸币器可接收面币, ${reply.base}, ${reply.decimal}, ${reply.faceValues}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取纸币器可接收面币失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取纸币器可接收面币")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readBillChangeBalance { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询纸币器可找零余额, ${reply.quantities}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询纸币器可找零余额失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询纸币器可找零余额")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readAuthStatus { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询身份认证是否成功, ${reply.status}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询身份认证是否成功失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询身份认证是否成功")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.finishPayment { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "完成收款",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "完成收款失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("完成收款")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.cancelPayment { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "取消当前收款",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "取消当前收款失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("取消当前收款")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.sendSaleResult(true) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "售卖结果设为成功",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "售卖结果设为失败 发起失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("售卖结果成功")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.sendSaleResult(false) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "售卖结果设为失败",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "售卖结果设为失败，发起失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("售卖结果失败")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setAllowedCoins(1) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "设置硬币器允许币种",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "设置硬币器允许币种失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("设置硬币器允许币种")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setAllowedBanknotes(1) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "设置允许接收的纸币类别",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "设置允许接收的纸币类别失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("设置允许接收的纸币类别")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setRefillMode(true) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "补币模式",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "补币模式失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("补币模式")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setPaymentMode(PaymentMode.AUTO) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "设置收款方式",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "设置收款方式失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("设置收款方式")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setAgeLimit(18) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "年龄限制",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "年龄限制失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("年龄限制")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.startChange(1.0, 2.0) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "发起找零",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "发起找零失败, error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("发起找零")
          }
        }

      }

      Spacer(modifier = Modifier.height(8.dp)) // 可选间隔

      // 第二半部分
      Logger(logs) {
        logs.clear()
        logs.add(
          LogEntry.new(
            message = context.getString(R.string.welcome_message, title),
            info = LogLevel.INFO
          )
        )
      }
    }
  }
}
