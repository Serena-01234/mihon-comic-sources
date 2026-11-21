# 源数据审计（58 条）

审计对象：仓库所有者提供的 `shareBookSource.json`（58 条，`bookSourceType=2` 56 条、`=0` 2 条）。
原始文件包含站点 Cookie、登录脚本和私有请求头，**不进入本仓库**，仅作迁移输入。

## 审计方法

1. **规则解析**：把每条源的 `searchUrl / ruleSearch / ruleBookInfo / ruleToc / ruleContent` 解析成结构化记录，
   识别 `@js:`、`{{表达式}}`、`$..`（JSONPath）、XPath（`//div[...]`）等非 CSS 语法。
2. **真实请求**：用与书源一致的 UA/Referer 对 58 个域名发起真实搜索请求（关键词「海贼」「斗罗」），
   记录 HTTP 状态码与命中条数。**不使用缓存、不使用伪造数据。**
3. **规则执行**：用一个最小 Legado 规则解释器执行 `bookList / name / bookUrl`，
   能命中条目才算「规则可静态解析」。
4. **接口复核**：对可疑源人工复核关键接口（例如漫客栈章节图片接口）。

复现方式：`python3 audit_sources.py`（脚本在提交历史中，依赖 requests + beautifulsoup4）。

## 结果

| status | 数量 | 判定依据 |
| --- | --- | --- |
| `converted_verified` | 2 | 已写成 Kotlin，编译通过，四环节线上实测通过（连连看、包子漫画简中） |
| `converted_site_broken` | 1 | 已写成 Kotlin 并编译通过，但站点图片域名 NXDOMAIN，隔离不上架（腐漫） |
| `covered_upstream` | 14 | Keiyoushi `src/zh/*` 的 baseUrl/host 与本条一致 |
| `needs_login` | 9 | 章节/搜索接口返回未登录，或源内含登录脚本 |
| `needs_api` | 9 | 搜索规则是 JSONPath 或 App API 主机 |
| `needs_js` | 10 | `@js` 脚本或页面 JS 渲染 |
| `empty` | 8 | 请求成功但选择器未命中（改版/需 POST） |
| `dead` | 4 | DNS 不存在、404、连接被重置 |
| `needs_manual_port` | 1 | XPath 规则，阅读 APP 专有语法 |

逐条结论见 `catalog/conversion-status.csv`。

## 关键实测记录

- **漫客栈（15/41/43/52/55）**：搜索页与详情页可匿名访问，但章节图片接口
  `https://comic.mkzcdn.com/chapter/content/v1/?chapter_id=…&comic_id=…` 返回
  `{"code":"302","message":"用户未登录"}`。书源之所以能用，是因为 JSON 里带了登录 Cookie，
  这类凭据不能进公开仓库，因此标记 `needs_login` 而不是"已转换"。
- **包子漫画（10）**：`cn.bzmanga.com` 首页 144 条、搜索 85 条。章节目录的 `<a>` 全部指向
  `/user/page_direct?comic_id=…&section_slot=…&chapter_slot=…`，初看像"所有链接相同"，实际靠
  `section_slot/chapter_slot` 两个查询参数区分；直接按 DOM 顺序取会得到回文序列
  （`729…0…729`），因此扩展按两个 slot 的数值重排，得到 730 章的正确顺序。
- **腐漫（23）**：正文页仍在线（列表 25 部、章节 160 条可解析），但封面与全部阅读页图片都指向
  `img.uumanhua.xyz`，该域名与根域 `uumanhua.xyz` 在阿里 DNS / DNSPod 均返回 NXDOMAIN，
  主域也不代理图片。属站点资源失效，代码保留在 `quarantine/fuman/`，不上架。
- **咚漫（42）**：`m.dongmanmanhua.cn` 搜索路径 404，上游 `zh/dongmanmanhua` 使用
  `www.dongmanmanhua.cn`，因此归入 `covered_upstream`。
- **漫画小屋（37）**：`m.dm.com` DNS 解析失败，属失效源。

## 未做的事（避免误导）

- 没有把原始 JSON 直接包装成扩展，也没有用空列表冒充功能。
- 没有把需要登录/已失效的源标成可用。
- `empty` 与 `needs_js` 两类仍需逐站人工还原接口，不是"已完成"。
