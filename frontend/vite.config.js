import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': '/src'   // 必须保留，否则 @/views 找不到
        }
    },
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
                // 不需要 rewrite，直接转发所有 /api 请求
            }
        }
    }
})