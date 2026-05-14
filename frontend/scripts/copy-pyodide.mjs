/**
 * 将 Pyodide 运行时文件从 node_modules 复制到 renderer public 目录
 * .whl 文件从 CDN 下载（node_modules 不包含）
 * 在 dev/build 前自动执行
 */
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')

const src = resolve(root, 'node_modules/pyodide')
const dest = resolve(root, 'src/renderer/public/pyodide')

const CDN_BASE = 'https://cdn.jsdelivr.net/pyodide/v0.26.4/full/'

/** 从 node_modules 复制的文件 */
const localFiles = [
  'pyodide.js',
  'pyodide.mjs',
  'pyodide.asm.js',
  'pyodide.asm.wasm',
  'python_stdlib.zip'
]

/** 需要从 CDN 下载的 .whl 文件 */
const remoteFiles = [
  'micropip-0.6.0-py3-none-any.whl',
  'packaging-23.2-py3-none-any.whl'
]

// 检查是否已存在（避免重复操作）
if (existsSync(resolve(dest, 'pyodide.asm.wasm')) && existsSync(resolve(dest, 'micropip-0.6.0-py3-none-any.whl')) && existsSync(resolve(dest, 'pyodide-lock.json'))) {
  console.log('[pyodide] 资源已就绪，跳过')
  process.exit(0)
}

if (!existsSync(src)) {
  console.error('[pyodide] node_modules/pyodide 不存在，请先运行 npm install')
  process.exit(1)
}

mkdirSync(dest, { recursive: true })

// 复制本地文件
for (const file of localFiles) {
  const srcFile = resolve(src, file)
  const destFile = resolve(dest, file)
  if (existsSync(srcFile)) {
    cpSync(srcFile, destFile)
    console.log(`[pyodide] 复制 ${file}`)
  } else {
    console.warn(`[pyodide] 警告: ${file} 不存在`)
  }
}

// 生成精简版 pyodide-lock.json（仅包含本地托管的 micropip 和 packaging）
const fullLock = JSON.parse(readFileSync(resolve(src, 'pyodide-lock.json'), 'utf-8'))
const minimalLock = {
  info: fullLock.info,
  packages: {
    micropip: fullLock.packages.micropip,
    packaging: fullLock.packages.packaging
  }
}
writeFileSync(resolve(dest, 'pyodide-lock.json'), JSON.stringify(minimalLock, null, 2))
console.log('[pyodide] 生成精简 pyodide-lock.json（仅 micropip + packaging）')

// 下载远程 .whl 文件
for (const file of remoteFiles) {
  const destFile = resolve(dest, file)
  if (existsSync(destFile)) {
    console.log(`[pyodide] ${file} 已存在，跳过下载`)
    continue
  }
  console.log(`[pyodide] 下载 ${file}...`)
  try {
    const response = await fetch(`${CDN_BASE}${file}`)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const buffer = Buffer.from(await response.arrayBuffer())
    writeFileSync(destFile, buffer)
    console.log(`[pyodide] 下载完成 ${file}`)
  } catch (error) {
    console.error(`[pyodide] 下载失败 ${file}: ${error.message}`)
    process.exit(1)
  }
}

console.log('[pyodide] 资源准备完成')
