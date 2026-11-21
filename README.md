# 商店轻享 · MarketLite

小米应用商店的界面自定义模块，由 ChatGPT 编写，与用户共同实机测试。免费开源（GPL-3.0-or-later）。

非小米官方项目，按现状提供，不保证兼容所有版本。

## 下载

[GitHub Releases（下载安装包）](https://github.com/w999w/MarketLite/releases)

0.4.1-alpha APK SHA-256: `DA3201B96E9713C89ECC579086A56DE59F730F364631F0F613BDF4F154CA1D01`

- 使用 Vector/Xposed 兼容框架，建议作用域仅为 `com.xiaomi.market` 主进程。
- 功能包括广告与推荐的展示调整、首页标签管理、底栏与个人页快捷入口；不修改安全检查、安装验证、下载请求或设备标识。
- 普通界面设置无需 Root；终止商店与主用户级快应用卸载/恢复才需要单独授权。

## 实机测试环境

| 项目 | 测试环境 |
| --- | --- |
| 机型 | Xiaomi 14 Pro（23116PN5BC / shennong） |
| 系统 | HyperOS 3.0.310.0.WNBCNXM（中国大陆版） |
| Android | 16 |
| 小米应用商店 | 4.131.s.00 |
| 模块框架 | Vector |

其他机型及商店版本尚未全面验证；仅在已知界面结构与方法签名匹配时应用规则。

## 使用与恢复

安装后在 Vector 启用模块并使用模块建议范围，重新打开商店；应用首页提供权限检查与 GitHub Releases 更新检查。设置保存后自动同步，已经加载的数据可能需要重新进入对应页面。下载新版本后由 Android 系统确认安装。

停用模块或卸载本应用后，强行停止并重新打开商店，即可恢复原始界面；卸载本应用会清除本地配置。通过系统工具卸载的快应用框架不会随本应用卸载自动恢复，请参阅 [快应用命令与恢复](docs/quick-app-recovery.md)。

## 构建

JDK 17、Gradle 8.11.1、Android SDK 35：`gradle :app:assembleDebug`。本地工作目录中的 `build-local.ps1`、`test-local.ps1` 用于已有 Windows 测试环境。CI 的 debug 签名可能与本地测试 APK 不同，不保证直接覆盖安装。

适配参考：[NewFuckMarketAds](https://github.com/callng/NewFuckMarketAds)（GPL-3.0）。[GPL-3.0 许可证](https://www.gnu.org/licenses/gpl-3.0.html)。
