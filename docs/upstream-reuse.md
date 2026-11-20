# 上游可复用映射（已核对）

核对方式：拉取 `keiyoushi/extensions-source`（commit `ef952a6`）后，逐个读取 `src/zh/<module>/build.gradle.kts`
中的 `baseUrl` 与 `deeplink.host(...)`，与 `shareBookSource.json` 的 `bookSourceUrl` 逐条比对域名。

结论：**14 条**输入源与上游模块同域名，直接安装 Keiyoushi 仓库中的对应扩展即可，不需要在本项目重写。

| 输入条目 | 源名称 | 输入域名 | 上游模块 | 上游 baseUrl / deeplink |
| --- | --- | --- | --- | --- |
| 03 | 嬉皮漫画 | `m.hipmh.com` | `zh/xipimanhua` | `https://m.hipmh.com` |
| 06 / 13 | 看漫 | `m.kanman.com` | `zh/kanman` | `https://www.kanman.com`，deeplink 含 `m.kanman.com` |
| 08 | 再漫画 | `manhua.zaimanhua.com` | `zh/zaimanhua` | `https://manhua.zaimanhua.com` |
| 16 | MY漫画辞晨 | `mycomic.com` | `zh/mycomic` | `https://mycomic.com` |
| 20 / 45 | 拷贝漫画 | `www.mangacopy.com` | `zh/kaobeimanhua` | deeplink 含 `www.mangacopy.com`、`copy4000.com` 等镜像族 |
| 30 | 漫画小屋 | `m.dm5.com` | `zh/dm5` | deeplink 含 `m.dm5.com` |
| 33 / 51 | 快看漫画 | `m.kuaikanmanhua.com` | `zh/kuaikanmanhua` | `https://www.kuaikanmanhua.com` + 通配子域 |
| 42 | 咚漫 | `m.dongmanmanhua.cn` | `zh/dongmanmanhua` | `https://www.dongmanmanhua.cn`（m 子域 404，走 www） |
| 49 | 漫畫狗网 | `dogemanga.com` | `zh/dogemanga` | `https://dogemanga.com` |
| 54 / 58 | 酸奶漫画 | `m.kkk.com` / `m.1kkk.com` | `zh/jisumanhua` | `https://www.1kkk.com`，deeplink 含 `m.1kkk.com` |

这 14 条覆盖了原清单里 9 个热门源中的 6 个（看漫、拷贝、快看、咚漫、再漫画、漫画小屋）。

## 未归入上游的三条"看起来像"

| 条目 | 原因 |
| --- | --- |
| 09 拷贝漫画 `www.kaobei.org` | 上游 `kaobeimanhua` 的域名族不含 `kaobei.org`，且该源搜索规则含 JS，需单独验证 |
| 10 包子漫画 `cn.bzmanga.com` | 上游 `baozimanhua` 的域名族是 `baozimh.com / webmota.com / kukuc.co / twmanga.com`，不含 `bzmanga.com` |
| 35 18禁漫 `m.18mhx.com` | 上游 `eighteenmanhua` 的 baseUrl 是 `https://18mh.org`，域名不同 |

## 使用方式

Mihon 0.21.3 → 扩展仓库 → 添加 Keiyoushi 仓库地址（当前为 `repo.json` + `index.pb` 新版格式）：

```
https://github.com/keiyoushi/extensions/raw/repo/index.pb
```

本项目**不复制**上游 APK，也不把上游地址当作本项目的仓库地址。
