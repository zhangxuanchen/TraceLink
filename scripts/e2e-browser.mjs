// 浏览器端到端验收：完成地图四视图 + 工单详情九条件卡片 + 知识库检索问答 + 执行者视角。
// 前置：后端 8090 与前端 dev server 5180 已启动（web 目录 npm run dev），数据库有种子数据。
// 运行：node scripts/e2e-browser.mjs
//   依赖 playwright-core 与 Chromium 缓存，按需用环境变量指定：
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core
//   CHROME_PATH=/path/to/Chromium.app/Contents/MacOS/Chromium
//   BASE=http://localhost:5180  OUT=/tmp/tracelink-e2e
// 副作用：1) 会把一条未读提醒标记为已读；2) 会上传一篇标题带【E2E】的知识文档（生产部私有，
// 纯字母唯一标识便于越权零命中断言）；3) 会触发一次历史措施沉淀。不改工单与条件配置。
// 清理 E2E 知识文档：mysql -uroot -ptracelink tracelink -e "delete c from kb_chunk c join kb_document d
//   on c.doc_id=d.id where d.title like '【E2E】%'; delete from kb_document where title like '【E2E】%';"
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import process from 'node:process'

const require = createRequire(import.meta.url)
const CORE = process.env.PLAYWRIGHT_CORE || '/opt/homebrew/lib/node_modules/openclaw/node_modules/playwright-core'
let chromium
try {
  ({ chromium } = require(CORE))
} catch (e) {
  console.error(`找不到 playwright-core：${CORE}\n可先 npm i -D playwright 再用 PLAYWRIGHT_CORE=./node_modules/playwright-core 指定`)
  process.exit(2)
}

const BASE = process.env.BASE || 'http://localhost:5180'
const SHOTS = process.env.OUT || '/tmp/tracelink-e2e'
const CHROME = process.env.CHROME_PATH
  || '/Users/zxc/Library/Caches/ms-playwright/chromium-1247/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing'
mkdirSync(SHOTS, { recursive: true })

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok), detail })
  console.log(`${ok ? '✔' : '✘'} ${name}${detail ? ` — ${detail}` : ''}`)
}

const browser = await chromium.launch({ headless: true, executablePath: CHROME })
const page = await browser.newPage({ viewport: { width: 1440, height: 960 } })

const consoleErrors = []
const pageErrors = []
const httpErrors = []
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(`${m.text()} @ ${m.location()?.url || ''}`)
})
page.on('pageerror', (e) => pageErrors.push(String(e)))
page.on('response', (r) => {
  if (r.status() >= 400) httpErrors.push(`${r.status()} ${r.url()}`)
})

async function login(username, password) {
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('#username')
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type=submit]')
  await page.waitForSelector('.shell', { timeout: 15000 })
}

async function goMap() {
  await page.goto(`${BASE}/#/map`)
  await page.waitForSelector('.tabs .tab', { timeout: 15000 })
}

