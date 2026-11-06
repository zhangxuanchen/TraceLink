// 新增页面的浏览器端到端：工单中心（列表 + 导出）、重点发现、人员管理（含角色）、模型配置。
// 前置：后端 8090 与前端 dev server 5180 已启动（web 目录 npm run dev），数据库有种子数据。
// 运行：node scripts/e2e-pages.mjs
//   依赖 playwright-core 与 Chromium 缓存，按需用环境变量指定：
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core
//   CHROME_PATH=/path/to/Chromium.app/Contents/MacOS/Chromium
//   BASE=http://localhost:5180  OUT=/tmp/tracelink-pages
// 副作用：1) 模型配置页用已保存的模型 ID 保存一次（写入 sys_config，值与当前环境变量一致）；
//   2) 触发一次日报异步导出（新增一条 export_task 与 job_task）；3) 给曹长坤临时加执行者角色后复原。
// 清理：mysql -uroot -ptracelink tracelink -e "delete from job_task where job_type='EXPORT' and id>1;
//   delete from export_task where template_code='DAILY' and created_at > now() - interval 1 day;"
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
const SHOTS = process.env.OUT || '/tmp/tracelink-pages'
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
  await page.evaluate(() => localStorage.clear()).catch(() => {})
  await page.goto(`${BASE}/#/login`)
  await page.waitForSelector('#username')
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type=submit]')
  await page.waitForSelector('.shell', { timeout: 20000 })
}

async function logout() {
  await page.locator('.who .btn-link', { hasText: '退出' }).click()
  await page.waitForSelector('#username', { timeout: 15000 })
}

function hash() {
  return page.evaluate(() => window.location.hash)
}

async function goto(path, selector, timeout = 20000) {
  await page.goto(`${BASE}/#${path}`)
  await page.waitForSelector(selector, { timeout })
}

async function apiGet(path) {
  return page.evaluate(async (url) => {
    const token = localStorage.getItem('tracelink.token')
    const response = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    const body = await response.json()
    return body.data
  }, path)
}

