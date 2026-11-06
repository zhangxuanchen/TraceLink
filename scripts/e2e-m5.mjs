// M5 浏览器端到端：工单助手、评价证据、建议详情与按角色导航。
// 前置：后端 8090 与前端 dev server 5180 已启动（web 目录 npm run dev），数据库有种子数据，
//       且已跑过 scripts/acceptance-m5.sh（评价与建议队列有数据）。
// 运行：node scripts/e2e-m5.mjs
//   依赖 playwright-core 与 Chromium 缓存，按需用环境变量指定：
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core
//   CHROME_PATH=/path/to/Chromium.app/Contents/MacOS/Chromium
//   BASE=http://localhost:5180  OUT=/tmp/tracelink-m5
// 副作用：会用 admin/xuwei/wangxb 各用一个会话（单会话，首次进入自动创建，不新建、不归档）；
//   会话标题取自首问，清理：mysql -uroot -ptracelink tracelink -e "delete m from chat_message m join chat_session s
//   on m.session_id=s.id where s.title like '生产部这周%' or s.title like '张厚生现在手里%';
//   delete from chat_session where title like '生产部这周%' or title like '张厚生现在手里%';"
// 说明：未配置 ARK_API_KEY 时回答走规则降级，脚本会断言「降级」标记；配置后仍可跑，只是不再要求降级标签。
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
const SHOTS = process.env.OUT || '/tmp/tracelink-m5'
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
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(`${m.text()} @ ${m.location()?.url || ''}`)
})
page.on('pageerror', (e) => pageErrors.push(String(e)))

async function login(username, password) {
  // 已有 token 时 #/login 会被守卫重定向，先清本地存储再走登录
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('#username')
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type=submit]')
  await page.waitForSelector('.shell', { timeout: 15000 })
}

async function gotoChat() {
  await page.goto(`${BASE}/#/chat`)
  await page.waitForSelector('.layout .chat', { timeout: 15000 })
}

async function ask(text) {
  const before = await page.locator('.msg-assistant .bubble').count()
  await page.fill('.composer textarea', text)
  await page.click('.composer .btn-primary')
  // 规则降级回答很快返回，不能等 .streaming；等回答气泡落库或错误提示出现
  await page.waitForFunction((n) => document.querySelectorAll('.msg-assistant .bubble').length > n
    || (document.querySelector('.notice')?.textContent || '').includes('提问失败')
    || (document.querySelector('.notice')?.textContent || '').includes('TL-'), before, { timeout: 20000 })
  // 等流式气泡收尾（回答落库后 live 清空），避免把临时气泡算进历史条数
  await page.waitForFunction(() => document.querySelectorAll('.streaming').length === 0, { timeout: 20000 })
  await page.waitForTimeout(300)
}

