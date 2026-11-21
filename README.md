# Mihon Comic Sources

把原始 `shareBookSource.json`（58 条阅读 APP 书源）迁移为 **Mihon 0.21.3** 可用的原生 Kotlin 扩展仓库。

- 扩展仓库地址（二选一，都能直接加进 Mihon）：
  - 主：`https://cdn.jsdelivr.net/gh/Serena-01234/mihon-comic-sources@gh-pages/index.min.json`
  - 备：`https://raw.githubusercontent.com/Serena-01234/mihon-comic-sources/gh-pages/index.min.json`
- 目标版本：Mihon 0.21.3（extensions-lib 1.6，`SUPPORTED_LIB_VERSIONS = [1.4, 1.6]`）
- 本项目只提供**自己维护的扩展**；上游 Keiyoushi 已有的源，直接用 Keiyoushi 仓库，不重复造轮子。

## 现在能用的部分

| 项 | 状态 |
| --- | --- |
| 扩展仓库结构（`index.min.json` + `repo.json` + `apk/` + `icon/`） | 已生成，脚本 `scripts/build_repo.py` 与构建产物一致 |
| 签名 | 本地用 4096 位 RSA 证书签名发布版 APK，证书 SHA-256 `3931aba9…7d7db2`，写入 `repo.json` 的 `signingKeyFingerprint` |
| 腐漫（fumanmanhua.com） | **已编译 + 逐项实测**：搜索 / 详情 / 章节 / 图片页 |
| 连连看漫画（hmmfk.com） | **已编译 + 19 项自测全绿**：列表 27 / 搜索 16 / 章节 38 / 图片 10 张 200 |
| 包子漫画简中（cn.bzmanga.com） | **已编译 + 19 项自测全绿**：首页 144 / 搜索 85 / 章节 730 / 图片 200 webp |
| 其余 55 条 | 逐条给出处理结论与证据，见 `catalog/conversion-status.csv` |

## 安装步骤

1. Mihon 0.21.3 → 设置 → 浏览 → 扩展 → 扩展仓库 → 添加：
   `https://cdn.jsdelivr.net/gh/Serena-01234/mihon-comic-sources@gh-pages/index.min.json`
   （若 jsDelivr 慢，换 `https://raw.githubusercontent.com/Serena-01234/mihon-comic-sources/gh-pages/index.min.json`）
2. 在扩展列表中找到 **Fuman**（NSFW）、**Lianliankan**（NSFW）、**Baozi Manhua CN** 并安装。
3. 若同时需要看漫、拷贝漫画、快看、咚漫、再漫画、漫画小屋、酸奶漫画等，另外添加官方仓库
   `https://github.com/keiyoushi/extensions/raw/repo/index.pb`（见 `docs/upstream-reuse.md`）。

> 仓库 URL 必须以 `/index.min.json` 结尾：Mihon 0.21.3 的 `ExtensionStoreService` 在读到 `[` 开头的
> legacy 索引后，才会按 `index.min.json` → `repo.json` → `index.min.json` 的顺序完成仓库发现。
> APK 地址由 `NetworkLegacyExtension` 按 `"$storeBaseUrl/apk/$apk"` 拼接，所以 `apk/` 子目录结构不能改。

> 为什么不用 `serena-01234.github.io`：GitHub Pages 需要单独开通，当前 App 凭据没有 Pages 权限（API 返回 403）。
> 仓库内容已全部在 `gh-pages` 分支上，raw/jsDelivr 直读同一份文件，效果等价；等 Pages 开关打开后原地址即可用。

## 为什么不是 58 个扩展

原始书源里有大量规则是 `@js:` 脚本、JSONPath 接口、签名算法或登录态依赖，这些不能直接"翻译"成 Kotlin
选择器，必须逐个还原站点接口。`catalog/conversion-status.csv` 是逐条实测结果，状态含义：

| status | 含义 | 数量 |
| --- | --- | --- |
| `converted_verified` | 已写成 Kotlin、release APK 编译通过、四个环节对线上 DOM 自测通过 | 3 |
| `covered_upstream` | Keiyoushi 已有同域名模块，安装上游即可（已逐条比对 baseUrl/host） | 14 |
| `portable_pending` | 规则可静态解析且搜索实测有结果，尚未写成 Kotlin | 0 |
| `needs_login` | 章节或搜索接口强制登录（实测返回"用户未登录"） | 9 |
| `needs_api` | 搜索规则是 JSONPath 或图片密钥过期，需还原站点接口 | 9 |
| `needs_js` | 规则含 `@js` 脚本或阅读页由加密 JS 渲染 | 10 |
| `empty` | 请求成功但选择器未命中（站点改版/需 POST） | 8 |
| `dead` | 域名不存在、404 或连接被重置 | 4 |
| `needs_manual_port` | XPath 规则，需人工改写 | 1 |

统计口径与证据都写在 CSV 的 `evidence` 列，没有把失效源标成可用。

## 仓库结构

```
src/zh/fuman/                 # 原生 Kotlin 扩展（唯一已验证模块）
scripts/build_repo.py         # 从构建产物生成 index.min.json / repo.json
catalog/conversion-status.csv # 58 条源逐条结论 + 证据
docs/source-audit.md          # 审计方法与实测数据
docs/upstream-reuse.md        # 14 条上游可复用映射（已核对 baseUrl）
docs/release.md               # 签名、索引、Pages 发布与 Secrets 配置
.github/workflows/publish.yml # 编译 → 验签 → 生成索引 → 发布 gh-pages / Release
```

## 本地构建

```bash
git clone https://github.com/keiyoushi/extensions-source.git upstream
cd upstream && git checkout ef952a64df831c33c29c276d9c283b4cd7633ce1 && cd ..
cp -R src/. upstream/src/
# 只编译本项目模块，避免拉全部 700+ 模块
python3 - <<'PY'
import os
p="upstream/settings.gradle.kts"; s=open(p).read()
mods=[f"src:{l}:{m}" for l in os.listdir("src") for m in os.listdir(f"src/{l}")]
s=s.replace("loadAllIndividualExtensions()","\n".join(f'include("{m}")' for m in mods),1)
open(p,"w").write(s)
PY
cd upstream
KEY_STORE_PASSWORD=... ALIAS=mihoncomics KEY_PASSWORD=... ./gradlew :src:zh:fuman:assembleRelease
```

## 许可与归属

扩展构建系统来自 [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source)（Apache-2.0），
本项目以固定 commit 复用其 Gradle 插件，不复制其扩展源码。
