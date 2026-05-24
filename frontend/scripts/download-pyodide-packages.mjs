/**
 * 下载 Pyodide 常用包到 public/pyodide/ 目录
 * 使得沙箱初始化时可从本地加载，不依赖网络
 *
 * 用法: node scripts/download-pyodide-packages.mjs
 */

import { writeFile, mkdir, readFile } from 'fs/promises'
import { existsSync } from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const PYODIDE_DIR = path.join(__dirname, '..', 'src', 'renderer', 'public', 'pyodide')

const CDN_BASE = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/'
const PYPI_BASE = 'https://pypi.tuna.tsinghua.edu.cn/simple/'

// Pyodide CDN 上的包（WASM 构建）
const CDN_PACKAGES = {
  // numpy
  'numpy': 'numpy-1.26.4-cp312-cp312-pyodide_2024_0_wasm32.whl',
  // pandas + 依赖
  'pandas': 'pandas-2.2.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'python-dateutil': 'python_dateutil-2.9.0.post0-py2.py3-none-any.whl',
  'pytz': 'pytz-2024.1-py2.py3-none-any.whl',
  'six': 'six-1.16.0-py2.py3-none-any.whl',
  // matplotlib + 依赖
  'matplotlib': 'matplotlib-3.5.2-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'pillow': 'pillow-10.2.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'kiwisolver': 'kiwisolver-1.4.5-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'cycler': 'cycler-0.12.1-py3-none-any.whl',
  'fonttools': 'fonttools-4.51.0-py3-none-any.whl',
  'pyparsing': 'pyparsing-3.1.2-py3-none-any.whl',
  'matplotlib-pyodide': 'matplotlib_pyodide-0.2.2-py3-none-any.whl',
  // scipy / statsmodels
  'scipy': 'scipy-1.12.0-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'statsmodels': 'statsmodels-0.14.2-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'patsy': 'patsy-0.5.6-py2.py3-none-any.whl',
  // 机器学习
  'scikit-learn': 'scikit_learn-1.4.2-cp312-cp312-pyodide_2024_0_wasm32.whl',
  // 数学 / 符号计算
  'sympy': 'sympy-1.12-py3-none-any.whl',
  'mpmath': 'mpmath-1.3.0-py3-none-any.whl',
  // 图论
  'networkx': 'networkx-3.3-py3-none-any.whl',
  // Excel / 数据解析
  'xlrd': 'xlrd-2.0.1-py2.py3-none-any.whl',
  'lxml': 'lxml-5.2.1-cp312-cp312-pyodide_2024_0_wasm32.whl',
  // HTML/XML 解析
  'beautifulsoup4': 'beautifulsoup4-4.12.3-py3-none-any.whl',
  'soupsieve': 'soupsieve-2.5-py3-none-any.whl',
  'html5lib': 'html5lib-1.1-py2.py3-none-any.whl',
  'cssselect': 'cssselect-1.2.0-py2.py3-none-any.whl',
  // 数据库
  'sqlalchemy': 'SQLAlchemy-2.0.29-cp312-cp312-pyodide_2024_0_wasm32.whl',
  // JSON / schema
  'jsonschema': 'jsonschema-4.21.1-py3-none-any.whl',
  'attrs': 'attrs-23.2.0-py3-none-any.whl',
  // 工具库
  'decorator': 'decorator-5.1.1-py3-none-any.whl',
  'more-itertools': 'more_itertools-10.2.0-py3-none-any.whl',
  'toolz': 'toolz-0.12.1-py3-none-any.whl',
  // 其他常用
  'regex': 'regex-2024.4.16-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'pyyaml': 'PyYAML-6.0.1-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'jinja2': 'Jinja2-3.1.3-py3-none-any.whl',
  'markupsafe': 'MarkupSafe-2.1.5-cp312-cp312-pyodide_2024_0_wasm32.whl',
  'typing-extensions': 'typing_extensions-4.11.0-py3-none-any.whl',
}

// 纯 Python 包（从 PyPI 下载）
const PYPI_PACKAGES = {
  'openpyxl': 'openpyxl-3.1.5-py2.py3-none-any.whl',
  'et-xmlfile': 'et_xmlfile-2.0.0-py3-none-any.whl',
  'pypdf': 'pypdf-5.1.0-py3-none-any.whl',
  'chardet': 'chardet-5.2.0-py3-none-any.whl',
  'tabulate': 'tabulate-0.9.0-py3-none-any.whl',
  'python-docx': 'python_docx-1.1.2-py3-none-any.whl',
  'xlsxwriter': 'XlsxWriter-3.2.0-py3-none-any.whl',
}

// PLACEHOLDER_DOWNLOAD

