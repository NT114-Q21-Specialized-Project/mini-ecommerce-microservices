# Sử dụng Node.js làm base image
FROM node:20-alpine

WORKDIR /app

# Chỉ copy package files trước để tối ưu build
COPY package*.json ./

# Xóa bỏ hoàn toàn node_modules cũ (nếu có) và cài mới sạch sẽ
RUN rm -rf node_modules && npm install

# Copy toàn bộ code (trừ những thứ trong .dockerignore)
COPY . .

# Expose port
EXPOSE 5173

# Chạy với host 0.0.0.0 để Docker map được port ra ngoài
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]