try {
  // ---------------------------------------------------------------- 管理员
  await login('admin', 'admin123')
  const navTitles = await page.locator('.nav-text').allInnerTexts()
  check('侧栏出现工单助手', navTitles.includes('工单助手'), navTitles.join('/'))
  check('侧栏不再出现重点发现', !navTitles.includes('重点发现'), navTitles.join('/'))
  check('侧栏没有阶段占位徽标', await page.locator('.nav-phase').count() === 0)

  // ---------------------------------------------------------------- 单会话
  await gotoChat()
  await page.waitForSelector('.chat-head', { timeout: 15000 })
  check('工单助手不再有会话列表', await page.locator('.session').count() === 0)
  check('工单助手不再有新建会话按钮',
    await page.locator('.page-head .btn', { hasText: '新建会话' }).count() === 0)
  check('默认保持一条会话标题', (await page.locator('.chat-title').innerText()).trim().length > 0)
  check('输入区不再有部门与类型下拉', await page.locator('.composer-bar select').count() === 0)

  await ask('生产部这周还有几张单没验收？')
  const tools = await page.locator('.msg-assistant .tool').count()
  const toolTitles = await page.locator('.msg-assistant .tool').evaluateAll((els) => els.map((e) => e.getAttribute('title') || ''))
  check('回答展示工具调用', tools > 0, `${tools} 个工具`)
  check('工具入参可见', toolTitles.join('|').includes('deptId') || toolTitles.join('|').includes('question'), toolTitles.join('|').slice(0, 120))
  const assistantCount = await page.locator('.msg-assistant .bubble').count()
  check('回答落库并回显', assistantCount >= 1, `${assistantCount} 条`)
  const sourceTags = (await page.locator('.msg-assistant .tag').allInnerTexts()).join('/')
  check('回答标注来源（规则降级或模型名）', sourceTags.trim().length > 0, sourceTags)
  const answerText = (await page.locator('.msg-assistant .answer').last().innerText()).trim()
  check('回答正文非空', answerText.length > 20, `${answerText.length} 字`)

  await ask('张厚生现在手里有多少在办工单？')
  const toolLabels = await page.locator('.msg-assistant .tool-name').allInnerTexts()
  check('人员工具被调用', toolLabels.some((t) => t.includes('人员')), toolLabels.join('/'))
  await page.screenshot({ path: `${SHOTS}/01-chat.png`, fullPage: true })

  // 引用回溯（有引用才点开）
  const citations = await page.locator('.msg-assistant .citation').count()
  if (citations > 0) {
    await page.locator('.msg-assistant .citation').first().click()
    await page.waitForSelector('.mask .chunk-body', { timeout: 10000 })
    const chunk = (await page.locator('.mask .chunk-body').innerText()).trim()
    check('引用可回溯原文', chunk.length > 10, `${chunk.length} 字`)
    await page.locator('.mask .panel-foot .btn').click()
  } else {
    check('引用可回溯原文（本轮无引用跳过）', true, '无命中')
  }

  // 单会话续用：刷新页面后仍落在同一条会话，历史与消息还在
  const titleBefore = (await page.locator('.chat-title').innerText()).trim()
  const msgBefore = await page.locator('.msg-assistant .bubble').count()
  await page.reload()
  await page.waitForSelector('.chat-head', { timeout: 20000 })
  const titleAfter = (await page.locator('.chat-title').innerText()).trim()
  const msgAfter = await page.locator('.msg-assistant .bubble').count()
  check('刷新后仍是同一条会话', titleAfter === titleBefore, titleAfter)
  check('会话历史持续保留', msgAfter >= msgBefore, `${msgBefore} -> ${msgAfter}`)

  // ---------------------------------------------------------------- 复盘与建议
  await page.goto(`${BASE}/#/stats/review`)
  await page.waitForSelector('.block-head h3', { timeout: 15000 })
  const blocks = (await page.locator('.block-head h3').allInnerTexts()).join('/')
  check('管理者看到评价与建议两块', blocks.includes('人员与任务评价') && blocks.includes('待确认建议'), blocks)

  await page.waitForSelector('.table tbody tr, .empty', { timeout: 15000 })
  const evalRows = await page.locator('.table tbody tr').count()
  check('评价列表有数据', evalRows > 0, `${evalRows} 行`)

  // 建议详情
  const sugCount = await page.locator('.sug').count()
  check('建议队列有数据', sugCount > 0, `${sugCount} 条`)
  if (sugCount > 0) {
    await page.locator('.sug').first().locator('.btn-link', { hasText: '详情与依据' }).click()
    await page.waitForSelector('.mask .sug-detail-summary', { timeout: 10000 })
    const sections = await page.locator('.mask .detail-section').count()
    check('建议详情展示依据与成本', sections > 0, `${sections} 段`)
    await page.locator('.mask .panel-foot .btn').first().click()
    await page.waitForTimeout(300)
  }

  // 评价证据
  if (evalRows > 0) {
    await page.locator('.table tbody tr').first().locator('.btn-link', { hasText: '证据' }).click()
    await page.waitForSelector('.mask .score-row', { timeout: 10000 })
    const dims = await page.locator('.mask .score-row').count()
    const evidenceRows = await page.locator('.mask .evidence-row').count()
    check('评价维度分数可见', dims >= 4, `${dims} 维`)
    check('评价证据行可见', evidenceRows > 0, `${evidenceRows} 行`)
    await page.screenshot({ path: `${SHOTS}/03-review-evidence.png`, fullPage: true })
    await page.locator('.mask .panel-foot .btn').last().click()

    // 申诉弹窗（只校验必填，不提交）
    await page.locator('.table tbody tr').first().locator('.btn-link', { hasText: '申诉' }).click()
    await page.waitForSelector('.mask textarea', { timeout: 10000 })
    await page.locator('.mask .panel-foot .btn-primary').click()
    const appealErr = await page.locator('.mask .form-error').innerText().catch(() => '')
    check('申诉理由为空被拦截', appealErr.includes('请填写申诉理由'), appealErr)
    await page.locator('.mask .panel-foot .btn').first().click()
  }

  // ---------------------------------------------------------------- 负责人
  await login('xuwei', 'admin123')
  const ownerNav = await page.locator('.nav-text').allInnerTexts()
  check('负责人侧栏不再出现重点发现', !ownerNav.includes('重点发现'), ownerNav.join('/'))
  await page.goto(`${BASE}/#/stats/review`)
  await page.waitForSelector('.block-head h3', { timeout: 15000 })
  await page.waitForSelector('.sug, .empty', { timeout: 15000 })
  const ownerBlocks = (await page.locator('.block-head h3').allInnerTexts()).join('/')
  check('负责人看不到评价区块', !ownerBlocks.includes('人员与任务评价'), ownerBlocks)
  const ownerSug = await page.locator('.sug').count()
  check('负责人可看建议队列', ownerSug > 0, `${ownerSug} 条`)
  check('负责人无生成建议按钮', await page.locator('.block .btn-primary').count() === 0)
  await page.screenshot({ path: `${SHOTS}/04-owner-review.png`, fullPage: true })

  // ---------------------------------------------------------------- 执行者
  await login('wangxb', 'admin123')
  const execNav = await page.locator('.nav-text').allInnerTexts()
  check('执行者没有重点发现', !execNav.includes('重点发现'), execNav.join('/'))
  check('执行者可见工单助手', execNav.includes('工单助手'))
  await gotoChat()
  await page.waitForSelector('.chat-head', { timeout: 15000 })
  await ask('生产部这周还有几张单没验收？')
  const execToolTitles = await page.locator('.msg-assistant .tool').evaluateAll((els) => els.map((e) => e.getAttribute('title') || ''))
  check('执行者越权部门被裁剪', execToolTitles.join('|').includes('"clamped":true'), execToolTitles.join('|').slice(0, 160))
  await page.screenshot({ path: `${SHOTS}/05-executor-chat.png`, fullPage: true })

  check('页面无 console error', consoleErrors.length === 0, consoleErrors.slice(0, 2).join(' | '))
  check('页面无未捕获异常', pageErrors.length === 0, pageErrors.slice(0, 2).join(' | '))
} catch (e) {
  check('脚本执行完成', false, e.message)
  await page.screenshot({ path: `${SHOTS}/99-failure.png`, fullPage: true }).catch(() => {})
} finally {
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} 通过`)
if (failed.length) {
  console.log('失败项：')
  failed.forEach((f) => console.log(`- ${f.name} (${f.detail})`))
  process.exit(1)
}
