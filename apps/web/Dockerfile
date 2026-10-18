# syntax=docker/dockerfile:1

# Las variables VITE_* se incrustan al compilar: hay que pasarlas como build args
# y deben ser las URLs que ve el NAVEGADOR (no los hosts internos de Docker).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

ARG VITE_IDENTITY_API_URL=http://localhost:8081/api/v1
ARG VITE_SYNC_WS_URL=ws://localhost:8083/ws/sync
ARG VITE_SOCIAL_WS_URL=ws://localhost:8082/ws/social
ARG VITE_ANALYTICS_API_URL=http://localhost:8084/api/v1
ARG VITE_SYNC_ENCODING=json
ARG VITE_DEMO_VIDEO_URL=
ARG VITE_FIREBASE_API_KEY=
ARG VITE_FIREBASE_AUTH_DOMAIN=
ARG VITE_FIREBASE_PROJECT_ID=
ENV VITE_IDENTITY_API_URL=$VITE_IDENTITY_API_URL \
    VITE_SYNC_WS_URL=$VITE_SYNC_WS_URL \
    VITE_SOCIAL_WS_URL=$VITE_SOCIAL_WS_URL \
    VITE_ANALYTICS_API_URL=$VITE_ANALYTICS_API_URL \
    VITE_SYNC_ENCODING=$VITE_SYNC_ENCODING \
    VITE_DEMO_VIDEO_URL=$VITE_DEMO_VIDEO_URL \
    VITE_FIREBASE_API_KEY=$VITE_FIREBASE_API_KEY \
    VITE_FIREBASE_AUTH_DOMAIN=$VITE_FIREBASE_AUTH_DOMAIN \
    VITE_FIREBASE_PROJECT_ID=$VITE_FIREBASE_PROJECT_ID
RUN npm run build

# Sirve dist/ como estático con fallback a index.html (react-router).
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 5173
