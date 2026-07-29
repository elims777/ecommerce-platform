FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Дефолты брендинга: frontend/.env не в git, на CI его нет — без этих значений
# Vite оставит буквальные %VITE_*% в index.html. Локальный .env имеет приоритет.
ARG VITE_APP_TITLE="РФснаб — комплексное снабжение"
ARG VITE_FAVICON=/favicon.png
ARG VITE_APPLE_TOUCH_ICON=/apple-touch-icon.png
ARG VITE_YM_ID=55086370
ENV VITE_APP_TITLE=$VITE_APP_TITLE \
    VITE_FAVICON=$VITE_FAVICON \
    VITE_APPLE_TOUCH_ICON=$VITE_APPLE_TOUCH_ICON \
    VITE_YM_ID=$VITE_YM_ID

RUN npm run build

FROM nginx:stable-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
