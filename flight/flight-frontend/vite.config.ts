import { defineConfig } from 'vite';
import tailwindcss from '@tailwindcss/vite';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectDirectory = dirname(fileURLToPath(import.meta.url));
const flightApiUrl = process.env.FLIGHT_API_URL ?? 'http://localhost:8080';

export default defineConfig({
    plugins: [tailwindcss()],
    base: './',
    publicDir: false,
    server: {
        port: 5173,
        strictPort: true,
        proxy: {
            '/api': {
                target: flightApiUrl,
                changeOrigin: true,
            },
        },
    },
    preview: {
        port: 5173,
        strictPort: true,
    },
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2022',
        rollupOptions: {
            input: {
                main: resolve(projectDirectory, 'index.html'),
                details: resolve(projectDirectory, 'html/flight-details.html'),
            },
        },
    },
});
