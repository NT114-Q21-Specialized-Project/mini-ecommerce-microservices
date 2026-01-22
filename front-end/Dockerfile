# ===== Build stage =====
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files trước để tận dụng cache
COPY package*.json ./
RUN npm ci

# Copy source code
COPY . .

# Build frontend (Vite -> dist/)
RUN npm run build

# ===== Runtime stage =====
FROM nginx:alpine

# Copy static files từ builder
COPY --from=builder /app/dist /usr/share/nginx/html

# (Tuỳ chọn) custom nginx config
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
