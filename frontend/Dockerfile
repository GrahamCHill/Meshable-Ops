# ─── Build stage ─────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --silent

COPY . .
RUN npm run build

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx.workspace-headers.conf /etc/nginx/snippets/workspace-headers.conf

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
