package com.awish.assistant

//import cc.uling.usdk.USDK
//import cc.uling.usdk.board.wz.para.BSReplyPara
//import cc.uling.usdk.board.wz.para.HCReplyPara
//import cc.uling.usdk.board.wz.para.IOReplyPara
//import cc.uling.usdk.board.wz.para.ResetReplyPara
//import cc.uling.usdk.board.wz.para.SReplyPara
//import cc.uling.usdk.board.wz.para.SSReplyPara
//import cc.uling.usdk.constants.CodeUtil
//import cc.uling.usdk.constants.ErrorConst
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
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.awish.assistant.drivers.BSRequestPara
import com.awish.assistant.drivers.FloorType
import com.awish.assistant.drivers.M2301
import com.awish.assistant.drivers.SReplyPara
import com.awish.assistant.err.ErrorCode
import com.awish.assistant.err.RunStatus
import com.awish.assistant.modbus.ModbusManager
import com.awish.assistant.ui.components.AddrSelectorDialog
import com.awish.assistant.ui.components.ChannelSelectorDialog
import com.awish.assistant.ui.components.FloorTypeSelectorDialog
import com.awish.assistant.ui.components.LoadingDialog
import com.awish.assistant.ui.components.Qrcode
import com.awish.assistant.ui.components.SerialPortSelectorDialog
import com.awish.assistant.ui.components.TextDivider
import com.awish.assistant.ui.templates.LogEntry
import com.awish.assistant.ui.templates.LogLevel
import com.awish.assistant.ui.templates.Logger
import com.awish.assistant.viewmodel.FloorTypeViewModel
import com.awish.assistant.viewmodel.SerialPortViewModel
import com.licheedev.modbus4android.ModbusCallback
import com.licheedev.modbus4android.param.SerialParam
import com.serotonin.modbus4j.ModbusMaster
import kotlinx.coroutines.launch