try {
  // ---------------------------------------------------------------- 管理员登录
  await login('admin', 'admin123')
  check('admin 登录后进入应用外壳', await page.locator('.shell').count() === 1)
  const navTitles = await page.locator('.nav-text').allInnerTexts()
  check('侧栏出现完成地图入口', navTitles.includes('完成地图'), navTitles.join('/'))
  const phaseCount = await page.locator('.nav-phase').count()
  check('侧栏只剩 M5 一个占位', phaseCount === 1, `${phaseCount} 个占位`)

  // ---------------------------------------------------------------- 部门矩阵
  await goMap()
  const tabLabels = await page.locator('.tabs .tab').allInnerTexts()
  check('管理者可见四个 tab', tabLabels.length === 4, tabLabels.join('/'))

  await page.waitForSelector('table.matrix tbody tr', { timeout: 15000 })
  const gateCols = await page.locator('th.gate-col').count()
  check('矩阵有九列条件', gateCols === 9, `${gateCols} 列`)
  const codes = (await page.locator('th.gate-col .gate-code').allInnerTexts()).map((t) => t.trim())
  check('列顺序为 G1..G9', codes.join(',') === 'G1,G2,G3,G4,G5,G6,G7,G8,G9', codes.join(','))
  const rowCount = await page.locator('table.matrix tbody tr').count()
  check('矩阵有在办工单行', rowCount > 0, `${rowCount} 行`)
  const hint = await page.locator('.toolbar .muted').first().innerText().catch(() => '')
  check('工具栏显示快照时间与在办数', /快照.*在办 \d+ 张/.test(hint), hint)
  check('矩阵无陈旧提示条', await page.locator('.banner').count() === 0)
  await page.screenshot({ path: `${SHOTS}/01-matrix.png`, fullPage: true })

  // 点 G6 表头排序：未达成排最前、不适用排最后
  const g6Index = codes.indexOf('G6')
  await page.locator('th.gate-col', { hasText: 'G6' }).first().click()
  await page.waitForTimeout(400)
  const g6Classes = await page.locator('table.matrix tbody tr').evaluateAll((rows, index) => rows.map((r) => {
    const cells = [...r.querySelectorAll('td.gate-cell')]
    const c = cells[index]
    if (!c) return -1
    return c.classList.contains('cell-todo') ? 0 : (c.classList.contains('cell-na') ? 2 : 1)
  }), g6Index)
  const g6Ordered = g6Classes.every((v, i) => i === 0 || g6Classes[i - 1] <= v)
  check('点 G6 表头后缺口排到最前', g6Classes.length > 0 && g6Ordered, `序 ${g6Classes.slice(0, 8).join('')}`)
  check('排序列表头高亮', await page.locator('th.gate-col.sorted').count() === 1)
  await page.screenshot({ path: `${SHOTS}/02-matrix-sorted.png`, fullPage: true })

  // 取一张在办单 ID，供事项视图用
  const firstTask = await page.evaluate(async () => {
    const tk = localStorage.getItem('tracelink.token') || ''
    const r = await fetch('/api/v1/completion-map/matrix?page=1&pageSize=5', { headers: { Authorization: `Bearer ${tk}` } })
    const j = await r.json()
    return j.data?.rows?.[0] || null
  })
  check('取到矩阵首行工单', Boolean(firstTask?.taskId), firstTask ? `${firstTask.taskNo} id=${firstTask.taskId}` : '无')

  // ---------------------------------------------------------------- 缺口排行
  await page.locator('.tab', { hasText: '缺口排行' }).click()
  await page.waitForSelector('.bar-row', { timeout: 15000 })
  const barRows = await page.locator('.bar-row').count()
  check('排行按九条条件列出', barRows === 9, `${barRows} 行`)
  const gapTitle = await page.locator('.gap-grid .block-title').first().innerText()
  const totalTodo = Number((gapTitle.match(/共 (\d+) 个缺口/) || [])[1] || 0)
  const barValues = (await page.locator('.bar-row .bar-value').allInnerTexts()).map((t) => Number(t.trim()) || 0)
  const sumBars = barValues.reduce((a, b) => a + b, 0)
  check('按条件合计等于总缺口数', totalTodo > 0 && sumBars === totalTodo, `${sumBars} vs ${totalTodo}`)
  const holderRows = await page.locator('.rank-list li').count()
  check('按承担者排行有数据', holderRows > 0, `${holderRows} 人`)
  const topRows = await page.locator('.gap-grid table.table tbody tr').count()
  check('滞留前 20 表有行', topRows > 0, `${topRows} 行`)
  await page.screenshot({ path: `${SHOTS}/03-gaps.png`, fullPage: true })

  // ---------------------------------------------------------------- 事项视图
  await page.locator('.tab', { hasText: '事项视图' }).click()
  await page.waitForSelector('.matter-search input', { timeout: 10000 })
  await page.fill('.matter-search input', String(firstTask.taskId))
  await page.locator('.matter-search .btn').click()
  await page.waitForSelector('.matter-head', { timeout: 15000 })
  const matterTitle = await page.locator('.matter-head strong').first().innerText()
  const matterCount = await page.locator('.matter-list > li').count()
  const chipCount = await page.locator('.matter-list .gate-chip').count()
  check('事项视图返回工单链', matterCount > 0, matterTitle)
  check('每张单带九条条件胶囊', chipCount === matterCount * 9, `${chipCount} / ${matterCount}×9`)
  await page.screenshot({ path: `${SHOTS}/04-matter.png`, fullPage: true })

  // ---------------------------------------------------------------- 我的提醒
  await page.locator('.tab', { hasText: '我的提醒' }).click()
  await page.waitForSelector('.inbox li', { timeout: 15000 })
  const inboxCount = await page.locator('.inbox li').count()
  check('提醒收件箱有记录', inboxCount > 0, `${inboxCount} 条（最多 30）`)
  const unreadText = async () => (await page.locator('.block-title').filter({ hasText: '我的缺口提醒' }).first().innerText()).match(/未读 (\d+) 条/)
  const unreadBefore = Number((await unreadText() || [])[1] ?? -1)
  const readBtn = page.locator('.inbox li .btn-link', { hasText: '标记已读' }).first()
  const hadUnread = await readBtn.count() > 0
  if (hadUnread) {
    await readBtn.click()
    await page.waitForTimeout(600)
  }
  const unreadAfter = Number((await unreadText() || [])[1] ?? -1)
  check('标记已读后未读数减一', !hadUnread || unreadAfter === unreadBefore - 1, `${unreadBefore} → ${unreadAfter}`)
  await page.screenshot({ path: `${SHOTS}/05-inbox.png`, fullPage: true })

  // ---------------------------------------------------------------- 详情九条件
  await page.locator('.tab', { hasText: '部门矩阵' }).click()
  await page.waitForSelector('table.matrix tbody tr', { timeout: 15000 })
  await page.locator('table.matrix tbody tr').first().click()
  await page.waitForSelector('.gate-list .gate-item', { timeout: 15000 })
  const gateItems = await page.locator('.gate-list .gate-item').count()
  check('详情右栏九条条件齐全', gateItems === 9, `${gateItems} 条`)
  const rateText = await page.locator('.gate-block .block-title').first().innerText()
  check('详情显示完成度', /达成 \d+%/.test(rateText), rateText.replace(/\n/g, ' '))
  const todoGates = await page.locator('.gate-item.gate-todo').count()
  const actions = await page.locator('.gate-item .gate-action').count()
  check('未达成条件可点去处理', todoGates > 0 && actions > 0, `未达成 ${todoGates} 条 / 动作 ${actions} 个`)
  const holderText = await page.locator('.gate-item .gate-meta').first().innerText()
  check('条件行带承担者', /承担者 \S/.test(holderText), holderText.trim())

  // 点 G3（问题关闭）去处理 → 滚动到问题项
  const g3 = page.locator('.gate-item', { hasText: 'G3' }).first()
  await g3.locator('.gate-action').click().catch(() => {})
  await page.waitForTimeout(500)
  const issueVisible = await page.locator('#issues').isVisible()
  check('G3 去处理跳到问题项区', issueVisible)
  await page.screenshot({ path: `${SHOTS}/06-detail.png`, fullPage: true })

  // 看事项链 → 跳到 map?taskId=
  const currentTaskId = page.url().split('/tasks/')[1] || ''
  await page.locator('.head-link', { hasText: '看事项链' }).first().click()
  await page.waitForSelector('.matter-head', { timeout: 15000 })
  const linked = page.url().includes(`taskId=${currentTaskId}`)
  check('看事项链回跳到事项视图并带 taskId', linked && page.url().includes('/map'), page.url())
  await page.screenshot({ path: `${SHOTS}/07-detail-to-matter.png`, fullPage: true })

  // ---------------------------------------------------------------- 知识库（M4）
  const kbMark = `KBX${Date.now().toString(36).toUpperCase()}`
  const kbTitle = `【E2E】${kbMark} 冲压工位毛刺处置制度`
  const kbFile = `${SHOTS}/kb-${kbMark}.md`
  writeFileSync(kbFile, `# 冲压工位毛刺处置制度（${kbMark}）

## 一、适用范围

本制度适用于生产部冲压工位。毛刺超标指单件毛刺高度超过 0.2 毫米，班组长应隔离当批在制品并在工单登记问题。

## 二、处置措施

1. 更换磨损模具，重新对刀并试冲三件，首件三检合格后再批量生产。
2. 增加去毛刺工序，使用锉刀与砂带机处理边角，处理后留存影像记录。
3. 班后组织原因分析，把模具保养周期写入点检表，防止同类问题重复发生。

## 三、记录与验收

处置完成后在工单追踪里写清措施、责任人与完成时间，提交验收时附首件三检记录。验收标识：${kbMark}
`, 'utf8')

  await page.goto(`${BASE}/#/kb`)
  await page.waitForSelector('.layout .table', { timeout: 15000 })
  check('知识库页面打开并有检索与文档库',
    await page.locator('.page-title', { hasText: '知识库' }).count() === 1
      && await page.locator('.ask-bar textarea').count() === 1
      && await page.locator('.card.block', { hasText: '文档库' }).count() === 1)
  check('管理员可见上传文档与沉淀历史措施',
    await page.locator('.head-actions .btn', { hasText: '上传文档' }).count() === 1
      && await page.locator('.head-actions .btn', { hasText: '沉淀历史措施' }).count() === 1)
  const docTableCols = await page.locator('.table thead th').count()
  check('文档库表头包含状态与同步时间', docTableCols >= 7, `${docTableCols} 列`)

  // 上传弹窗：字段齐全、选了部门才出现可见范围
  await page.locator('.head-actions .btn', { hasText: '上传文档' }).click()
  await page.waitForSelector('.mask .panel', { timeout: 10000 })
  check('上传弹窗含文件、标题、知识域、部门',
    await page.locator('.mask input[type=file]').count() === 1
      && await page.locator('.mask .field', { hasText: '知识域' }).locator('select').count() === 1
      && await page.locator('.mask .field', { hasText: '所属部门' }).locator('select').count() === 1)
  check('未选部门时不显示可见范围', await page.locator('.mask .field', { hasText: '可见范围' }).count() === 0)
  await page.locator('.mask input[type=file]').setInputFiles(kbFile)
  await page.fill('.mask input[placeholder="默认取文件名，不超过 200 字"]', kbTitle)
  await page.locator('.mask .field', { hasText: '所属部门' }).locator('select').selectOption('2')
  await page.waitForTimeout(200)
  check('选择部门后出现可见范围', await page.locator('.mask .field', { hasText: '可见范围' }).locator('select').count() === 1)
  check('选中文件名回显', (await page.locator('.mask .file-btn').innerText()).includes(kbMark),
    await page.locator('.mask .file-btn').innerText())
  await page.locator('.mask .panel-foot .btn', { hasText: '上传并解析' }).click()
  await page.waitForSelector('.mask', { state: 'detached', timeout: 20000 })
  check('上传成功后关闭弹窗', true)

  // 轮询文档行状态直到就绪（页面在管理员视角会自动同步并刷新）
  const kbRow = page.locator('.table tbody tr', { hasText: kbMark }).first()
  let kbStatus = ''
  for (let i = 0; i < 40 && kbStatus !== '已就绪'; i += 1) {
    await page.waitForTimeout(1000)
    if (await kbRow.count() === 0) continue
    kbStatus = (await kbRow.locator('td').nth(5).innerText()).trim()
  }
  check('上传文档进入文档库并解析就绪', kbStatus === '已就绪', kbStatus || '未出现在列表')
  const kbChunks = (await kbRow.locator('td').nth(4).innerText()).trim()
  check('就绪文档有切片数', Number(kbChunks) > 0, `${kbChunks} 片`)
  check('列表显示生产部与可见范围', (await kbRow.locator('td').nth(2).innerText()).includes('生产部')
    && (await kbRow.locator('td').nth(3).innerText()).includes('部门'), await kbRow.locator('td').nth(3).innerText())
  await page.screenshot({ path: `${SHOTS}/08-kb-doc.png`, fullPage: true })

  // 检索：纯字母唯一标识命中本文档，点击命中项看切片原文
  await page.fill('.ask-bar textarea', kbMark)
  await page.locator('.ask-controls button.btn-primary').click()
  await page.waitForSelector('.result .hit', { timeout: 15000 })
  const searchHead = await page.locator('.result .result-head').innerText()
  const searchTotal = Number((searchHead.match(/命中 (\d+) 条/) || [])[1] || 0)
  check('检索命中文档切片', searchTotal > 0, searchHead.replace(/\n/g, ' '))
  check('检索结果标注检索来源', /本地降级|RAGFlow/.test(searchHead), searchHead.split('\n')[0])
  await page.locator('.result .hit').first().click()
  await page.waitForSelector('.mask .chunk-body', { timeout: 15000 })
  const chunkBody = await page.locator('.mask .chunk-body').innerText()
  check('点命中项可回溯切片原文', chunkBody.includes(kbMark), `${chunkBody.length} 字`)
  check('切片弹窗带文档编号', await page.locator('.mask .chunk-meta .tag', { hasText: '文档 #' }).count() >= 1)
  await page.screenshot({ path: `${SHOTS}/09-kb-chunk.png`, fullPage: true })
  await page.locator('.mask .panel-foot .btn', { hasText: '关闭' }).click()
  await page.waitForSelector('.mask', { state: 'detached', timeout: 10000 })

  // 智能问答：先给召回，再流式答案；未配密钥时应给出 TL-5031 可解释提示
  await page.locator('.seg button', { hasText: '智能问答' }).click()
  await page.fill('.ask-bar textarea', kbMark)
  await page.locator('.ask-controls button.btn-primary').click()
  await page.waitForSelector('.result .form-error, .result .answer', { timeout: 25000 })
  const askHead = await page.locator('.result .result-head').innerText()
  check('问答先返回召回条数', /召回 \d+ 条/.test(askHead), askHead.replace(/\n/g, ' '))
  const askError = await page.locator('.result .form-error').count() ? await page.locator('.result .form-error').innerText() : ''
  if (askError) {
    check('未配模型密钥时问答返回 TL-5031 提示', askError.includes('TL-5031') && askError.includes('模型密钥未配置'),
      askError.replace(/\n/g, ' ').slice(0, 90))
  } else {
    const answer = await page.locator('.result .answer').innerText()
    const citations = await page.locator('.result .citation').count()
    check('配置模型密钥时返回答案与可点引用', answer.length > 0 && citations > 0, `${answer.length} 字 / ${citations} 引用`)
  }
  check('问答结果附带召回切片', await page.locator('.result .hits.compact .hit').count() > 0,
    `${await page.locator('.result .hits.compact .hit').count()} 条`)
  await page.screenshot({ path: `${SHOTS}/10-kb-ask.png`, fullPage: true })

  // 历史问题与措施沉淀
  await page.locator('.head-actions .btn', { hasText: '沉淀历史措施' }).click()
  await page.waitForSelector('.mask .panel', { timeout: 10000 })
  check('沉淀弹窗含部门与扫描上限',
    await page.locator('.mask .field', { hasText: '限定部门' }).count() === 1
      && await page.locator('.mask .field', { hasText: '最多扫描' }).count() === 1)
  await page.locator('.mask .panel-foot .btn', { hasText: '开始沉淀' }).click()
  await page.waitForSelector('.mask .history-result', { timeout: 90000 })
  const histTags = (await page.locator('.mask .history-result .tag').allInnerTexts()).map((t) => t.trim())
  check('沉淀返回扫描/新建/跳过结果',
    histTags.length === 3 && /扫描 \d+/.test(histTags[0]) && /新建 \d+/.test(histTags[1]) && /跳过 \d+/.test(histTags[2]),
    histTags.join(' / '))
  await page.screenshot({ path: `${SHOTS}/11-kb-history.png`, fullPage: true })
  await page.locator('.mask .panel-foot .btn', { hasText: '关闭' }).click()
  await page.waitForSelector('.mask', { state: 'detached', timeout: 10000 })

  // ---------------------------------------------------------------- 执行者视角
  await page.locator('.who .btn-link', { hasText: '退出' }).click()
  await page.waitForSelector('#username', { timeout: 10000 })
  await login('wangxb', 'admin123')
  await goMap()
  const exTabs = await page.locator('.tabs .tab').allInnerTexts()
  check('执行者只看到事项视图与提醒', exTabs.length === 2 && exTabs.includes('我的提醒'), exTabs.join('/'))
  check('执行者看不到重算快照', await page.locator('.head-actions button', { hasText: '重算快照' }).count() === 0)
  await page.waitForSelector('.inbox li', { timeout: 15000 })
  check('执行者收件箱有记录', await page.locator('.inbox li').count() > 0)
  await page.screenshot({ path: `${SHOTS}/12-executor-inbox.png`, fullPage: true })

  // 执行者看知识库：无管理入口，其它部门私有文档零命中
  await page.goto(`${BASE}/#/kb`)
  await page.waitForSelector('.layout .table', { timeout: 15000 })
  check('执行者看不到上传与沉淀入口',
    await page.locator('.head-actions .btn', { hasText: '上传文档' }).count() === 0
      && await page.locator('.head-actions .btn', { hasText: '沉淀历史措施' }).count() === 0)
  check('执行者文档库不列生产部私有文档',
    await page.locator('.table tbody tr', { hasText: kbMark }).count() === 0)
  await page.fill('.ask-bar textarea', kbMark)
  await page.locator('.ask-controls button.btn-primary').click()
  await page.waitForSelector('.result .result-head', { timeout: 15000 })
  const exSearchHead = await page.locator('.result .result-head').innerText()
  const exTotal = Number((exSearchHead.match(/命中 (\d+) 条/) || [])[1] || 0)
  check('执行者检索其它部门私有文档零命中', exTotal === 0, exSearchHead.replace(/\n/g, ' '))
  await page.screenshot({ path: `${SHOTS}/13-executor-kb.png`, fullPage: true })
} catch (e) {
  check('脚本执行未中断', false, String(e).split('\n')[0])
  await page.screenshot({ path: `${SHOTS}/99-failure.png`, fullPage: true }).catch(() => {})
} finally {
  check('无浏览器运行时异常', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '))
  check('无接口 4xx/5xx', httpErrors.length === 0, httpErrors.slice(0, 5).join(' | '))
  check('无控制台错误', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '))
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n通过 ${results.length - failed.length} 项，失败 ${failed.length} 项；截图在 ${SHOTS}`)
process.exit(failed.length ? 1 : 0)
