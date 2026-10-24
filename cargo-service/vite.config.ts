import { defineConfig } from 'vite';

export default defineConfig({
    base: './',
    publicDir: false,
    build: {
        outDir: 'build/generated/javascript',
        emptyOutDir: true,
        target: 'es2022',
        rollupOptions: {
            input: {
                'flights-to-load': 'src/main/typescript/flights-to-load.ts',
                'cargo-plan': 'src/main/typescript/cargo-plan.ts',
            },
            output: {
                entryFileNames: '[name].js',
                chunkFileNames: '[name]-[hash].js',
            },
        },
    },
});
