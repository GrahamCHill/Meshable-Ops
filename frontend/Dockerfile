# ─── Shared dependencies ─────────────────────────────────────────────────────
FROM node:22-alpine AS dependencies
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --silent

# ─── Local development ───────────────────────────────────────────────────────
FROM dependencies AS development
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--strictPort"]

# ─── Build stage ─────────────────────────────────────────────────────────────
FROM dependencies AS build
COPY . .
RUN npm run build

# ─── Shared proxy ────────────────────────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine AS proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx.workspace-headers.conf /etc/nginx/snippets/workspace-headers.conf
COPY nginx.frontend.conf /etc/nginx/snippets/frontend.conf

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]

# ─── Production runtime ───────────────────────────────────────────────────────
FROM proxy AS production
COPY --from=build /app/dist /usr/share/nginx/html