try {
  // ---------------------------------------------------------------- 管理员 · 工单中心（页头已精简，只留列表）
  await login('admin', 'admin123')
  check('管理员登录后落在工单中心', (await hash()) === '#/tasks', await hash())
  await page.waitForSelector('.toolbar', { timeout: 20000 })
  check('工单中心不再有顶部口径 KPI 卡',
    await page.locator('.task-kpis').count() === 0 && await page.locator('.kpi-card').count() === 0)
  check('视图切换那一排已删掉',
    await page.locator('.view-switch').count() === 0 && await page.locator('.desk-tabs').count() === 0)
  await page.screenshot({ path: `${SHOTS}/01-tasks.png`, fullPage: true })
  const adminNav = await page.locator('.nav-text').allInnerTexts()
  check('我的工作台已并进工单中心，系统设置只留模型配置',
    ['工单中心', '模型配置'].every((title) => adminNav.includes(title))
    && !adminNav.includes('我的工作台')
    && !adminNav.includes('模板与参数') && !adminNav.includes('审计日志'), adminNav.join('/'))
  check('角色入口已并进人员管理，侧栏不再单列',
    !adminNav.includes('角色与数据范围') && adminNav.includes('人员管理'), adminNav.join('/'))
  check('驾驶舱已下线，侧栏没有入口', !adminNav.includes('驾驶舱'), adminNav.join('/'))
  check('侧栏按组分行且无阶段占位',
    await page.locator('.nav-group').count() >= 2 && await page.locator('.nav-phase').count() === 0)
  check('看板已并入工单中心，不再单列',
    !adminNav.includes('看板') && adminNav.includes('工单中心'), adminNav.join('/'))
  const adminGroups = (await page.locator('.nav-group').allInnerTexts()).map((text) => text.trim())
  check('侧栏一级分组为工单 / 组织 / 系统设置',
    ['工单', '组织', '系统设置'].every((label) => adminGroups.includes(label))
      && !adminGroups.includes('分析与工具'),
    adminGroups.join('/'))
  check('统计看板与知识库并入工单组且紧随工单助手',
    adminNav.indexOf('工单助手') === adminNav.indexOf('工单中心') + 1
      && adminNav.indexOf('统计看板') === adminNav.indexOf('工单助手') + 1
      && adminNav.indexOf('知识库') === adminNav.indexOf('统计看板') + 1, adminNav.join('/'))
  await page.goto(`${BASE}/#/dashboard`)
  await page.waitForSelector('.toolbar', { timeout: 20000 })
  check('旧驾驶舱链接回到工单中心', (await hash()) === '#/tasks', await hash())

  // ---------------------------------------------------------------- 工单中心：列表
  await goto('/tasks', '.toolbar')
  check('页头与上传工作簿入口都已下线',
    (await page.locator('.head-actions button', { hasText: '上传工作簿' }).count()) === 0
    && (await page.locator('.page-title').count()) === 0
    && (await page.locator('.view-switch').count()) === 0)
  check('新建工单在导出面板里紧挨开始导出',
    (await page.locator('.export-panel .btn', { hasText: '新建工单' }).count()) === 1)
  // 逾期快捷条：点击后保持选中，重置后取消
  await page.locator('.count-chip', { hasText: '逾期' }).click()
  await page.waitForFunction(() => {
    const chip = [...document.querySelectorAll('.count-chip')].find((el) => el.innerText.includes('逾期'))
    return chip?.classList.contains('active')
  }, { timeout: 20000 })
  check('逾期快捷条可筛列表',
    (await page.locator('.count-chip.active', { hasText: '逾期' }).count()) === 1)
  await page.locator('.toolbar .btn', { hasText: '重置' }).click()
  await page.waitForFunction(() => {
    const chip = [...document.querySelectorAll('.count-chip')].find((el) => el.innerText.includes('逾期'))
    return chip && !chip.classList.contains('active')
  }, { timeout: 20000 })
  await page.waitForSelector('table.table tbody tr', { timeout: 20000 })
  const adminRows = await page.locator('table.table tbody tr').count()
  check('工单中心默认列表有数据且没有报错条',
    adminRows > 0 && (await page.locator('.form-error').count()) === 0, `${adminRows} 行`)
  await page.screenshot({ path: `${SHOTS}/01b-tasks-list.png`, fullPage: true })

  // 状态 / 类型 / 部门都是下拉多选：勾两个状态后列表按并集过滤
  const statusPick = page.locator('.toolbar .multi-select').first()
  await statusPick.locator('.ms-trigger').click()
  await statusPick.locator('.ms-option', { hasText: '进行中' }).click()
  await statusPick.locator('.ms-option', { hasText: '待接单' }).click()
  await page.mouse.click(10, 10)
  await page.waitForFunction(() => {
    const tags = [...document.querySelectorAll('table.table tbody tr td:nth-child(9) .tag')]
    return tags.length > 0 && tags.every((el) => ['进行中', '待接单'].includes(el.innerText.trim()))
  }, { timeout: 20000 })
  const pickedLabels = await page.locator('table.table tbody tr td:nth-child(9) .tag').allInnerTexts()
  const triggerText = (await statusPick.locator('.ms-trigger').innerText()).trim()
  check('状态支持多选，列表按并集过滤',
    pickedLabels.every((label) => ['进行中', '待接单'].includes(label.trim())), `${pickedLabels.length} 行`)
  check('多选回显选中的项数', triggerText.includes('+1'), triggerText)
  await page.locator('.toolbar .btn', { hasText: '重置' }).click()
  await page.waitForFunction(() => {
    const tags = [...document.querySelectorAll('table.table tbody tr td:nth-child(9) .tag')]
    return tags.length > 0 && !tags.every((el) => ['进行中', '待接单'].includes(el.innerText.trim()))
  }, { timeout: 20000 })
  await page.goto(`${BASE}/#/tasks/board`)
  await page.waitForSelector('.toolbar', { timeout: 20000 })
  check('看板已下线，旧地址回到列表', (await hash()) === '#/tasks', await hash())
  check('看板组件不再渲染', await page.locator('.board-col').count() === 0)

  // ---------------------------------------------------------------- 统计看板：只按部门与个人两个维度看七项率
  await goto('/stats', '.card .table')
  await page.waitForFunction(() => document.querySelectorAll('.card .table tbody tr').length > 0, { timeout: 30000 })
  check('统计看板有部门与个人两张率表', await page.locator('.card .table').count() === 2)
  const rateHeaders = (await page.locator('.card .table').first().locator('thead').innerText()).replace(/\n/g, ' ')
  check('两张表都带七项口径列',
    ['完成率', '按时完成率', '平均完成时长', '逾期率', '一次通过率', '结果留痕率', '复发率']
      .every((label) => rateHeaders.includes(label)), rateHeaders)
  check('统计看板不再画趋势与分布图', await page.locator('.chart-grid, .chart-box, canvas').count() === 0)
  check('统计看板不再有顶部整体 KPI 卡', await page.locator('.kpis').count() === 0)
  // 三项结论固定在统计看板页首
  await page.waitForFunction(
    () => document.querySelectorAll('.finding-grid .finding-card').length === 3, { timeout: 30000 })
  const findingKinds = await page.locator('.finding-card .finding-kind').allInnerTexts()
  const findingTitles = await page.locator('.finding-card .finding-title').allInnerTexts()
  check('统计看板固定给出三项结论',
    findingKinds.join('/') === '时效/达成/质量' && findingTitles.length === 3,
    `${findingKinds.join('/')} · ${findingTitles.join(' | ')}`)
  const calibers = await page.locator('.finding-card .finding-caliber').allInnerTexts()
  check('三项都写明口径', calibers.length === 3 && calibers.every((text) => text.includes('口径')))
  await page.screenshot({ path: `${SHOTS}/05b-stats-findings.png`, fullPage: true })
  check('部门维度带多选条件', await page.locator('.card .multi-select').count() === 1)
  check('表格支持分页', (await page.locator('.card .pager').count()) >= 1)
  const deptRowsBefore = await page.locator('.card .table').first().locator('tbody tr').count()
  await page.locator('.card .ms-trigger').click()
  await page.locator('.card .ms-option input').first().check()
  await page.waitForFunction((before) => {
    const table = document.querySelectorAll('.card .table')[0]
    return table && table.querySelectorAll('tbody tr').length < before
  }, deptRowsBefore, { timeout: 5000 })
  const deptRowsAfter = await page.locator('.card .table').first().locator('tbody tr').count()
  check('部门多选后表格按条件过滤', deptRowsAfter === 1, `${deptRowsBefore} → ${deptRowsAfter}`)
  await page.locator('.card .ms-option input').first().uncheck()
  await page.locator('.ms-trigger').click()
  const personBefore = await page.locator('.card .table').nth(1).locator('tbody tr').count()
  await page.locator('.card .member-search').fill('许')
  await page.waitForFunction((before) => {
    const table = document.querySelectorAll('.card .table')[1]
    const rows = table ? table.querySelectorAll('tbody tr').length : 0
    return rows > 0 && rows < before
  }, personBefore, { timeout: 5000 })
  const personAfter = await page.locator('.card .table').nth(1).locator('tbody tr').count()
  check('个人维度支持姓名模糊匹配', personAfter > 0 && personAfter < personBefore, `${personBefore} → ${personAfter}`)
  await page.locator('.card .member-search').fill('')
  const statsExport = page.locator('.export-panel')
  check('统计看板导出嵌在查询条里', await page.locator('.toolbar .export-panel').count() === 1)
  check('统计看板页头不再有刷新按钮', await page.locator('.page-head .btn').count() === 0)
  check('统计看板导出不再提供模板下拉', await statsExport.locator('select').count() === 0)
  check('统计看板导出只保留开始导出按钮',
    (await statsExport.locator('.panel-form .btn-primary', { hasText: '开始导出' }).count()) === 1)
  check('统计看板导出不再重复日期区间',
    await statsExport.locator('input[type="date"]').count() === 0)
  await page.screenshot({ path: `${SHOTS}/05-stats-rates.png`, fullPage: true })
  const overdueRow = page.locator('.finding-card').first().locator('.finding-list li.clickable').first()
  check('逾期结论按部门聚合且可点', await overdueRow.count() === 1)
  await overdueRow.click()
  await page.waitForFunction(() => /#\/tasks\?/.test(location.hash) && /overdue=1/.test(location.hash), { timeout: 10000 })
  check('点部门逾期行跳到逾期池并带部门条件', /#\/tasks\?.*overdue=1/.test(await hash()), await hash())

  await goto('/stats', '.finding-card')
  check('三项结论不再挂底部链接', await page.locator('.finding-foot, .finding-card .btn-link').count() === 0)
  const qualityRow = page.locator('.finding-card').nth(2).locator('.finding-list li.clickable').first()
  check('质量结论行可点', await qualityRow.count() === 1)
  await qualityRow.click()
  await page.waitForFunction(() => /#\/insights\?/.test(location.hash) && /deptId=/.test(location.hash), { timeout: 10000 })
  check('点质量部门行跳到完成地图并带部门', /#\/insights\?.*deptId=/.test(await hash()), await hash())

  // ---------------------------------------------------------------- 工单中心：导出（日报即工单集合）
  await goto('/reports', '.toolbar')
  check('旧链接 /reports 回到工单中心', (await hash()).includes('#/tasks'), await hash())
  // 导出控件并进筛选条：不再单独占一张「导出工单与日报」卡片（DESIGN 9.2）
  check('导出不再单独占一张卡片',
    (await page.locator('.export-panel.card').count()) === 0
    && (await page.locator('.export-panel .panel-head').count()) === 0)
  check('导出控件嵌在筛选条里', await page.locator('.toolbar .export-panel').count() === 1)
  const exportCodes = await page.locator('.export-panel select option').allInnerTexts()
  check('导出面板含工单台账与日报回填两个模板',
    exportCodes.length === 2 && exportCodes.join('/').includes('工单台账') && exportCodes.join('/').includes('日报回填'), exportCodes.join('/'))
  await page.locator('.export-panel select').selectOption('DAILY')
  check('导出面板不再有综合筛选行', (await page.locator('.export-panel .filter-line').count()) === 0)
  await page.locator('.export-panel .panel-form .btn-primary', { hasText: '开始导出' }).click()
  await page.waitForFunction(() => {
    const el = document.querySelector('.export-panel .job-line')
    return el && el.innerText.includes('已完成')
  }, { timeout: 90000 })
  const jobText = (await page.locator('.export-panel .job-line').innerText()).replace(/\n/g, ' ')
  check('异步导出任务到达已完成', jobText.includes('已完成') && jobText.includes('100%'), jobText.slice(0, 120))
  await page.waitForSelector('.export-panel .job-line .btn-link', { timeout: 30000 })
  check('导出完成给下载入口', await page.locator('.export-panel .job-line .btn-link', { hasText: '下载导出件' }).count() === 1)
  await page.screenshot({ path: `${SHOTS}/03-export.png`, fullPage: true })

  // ---------------------------------------------------------------- 重点发现：三项结论已移到统计看板
  await goto('/insights', '.insight-body')
  check('重点发现页不再重复三项结论', await page.locator('.finding-grid .finding-card').count() === 0)
  check('重点发现与完成地图同页可下钻', await page.locator('.insight-body').count() === 1)

  // ---------------------------------------------------------------- 模型配置（火山方舟 AK + 模型 ID）
  await goto('/settings/model', '.ai-form')
  const modelId = (await page.locator('.ai-model').inputValue()).trim()
  const keyMask = (await page.locator('.ai-mask').innerText()).replace(/\s+/g, ' ').trim()
  check('模型配置展示火山 AK 与模型 ID',
    modelId.length > 0 && keyMask.includes('****')
    && (await page.locator('.ai-state').innerText()).trim() === '已配置', `${modelId} · ${keyMask}`)
  check('已配置时自检按钮可用', await page.locator('.ai-test').isEnabled())
  await page.locator('.ai-save').click()
  await page.waitForSelector('.ai-notice', { timeout: 15000 })
  const savedSource = (await page.locator('.ai-source').innerText()).trim()
  check('保存后即时回显且来源改为页面配置',
    savedSource.includes('页面配置') && (await page.locator('.ai-state').innerText()).includes('已配置'), savedSource)
  await page.screenshot({ path: `${SHOTS}/04-settings-model.png`, fullPage: true })
  await page.goto(`${BASE}/#/settings/templates`)
  await page.waitForSelector('.ai-form', { timeout: 20000 })
  const templatesHash = await hash()
  await page.goto(`${BASE}/#/settings/audit`)
  await page.waitForSelector('.ai-form', { timeout: 20000 })
  const auditHash = await hash()
  check('模板与参数和审计日志的旧链接回到模型配置',
    templatesHash === '#/settings/model' && auditHash === '#/settings/model', `${templatesHash} · ${auditHash}`)

  // ---------------------------------------------------------------- 人员管理：角色改在这里
  await goto('/employees', '.role-cell')
  const firstRow = page.locator('table.table tbody tr').first()
  const roleLabels = (await firstRow.locator('.role-check').allInnerTexts()).join('/')
  check('人员管理的角色列列出四种角色', await firstRow.locator('.role-check').count() === 4
    && ['系统管理员', '管理者', '负责人', '执行者'].every((name) => roleLabels.includes(name)), roleLabels)
  const caoRow = page.locator('table.table tbody tr').filter({
    has: page.locator('td:nth-child(2) div', { hasText: /^曹长坤$/ })
  }).first()
  check('未建账号人员在列表里标注', (await caoRow.innerText()).includes('未建账号'))
  const execCheckbox = caoRow.locator('.role-check', { hasText: '执行者' }).locator('input')
  await execCheckbox.check()
  await caoRow.locator('.role-save').click()
  await page.waitForTimeout(800)
  const rolesAfter = await apiGet('/api/v1/accounts')
  const caoRoles = (rolesAfter.find((a) => a.employeeId === 4)?.roleCodes) || []
  check('角色改动落库到 user_role', caoRoles.includes('EXECUTOR'), caoRoles.join('/'))
  await execCheckbox.uncheck()
  await caoRow.locator('.role-save').click()
  await page.waitForTimeout(800)
  const rolesReverted = await apiGet('/api/v1/accounts')
  const caoReverted = (rolesReverted.find((a) => a.employeeId === 4)?.roleCodes) || []
  check('角色改动可复原', caoReverted.length === 0, caoReverted.join('/') || '空')
  await page.screenshot({ path: `${SHOTS}/05-employees-roles.png`, fullPage: true })

  // ---------------------------------------------------------------- 负责人 · 工单中心
  await logout()
  await login('xuwei', 'admin123')
  check('负责人登录后落在工单中心', (await hash()) === '#/tasks', await hash())
  await page.waitForSelector('.toolbar', { timeout: 30000 })
  await page.waitForFunction(() => document.querySelectorAll('table.table tbody tr').length > 0
    || document.querySelector('.card .empty'), { timeout: 30000 })
  const ownerRows = await page.locator('table.table tbody tr').count()
  const ownerEmpty = await page.locator('.card .empty').count()
  check('负责人可直接看列表', ownerRows > 0 || ownerEmpty > 0, `${ownerRows} 行 · 空态 ${ownerEmpty}`)
  const ownerNav = await page.locator('.nav-text').allInnerTexts()
  check('负责人可见工单中心与统计看板', ownerNav.includes('工单中心') && ownerNav.includes('统计看板'), ownerNav.join('/'))
  check('负责人侧栏没有重点发现入口', !ownerNav.includes('重点发现'), ownerNav.join('/'))
  check('负责人不再看到独立的日报中心', !ownerNav.includes('日报中心'), ownerNav.join('/'))
  check('负责人看不到驾驶舱', !ownerNav.includes('驾驶舱'), ownerNav.join('/'))
  await page.goto(`${BASE}/#/desk`)
  await page.waitForSelector('.toolbar', { timeout: 30000 })
  check('旧工作台链接回到工单中心', (await hash()) === '#/tasks', await hash())
  await page.screenshot({ path: `${SHOTS}/07-owner-tasks.png`, fullPage: true })

  // ---------------------------------------------------------------- 执行者 · 工单中心
  await logout()
  await login('wangxb', 'admin123')
  check('执行者登录后落在工单中心', (await hash()) === '#/tasks', await hash())
  await page.waitForSelector('.toolbar', { timeout: 30000 })
  const execNav = await page.locator('.nav-text').allInnerTexts()
  check('执行者看不到驾驶舱、重点发现与日报中心',
    !execNav.includes('驾驶舱') && !execNav.includes('重点发现') && !execNav.includes('日报中心'), execNav.join('/'))
  check('执行者看不到设置与复盘建议入口',
    !execNav.includes('模型配置') && !execNav.includes('模板与参数') && !execNav.includes('审计日志')
    && !execNav.includes('复盘与建议') && !execNav.includes('导入向导'), execNav.join('/'))
  check('执行者保留工单助手与知识库',
    execNav.includes('工单助手') && execNav.includes('知识库'), execNav.join('/'))
  check('执行者侧栏首组是工单中心与工单助手',
    execNav[0] === '工单中心' && execNav[1] === '工单助手', execNav.join('/'))
  check('执行者侧栏没有我的工作台', !execNav.includes('我的工作台'), execNav.join('/'))
  await page.goto(`${BASE}/#/tasks`)
  await page.waitForSelector('.toolbar', { timeout: 20000 })
  check('执行者在工单中心看不到口径 KPI',
    await page.locator('.task-kpis').count() === 0 && await page.locator('.kpi-card').count() === 0)
  check('执行者也没有视图切换那一排', await page.locator('.view-switch').count() === 0)
  check('执行者保留新建工单入口',
    await page.locator('.create-row .btn', { hasText: '新建工单' }).count() === 1)
  await page.goto(`${BASE}/#/employees`)
  await page.waitForSelector('table.table tbody tr', { timeout: 20000 })
  check('执行者在人员管理看不到角色勾选',
    await page.locator('.role-cell').count() === 0 && await page.locator('.role-check').count() === 0)
  await page.goto(`${BASE}/#/settings/model`)
  await page.waitForTimeout(1200)
  check('执行者访问设置页被挡回工单中心', (await hash()) === '#/tasks', await hash())
  await page.screenshot({ path: `${SHOTS}/08-executor-tasks.png`, fullPage: true })
} catch (e) {
  check('脚本执行未中断', false, String(e).split('\n')[0])
  await page.screenshot({ path: `${SHOTS}/99-failure.png`, fullPage: true }).catch(() => {})
} finally {
  // 预期内的校验失败会造成一条 422 与一次失败请求，单独剔除后再断言无异常
  const expected = (text) => text.includes('/api/v1/gate-rules/') || text.includes('422')
  check('无浏览器运行时异常', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '))
  check('无预期外的接口 4xx/5xx', httpErrors.filter((e) => !expected(e)).length === 0,
    httpErrors.filter((e) => !expected(e)).slice(0, 5).join(' | '))
  check('无预期外的控制台错误', consoleErrors.filter((e) => !expected(e)).length === 0,
    consoleErrors.filter((e) => !expected(e)).slice(0, 3).join(' | '))
  await browser.close()
}

const failed = results.filter((r) => !r.ok)
console.log(`\n通过 ${results.length - failed.length} 项，失败 ${failed.length} 项；截图在 ${SHOTS}`)
process.exit(failed.length ? 1 : 0)
