# syntax=docker/dockerfile:1

# Dependencias completas (dev incluidas) para compilar y migrar.
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# Compila src/ a dist/ con tsup.
FROM deps AS build
COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

# Migraciones de Drizzle: `docker compose run migrate` (drizzle-kit es devDependency).
FROM deps AS migrate
COPY drizzle.config.ts ./
COPY src/db/schema.ts ./src/db/schema.ts
COPY migrations/ ./migrations/
USER node
CMD ["npx", "drizzle-kit", "migrate"]

# Imagen final: solo dependencias de producción y dist/.
FROM node:22-alpine AS runtime
ENV NODE_ENV=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
USER node
EXPOSE 8081
CMD ["node", "dist/server.js"]
