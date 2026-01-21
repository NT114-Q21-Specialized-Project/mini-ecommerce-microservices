# Sử dụng Node.js làm base image
FROM node:20-alpine

WORKDIR /app

# Copy package files và cài đặt
COPY package*.json ./
RUN npm install

# Copy toàn bộ code
COPY . .

# Expose port và chạy Vite với mode --host để truy cập được từ bên ngoài container
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]