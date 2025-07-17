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
import androidx.compose.runtime.mutableIntStateOf
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
import com.awish.assistant.drivers.W50x
import com.awish.assistant.drivers.WeighRequestPara
import com.awish.assistant.modbus.ModbusManager
import com.awish.assistant.ui.components.LoadingDialog
import com.awish.assistant.ui.components.SerialPortSelectorDialog
import com.awish.assistant.ui.components.TextDivider
import com.awish.assistant.ui.components.W51xSelectorDialog
import com.awish.assistant.ui.templates.LogEntry
import com.awish.assistant.ui.templates.LogLevel
import com.awish.assistant.ui.templates.Logger
import com.awish.assistant.viewmodel.SerialPortViewModel
import com.google.gson.Gson
import com.licheedev.modbus4android.ModbusCallback
import com.licheedev.modbus4android.param.SerialParam
import com.serotonin.modbus4j.ModbusMaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W50x(navController: NavHostController) {
  var option by remember {
    mutableStateOf(
      Option(
        commid = "/dev/ttyS1",
        addr = 1,
        isDc = false,
        isLp = false,
        code = 1,
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

  var driver: W50x? by remember {
    mutableStateOf(null)
  }

  var visibleOfW51xSelectorDialog by remember { mutableStateOf(false) }
  var count by remember { mutableIntStateOf(8) }

  var visibleOfSerialPortModel by remember { mutableStateOf(false) }
  val isLoading by remember { mutableStateOf(false) }

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
        title = { Text(text = "W50x 称重售卖机") },
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
                  driver = W50x(ModbusManager.get())
                  driver.apply {
                    this?.readHardwareVersion(option.addr) { result ->
                      result.onSuccess { reply ->
                        logs.add(
                          LogEntry.new(
                            context.getString(
                              R.string.open_serial_path_success, option.commid
                            ), LogLevel.SUCCESS
                          )
                        )
                        count = reply.count
                        option = option.copy(isOpened = true)
                        logs.add(
                          LogEntry.new(
                            "硬件版本： ${reply.version}， 称重传感器数量: ${reply.count}",
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

        if (visibleOfW51xSelectorDialog) {
          W51xSelectorDialog(
            count = count,
            defaultValue = option.code,
            onSelect = { addr ->
              option.code = addr
              visibleOfW51xSelectorDialog = false
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
              driver?.readHardwareVersion(option.addr) { result ->
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
              driver?.readSoftwareVersion(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取软件版本: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取软件版本 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
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
              driver?.readTemperatureAndHumidity(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取温湿度: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取温湿度 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取温湿度")
          }
        }

        TextDivider("柜门", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readDoorStatus(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询柜门开关状态: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询柜门开关状态 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询柜门开关状态")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.controlDoorLock(option.addr, 2) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "开2号门: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "开2号门 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("开2号门")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.controlDoorLock(option.addr, 1) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "开1号门: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "开1号门 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("开1号门")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Spacer(
            modifier = Modifier
              .weight(1f, fill = true)
              .height(0.dp)
          )
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.controlDoorLock(option.addr, 2, false) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "关2号门: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "关2号门 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("关2号门")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.controlDoorLock(option.addr, 1, false) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "关1号门: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "关1号门 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("关1号门")
          }
        }

        TextDivider("外设", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Spacer(
            modifier = Modifier
              .weight(1f, fill = true)
              .height(0.dp)
          )

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readICCards(option.addr) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "读取 IC 卡: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取 IC 卡 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取 IC 卡")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readKeypad(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "按键读取: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "按键读取 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("按键读取")
          }
        }

        TextDivider("语音播报", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            var text by remember { mutableStateOf("欢迎光临") }

            OutlinedTextField(
              value = text,
              onValueChange = { text = it },      // ② 直接更新本地状态
              label = { Text("文本内容") },   // ③ 输入框上方的 Label
              singleLine = true,                  // 单行
              modifier = Modifier.fillMaxWidth()
            )
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setVoiceBroadcast(option.addr) { result ->
                result.onSuccess { reply ->

                  logs.add(
                    LogEntry.new(
                      "语音播报: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "语音播报 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("设置(需语音模块)")
          }
        }

        TextDivider("校准", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
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
                visibleOfW51xSelectorDialog = true
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = option.code?.toString() ?: "",
                onValueChange = { },
                label = {
                  Text(
                    "请选择编号"
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
            enabled = option.isOpened,
            onClick = {
              driver?.calibrateWeightChannel(option.addr, channel = option.code!!) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "称重${option.code!!}校准指令: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "称重${option.code!!}校准指令 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("货道${option.code!!}校准指令")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.zeroCalibration(option.addr, channelNumber=option.code!!) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "零点校准${option.code}指令: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "零点校准${option.code}指令 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("货道${option.code}去皮")
          }
        }

        TextDivider("称重", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
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
                visibleOfW51xSelectorDialog = true
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = option.code?.toString() ?: "",
                onValueChange = { },
                label = {
                  Text(
                    "请选择编号"
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
            enabled = option.isOpened,
            onClick = {
              driver?.setCalibrationWeight(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "设置校准砝码重量: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "设置校准砝码重量 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("设置校准砝码重量1000g")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              val para = WeighRequestPara(addr = option.addr, channelCount = option.code!!)
              driver?.readWeights(para) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读取${option.code}称重数据: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读取${option.code}称重数据 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读取${option.code}称重数据")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.checkWeightReadCompleted(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询重量读完: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询重量读完 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询重量读完")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.querySensorStatus(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "查询称重传感器状态: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "查询称重传感器状态 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("查询称重传感器状态(32位)")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readMultiParameters(option.addr, channelCount = count) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "多参数一次性读取: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "多参数一次性读取 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("多参数一次性读取")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.readWeightStability(option.addr, option.code!!) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "读称重传感器${option.code}稳定性: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "读称重传感器${option.code}稳定性 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("读称重传感器稳定性")
          }
        }

        TextDivider("价签", padding = 12.dp)

        Row(
          modifier = Modifier.fillMaxWidth(),
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
                visibleOfW51xSelectorDialog = true
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              OutlinedTextField(
                value = option.code?.toString() ?: "",
                onValueChange = { },
                label = {
                  Text(
                    "请选择编号"
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
            enabled = option.isOpened,
            onClick = {
              driver?.clearPriceTag(option.addr, tagId = option.code!!) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "价签清屏指令: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "价签清屏指令 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("价签清屏")
          }

          Button(
            enabled = option.isOpened,
            onClick = {
              // 要显示的行号与文本（按顺序）
              val lines = listOf(
                1 to "功夫茶",
                2 to "销售价:19.9元",
                3 to "会员价:9.9元",
                4 to "质检员:大佬刘"
              )

              var idx = 0
              while (idx < lines.size) {
                val (rowNum, rowText) = lines[idx]

                driver?.setChineseDisplay(
                  option.addr,
                  screenId = option.code!!,
                  row = rowNum,
                  text = rowText
                ) { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        "中文价签$rowNum: ${Gson().toJson(reply)}",
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        "中文价签$rowNum error: ${e.message}",
                        LogLevel.DANGER
                      )
                    )
                  }
                }
                // 如果并行请求太快，会导致部分文本没有写入
                Thread.sleep(500)
                idx++         // 别忘了递增索引
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("中文价签")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Spacer(
            modifier = Modifier
              .weight(2f, fill = true)
              .height(0.dp)
          )
          Button(
            enabled = option.isOpened,
            onClick = {
              driver?.setDigitalTubeDisplay(option.addr) { result ->
                result.onSuccess { reply ->
                  logs.add(
                    LogEntry.new(
                      "数码管显示设置: ${Gson().toJson(reply)}",
                      LogLevel.SUCCESS
                    )
                  )
                }.onFailure { e ->
                  logs.add(
                    LogEntry.new(
                      "数码管显示设置 error: ${e.message}",
                      LogLevel.DANGER
                    )
                  )
                }
              }
            },
            modifier = Modifier.weight(1f)
          ) {
            Text("数码管显示设置")
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
