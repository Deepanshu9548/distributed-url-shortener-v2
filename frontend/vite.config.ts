import path from "path"
import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Short code redirect proxy, match 1-32 chars alphanumeric+dash+underscore (no slash)
      '^/[0-9a-zA-Z_-]{1,32}$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        bypass: (req) => {
          const reservedPaths = ['/login', '/register', '/dashboard', '/links', '/src', '/node_modules', '/@'];
          const url = req.url;
          if (url && reservedPaths.some(p => url.startsWith(p) || url === '/')) {
            return url; // Bypass proxy, let Vite handle it
          }
        }
      }
    }
  }
})
