# 隔离：Fuman（腐漫）

**状态：代码保留，不上架、不进索引。** 站点恢复图片域名后可直接重新启用。

## 为什么隔离

2026-11-21 复测（站点正文页仍在线，但图片全挂）：

| 检查 | 结果 |
| --- | --- |
| 首页 / 搜索 / 详情 / 章节解析 | 正常：首页 25 部、搜索有结果、章节 160 条 |
| 封面与阅读页图片地址 | 全部指向 `img.uumanhua.xyz/bookimages/...` |
| `img.uumanhua.xyz` DNS | **NXDOMAIN**（阿里 DNS DoH 与 DNSPod DoH 均 `Status 3`，权威 SOA 为 `xyz.` TLD） |
| 根域 `uumanhua.xyz` DNS | 同样 NXDOMAIN，说明域名已不存在，不是解析污染或地区限制 |
| 主域是否代理图片 | `https://www.fumanmanhua.com/bookimages/...` → 404 |
| 抽样范围 | 4 部漫画（各取首章并抽查相邻章），图片域名无一例外 |

页面本身提示换域名为 `www.uuwangzhi.com`，该域名同样无法解析，属于站点侧的域名问题，扩展无法修复。

结论：能列出书目但一张图都加载不出来。按"不把失效源标成可用"的原则，模块移出 `src/`，APK 与索引 entry 一并撤下。

## 恢复步骤

1. 确认新图片域名可解析，且单页图片返回 `200 image/*`。
2. 如需改域名，同步修改 `Fuman.kt` 的 `baseUrl` 与 `@Source` 的 `baseUrl`。
3. `git mv quarantine/fuman src/zh/fuman`
4. 重新编译，APK 放回 `gh-pages` 的 `apk/`，在 `index.min.json` 加回 entry，更新 `repo.json` 指纹（若换签名）。
