# Stage 1: build. Compiles the TypeScript with the full dev toolchain,
# none of which the running service needs.
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

# Stage 2: run. Only production dependencies and the compiled dist/.
FROM node:20-alpine
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist

USER node

EXPOSE 3000
CMD ["node", "dist/main.js"]
