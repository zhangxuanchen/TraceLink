// 浏览器端到端验收：完成地图四视图 + 工单详情九条件卡片 + 执行者视角。
// 前置：后端 8090 与前端 dev server 5180 已启动（web 目录 npm run dev），数据库有种子数据。
// 运行：node scripts/e2e-browser.mjs
//   依赖 playwright-core 与 Chromium 缓存，按需用环境变量指定：
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core
//   CHROME_PATH=/path/to/Chromium.app/Contents/MacOS/Chromium
//   BASE=http://localhost:5180  OUT=/tmp/tracelink-e2e
// 副作用：会把一条未读提醒标记为已读，用于验证已读链路；不改工单与条件配置。
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
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
  check('侧栏里 M4/M5 仍是占位', await page.locator('.nav-phase').count() === 2)

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
  await page.screenshot({ path: `${SHOTS}/08-executor-inbox.png`, fullPage: true })
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
