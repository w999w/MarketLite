# 快应用框架：命令与恢复

适用条件：系统原本包含 `com.miui.hybrid`，目标为 Android 主用户 0。商店轻享通过经过用户确认的 Root 命令，仅管理该用户的安装状态；不删除系统镜像，不修改分区。

## 应用内命令

卸载（保留数据）：

```sh
su -c "pm uninstall -k --user 0 com.miui.hybrid"
```

恢复系统预装组件：

```sh
su -c "cmd package install-existing --user 0 com.miui.hybrid"
```

应用会读取 Android 包管理器的状态，确认是否已为主用户安装。命令退出成功并不等于组件已实际恢复。

## 无法打开应用时

1. 在 Vector 中停用商店轻享，并强行停止、重新打开小米应用商店；这可恢复模块的界面修改。
2. 如果此前通过本应用卸载了快应用框架，需要单独恢复。电脑连接已授权的 ADB 后先核对系统仍认识该包：

   ```sh
   adb shell pm list packages -u --user 0 com.miui.hybrid
   ```

3. 若包仍存在，可尝试：

   ```sh
   adb shell cmd package install-existing --user 0 com.miui.hybrid
   ```

   如果提示权限不足，使用已获授权的 Root 终端执行上方恢复命令。
4. 检查 `adb shell pm list packages --user 0 com.miui.hybrid`，并检查系统应用信息。没有原始预装包时，命令无法恢复，不要刷写不匹配的组件或系统镜像。

卸载商店轻享、关闭模块或暂停界面修改，都**不会**自动恢复已经卸载的快应用框架，也不会改变手机的 Root 状态。