async function main() {
  if (!existsSync(PYODIDE_DIR)) {
    await mkdir(PYODIDE_DIR, { recursive: true })
  }

  console.log('开始下载 Pyodide 包到:', PYODIDE_DIR)
  console.log('')

  let totalSize = 0
  let success = 0
  let failed = 0

  // 1. 下载 CDN 包
  console.log('── Pyodide CDN 包 ──')
  for (const [name, whl] of Object.entries(CDN_PACKAGES)) {
    const destPath = path.join(PYODIDE_DIR, whl)
    if (existsSync(destPath)) {
      console.log(`  跳过 (已存在): ${whl}`)
      success++
      continue
    }
    try {
      const size = await downloadFile(CDN_BASE + whl, destPath)
      totalSize += size
      success++
    } catch (e) {
      console.error(`  失败: ${name} - ${e.message}`)
      failed++
    }
  }

  // 2. 下载 PyPI 纯 Python 包
  console.log('')
  console.log('── PyPI 纯 Python 包 ──')
  for (const [name, whl] of Object.entries(PYPI_PACKAGES)) {
    const destPath = path.join(PYODIDE_DIR, whl)
    if (existsSync(destPath)) {
      console.log(`  跳过 (已存在): ${whl}`)
      success++
      continue
    }
    try {
      const url = await findPypiWhlUrl(name, whl)
      const size = await downloadFile(url, destPath)
      totalSize += size
      success++
    } catch (e) {
      console.error(`  失败: ${name} - ${e.message}`)
      failed++
    }
  }

  console.log('')
  console.log(`完成: ${success} 个成功, ${failed} 个失败, 共 ${(totalSize / 1024 / 1024).toFixed(1)} MB`)

  // 3. 更新 lock 文件
  if (failed === 0 || process.argv.includes('--update-lock')) {
    await updateLockFile()
  }
}

async function downloadFile(url, destPath) {
  console.log(`  下载: ${path.basename(destPath)}`)
  const response = await fetch(url, { redirect: 'follow' })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${url}`)
  }
  const buffer = await response.arrayBuffer()
  await writeFile(destPath, Buffer.from(buffer))
  return buffer.byteLength
}

/** 从 PyPI simple API 查找 whl 下载链接 */
async function findPypiWhlUrl(name, targetWhl) {
  // 尝试清华镜像
  const normalizedName = name.replace(/-/g, '-')
  const urls = [
    `https://pypi.tuna.tsinghua.edu.cn/simple/${normalizedName}/`,
    `https://pypi.org/simple/${normalizedName}/`
  ]

  for (const indexUrl of urls) {
    try {
      const response = await fetch(indexUrl, {
        headers: { 'Accept': 'text/html' }
      })
      if (!response.ok) continue
      const html = await response.text()

      // 从 HTML 中找到目标 whl 的链接
      const regex = new RegExp(`href="([^"]*${targetWhl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[^"]*)"`, 'i')
      const match = html.match(regex)
      if (match) {
        let href = match[1]
        // 处理相对路径和 hash
        if (href.includes('#')) href = href.split('#')[0]
        if (!href.startsWith('http')) {
          href = new URL(href, indexUrl).href
        }
        return href
      }

      // 如果精确匹配不到，找同名不同版本的 none-any whl
      const looseRegex = new RegExp(`href="([^"]*${name.replace(/-/g, '[_-]')}[^"]*py3-none-any\\.whl[^"]*)"`, 'i')
      const looseMatch = html.match(looseRegex)
      if (looseMatch) {
        let href = looseMatch[1]
        if (href.includes('#')) href = href.split('#')[0]
        if (!href.startsWith('http')) {
          href = new URL(href, indexUrl).href
        }
        return href
      }
    } catch { /* try next */ }
  }

  throw new Error(`在 PyPI 上找不到: ${targetWhl}`)
}

async function updateLockFile() {
  const lockPath = path.join(PYODIDE_DIR, 'pyodide-lock.json')
  const lockContent = JSON.parse(await readFile(lockPath, 'utf-8'))

  // 从 CDN 获取完整 lock 文件来获取包的元信息
  console.log('')
  console.log('从 CDN 获取包元信息并更新 pyodide-lock.json...')
  const response = await fetch(CDN_BASE + 'pyodide-lock.json')
  const fullLock = await response.json()

  // 添加 CDN 包到 lock 文件
  for (const [name, whl] of Object.entries(CDN_PACKAGES)) {
    if (fullLock.packages[name]) {
      lockContent.packages[name] = fullLock.packages[name]
    }
  }

  // 添加 PyPI 包到 lock 文件（手动构造元信息）
  for (const [name, whl] of Object.entries(PYPI_PACKAGES)) {
    lockContent.packages[name] = {
      name,
      version: whl.match(/-([\d.]+)/)?.[1] || '0.0.0',
      file_name: whl,
      install_dir: 'site',
      depends: [],
      imports: [name.replace(/-/g, '_')]
    }
  }

  await writeFile(lockPath, JSON.stringify(lockContent, null, 2))
  console.log('pyodide-lock.json 已更新')
}

main().catch(e => {
  console.error('脚本执行失败:', e)
  process.exit(1)
})
