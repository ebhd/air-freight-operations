import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectDirectory = dirname(fileURLToPath(import.meta.url));
const cargoApiUrl = process.env.CARGO_API_URL ?? 'http://localhost:8081';

export default defineConfig({
    plugins: [tailwindcss()],
    base: './',
    publicDir: false,
    server: {
        port: 5174,
        strictPort: true,
        proxy: {
            '/api': {
                target: cargoApiUrl,
                changeOrigin: true,
            },
        },
    },
    preview: {
        port: 5174,
        strictPort: true,
    },
    build: {
        outDir: 'build/dist',
        emptyOutDir: true,
        target: 'es2022',
        cssCodeSplit: false,
        rollupOptions: {
            input: {
                'flights-to-load': resolve(projectDirectory, 'index.html'),
                'cargo-plan': resolve(projectDirectory, 'cargo-plan.html'),
            },
            output: {
                entryFileNames: 'js/[name].js',
                chunkFileNames: 'js/[name]-[hash].js',
                assetFileNames: asset => asset.names.some(name => name.endsWith('.css'))
                    ? 'css/app.css'
                    : 'assets/[name]-[hash][extname]',
            },
        },
    },
});