data class Option(
  var commid: String?,
  var addr: Int,
  var isDc: Boolean,
  var isLp: Boolean,
  var code: Int?,
  var floorType: Int?,
  var isOpened: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Welcome(navController: NavHostController) {
  var option by remember {
    mutableStateOf(
      Option(
        commid = "/dev/ttyS1",
        addr = 1,
        isDc = false,
        isLp = false,
        code = 0,
        floorType = 1,
        isOpened = false
      )
    )
  }
//  var board by remember { mutableStateOf(USDK.getInstance().create(option.commid)) }

  val context = LocalContext.current

  val modbusManager: ModbusManager by remember {
    mutableStateOf(ModbusManager.get())
  }
  modbusManager.apply {
    this.closeModbusMaster()
  }

  var driver: M2301? by remember {
    mutableStateOf(null)
  }

  var visibleOfFloorTypeModel by remember { mutableStateOf(false) }
  var visibleOfAddrModel by remember { mutableStateOf(false) }
  var visibleOfChannelModel by remember { mutableStateOf(false) }
  var visibleOfSerialPortModel by remember { mutableStateOf(false) }
  val isLoading by remember { mutableStateOf(false) }

  val ftvm: FloorTypeViewModel = hiltViewModel()
  val spvm: SerialPortViewModel = hiltViewModel()
  val serialPaths: List<String> = spvm.serialPaths

  var expanded by remember { mutableStateOf(false) }

  val appName = stringResource(id = R.string.app_name)
  val appVersion = BuildConfig.VERSION_NAME
  val title = "$appName v$appVersion"

  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()

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

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Text(
          text = title,
          modifier = Modifier.padding(16.dp),
          style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        NavigationDrawerItem(
          label = { Text("MDB230x 板卡") },
          selected = false,
          onClick = {
            scope.launch { drawerState.close() }
            expanded = false
            modbusManager.apply {
              this.closeModbusMaster()
              option.copy(isOpened = false)
            }
            navController.navigate("mdb230x")
          }
        )
        NavigationDrawerItem(
          label = { Text("W50x 称重售卖机") },
          selected = false,
          onClick = {
            scope.launch { drawerState.close() }
            expanded = false
            modbusManager.apply {
              this.closeModbusMaster()
              option.copy(isOpened = false)
            }
            navController.navigate("w50x")
          }
        )
        NavigationDrawerItem(
          label = { Text(stringResource(id = R.string.nav_about)) },
          selected = false,
          onClick = {
            scope.launch { drawerState.close() }
            expanded = false
            modbusManager.apply {
              this.closeModbusMaster()
              option.copy(isOpened = false)
            }
            navController.navigate("about")
          }
        )

        Spacer(modifier = Modifier.weight(1f))

        Qrcode()
      }
    }
  ) {
    Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
          navigationIcon = {
            IconButton(onClick = {
              scope.launch {
                if (drawerState.isClosed) {
                  drawerState.open()
                } else {
                  drawerState.close()
                }
              }
            }) {
              Icon(
                imageVector = if (drawerState.isOpen) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                contentDescription = if (drawerState.isOpen) "Close menu" else "Open menu"
              )
            }
          },
          title = { Text("$appName v$appVersion") },
          colors = TopAppBarDefaults.topAppBarColors(
            navigationIconContentColor = Color.White, // 左边图标颜色
            containerColor = Color(0xFF424ea0), // 背景颜色
            titleContentColor = Color.White,     // 文本颜色
            actionIconContentColor = Color.White     // 右边图标颜色
          ),
          actions = {
            IconButton(onClick = { expanded = !expanded }) {
              Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
              expanded = expanded,
              onDismissRequest = { expanded = false }
            ) {
              DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.nav_about)) },
                onClick = {
                  expanded = false
                  modbusManager.apply {
                    this.closeModbusMaster()
                    option.copy(isOpened = false)
                  }
                  navController.navigate("about")
                }
              )
            }
          }
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
                    driver = M2301(ModbusManager.get())
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
                          option = option.copy(isOpened = true)
                          logs.add(
                            LogEntry.new(
                              context.getString(
                                R.string.read_config_success,
                                reply.row,
                                reply.column,
                                reply.version
                              ),
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

          if (visibleOfFloorTypeModel) {
            FloorTypeSelectorDialog(
              options = ftvm.options,
              defaultValue = option.floorType,
              onSelect = { selected ->
                option = option.copy(floorType = selected.id)
                visibleOfFloorTypeModel = false
                logs.add(
                  LogEntry.new(
                    context.getString(R.string.floor_type_value, selected.title),
                    LogLevel.WARN
                  )
                )
              }
            )
          }

          if (visibleOfAddrModel) {
            AddrSelectorDialog(
              defaultValue = option.addr,
              onSelect = { selected ->
                option = option.copy(addr = selected)
                visibleOfAddrModel = false
                logs.add(
                  LogEntry.new(
                    context.getString(R.string.board_addr_value, option.addr),
                    LogLevel.WARN
                  )
                )
              }
            )
          }

          if (visibleOfChannelModel) {
            ChannelSelectorDialog(
              defaultValue = option.code,
              onSelect = { selected ->
                option = option.copy(code = selected)
                visibleOfChannelModel = false
                logs.add(
                  LogEntry.new(
                    context.getString(R.string.channel_value, option.code),
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
                driver?.getIOStatus(option.addr) { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        context.getString(
                          R.string.read_io_status_success,
                          reply.io0,
                          reply.io1,
                          reply.io2,
                          reply.io3,
                          reply.io4,
                          reply.io5,
                          reply.io6,
                          reply.io7,
                          reply.io8,
                          reply.io9,
                          reply.io10,
                          reply.io11,
                          reply.io12
                        ),
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.read_io_status_fail) + ", error: ${e.message}",
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
              modifier = Modifier.weight(1f)
            ) {
              Text(stringResource(R.string.read_io_status))
            }

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
          }

          TextDivider(stringResource(R.string.btn_shipment_test), padding = 12.dp)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.weight(1.5f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = option.isDc,
                onCheckedChange = {
                  option = option.copy(isDc = it)

                  val status =
                    if (it) context.getString(R.string.status_on) else context.getString(R.string.status_off)
                  logs.add(
                    LogEntry.new(
                      context.getString(
                        R.string.dc_status,
                        status
                      ),
                      if (it) LogLevel.SUCCESS else LogLevel.DANGER
                    )
                  )
                },
                enabled = option.isOpened,
              )
              Text(
                stringResource(R.string.is_dc)
              )
            }

            Row(
              modifier = Modifier.weight(1.5f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = option.isLp,
                onCheckedChange = {
                  option = option.copy(isLp = it)
                  val status =
                    if (it) context.getString(R.string.status_on) else context.getString(R.string.status_off)
                  logs.add(
                    LogEntry.new(
                      context.getString(R.string.lp_status, status),
                      if (it) LogLevel.SUCCESS else LogLevel.DANGER
                    )
                  )
                },
                enabled = option.isOpened,
              )
              Text(
                stringResource(R.string.is_lp)
              )
            }

            Button(
              modifier = Modifier.weight(2f),
              enabled = option.isOpened,
              onClick = {
                driver?.resetLift(option.addr) { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.reset_lift_success),
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.reset_lift_fail),
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
            ) {
              Text(stringResource(R.string.reset_lift))
            }
          }

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
                  visibleOfAddrModel = true
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                OutlinedTextField(
                  value = "${option.addr}",
                  onValueChange = { },
                  label = {
                    Text(
                      if (option.floorType != null) stringResource(R.string.select_addr) else stringResource(
                        R.string.select_addr
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

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
              ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = {
                  visibleOfChannelModel = true
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                OutlinedTextField(
                  value = if (option.code != null) "${option.code}" else stringResource(R.string.label_select),
                  onValueChange = { },
                  label = {
                    Text(
                      if (option.code != null) stringResource(R.string.channel_slot) else stringResource(
                        R.string.select_channel
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

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
              ExposedDropdownMenuBox(
                expanded = false,
                onExpandedChange = {
                  visibleOfFloorTypeModel = true
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                OutlinedTextField(
                  value = ftvm.options.find { it.id == option.floorType }?.title
                    ?: stringResource(R.string.label_select),
                  onValueChange = { },
                  label = {
                    Text(
                      if (option.floorType != null) stringResource(R.string.floor_type) else stringResource(
                        R.string.select_floor_type
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
              enabled = option.isOpened,
              onClick = {
                val floorType = when (option.floorType) {
                  1 -> FloorType.SpringGMotor
                  2 -> FloorType.Electromagnetic
                  3 -> FloorType.Caterpillar
                  else -> FloorType.SpringGMotor
                }
                driver?.runShipment(
                  SReplyPara(
                    option.addr,
                    option.code!!,
                    floorType,
                    option.isDc,
                    option.isLp
                  )
                ) { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.shipment_success),
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.shipment_fail),
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
              modifier = Modifier.weight(1f)
            ) {
              Text(stringResource(R.string.btn_shipment))
            }

            Button(
              enabled = option.isOpened,
              onClick = {
                driver?.readShipmentStatus(option.addr) { result ->
                  result.onSuccess { reply ->
                    val info = when (reply.runStatus) {
                      0 -> LogLevel.INFO
                      1 -> LogLevel.WARN
                      2 -> LogLevel.SUCCESS
                      else -> LogLevel.ERROR
                    }
                    logs.add(
                      LogEntry.new(
                        context.getString(
                          R.string.query_shipment_status_success,
                          reply.runStatus,
                          RunStatus.descriptionFor(reply.runStatus),
                          reply.faultCode,
                          ErrorCode.descriptionFor(reply.faultCode)
                        ),
                        info
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.query_shipment_status_fail),
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
              modifier = Modifier.weight(1f)
            ) {
              Text(stringResource(R.string.query_shipment_status))
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Spacer(modifier = Modifier.weight(2f))
            Button(
              onClick = {
                driver?.readTemperatureAndHumidity(option.addr) { result ->
                  result.onSuccess { reply ->
                    logs.add(
                      LogEntry.new(
                        context.getString(
                          R.string.query_temperature_humidity_success,
                          String.format("%.2f", reply.humidity / 10.0),
                          String.format("%.2f", reply.temperature / 10.0)
                        ),
                        LogLevel.SUCCESS
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.query_temperature_humidity_fail),
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
              modifier = Modifier.weight(1f),
              enabled = option.isOpened
            ) {
              Text(stringResource(R.string.btn_temperature_and_humidity))
            }

            Button(
              onClick = {
                if (!option.isOpened) {
                  logs.add(
                    LogEntry.new(
                      context.getString(R.string.open_serial_first),
                      LogLevel.DANGER
                    )
                  )
                  return@Button
                }

                logs.add(
                  LogEntry.new(
                    context.getString(R.string.querying_box_status, option.code),
                    LogLevel.INFO
                  )
                )

                driver?.getBoxStatus(BSRequestPara(option.addr, option.code!!)) { result ->
                  result.onSuccess { reply ->
                    val info = when (reply.status) {
                      0 -> LogLevel.SUCCESS
                      else -> LogLevel.ERROR
                    }
                    logs.add(
                      LogEntry.new(
                        context.getString(
                          R.string.query_box_status_success,
                          reply.status,
                          reply.code
                        ),
                        info
                      )
                    )
                  }.onFailure { e ->
                    logs.add(
                      LogEntry.new(
                        context.getString(R.string.query_box_status_fail),
                        LogLevel.DANGER
                      )
                    )
                  }
                }
              },
              modifier = Modifier.weight(2f),
              enabled = option.floorType == 2
            ) {
              Text(stringResource(R.string.query_box_status))
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

}
