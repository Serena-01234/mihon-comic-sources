# 签名、索引与发布

## 1. 仓库协议（与 Mihon 0.21.3 对齐）

Mihon 0.21.3 的 `ExtensionStoreService.fetch()` 按首字节判断格式：

| 首字节 | 处理 |
| --- | --- |
| `[` | 只接受以 `/index.min.json` 结尾的 URL，随后改读同目录 `repo.json` |
| `{` | 先按 `NetworkLegacyExtensionRepo` 解析；失败再按 protobuf store |
| 其它 | 直接按 protobuf `NetworkExtensionStore` 解析（`index.pb`） |

`repo.json` 里 `index_v2 == null` 时走 legacy 流程：再取 `/index.min.json` 解析成
`List<NetworkLegacyExtension>`。本项目就用这条链路，因此**不需要生成 protobuf**：

```
repo.json  -> {"index_v2": null, "meta": {... "signingKeyFingerprint": "<证书 SHA-256>"}}
index.min.json -> [{"name","pkg","apk","lang","code","version","nsfw","sources":[...]}]
apk/<file>.apk
```

注意 legacy 条目里 `version` 必须是 `<libVersion>.<扩展版本>`（本项目 `1.6.1`），
Mihon 用 `version.substringBeforeLast('.')` 得到 `1.6` 做兼容性判断；
`ExtensionLoader.SUPPORTED_LIB_VERSIONS = [1.4, 1.6]`。

APK 签名校验：`extension.store.signingKey in ExtensionLoader.getSignatures(apk)`，
`getSignatures` 是证书 DER 的 SHA-256 **小写十六进制**（`Hash.encodeHex`），
所以 `repo.json` 里的指纹必须同格式（不带冒号、全小写）。

## 2. 密钥

本项目的发布证书（RSA 4096，10000 天）：

```
alias:     mihoncomics
cert SHA-256: 3931aba9b7cb45003a4f1d5ce3e6f648613ab0cfc5765ddaf20f0a547e7d7db2
```

keystore 与口令**不进仓库**，放在所有者本地和 GitHub Actions Secrets。

## 3. GitHub Secrets（4 个，必须手动添加一次）

Settings → Secrets and variables → Actions → New repository secret：

| Name | 值 |
| --- | --- |
| `KEYSTORE_B64` | `base64 -w0 signingkey.jks` 的输出 |
| `KEY_STORE_PASSWORD` | keystore 口令 |
| `ALIAS` | `mihoncomics` |
| `KEY_PASSWORD` | key 口令（与 keystore 相同） |

> 换密钥会导致已安装用户无法覆盖升级（Mihon 会报 "isn't signed with the same key"），
> 请妥善备份 `signingkey.jks`。

## 4. GitHub Pages

Settings → Pages → Build and deployment → Source: **Deploy from a branch** →
Branch: `gh-pages` / `root`。首次需要等 `publish.yml` 跑完生成 `gh-pages` 分支后再选。

最终仓库地址（Pages 开关未开启，先用直读地址，内容与 Pages 完全一致）：

```
https://cdn.jsdelivr.net/gh/Serena-01234/mihon-comic-sources@gh-pages/index.min.json
https://raw.githubusercontent.com/Serena-01234/mihon-comic-sources/gh-pages/index.min.json
```

## 5. 本地已验证的结果

```
$ apksigner verify --print-certs -v tachiyomi-zh.hmmfk-v1.6.1.apk
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
V2 Signer: certificate DN: CN=Serena-01234 Mihon Extensions, O=Serena-01234, C=CN
V2 Signer: certificate SHA-256 digest: 3931aba9b7cb45003a4f1d5ce3e6f648613ab0cfc5765ddaf20f0a547e7d7db2
```

manifest 关键字段（由 `kei.plugins.extension` 生成）：

```
package="eu.kanade.tachiyomi.extension.zh.hmmfk" versionCode="106001" versionName="1.6.1"
meta-data tachiyomix.extensionLib = 1.6
meta-data tachiyomi.extension.nsfw = 1
source id = 1138712607578143340
```

## 6. CI 流程（存档于 `docs/ci-workflow.yml`）

1. 检出本仓库；
2. 按 `UPSTREAM_SHA` 拉取 Keiyoushi 构建系统（固定 commit，避免上游漂移）；
3. 覆盖 `src/`，改写 `settings.gradle.kts` **只** include 本项目模块；
4. 从 Secret 还原 `signingkey.jks`；
5. `assembleRelease` 编译 + 签名，日志作为 artifact 上传；
6. `apksigner verify` 验签；
7. `scripts/build_repo.py` 从构建产物（merged manifest + KSP `Generated.kt`）生成索引，
   指纹从 keystore 现算；
8. 发布到 `gh-pages`，并把 APK 附到 `build-<run_number>` Release。

> GitHub App 没有 `workflows` 权限，工作流文件无法由 App 推送，因此存为 `docs/ci-workflow.yml`。
> 手动启用：复制到 `.github/workflows/publish.yml` 后提交（需具备 workflows 权限的账号）。
