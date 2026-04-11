import { resolve } from 'path'
import { defineConfig, externalizeDepsPlugin } from 'electron-vite'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import pkg from './package.json'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    main: {
      plugins: [externalizeDepsPlugin()],
      define: {
        __API_BASE_URL__: JSON.stringify(env.VITE_API_BASE_URL || 'http://localhost:8080')
      }
    },
    preload: {
      plugins: [externalizeDepsPlugin()]
    },
    renderer: {
      define: {
        __APP_VERSION__: JSON.stringify(pkg.version),
        __BUILD_DATE__: JSON.stringify(new Date().toISOString().split('T')[0])
      },
      resolve: {
        alias: {
          '@': resolve('src/renderer/src')
        }
      },
      plugins: [vue(), tailwindcss()]
    }
  }
})
