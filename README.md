# Run

the demo just support android 7.0 or android 7.1.1, if you want to run it on other android version,
you need to modify the code.

## GetSoftwareVersion

> will restart the lifting platform

## SerialPort

硬件操作工具类

```kotlin
implementation("com.licheedev:hardwareutils:1.0.0")
implementation("com.licheedev:logplus:1.0.0")
implementation("com.licheedev:android-serialport:2.1.2")
implementation("com.github.licheedev.CommonSize:common_size_w1000_n1920:1.3.1")
```

```
// 协程写法
lifecycleScope.launch {
    try {
        val response =
            ModbusManager.get().awaitReadHoldingRegisters(mSalveId, mOffset, mAmount)
        val data: ByteArray = response.data
        appendText("F03读取：" + ByteUtil.bytes2HexStr(data) + "\n")
    } catch (e: Exception) {
        appendError("F03", e)
    }
}
```

## Apollo

```bash
./gradlew downloadApolloSchema --endpoint="{endpoint}" --schema="app/src/main/graphql/graphql.schema.graphqls" --header="Appid: {appid}" --header="Authorization: Bearer {token}"

./gradlew :app:generateServiceApolloSources
```
